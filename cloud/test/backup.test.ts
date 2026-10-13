import { test, mock } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { randomUUID, createHash } from 'node:crypto';
import bmp from 'bmp-js';
import { createApp } from '../server/app.js';
import { strictJson, validateSnapshot } from '../shared/snapshot.js';
import { weekStats } from '../shared/statistics.js';
const fixture = await fs.readFile(new URL('../../shared/fixtures/snapshot-v1.json', import.meta.url), 'utf8');
const expected = JSON.parse(await fs.readFile(new URL('../../shared/fixtures/expected-v1.json', import.meta.url), 'utf8'));
const write = 'W'.repeat(43), read = 'R'.repeat(43);
const headers = { authorization: `Bearer ${write}` };

test('actual Android ZIP manifest retains the native compatibility mirror', async () => {
  const text = await fs.readFile(new URL('../../shared/fixtures/native-snapshot-v1.json', import.meta.url), 'utf8');
  const s = validateSnapshot(strictJson(text)); assert.ok(s.upstreamData); assert.equal(s.records.length, 1);
  const bad = JSON.parse(text); bad.upstreamData.tables.records[0].summary = 'conflict'; assert.throws(() => validateSnapshot(bad));
  const omitted = JSON.parse(text); omitted.upstreamData.tables.weeklyReviews[0].content_json = '{}'; assert.throws(() => validateSnapshot(omitted));
});
async function setup() {
  const base = path.resolve('../../.life-clock-tools/m7-tests'); await fs.mkdir(base, { recursive: true });
  const data = await fs.mkdtemp(path.join(base, 'server-'));
  const app = await createApp({ dataDir: data, writeToken: write, readToken: read });
  return { app, data, close: async () => { await app.close(); await fs.rm(data, { recursive: true, force: true }); } };
}
const fresh = (time = 0) => { const s = JSON.parse(fixture); s.snapshotId = randomUUID(); s.createdAt += time; return s; };
async function prepare(app: Awaited<ReturnType<typeof createApp>>, s: unknown, parent: string | null = null) {
  return app.inject({ method: 'POST', url: '/api/backups/prepare', headers, payload: { snapshotJson: JSON.stringify(s), parentSnapshotId: parent } });
}
async function photo(app: Awaited<ReturnType<typeof createApp>>, s: ReturnType<typeof fresh>) {
  const bytes = await fs.readFile(new URL(`../../shared/fixtures/${s.photos[0].relativePath}`, import.meta.url));
  return app.inject({ method: 'PUT', url: `/api/backups/${s.snapshotId}/photos/${s.photos[0].sha256}`, headers: { ...headers, 'content-type': 'application/octet-stream' }, payload: bytes });
}
async function commit(app: Awaited<ReturnType<typeof createApp>>, s: ReturnType<typeof fresh>, parent: string | null = null) {
  return app.inject({ method: 'POST', url: `/api/backups/${s.snapshotId}/commit`, headers, payload: { parentSnapshotId: parent } });
}
test('browser and phone share exact fixture statistics without counting upload time', () => {
  const s = validateSnapshot(strictJson(fixture)); const stats = weekStats(s, '2026-10-05', 'Asia/Shanghai');
  for (const key of ['elapsedMs','recordedUnionMs','unrecordedMs','confirmedMs','pendingMs','runningMs','confirmedEventCount','confirmedByType','happiness','meaning'] as const) assert.deepEqual(stats[key], expected[key], key);
  assert.deepEqual(stats.dailyConfirmedMs.slice(0, 2), expected.dailyConfirmedMs); assert.deepEqual(stats.dailyConfirmedMs.slice(2), [0,0,0,0,0]);
  const filtered = weekStats(s, '2026-10-05', 'Asia/Shanghai', ['201','202']);
  assert.equal(filtered.recordedUnionMs, expected.combinedTagFilterMs); assert.deepEqual(filtered.records.map(r => r.id), expected.combinedTagFilterIds);
});
test('DST and cross-midnight scores use real elapsed time and unique identity', () => {
  const s = validateSnapshot(strictJson(fixture)); s.photos = []; s.records = [s.records[0]]; s.runningRecords = [];
  const result = weekStats(s, '2026-10-05', 'Asia/Shanghai'); assert.equal(result.records.length, 1); assert.equal(result.blocks.length, 2);
  const day = weekStats(s, '2026-10-26', 'America/New_York'); assert.equal(day.days[6].end - day.days[6].start, 25 * 3600000);
});
test('strict protocol rejects duplicate JSON, unsafe IDs, references, scores and paths', () => {
  assert.throws(() => strictJson('{"a":1,"a":2}'));
  for (const change of [(s: any) => s.records[0].typeId = '999', (s: any) => s.records[0].id = '01', (s: any) => s.photos[0].relativePath = '../secret',
    (s: any) => s.records[1].tags.find((t: any) => t.tagId === s.ratingTagIds.happinessTagId).numericValue = 2.5, (s: any) => s.businessSettings.writeToken = 'secret']) {
    const s = fresh(); change(s); assert.throws(() => validateSnapshot(s));
  }
});
test('read credentials cannot write and every snapshot and photo requires auth', async () => {
  const { app, close } = await setup(); try {
    assert.equal((await app.inject('/api/backups/latest')).statusCode, 401);
    assert.equal((await app.inject({ url: '/api/backups/latest', headers: { authorization: `Bearer ${read}` } })).statusCode, 200);
    assert.equal((await app.inject({ method: 'POST', url: '/api/backups/prepare', headers: { authorization: `Bearer ${read}` }, payload: {} })).statusCode, 403);
    assert.equal((await app.inject(`/api/backups/${fresh().snapshotId}`)).statusCode, 401);
    for (const url of ['/api%2fbackups/latest','/api/backups%2flatest','/api/backups/latest?ignored=true']) assert.notEqual((await app.inject(url)).statusCode, 200);
  } finally { await close(); }
});

test('native BMP files upload and malformed image payloads stay unpublished', async () => {
  const { app, close } = await setup(); const s = fresh();
  const bytes = bmp.encode({ width: 1, height: 1, data: Buffer.from([255, 30, 80, 40]) }).data;
  const hash = createHash('sha256').update(bytes).digest('hex');
  s.photos[0] = { ...s.photos[0], sha256: hash, relativePath: `photos/${hash}.bmp`, mimeType: 'image/bmp', sizeBytes: bytes.length };
  try {
    assert.equal((await prepare(app,s)).statusCode,200);
    const uploaded=await app.inject({method:'PUT',url:`/api/backups/${s.snapshotId}/photos/${hash}`,headers:{...headers,'content-type':'application/octet-stream'},payload:bytes});
    assert.equal(uploaded.statusCode,200); assert.equal((await commit(app,s)).statusCode,200);
  } finally { await close(); }
});
test('missing or damaged photos never publish a snapshot, retry is idempotent', async () => {
  const { app, close } = await setup(); const s = fresh(); try {
    assert.equal((await prepare(app, s)).statusCode, 200);
    assert.equal((await commit(app, s)).statusCode, 422);
    assert.equal((await app.inject({ url: '/api/backups/latest', headers })).json().latest, null);
    const bad = await app.inject({ method: 'PUT', url: `/api/backups/${s.snapshotId}/photos/${s.photos[0].sha256}`, headers: { ...headers, 'content-type': 'application/octet-stream' }, payload: Buffer.from('bad') });
    assert.equal(bad.statusCode, 422);
    assert.equal((await photo(app, s)).statusCode, 200);
    assert.equal((await commit(app, s)).statusCode, 200);
    assert.equal((await commit(app, s)).statusCode, 200);
    assert.equal((await prepare(app, s)).json().committed, true);
  } finally { await close(); }
});
test('late commits and historical retries never replace the newest snapshot', async () => {
  const { app, close } = await setup(); const first = fresh(), old = fresh(1000), next = fresh(2000); try {
    await prepare(app, first); await photo(app, first); assert.equal((await commit(app, first)).statusCode, 200);
    await prepare(app, old, first.snapshotId); await prepare(app, next, first.snapshotId);
    assert.equal((await commit(app, next, first.snapshotId)).statusCode, 200);
    assert.equal((await commit(app, old, first.snapshotId)).statusCode, 409);
    assert.equal((await commit(app, first)).statusCode, 200);
    assert.equal((await app.inject({ url: '/api/backups/latest', headers })).json().latest.snapshotId, next.snapshotId);
    assert.equal((await prepare(app, first)).json().committed, true);
  } finally { await close(); }
});
test('immutable reads pin photos to one version and reject unreferenced media', async () => {
  const { app, close } = await setup(); const s = fresh(); try {
    await prepare(app, s); await photo(app, s); await commit(app, s);
    assert.equal((await app.inject({ url: `/api/backups/${s.snapshotId}`, headers })).statusCode, 200);
    const image = await app.inject({ url: `/api/backups/${s.snapshotId}/photos/${s.photos[0].sha256}`, headers }); assert.equal(image.statusCode, 200); assert.equal(image.rawPayload.length, 68);
    assert.equal((await app.inject({ url: `/api/backups/${s.snapshotId}/photos/${'0'.repeat(64)}`, headers })).statusCode, 404);
    const conflict = structuredClone(s); conflict.records[0].summary = 'changed'; assert.equal((await prepare(app, conflict, s.snapshotId)).statusCode, 409);
  } finally { await close(); }
});
test('disk failure before pointer publication preserves the last complete snapshot', async () => {
  const { app, data, close } = await setup(); const s = fresh(), next = fresh(2000); try {
    await prepare(app, s); await photo(app, s); await commit(app, s); await prepare(app, next, s.snapshotId);
    const rename = fs.rename.bind(fs);
    const spy = mock.method(fs, 'rename', async (from: Parameters<typeof fs.rename>[0], to: Parameters<typeof fs.rename>[1]) => {
      if (String(to) === path.join(data, 'latest.json')) throw Object.assign(new Error('disk full'), { code: 'ENOSPC' }); return rename(from, to);
    });
    try { assert.equal((await commit(app, next, s.snapshotId)).statusCode, 503); } finally { spy.mock.restore(); }
    assert.equal((await app.inject({ url: '/api/backups/latest', headers })).json().latest.snapshotId, s.snapshotId);
    assert.equal((await commit(app, next, s.snapshotId)).statusCode, 200);
  } finally { await close(); }
});
