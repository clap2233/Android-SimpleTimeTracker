import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { validateSnapshot, type Snapshot } from '../shared/snapshot.js';
import { experienceScores, rangeStats, recordText } from '../shared/analysis.js';
import { weekStats } from '../shared/statistics.js';

const fixture = JSON.parse(await fs.readFile(new URL('../../shared/fixtures/snapshot-v1.json', import.meta.url), 'utf8'));
const nativeFixture = JSON.parse(await fs.readFile(new URL('../../shared/fixtures/native-snapshot-v1.json', import.meta.url), 'utf8'));
const fresh = () => ({ ...structuredClone(fixture), schemaVersion: 3, activityDefaultsVersion: 1 });

test('both signed scores retain negative, neutral and missing values without confirmation', () => {
  const raw = fresh(), record = raw.records[3];
  const happy = record.tags.find((t: { tagId: string }) => t.tagId === raw.ratingTagIds.happinessTagId);
  const meaning = record.tags.find((t: { tagId: string }) => t.tagId === raw.ratingTagIds.meaningTagId);
  for (const [h, m, total] of [[-5, -5, -10], [0, 0, 0], [5, 5, 10], [null, 0, null]]) {
    happy.numericValue = h; meaning.numericValue = m;
    const s = validateSnapshot(raw), r = s.records[3];
    assert.equal(experienceScores(s, r).combined, total);
    assert.equal(recordText(s, r).hasOwnProperty('reviewState'), false);
    const stats = rangeStats(s, s.createdAt - 7 * 86400000, s.createdAt);
    assert.equal(stats.happiness.distribution.length, 11);
    assert.equal(stats.meaning.distribution.length, 11);
    assert.equal(stats.recordCount, 5);
    if (h !== null) assert.equal(stats.happiness.distribution[h + 5] >= 1, true);
  }
  happy.numericValue = -6; assert.throws(() => validateSnapshot(raw));
  happy.numericValue = 1.5; assert.throws(() => validateSnapshot(raw));
});

test('legacy v1 and v2 keep their old signed-score contract and default ungrouped data', () => {
  for (const version of [1, 2]) {
    const raw = structuredClone(fixture); raw.schemaVersion = version;
    const s = validateSnapshot(raw);
    assert.equal(s.activityDefaultsVersion, 0);
    assert.ok(s.recordTypes.every(t => t.parentTypeId === null && t.presetKey === null));
    raw.records[1].tags.find((t: { tagId: string }) => t.tagId === raw.ratingTagIds.happinessTagId).numericValue = -1;
    assert.throws(() => validateSnapshot(raw));
  }
});

test('group totals count a child and the generic parent once, pending records are ordinary experiences', () => {
  const raw = fresh();
  raw.recordTypes[1].parentTypeId = raw.recordTypes[0].id;
  raw.recordTypes[0].presetKey = 'learning'; raw.recordTypes[1].presetKey = 'learning.english';
  const s = validateSnapshot(raw), stats = weekStats(s, '2026-10-05', 'Asia/Shanghai');
  assert.equal(stats.confirmedMs, 6160000); assert.equal(stats.pendingMs, 0); assert.equal(stats.confirmedEventCount, 5);
  assert.equal(stats.confirmedByGroup['1'], 3600000); assert.equal(stats.confirmedByType['2'], 1800000);
  assert.equal(Object.values(stats.confirmedByGroup).reduce((a, b) => a + b, 0), stats.confirmedMs);
  const range = rangeStats(s, stats.days[0].start, s.createdAt);
  assert.equal(range.byGroup.find(g => g.typeId === '1')?.actualMillis, 3600000);
  assert.equal(range.byGroup.reduce((n, g) => n + g.actualMillis, 0), range.actualMillis);
});

test('activity groups reject dangling, self, circular, deep and duplicate preset references', () => {
  const mutations = [
    (s: Snapshot) => { s.recordTypes[0].parentTypeId = '99999'; },
    (s: Snapshot) => { s.recordTypes[0].parentTypeId = s.recordTypes[0].id; },
    (s: Snapshot) => { s.recordTypes[0].parentTypeId = s.recordTypes[1].id; s.recordTypes[1].parentTypeId = s.recordTypes[0].id; },
    (s: Snapshot) => { s.recordTypes[1].parentTypeId = s.recordTypes[0].id; s.recordTypes[2].parentTypeId = s.recordTypes[1].id; },
    (s: Snapshot) => { s.recordTypes[0].presetKey = 'same'; s.recordTypes[1].presetKey = 'same'; },
    (s: Snapshot) => { s.recordTypes[0].presetKey = ' '; },
  ];
  for (const mutate of mutations) { const s = validateSnapshot(fresh()); mutate(s); assert.throws(() => validateSnapshot(s)); }
});

test('native schema 37, 38 and 39 mirrors preserve defaults and reject contradictory grouping', () => {
  assert.equal(validateSnapshot(nativeFixture).upstreamData?.databaseVersion, 37);
  const v2 = structuredClone(nativeFixture); v2.schemaVersion = 2; v2.upstreamData.databaseVersion = 38;
  v2.recordTypes[0].timerPolicy = null; v2.records[0].recovery = null;
  v2.upstreamData.tables.recordTypes[0].timer_policy = null; v2.upstreamData.tables.records[0].recovery = null;
  assert.equal(validateSnapshot(v2).activityDefaultsVersion, 0);
  const v3 = structuredClone(v2); v3.schemaVersion = 3; v3.activityDefaultsVersion = 1; v3.upstreamData.databaseVersion = 39;
  v3.recordTypes[0].parentTypeId = null; v3.recordTypes[0].presetKey = 'learning';
  Object.assign(v3.upstreamData.tables.recordTypes[0], { parent_type_id: null, preset_key: 'learning' });
  v3.upstreamData.tables.lifeClockSettings[0].activity_defaults_version = '1';
  assert.equal(validateSnapshot(v3).recordTypes[0].presetKey, 'learning');
  const conflict = structuredClone(v3); conflict.upstreamData.tables.recordTypes[0].preset_key = 'other';
  assert.throws(() => validateSnapshot(conflict));
  delete v3.upstreamData.tables.lifeClockSettings[0].activity_defaults_version;
  assert.throws(() => validateSnapshot(v3));
});
