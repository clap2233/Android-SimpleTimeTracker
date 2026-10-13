import { DateTime } from 'luxon';
import { repairIds, type Snapshot, type Experience } from './snapshot.js';
export type Block = { id: string; typeId: string; start: number; end: number; day: number; state: 'CONFIRMED' | 'PENDING' | 'RUNNING' };
export function weekStart(timestamp: number, zone: string): string { return DateTime.fromMillis(timestamp, { zone }).startOf('week').toISODate()!; }
export function weekStats(s: Snapshot, week: string, zone: string, people: string[] = [], environments: string[] = []) {
  const date = DateTime.fromISO(week, { zone }).startOf('day');
  if (!date.isValid || date.weekday !== 1) throw new Error('Invalid Monday');
  const days = Array.from({ length: 7 }, (_, i) => { const d = date.plus({ days: i }); return { date: d.toISODate()!, start: d.toMillis(), end: d.plus({ days: 1 }).toMillis() }; });
  const start = days[0].start, end = days[6].end, cutoff = Math.min(end, Math.max(start, s.createdAt));
  const invalid = repairIds(s);
  const matches = (tags: { tagId: string }[]) => (!people.length || tags.some(t => people.includes(t.tagId))) && (!environments.length || tags.some(t => environments.includes(t.tagId)));
  const records = s.records.filter(r => !invalid.has(r.id) && r.timeStarted < cutoff && r.timeEnded > start && matches(r.tags));
  const blocks: Block[] = [];
  function add(id: string, typeId: string, from: number, to: number, state: Block['state']) {
    days.forEach((d, day) => { const a = Math.max(from, d.start), b = Math.min(to, d.end, cutoff); if (a < b) blocks.push({ id, typeId, start: a, end: b, day, state }); });
  }
  for (const r of records) add(r.id, r.typeId, r.timeStarted, r.timeEnded, r.reviewState);
  for (const r of s.runningRecords.filter(r => r.timeStarted < cutoff && matches(r.tags))) {
    const overlap = s.records.some(v => v.timeEnded > v.timeStarted && v.timeStarted < s.createdAt && r.timeStarted < v.timeEnded);
    if (!overlap) add(r.id, r.id, r.timeStarted, cutoff, 'RUNNING');
  }
  const duration = (state: Block['state']) => blocks.filter(b => b.state === state).reduce((n, b) => n + b.end - b.start, 0);
  const byType: Record<string, number> = {}, confirmedByType: Record<string, number> = {};
  for (const b of blocks) { byType[b.typeId] = (byType[b.typeId] ?? 0) + b.end - b.start; if (b.state === 'CONFIRMED') confirmedByType[b.typeId] = (confirmedByType[b.typeId] ?? 0) + b.end - b.start; }
  const confirmed = records.filter(r => r.reviewState === 'CONFIRMED');
  function scores(id: string) { const values = confirmed.map(r => rating(r, id)); return { distribution: [1, 2, 3, 4, 5].map(v => values.filter(n => n === v).length), ratedCount: values.filter(v => v !== null).length, unratedCount: values.filter(v => v === null).length }; }
  const recordedUnionMs = blocks.reduce((n, b) => n + b.end - b.start, 0);
  return { days, cutoff, elapsedMs: cutoff - start, recordedUnionMs, unrecordedMs: cutoff - start - recordedUnionMs,
    confirmedMs: duration('CONFIRMED'), pendingMs: duration('PENDING'), runningMs: duration('RUNNING'),
    confirmedEventCount: confirmed.length, byType, confirmedByType, blocks, records,
    repairs: s.records.filter(r => invalid.has(r.id) && r.timeStarted < end && (r.timeEnded > start || r.timeStarted >= start)),
    happiness: scores(s.ratingTagIds.happinessTagId), meaning: scores(s.ratingTagIds.meaningTagId),
    dailyConfirmedMs: days.map((_, i) => blocks.filter(b => b.day === i && b.state === 'CONFIRMED').reduce((n, b) => n + b.end - b.start, 0)) };
}
export function rating(record: Experience, id: string): number | null { const value = record.tags.find(t => t.tagId === id)?.numericValue; return Number.isInteger(value) && value! >= 1 && value! <= 5 ? value! : null; }
export function usableComparisons(s: Snapshot) { const invalid = repairIds(s); return s.records.filter(r => !invalid.has(r.id)); }
