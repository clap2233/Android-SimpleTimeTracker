import { repairIds, type Experience, type Snapshot } from './snapshot.js';
import { groupId, rating } from './statistics.js';
import { occurrences } from './planning.js';

export function experienceScores(s: Snapshot, record: Experience) {
  const happiness = rating(record, s.ratingTagIds.happinessTagId);
  const meaning = rating(record, s.ratingTagIds.meaningTagId, -5);
  return { happiness, meaning, combined: happiness === null || meaning === null ? null : happiness + meaning, recovery: record.recovery ?? null };
}
export function recordText(s: Snapshot, r: Experience) {
  const tags = new Map(s.tags.map(t => [t.id, t]));
  return { id: r.id, typeId: r.typeId, groupTypeId: groupId(s, r.typeId), activity: s.recordTypes.find(t => t.id === r.typeId)?.name ?? '',
    timeStarted: r.timeStarted, timeEnded: r.timeEnded, summary: r.summary, comment: r.comment,
    isUnfinishedFocus: r.isUnfinishedFocus, unfinishedReason: r.unfinishedReason,
    zoneId: r.zoneId ?? null, templateId: r.templateId ?? null, ...experienceScores(s, r),
    tags: r.tags.map(t => ({ ...t, name: tags.get(t.tagId)?.name ?? '', purpose: tags.get(t.tagId)?.purpose ?? 'GENERAL' })),
    planLinks: s.planRecordLinks.filter(l => l.recordId === r.id) };
}
export function unionMillis(intervals: { start: number; end: number }[], from: number, until: number) {
  let end = from, total = 0;
  for (const i of [...intervals].sort((a, b) => a.start - b.start)) {
    const a = Math.max(from, end, i.start), b = Math.min(until, i.end);
    if (b > a) total += b - a;
    end = Math.max(end, b);
  }
  return total;
}
export function rangeStats(s: Snapshot, from: number, until: number) {
  const cutoff = Math.min(until, s.createdAt), invalid = repairIds(s);
  const valid = s.records.filter(r => !invalid.has(r.id) && r.timeStarted < cutoff && r.timeEnded > from);
  const records = valid.filter(r => !r.isUnfinishedFocus), unfinishedFocus = valid.filter(r => r.isUnfinishedFocus);
  const rated = records.map(r => experienceScores(s, r));
  const recovery = (values: typeof rated) => ({ MORE_ENERGY: values.filter(r => r.recovery === 'MORE_ENERGY').length,
    UNCHANGED: values.filter(r => r.recovery === 'UNCHANGED').length, MORE_TIRED: values.filter(r => r.recovery === 'MORE_TIRED').length,
    unrated: values.filter(r => r.recovery === null).length });
  const score = (key: 'happiness' | 'meaning', min: number) => ({ minimum: min,
    distribution: Array.from({ length: 6 - min }, (_, i) => rated.filter(r => r[key] === i + min).length),
    unrated: rated.filter(r => r[key] === null).length });
  const plans = occurrences(s.planSchedules, s.planExceptions, from, until);
  const active = plans.filter(p => p.status !== 'CANCELLED' && p.status !== 'SKIPPED');
  return { from, until, cutoff, recordCount: records.length,
    unfinishedFocus: { count: unfinishedFocus.length,
      actualMillis: unionMillis(unfinishedFocus.map(r => ({ start: r.timeStarted, end: r.timeEnded })), from, cutoff) },
    actualMillis: unionMillis(records.map(r => ({ start: r.timeStarted, end: r.timeEnded })), from, cutoff),
    happiness: score('happiness', -5), meaning: score('meaning', -5), recovery: recovery(rated),
    byType: [...new Set(records.map(r => r.typeId))].map(typeId => ({ typeId,
      actualMillis: unionMillis(records.filter(r => r.typeId === typeId).map(r => ({ start: r.timeStarted, end: r.timeEnded })), from, cutoff) })),
    byGroup: [...new Set(records.map(r => groupId(s, r.typeId)))].map(typeId => ({ typeId,
      actualMillis: unionMillis(records.filter(r => groupId(s, r.typeId) === typeId).map(r => ({ start: r.timeStarted, end: r.timeEnded })), from, cutoff) })),
    byTemplate: [...new Set(records.map(r => r.templateId ?? null))].map(id => {
      const subset = records.filter(r => (r.templateId ?? null) === id);
      return { templateId: id, count: subset.length, actualMillis: unionMillis(subset.map(r => ({ start: r.timeStarted, end: r.timeEnded })), from, cutoff),
        recovery: recovery(subset.map(r => experienceScores(s, r))) };
    }),
    plannedMillis: unionMillis(active.filter(p => p.schedule.kind !== 'BUFFER'), from, until),
    bufferMillis: unionMillis(active.filter(p => p.schedule.kind === 'BUFFER'), from, until),
    unplannedMillis: until - from - unionMillis(active, from, until),
    note: 'Unfinished focus is retained separately and excluded from formal time, ratings and recovery totals. Planned time is separate from actual experience. No aggregate life score is calculated.' };
}
