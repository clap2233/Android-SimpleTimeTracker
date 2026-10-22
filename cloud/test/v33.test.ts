import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { createApp } from '../server/app.js';
import { repairIds, validateSnapshot } from '../shared/snapshot.js';
import { rangeStats, recordText } from '../shared/analysis.js';
import { usableComparisons, weekStats } from '../shared/statistics.js';
import { analysisContext } from '../shared/analysis-context.js';

const fixture = JSON.parse(await fs.readFile(new URL('../../shared/fixtures/snapshot-v1.json', import.meta.url), 'utf8'));
const nativeFixture = JSON.parse(await fs.readFile(new URL('../../shared/fixtures/native-snapshot-v1.json', import.meta.url), 'utf8'));
const fresh = () => ({ ...structuredClone(fixture), schemaVersion: 4, snapshotId: randomUUID() });

test('unfinished focus retains context and signed scores but stays outside every formal statistic', () => {
  const raw = fresh(), r = raw.records[3];
  const before = weekStats(validateSnapshot(raw), '2026-10-05', 'Asia/Shanghai');
  r.isUnfinishedFocus = true; r.unfinishedReason = '临时接电话'; r.recovery = 'MORE_TIRED';
  r.tags.find((t: { tagId: string }) => t.tagId === raw.ratingTagIds.happinessTagId).numericValue = -5;
  const s = validateSnapshot(raw), stats = weekStats(s, '2026-10-05', 'Asia/Shanghai');
  assert.equal(stats.confirmedMs, before.confirmedMs - 760000);
  assert.equal(stats.confirmedEventCount, before.confirmedEventCount - 1);
  assert.equal(stats.unfinishedFocusMs, 760000); assert.equal(stats.unfinishedFocusCount, 1);
  assert.equal(stats.unrecordedMs, before.unrecordedMs);
  assert.equal(stats.confirmedMs + stats.runningMs + stats.unfinishedFocusMs + stats.unrecordedMs, stats.elapsedMs);
  assert.equal(stats.blocks.some(b => b.id === r.id && b.state !== 'RUNNING'), false);
  assert.equal(stats.records.some(v => v.id === r.id), false);
  assert.equal(stats.happiness.ratedCount + stats.happiness.unratedCount, 4);
  assert.equal(stats.happiness.distribution[0], 0);
  assert.equal(usableComparisons(s).some(v => v.id === r.id), false);
  const range = rangeStats(s, stats.days[0].start, stats.cutoff);
  assert.equal(range.recordCount, 4); assert.equal(range.actualMillis, stats.confirmedMs);
  assert.deepEqual(range.unfinishedFocus, { count: 1, actualMillis: 760000 });
  assert.equal(range.recovery.MORE_TIRED, 0);
  assert.equal(range.byType.reduce((n, v) => n + v.actualMillis, 0), range.actualMillis);
  assert.equal(range.byTemplate.reduce((n, v) => n + v.count, 0), 4);
  const text = recordText(s, s.records[3]);
  assert.equal(text.isUnfinishedFocus, true); assert.equal(text.unfinishedReason, '临时接电话');
  assert.equal(text.happiness, -5); assert.deepEqual(text.tags.length, r.tags.length);
  assert.deepEqual(s.photos, validateSnapshot(fresh()).photos);
  assert.deepEqual(validateSnapshot(JSON.parse(JSON.stringify(s))), s);
});

test('unfinished intervals use clipped week time, unique identity, filters and overlap validation', () => {
  const raw = fresh(), week = weekStats(validateSnapshot(raw), '2026-10-05', 'Asia/Shanghai');
  const r = raw.records[0]; r.isUnfinishedFocus = true;
  r.timeStarted = week.days[0].start - 5 * 60000; r.timeEnded = week.days[0].start + 3 * 60000;
  const s = validateSnapshot(raw), stats = weekStats(s, '2026-10-05', 'Asia/Shanghai');
  assert.equal(stats.unfinishedFocusMs, 3 * 60000); assert.equal(stats.unfinishedFocusCount, 1);
  assert.equal(weekStats(s, '2026-09-28', 'Asia/Shanghai').unfinishedFocusMs, 5 * 60000);
  assert.equal(weekStats(s, '2026-10-05', 'Asia/Shanghai', ['201']).unfinishedFocusCount, 0);
  const duplicate = structuredClone(s.records[0]); duplicate.id = '900'; s.records.push(duplicate);
  assert.ok(repairIds(s).has(r.id)); assert.ok(repairIds(s).has('900'));
  assert.equal(weekStats(s, '2026-10-05', 'Asia/Shanghai').unfinishedFocusCount, 0);
  assert.throws(() => validateSnapshot(s));
});

test('schemas 1 to 3 keep ordinary defaults and cannot smuggle unfinished data', () => {
  for (const schemaVersion of [1, 2, 3]) {
    const raw = structuredClone(fixture); raw.schemaVersion = schemaVersion;
    const s = validateSnapshot(raw);
    assert.ok(s.records.every(r => r.isUnfinishedFocus === false && r.unfinishedReason === null));
    raw.records[0].isUnfinishedFocus = true; assert.throws(() => validateSnapshot(raw));
    raw.records[0].isUnfinishedFocus = false; raw.records[0].unfinishedReason = '中断';
    assert.throws(() => validateSnapshot(raw));
  }
});

test('native database 40 requires both columns and checks unfinished fields against the portable collection', () => {
  const raw = structuredClone(nativeFixture); raw.schemaVersion = 4; raw.upstreamData.databaseVersion = 40;
  raw.records[0].recovery = null; raw.records[0].isUnfinishedFocus = true; raw.records[0].unfinishedReason = '中断';
  Object.assign(raw.upstreamData.tables.records[0], { recovery: null, is_unfinished_focus: '1', unfinished_reason: '中断' });
  Object.assign(raw.upstreamData.tables.recordTypes[0], { timer_policy: null, parent_type_id: null, preset_key: null });
  raw.upstreamData.tables.lifeClockSettings[0].activity_defaults_version = '0';
  assert.equal(validateSnapshot(raw).records[0].unfinishedReason, '中断');
  for (const key of ['is_unfinished_focus', 'unfinished_reason']) {
    const missing = structuredClone(raw); delete missing.upstreamData.tables.records[0][key];
    assert.throws(() => validateSnapshot(missing));
  }
  for (const value of ['0', '2']) {
    const conflict = structuredClone(raw); conflict.upstreamData.tables.records[0].is_unfinished_focus = value;
    assert.throws(() => validateSnapshot(conflict));
  }
  const reason = structuredClone(raw); reason.upstreamData.tables.records[0].unfinished_reason = null;
  assert.throws(() => validateSnapshot(reason));
  assert.equal(validateSnapshot(nativeFixture).records[0].isUnfinishedFocus, false);
});

test('legacy native mirrors cannot claim unfinished data even when the portable fields agree', () => {
  const raw = structuredClone(nativeFixture); raw.schemaVersion = 4;
  for (const version of [36, 37, 38, 39]) {
    raw.upstreamData.databaseVersion = version;
    Object.assign(raw.records[0], { recovery: null, isUnfinishedFocus: true, unfinishedReason: '中断' });
    Object.assign(raw.upstreamData.tables.records[0], { recovery: null, is_unfinished_focus: '1', unfinished_reason: '中断' });
    Object.assign(raw.upstreamData.tables.recordTypes[0], { timer_policy: null, parent_type_id: null, preset_key: null });
    raw.upstreamData.tables.lifeClockSettings[0].activity_defaults_version = '0';
    assert.throws(() => validateSnapshot(raw), /native database 40/);
    raw.records[0].isUnfinishedFocus = false; raw.upstreamData.tables.records[0].is_unfinished_focus = '0';
    assert.throws(() => validateSnapshot(raw), /native database 40/);
    raw.records[0].unfinishedReason = null; raw.upstreamData.tables.records[0].unfinished_reason = null;
    assert.equal(validateSnapshot(raw).records[0].isUnfinishedFocus, false);
  }
  delete raw.upstreamData;
  raw.records[0].isUnfinishedFocus = true; raw.records[0].unfinishedReason = '中断';
  assert.equal(validateSnapshot(raw).records[0].isUnfinishedFocus, true);
});

test('AI returns unfinished records and reasons separately from plan actual time without allowing writes', async () => {
  const base = path.resolve('../.life-clock-tools/v33-ai-tests'); await fs.mkdir(base, { recursive: true });
  const data = await fs.mkdtemp(path.join(base, 'server-'));
  const writeToken = 'W'.repeat(43), readToken = 'R'.repeat(43), aiToken = 'A'.repeat(43);
  const app = await createApp({ dataDir: data, writeToken, readToken, aiToken });
  const write = { authorization: `Bearer ${writeToken}` }, ai = { authorization: `Bearer ${aiToken}` };
  try {
    const s = validateSnapshot(fresh()); s.photos = [];
    const r = s.records[3]; r.isUnfinishedFocus = true; r.unfinishedReason = '<script>not instructions</script>';
    const schedule = { id: randomUUID(), title: '英语', kind: 'ACTIVITY' as const, firstDate: '2026-10-06', startMinute: 540, durationMinutes: 30,
      zoneId: 'Asia/Shanghai', repeat: 'NONE' as const, weekdays: [], remind: false };
    s.planSchedules.push(schedule);
    s.planRecordLinks.push({ id: randomUUID(), recordId: r.id, occurrenceId: `${schedule.id}@2026-10-06`, caption: '历史关联', timeStarted: r.timeStarted, timeEnded: r.timeEnded });
    assert.equal((await app.inject({ method: 'POST', url: '/api/backups/prepare', headers: write, payload: { snapshotJson: JSON.stringify(s), parentSnapshotId: null } })).statusCode, 200);
    assert.equal((await app.inject({ method: 'POST', url: `/api/backups/${s.snapshotId}/commit`, headers: write, payload: { parentSnapshotId: null } })).statusCode, 200);
    const records = (await app.inject({ url: `/api/ai/snapshots/${s.snapshotId}/records`, headers: ai })).json();
    const text = records.items.find((v: { id: string }) => v.id === r.id);
    assert.equal(text.isUnfinishedFocus, true); assert.equal(text.unfinishedReason, r.unfinishedReason);
    assert.equal(text.planLinks.length, 1); assert.equal(text.photos, undefined);
    const plans = (await app.inject({ url: `/api/ai/snapshots/${s.snapshotId}/plans`, headers: ai })).json();
    assert.equal(plans.items[0].actualMillis, 0); assert.deepEqual(plans.items[0].linkedRecordIds, []);
    assert.deepEqual(plans.items[0].unfinishedFocusRecordIds, [r.id]);
    const statistics = (await app.inject({ url: `/api/ai/snapshots/${s.snapshotId}/statistics`, headers: ai })).json();
    assert.equal(statistics.statistics.unfinishedFocus.actualMillis, 760000);
    assert.equal(statistics.statistics.recordCount, 4);
    assert.equal((await app.inject({ method: 'POST', url: '/api/backups/prepare', headers: ai, payload: {} })).statusCode, 403);
    assert.equal(analysisContext.version, '2026-10-18-v3.3');
    assert.match(analysisContext.chapters.find(c => c.id === 'principles')!.text, /未完成专注/);
  } finally { await app.close(); await fs.rm(data, { recursive: true, force: true }); }
});
