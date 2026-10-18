import { DateTime } from 'luxon';
import { repairIds, type Snapshot, type Experience } from './snapshot.js';
export type Block = { id: string; typeId: string; start: number; end: number; day: number; state: 'CONFIRMED' | 'PENDING' | 'RUNNING' };
export function groupId(s: Snapshot, typeId: string): string { return s.recordTypes.find(t => t.id === typeId)?.parentTypeId ?? typeId; }
export function weekStart(timestamp: number, zone: string): string { return DateTime.fromMillis(timestamp, { zone }).startOf('week').toISODate()!; }
export function weekStats(s: Snapshot, week: string, zone: string, people: string[] = [], environments: string[] = []) {
  const date = DateTime.fromISO(week, { zone }).startOf('day');
  if (!date.isValid || date.weekday !== 1) throw new Error('Invalid Monday');
  const days = Array.from({ length: 7 }, (_, i) => { const d = date.plus({ days: i }); return { date: d.toISODate()!, start: d.toMillis(), end: d.plus({ days: 1 }).toMillis() }; });
  const start = days[0].start, end = days[6].end, cutoff = Math.min(end, Math.max(start, s.createdAt));
  const invalid = repairIds(s);
  const matches = (tags: { tagId: string }[]) => (!people.length || tags.some(t => people.includes(t.tagId))) && (!environments.length || tags.some(t => environments.includes(t.tagId)));
  const matched = s.records.filter(r => !invalid.has(r.id) && r.timeStarted < cutoff && r.timeEnded > start && matches(r.tags));
  const records = matched.filter(r => !r.isUnfinishedFocus), unfinishedFocus = matched.filter(r => r.isUnfinishedFocus);
  const unfinishedFocusMs = unfinishedFocus.reduce((n, r) => n + Math.min(r.timeEnded, cutoff) - Math.max(r.timeStarted, start), 0);
  const blocks: Block[] = [];
  function add(id: string, typeId: string, from: number, to: number, state: Block['state']) {
    days.forEach((d, day) => { const a = Math.max(from, d.start), b = Math.min(to, d.end, cutoff); if (a < b) blocks.push({ id, typeId, start: a, end: b, day, state }); });
  }
  for (const r of records) add(r.id, r.typeId, r.timeStarted, r.timeEnded, 'CONFIRMED');
  for (const r of s.runningRecords.filter(r => r.timeStarted < cutoff && matches(r.tags))) {
    const overlap = s.records.some(v => v.timeEnded > v.timeStarted && v.timeStarted < s.createdAt && r.timeStarted < v.timeEnded);
    if (!overlap) add(r.id, r.id, r.timeStarted, cutoff, 'RUNNING');
  }
  const duration = (state: Block['state']) => blocks.filter(b => b.state === state).reduce((n, b) => n + b.end - b.start, 0);
  const byType: Record<string, number> = {}, confirmedByType: Record<string, number> = {}, byGroup: Record<string, number> = {}, confirmedByGroup: Record<string, number> = {};
  for (const b of blocks) {
    const group = groupId(s, b.typeId), ms = b.end - b.start;
    byType[b.typeId] = (byType[b.typeId] ?? 0) + ms; byGroup[group] = (byGroup[group] ?? 0) + ms;
    if (b.state === 'CONFIRMED') { confirmedByType[b.typeId] = (confirmedByType[b.typeId] ?? 0) + ms; confirmedByGroup[group] = (confirmedByGroup[group] ?? 0) + ms; }
  }
  const confirmed = records;
  function scores(id: string, minimum: number) { const values = confirmed.map(r => rating(r, id, minimum)); return { distribution: Array.from({length: 6 - minimum}, (_,i) => i + minimum).map(v => values.filter(n => n === v).length), ratedCount: values.filter(v => v !== null).length, unratedCount: values.filter(v => v === null).length }; }
  const recordedUnionMs = blocks.reduce((n, b) => n + b.end - b.start, 0);
  return { days, cutoff, elapsedMs: cutoff - start, recordedUnionMs, unrecordedMs: cutoff - start - recordedUnionMs - unfinishedFocusMs,
    unfinishedFocus, unfinishedFocusMs, unfinishedFocusCount: unfinishedFocus.length,
    confirmedMs: duration('CONFIRMED'), pendingMs: duration('PENDING'), runningMs: duration('RUNNING'),
    confirmedEventCount: confirmed.length, byType, confirmedByType, byGroup, confirmedByGroup, blocks, records,
    repairs: s.records.filter(r => invalid.has(r.id) && r.timeStarted < end && (r.timeEnded > start || r.timeStarted >= start)),
    happiness: scores(s.ratingTagIds.happinessTagId, -5), meaning: scores(s.ratingTagIds.meaningTagId, -5),
    dailyConfirmedMs: days.map((_, i) => blocks.filter(b => b.day === i && b.state === 'CONFIRMED').reduce((n, b) => n + b.end - b.start, 0)) };
}
export function rating(record: Experience, id: string, minimum = -5): number | null { const value = record.tags.find(t => t.tagId === id)?.numericValue; return Number.isInteger(value) && value! >= minimum && value! <= 5 ? value! : null; }
export function usableComparisons(s: Snapshot) { const invalid = repairIds(s); return s.records.filter(r => !r.isUnfinishedFocus && !invalid.has(r.id)); }
