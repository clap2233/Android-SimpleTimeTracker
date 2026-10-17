import { z } from 'zod';
import { DateTime } from 'luxon';

const id = z.string().regex(/^[1-9][0-9]{0,18}$/).refine(s => BigInt(s) <= 9223372036854775807n);
const date = z.string().regex(/^\d{4}-\d{2}-\d{2}$/).refine(s => DateTime.fromISO(s, { zone: 'UTC' }).isValid);
const instant = z.number().int().refine(Number.isSafeInteger);
const status = z.enum(['OPEN', 'DONE', 'SKIPPED', 'CANCELLED']);
export const timerPolicySchema = z.object({ mode: z.enum(['ORDINARY', 'FOCUS_EXIT', 'INTERVAL']), intervalMinutes: z.number().int() }).strict()
  .refine(p => p.mode === 'INTERVAL' ? p.intervalMinutes >= 1 && p.intervalMinutes <= 1440 : p.intervalMinutes === 0);
export const timerSessionSchema = z.object({ id: z.string().uuid(), startedAt: instant.min(0), policy: timerPolicySchema, muted: z.boolean(), focusNotified: z.boolean() }).strict();
export const recoverySchema = z.enum(['MORE_ENERGY', 'UNCHANGED', 'MORE_TIRED']);
export const planTaskSchema = z.object({ id: z.string().uuid(), title: z.string().trim().min(1), note: z.string(), templateId: id.nullable().optional(),
  important: z.boolean().nullable().optional(), urgent: z.boolean().nullable().optional(), deadline: date.nullable().optional(), status, archived: z.boolean() }).strict();
export const planScheduleSchema = z.object({ id: z.string().uuid(), taskId: z.string().uuid().nullable().optional(), title: z.string().trim().min(1),
  kind: z.enum(['ACTIVITY', 'FREE', 'BUFFER']), firstDate: date, startMinute: z.number().int().min(0).max(1439), durationMinutes: z.number().int().min(1).max(1440),
  zoneId: z.string().refine(v => DateTime.now().setZone(v).isValid), repeat: z.enum(['NONE', 'DAILY', 'WEEKLY']), weekdays: z.array(z.number().int().min(1).max(7)),
  untilDate: date.nullable().optional(), remind: z.boolean() }).strict().refine(p => (!p.untilDate || p.untilDate >= p.firstDate) &&
    new Set(p.weekdays).size === p.weekdays.length && (p.repeat === 'WEEKLY' ? p.weekdays.length > 0 : p.weekdays.length === 0));
export const planExceptionSchema = z.object({ id: z.string(), scheduleId: z.string().uuid(), date, status,
  startMinute: z.number().int().min(0).max(1439).nullable().optional(), durationMinutes: z.number().int().min(1).max(1440).nullable().optional(),
  movedDate: date.nullable().optional(), title: z.string().trim().min(1).nullable().optional(),
  kind: z.enum(['ACTIVITY', 'FREE', 'BUFFER']).nullable().optional(), remind: z.boolean().nullable().optional() }).strict()
  .refine(p => p.id === `${p.scheduleId}@${p.date}`);
export const planLinkSchema = z.object({ id: z.string().uuid(), recordId: id.nullable().optional(), runningRecordId: id.nullable().optional(),
  occurrenceId: z.string().refine(v => { const parts = v.split('@'); return parts.length === 2 && z.string().uuid().safeParse(parts[0]).success && date.safeParse(parts[1]).success; }),
  caption: z.string(), timeStarted: instant, timeEnded: instant.nullable().optional() }).strict().refine(p =>
    Boolean(p.recordId) !== Boolean(p.runningRecordId) && (p.recordId ? p.timeEnded != null && p.timeEnded > p.timeStarted : p.timeEnded == null));
export const planningFields = {
  planTasks: z.array(planTaskSchema).default([]), planSchedules: z.array(planScheduleSchema).default([]),
  planExceptions: z.array(planExceptionSchema).default([]), planRecordLinks: z.array(planLinkSchema).default([]),
};
export const planningTables = ['planTasks', 'planSchedules', 'planExceptions', 'planRecordLinks'] as const;
export const planningSchemas = { planTasks: planTaskSchema, planSchedules: planScheduleSchema, planExceptions: planExceptionSchema, planRecordLinks: planLinkSchema };
export type Schedule = z.infer<typeof planScheduleSchema>;
export type Exception = z.infer<typeof planExceptionSchema>;
export function occurrences(schedules: Schedule[], exceptions: Exception[], from: number, until: number) {
  if (until <= from || until - from > 370 * 86400000) throw new Error('Invalid calendar range');
  const overrides = new Map(exceptions.map(e => [e.id, e]));
  const result: { id: string; date: string; schedule: Schedule; start: number; end: number; status: z.infer<typeof status> }[] = [];
  for (const schedule of schedules) {
    let day = DateTime.fromMillis(from, { zone: schedule.zoneId }).startOf('day').minus({ days: 1 });
    const dates = new Set<string>();
    while (+day < until) {
      dates.add(day.toISODate()!);
      day = day.plus({ days: 1 });
    }
    for (const exception of exceptions) if (exception.scheduleId === schedule.id && exception.movedDate) dates.add(exception.date);
    for (const text of dates) {
      const sourceDay = DateTime.fromISO(text, { zone: schedule.zoneId });
      if (text >= schedule.firstDate && (!schedule.untilDate || text <= schedule.untilDate) &&
          (schedule.repeat === 'DAILY' || schedule.repeat === 'WEEKLY' && schedule.weekdays.includes(sourceDay.weekday) || text === schedule.firstDate && schedule.repeat === 'NONE')) {
        const key = `${schedule.id}@${text}`, override = overrides.get(key), minute = override?.startMinute ?? schedule.startMinute;
        // Match Android Calendar: a repeated local hour uses the later occurrence.
        const local = DateTime.fromISO(override?.movedDate ?? text, { zone: schedule.zoneId }).set({ hour: Math.floor(minute / 60), minute: minute % 60 });
        const start = Math.max(...local.getPossibleOffsets().map(value => +value));
        const end = start + (override?.durationMinutes ?? schedule.durationMinutes) * 60000;
        const effective = { ...schedule, title: override?.title ?? schedule.title, kind: override?.kind ?? schedule.kind, remind: override?.remind ?? schedule.remind };
        if (start < until && end > from) result.push({ id: key, date: text, schedule: effective, start, end, status: override?.status ?? 'OPEN' });
      }
    }
  }
  return result.sort((a,b) => a.start - b.start);
}
