import { createHash, randomUUID } from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
import bmp from 'bmp-js';
import heic from 'heic-decode';
import { check, strictJson, validateSnapshot, type Snapshot, type Photo } from '../shared/snapshot.js';

export type Latest = { snapshotId: string; dataSetId: string; createdAt: number; sha256: string };
type Pending = { parentSnapshotId: string | null; digest: string; createdAt: number; dataSetId: string };
type Committed = Latest & { parentSnapshotId: string | null };
export class StoreError extends Error { constructor(public status: number, public code: string) { super(code); } }
const digest = (bytes: Buffer | string) => createHash('sha256').update(bytes).digest('hex');
async function readJson<T>(file: string): Promise<T | null> {
  try { return strictJson(await fs.readFile(file, 'utf8')) as T; }
  catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return null; throw error; }
}
async function atomic(file: string, bytes: string | Buffer) {
  const temporary = `${file}.${randomUUID()}.tmp`;
  try {
    const handle = await fs.open(temporary, 'wx', 0o600);
    try { await handle.writeFile(bytes); await handle.sync(); } finally { await handle.close(); }
    await fs.rename(temporary, file);
    if (process.platform !== 'win32') {
      const directory = await fs.open(path.dirname(file), 'r');
      try { await directory.sync(); } finally { await directory.close(); }
    }
  } finally { await fs.unlink(temporary).catch(() => {}); }
}

export class BackupStore {
  private tail: Promise<unknown> = Promise.resolve();
  private readonly cache = new Map<string, Snapshot>();
  private readonly reads = new Map<string, Promise<Snapshot>>();
  constructor(readonly root: string) { }
  private file(...parts: string[]) { return path.join(this.root, ...parts); }
  private async serial<T>(work: () => Promise<T>): Promise<T> {
    const current = this.tail.then(work, work); this.tail = current.catch(() => {}); return current;
  }
  async initialize() {
    for (const dir of ['snapshots', 'pending', 'photos']) await fs.mkdir(this.file(dir), { recursive: true, mode: 0o700 });
    const latest = await this.latest();
    if (latest) {
      const bytes = await fs.readFile(this.file('snapshots', `${latest.snapshotId}.json`));
      check(digest(bytes) === latest.sha256, 'Corrupt committed snapshot');
      const snapshot = validateSnapshot(strictJson(bytes.toString('utf8')));
      check(snapshot.dataSetId === latest.dataSetId && snapshot.snapshotId === latest.snapshotId && snapshot.createdAt === latest.createdAt, 'Corrupt latest pointer');
      for (const photo of snapshot.photos) await this.verifyStored(photo);
    }
  }
  async latest(): Promise<Latest | null> { return readJson<Latest>(this.file('latest.json')); }
  async snapshot(id: string): Promise<Snapshot> {
    const cached = this.cache.get(id); if (cached) return cached;
    const reading = this.reads.get(id); if (reading) return reading;
    const work = this.readSnapshot(id);
    this.reads.set(id, work);
    try { return await work; } finally { this.reads.delete(id); }
  }
  private async readSnapshot(id: string): Promise<Snapshot> {
    // Only a published commit marker permits reads; orphan files from failed commits stay private.
    const committed = await readJson<Latest>(this.file('snapshots', `${id}.commit.json`));
    if (!committed) throw new StoreError(404, 'SNAPSHOT_NOT_FOUND');
    const bytes = await fs.readFile(this.file('snapshots', `${id}.json`));
    check(digest(bytes) === committed.sha256, 'Corrupt snapshot');
    const snapshot = validateSnapshot(strictJson(bytes.toString('utf8')));
    if (bytes.length <= 4 * 1024 * 1024) {
      this.cache.set(id, snapshot);
      while (this.cache.size > 2) this.cache.delete(this.cache.keys().next().value!);
    }
    return snapshot;
  }
  async prepare(raw: string, parent: string | null) {
    return this.serial(async () => {
      const snapshot = validateSnapshot(strictJson(raw));
      const hash = digest(raw);
      const committed = await readJson<Latest>(this.file('snapshots', `${snapshot.snapshotId}.commit.json`));
      if (committed) {
        if (committed.sha256 !== hash) throw new StoreError(409, 'SNAPSHOT_ID_CONFLICT');
        return { snapshotId: snapshot.snapshotId, parentSnapshotId: parent, missingPhotos: [], committed: true, latest: await this.latest() };
      }
      const latest = await this.latest();
      if ((latest?.snapshotId ?? null) !== parent) throw new StoreError(409, 'STALE_PARENT');
      if (latest?.dataSetId === snapshot.dataSetId && snapshot.createdAt < latest.createdAt) throw new StoreError(409, 'OLDER_SNAPSHOT');
      const existing = await readJson<Pending>(this.file('pending', `${snapshot.snapshotId}.meta.json`));
      if (existing && (existing.digest !== hash || existing.parentSnapshotId !== parent)) throw new StoreError(409, 'SNAPSHOT_ID_CONFLICT');
      await atomic(this.file('pending', `${snapshot.snapshotId}.json`), raw);
      await atomic(this.file('pending', `${snapshot.snapshotId}.meta.json`), JSON.stringify({ parentSnapshotId: parent, digest: hash, createdAt: snapshot.createdAt, dataSetId: snapshot.dataSetId } satisfies Pending));
      const missing = new Set<string>();
      for (const photo of snapshot.photos) {
        try { await this.verifyStored(photo); } catch { missing.add(photo.sha256); }
      }
      return { snapshotId: snapshot.snapshotId, parentSnapshotId: parent, missingPhotos: [...missing], committed: false, latest };
    });
  }
  private async pending(id: string) {
    const meta = await readJson<Pending>(this.file('pending', `${id}.meta.json`));
    if (!meta) throw new StoreError(404, 'UPLOAD_NOT_PREPARED');
    const raw = await fs.readFile(this.file('pending', `${id}.json`), 'utf8');
    check(digest(raw) === meta.digest, 'Corrupt pending snapshot');
    return { meta, raw, snapshot: validateSnapshot(strictJson(raw)) };
  }
  async putPhoto(id: string, hash: string, bytes: Buffer) {
    return this.serial(async () => {
      const { snapshot, meta } = await this.pending(id);
      if ((await this.latest())?.snapshotId !== meta.parentSnapshotId && !((await this.latest()) === null && meta.parentSnapshotId === null)) throw new StoreError(409, 'STALE_PARENT');
      const photo = snapshot.photos.find(p => p.sha256 === hash);
      if (!photo) throw new StoreError(404, 'PHOTO_NOT_REFERENCED');
      try { await verifyBytes(photo, bytes); } catch { throw new StoreError(422, 'PHOTO_INVALID'); }
      await atomic(this.file('photos', hash), bytes);
    });
  }
  private async verifyStored(photo: Photo) {
    const bytes = await fs.readFile(this.file('photos', photo.sha256));
    await verifyBytes(photo, bytes);
  }
  async commit(id: string, parent: string | null): Promise<Latest> {
    return this.serial(async () => {
      const existing = await readJson<Committed>(this.file('snapshots', `${id}.commit.json`));
      const latest = await this.latest();
      if (existing) {
        if (latest?.snapshotId !== id && (latest?.snapshotId ?? null) === existing.parentSnapshotId && parent === existing.parentSnapshotId) {
          const snapshot = await this.snapshot(id);
          for (const p of snapshot.photos) await this.verifyStored(p);
          const { parentSnapshotId: _parent, ...info } = existing;
          await atomic(this.file('latest.json'), JSON.stringify(info));
        }
        // A historical retry never makes an older snapshot latest again.
        return existing;
      }
      if ((latest?.snapshotId ?? null) !== parent) throw new StoreError(409, 'STALE_PARENT');
      const { raw, snapshot, meta } = await this.pending(id);
      if (meta.parentSnapshotId !== parent) throw new StoreError(409, 'STALE_PARENT');
      if (latest?.dataSetId === snapshot.dataSetId && snapshot.createdAt < latest.createdAt) throw new StoreError(409, 'OLDER_SNAPSHOT');
      for (const photo of snapshot.photos) {
        try { await this.verifyStored(photo); } catch { throw new StoreError(422, 'PHOTO_MISSING_OR_INVALID'); }
      }
      const info: Latest = { snapshotId: id, dataSetId: snapshot.dataSetId, createdAt: snapshot.createdAt, sha256: meta.digest };
      await atomic(this.file('snapshots', `${id}.json`), raw);
      // Readers may see an immutable complete version before publication; latest moves only last.
      await atomic(this.file('snapshots', `${id}.commit.json`), JSON.stringify({ ...info, parentSnapshotId: parent }));
      try { await atomic(this.file('latest.json'), JSON.stringify(info)); }
      catch (error) {
        if ((await this.latest())?.snapshotId === id) return info;
        await fs.unlink(this.file('snapshots', `${id}.commit.json`)).catch(() => {}); throw error;
      }
      return info;
    });
  }
  async photo(id: string, hash: string): Promise<{ file: string; mime: string }> {
    const snapshot = await this.snapshot(id);
    const photo = snapshot.photos.find(p => p.sha256 === hash);
    if (!photo) throw new StoreError(404, 'PHOTO_NOT_REFERENCED');
    return { file: this.file('photos', hash), mime: photo.mimeType };
  }
}

async function verifyBytes(photo: Photo, bytes: Buffer) {
  check(bytes.length === photo.sizeBytes && digest(bytes) === photo.sha256, 'Invalid photo hash or size');
  if (photo.mimeType === 'image/bmp') {
    check(bytes.length >= 54 && bytes.toString('ascii', 0, 2) === 'BM', 'Invalid BMP');
    const width = bytes.readUInt32LE(18), height = Math.abs(bytes.readInt32LE(22));
    const bits = bytes.readUInt16LE(28), colors = bytes.readUInt32LE(46);
    check(width > 0 && height > 0 && width * height <= 100_000_000 && [1,4,8,16,24,32].includes(bits), 'Invalid BMP dimensions');
    check(bits > 8 || colors <= 2 ** bits, 'Invalid BMP palette');
    const decoded = bmp.decode(bytes); check(decoded.width === width && decoded.height === height && decoded.data.length === width * height * 4, 'Invalid BMP pixels');
    return;
  }
  if (photo.mimeType === 'image/heic' || photo.mimeType === 'image/heif') {
    const images = await heic.all({ buffer: bytes });
    try {
      check(images.length > 0 && images.length <= 1024 && images[0].width > 0 && images[0].height > 0 && images[0].width * images[0].height <= 100_000_000, 'Invalid HEIC dimensions');
      const decoded = await images[0].decode(); check(decoded.data.length === decoded.width * decoded.height * 4, 'Invalid HEIC pixels');
    } finally { images.dispose(); }
    return;
  }
  const image = sharp(bytes, { limitInputPixels: 100_000_000, failOn: 'error' });
  const metadata = await image.metadata();
  const mime: Record<string, string[]> = { png: ['image/png'], jpeg: ['image/jpeg', 'image/jpg'], webp: ['image/webp'], gif: ['image/gif'], heif: ['image/heif', 'image/heic', 'image/avif'], avif: ['image/avif'] };
  check(metadata.format && mime[metadata.format]?.includes(photo.mimeType) && metadata.width && metadata.height, 'Invalid photo type');
  await image.resize(1, 1).raw().toBuffer();
}
