import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { createApp } from '../server/app.js';
import { validateSnapshot, repairIds } from '../shared/snapshot.js';
import { experienceScores, rangeStats } from '../shared/analysis.js';
import { occurrences, planExceptionSchema } from '../shared/planning.js';

const fixture = JSON.parse(await fs.readFile(new URL('../../shared/fixtures/snapshot-v1.json', import.meta.url), 'utf8'));
const fresh = () => ({ ...structuredClone(fixture), schemaVersion: 2, snapshotId: randomUUID(), photos: [], planTasks: [], planSchedules: [], planExceptions: [], planRecordLinks: [] });

test('a moved instance keeps its identity and details outside the original series range', () => {
  const plan = { id: randomUUID(), title: '阅读', kind: 'ACTIVITY' as const, firstDate: '2026-10-12', startMinute: 540, durationMinutes: 30,
    zoneId: 'Asia/Shanghai', repeat: 'DAILY' as const, weekdays: [], untilDate: '2026-10-14', remind: true };
  const edit = planExceptionSchema.parse({ id: `${plan.id}@2026-10-13`, scheduleId: plan.id, date: '2026-10-13', status: 'OPEN',
    movedDate: '2026-11-01', title: '留白', kind: 'BUFFER', remind: false, startMinute: 1410, durationMinutes: 90 });
  const original = occurrences([plan], [edit], Date.parse('2026-10-12T00:00:00+08:00'), Date.parse('2026-10-15T00:00:00+08:00'));
  assert.deepEqual(original.map(p => p.date), ['2026-10-12', '2026-10-14']);
  const [moved] = occurrences([plan], [edit], Date.parse('2026-11-02T00:00:00+08:00'), Date.parse('2026-11-03T00:00:00+08:00'));
  assert.equal(moved.id, edit.id); assert.equal(moved.schedule.title, '留白'); assert.equal(moved.schedule.kind, 'BUFFER');
  assert.equal(moved.schedule.remind, false); assert.equal(moved.end - moved.start, 90 * 60000);
  const snapshot = fresh(); snapshot.planSchedules = [plan]; snapshot.planExceptions = [edit];
  assert.deepEqual(validateSnapshot(snapshot).planExceptions[0], edit);
  assert.equal(planExceptionSchema.safeParse({ ...edit, remind: 'false' }).success, false);
});

test('calendar DST overlap and gap match Android Calendar instants', () => {
  const plan = { id: randomUUID(), title: '休息', kind: 'FREE' as const, firstDate: '2026-11-01', startMinute: 90, durationMinutes: 30,
    zoneId: 'America/New_York', repeat: 'NONE' as const, weekdays: [], remind: false };
  assert.equal(occurrences([plan], [], Date.parse('2026-11-01T00:00:00Z'), Date.parse('2026-11-02T12:00:00Z'))[0].start, 1793514600000);
  assert.equal(occurrences([{ ...plan, firstDate: '2026-03-08', startMinute: 150 }], [], Date.parse('2026-03-08T00:00:00Z'), Date.parse('2026-03-09T12:00:00Z'))[0].start, 1772955000000);
});
test('arbitrary real durations, negative meaning, neutral and missing values remain distinct', () => {
  const raw = fresh(), record = raw.records[1];
  record.timeEnded = record.timeStarted + 760000;
  record.tags.find((t: any) => t.tagId === raw.ratingTagIds.happinessTagId).numericValue = 4;
  record.tags.find((t: any) => t.tagId === raw.ratingTagIds.meaningTagId).numericValue = -3;
  const s = validateSnapshot(raw), r = s.records[1];
  assert.equal(repairIds(s).has(r.id), false);
  assert.equal(experienceScores(s, r).combined, 1);
  r.tags.find(t => t.tagId === s.ratingTagIds.happinessTagId)!.numericValue = 1;
  r.tags.find(t => t.tagId === s.ratingTagIds.meaningTagId)!.numericValue = -5;
  assert.equal(experienceScores(s, r).combined, -4);
  r.tags.find(t => t.tagId === s.ratingTagIds.meaningTagId)!.numericValue = 0;
  assert.equal(experienceScores(s, r).combined, 1);
  r.tags.find(t => t.tagId === s.ratingTagIds.meaningTagId)!.numericValue = null;
  assert.equal(experienceScores(s, r).combined, null);
  assert.equal(rangeStats(s, s.createdAt - 7 * 86400000, s.createdAt).meaning.distribution.length, 11);
});
test('legacy repair declarations are verified before removing the old quarter-hour restriction', () => {
  const raw = structuredClone(fixture);
  raw.records[1].timeEnded = raw.records[1].timeStarted + 760000;
  raw.repairRecordIds = [...(raw.repairRecordIds ?? []), raw.records[1].id];
  const s = validateSnapshot(raw);
  assert.equal(repairIds(s).has(raw.records[1].id), false);
  const negative = fresh(); negative.schemaVersion = 1;
  negative.records[1].tags.find((t: any) => t.tagId === negative.ratingTagIds.meaningTagId).numericValue = -1;
  assert.throws(() => validateSnapshot(negative));
});
test('plans repeat across midnight without adding time to actual records', () => {
  const s = validateSnapshot(fresh());
  const schedule = { id: randomUUID(), title: '阅读', kind: 'ACTIVITY' as const, firstDate: '2026-10-05', startMinute: 23 * 60 + 45, durationMinutes: 30,
    zoneId: 'Asia/Shanghai', repeat: 'DAILY' as const, weekdays: [], remind: false };
  s.planSchedules.push(schedule);
  const from = 1791129600000, end = from + 86400000;
  const before = rangeStats(s, from, end);
  assert.equal(occurrences(s.planSchedules, [], from, end).length, 1);
  assert.equal(before.plannedMillis, 15 * 60000);
  s.planExceptions.push({ id: `${schedule.id}@2026-10-05`, scheduleId: schedule.id, date: '2026-10-05', status: 'CANCELLED' });
  const after = rangeStats(s, from, end);
  assert.equal(after.plannedMillis, 0);
  assert.equal(after.actualMillis, before.actualMillis);
});
test('AI credentials are text-only, read-only and snapshot-pinned', async () => {
  const base = path.resolve('../.life-clock-tools/v3-ai-tests'); await fs.mkdir(base, { recursive: true });
  const data = await fs.mkdtemp(path.join(base, 'server-'));
  const writeToken = 'W'.repeat(43), readToken = 'R'.repeat(43), aiToken = 'A'.repeat(43);
  const app = await createApp({ dataDir: data, writeToken, readToken, aiToken });
  const write = { authorization: `Bearer ${writeToken}` }, ai = { authorization: `Bearer ${aiToken}` };
  try {
    assert.equal((await app.inject({ url: '/api/ai/latest', headers: ai })).json().latest, null);
    const s = fresh();
    assert.equal((await app.inject({ method: 'POST', url: '/api/backups/prepare', headers: write, payload: { snapshotJson: JSON.stringify(s), parentSnapshotId: null } })).statusCode, 200);
    assert.equal((await app.inject({ method: 'POST', url: `/api/backups/${s.snapshotId}/commit`, headers: write, payload: { parentSnapshotId: null } })).statusCode, 200);
    for (const url of ['/api/backups/latest', `/api/backups/${s.snapshotId}`, `/api/backups/${s.snapshotId}/photos/${'a'.repeat(64)}`]) {
      assert.equal((await app.inject({ url, headers: ai })).statusCode, 403);
    }
    assert.equal((await app.inject({ method: 'POST', url: '/api/backups/prepare', headers: ai, payload: {} })).statusCode, 403);
    assert.equal((await app.inject({ url: '/api/ai/latest', headers: { authorization: `Bearer ${readToken}` } })).statusCode, 401);
    const first = (await app.inject({ url: `/api/ai/snapshots/${s.snapshotId}/records?limit=1`, headers: ai })).json();
    assert.equal(first.items.length, 1); assert.equal(first.nextOffset, 1); assert.equal(first.snapshotId, s.snapshotId);
    assert.equal(first.items[0].photos, undefined); assert.equal(first.upstreamData, undefined);
    assert.equal(first.items[0].reviewState, undefined);
    const second = fresh(); second.createdAt += 1000;
    await app.inject({ method: 'POST', url: '/api/backups/prepare', headers: write, payload: { snapshotJson: JSON.stringify(second), parentSnapshotId: s.snapshotId } });
    await app.inject({ method: 'POST', url: `/api/backups/${second.snapshotId}/commit`, headers: write, payload: { parentSnapshotId: s.snapshotId } });
    assert.equal((await app.inject({ url: `/api/ai/snapshots/${s.snapshotId}/info`, headers: ai })).json().snapshotId, s.snapshotId);
    const context = (await app.inject({ url: '/api/ai/context?chapter=principles&limit=40', headers: ai })).json();
    assert.equal(context.text.length, 40); assert.equal(context.nextOffset, 40);
    assert.equal((await app.inject({ url: `/api/ai/snapshots/${s.snapshotId}/records?path=../../secret`, headers: ai })).statusCode, 422);
  } finally { await app.close(); await fs.rm(data, { recursive: true, force: true }); }
});
