import Fastify from 'fastify';
import { timingSafeEqual } from 'node:crypto';
import { createReadStream } from 'node:fs';
import fs from 'node:fs/promises';
import path from 'node:path';
import { z } from 'zod';
import { uuid } from '../shared/snapshot.js';
import { BackupStore, StoreError } from './store.js';

export async function createApp(config: { dataDir: string; writeToken: string; readToken: string; webDir?: string }) {
  if (config.writeToken.length < 32 || config.readToken.length < 32 || config.readToken === config.writeToken) throw new Error('Distinct tokens of at least 32 characters are required');
  const store = new BackupStore(path.resolve(config.dataDir)); await store.initialize();
  const app = Fastify({ logger: false, bodyLimit: 128 * 1024 * 1024, requestTimeout: 120000 });
  function matches(given: string, wanted: string) {
    const a = Buffer.from(given), b = Buffer.from(wanted); return a.length === b.length && timingSafeEqual(a, b);
  }
  let activeWrite: string | null = null;
  app.addHook('onRequest', async (request, reply) => {
    reply.header('Cache-Control', 'no-store').header('X-Content-Type-Options', 'nosniff').header('Referrer-Policy', 'no-referrer');
    if (request.routeOptions.url?.startsWith('/api/') !== true) return;
    const bearer = request.headers.authorization?.match(/^Bearer (\S+)$/)?.[1] ?? '';
    const write = request.method !== 'GET' && request.method !== 'HEAD';
    if (!matches(bearer, config.writeToken) && (write || !matches(bearer, config.readToken))) return reply.code(write && matches(bearer, config.readToken) ? 403 : 401).send({ error: 'AUTH_REQUIRED' });
    if (write) {
      if (activeWrite) return reply.code(409).send({ error: 'UPLOAD_BUSY' });
      activeWrite = request.id;
    }
  });
  app.addHook('onResponse', async request => { if (activeWrite === request.id) activeWrite = null; });
  app.addHook('onRequestAbort', async request => { if (activeWrite === request.id) activeWrite = null; });
  app.addContentTypeParser('application/octet-stream', { parseAs: 'buffer', bodyLimit: 50 * 1024 * 1024 }, (_req, body, done) => done(null, body));
  app.setErrorHandler((error, _request, reply) => {
    if (error instanceof StoreError) return reply.code(error.status).send({ error: error.code });
    if (error instanceof z.ZodError || error instanceof SyntaxError || (error as Error).name === 'Error' && !('code' in (error as object))) return reply.code(422).send({ error: 'INVALID_BACKUP' });
    return reply.code(503).send({ error: 'BACKUP_UNAVAILABLE' });
  });
  app.get('/api/backups/latest', async () => ({ latest: await store.latest() }));
  const prepare = z.object({ snapshotJson: z.string().max(64 * 1024 * 1024), parentSnapshotId: uuid.nullable() }).strict();
  app.post('/api/backups/prepare', async request => { const body = prepare.parse(request.body); if (Buffer.byteLength(body.snapshotJson) > 64 * 1024 * 1024) throw new StoreError(413, 'MANIFEST_TOO_LARGE'); return store.prepare(body.snapshotJson, body.parentSnapshotId); });
  app.put('/api/backups/:id/photos/:hash', async request => {
    const { id, hash } = z.object({ id: uuid, hash: z.string().regex(/^[a-f0-9]{64}$/) }).parse(request.params);
    if (!Buffer.isBuffer(request.body)) throw new StoreError(415, 'BINARY_PHOTO_REQUIRED');
    await store.putPhoto(id, hash, request.body); return { uploaded: true };
  });
  app.post('/api/backups/:id/commit', async request => {
    const { id } = z.object({ id: uuid }).parse(request.params);
    const { parentSnapshotId } = z.object({ parentSnapshotId: uuid.nullable() }).strict().parse(request.body);
    return { committed: await store.commit(id, parentSnapshotId), latest: await store.latest() };
  });
  app.get('/api/backups/:id', async request => { const { id } = z.object({ id: uuid }).parse(request.params); return store.snapshot(id); });
  app.get('/api/backups/:id/photos/:hash', async (request, reply) => {
    const { id, hash } = z.object({ id: uuid, hash: z.string().regex(/^[a-f0-9]{64}$/) }).parse(request.params);
    const photo = await store.photo(id, hash); return reply.type(photo.mime).send(createReadStream(photo.file));
  });
  app.get('/health', async () => ({ status: 'ok' }));
  if (config.webDir) {
    const root = path.resolve(config.webDir);
    app.get('/*', async (request, reply) => {
      const pathname = new URL(request.url, 'http://localhost').pathname;
      if (pathname.startsWith('/api/')) return reply.code(404).send({ error: 'NOT_FOUND' });
      const relative = pathname === '/' ? 'index.html' : pathname.slice(1);
      if (!(relative === 'index.html' || /^assets\/[a-zA-Z0-9._-]+\.(js|css)$/.test(relative))) return reply.code(404).send('Not found');
      const file = path.join(root, relative);
      try { await fs.access(file); } catch { return reply.code(404).send('Not found'); }
      reply.header('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' blob:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
      return reply.type(relative.endsWith('.js') ? 'application/javascript' : relative.endsWith('.css') ? 'text/css' : 'text/html').send(createReadStream(file));
    });
  }
  return app;
}
