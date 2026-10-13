import { z } from 'zod';
import { DateTime } from 'luxon';
import { parseTree, type Node as JsonNode, type ParseError } from 'jsonc-parser';
import { nativeSchema, businessKinds } from './native-schema.js';

export const uuid = z.string().uuid();
export const entityId = z.string().regex(/^[1-9][0-9]{0,18}$/).refine(s => BigInt(s) <= 9223372036854775807n);
const zeroId = z.union([entityId, z.literal('0')]);
const time = z.number().int().refine(Number.isSafeInteger);
const zone = z.string().refine(v => DateTime.now().setZone(v).isValid);
const color = z.object({ colorId: z.number().int().min(-2147483648).max(2147483647),
  colorInt: z.string().default('').refine(s => s === '' || /^-?[0-9]+$/.test(s) && Number(s) >= -2147483648 && Number(s) <= 2147483647) }).strict();
const tagValue = z.object({ tagId: entityId, numericValue: z.number().nullable(), ratedAt: time.nullable().optional() }).strict();
const context = { comment: z.string().default(''), summary: z.string().default(''), zoneId: zone.nullable().optional(), templateId: entityId.nullable().optional() };
const record = z.object({ id: entityId, typeId: zeroId, timeStarted: time, timeEnded: time,
  tags: z.array(tagValue), reviewState: z.enum(['CONFIRMED', 'PENDING']).default('PENDING'), ...context }).strict();
const running = z.object({ id: entityId, timeStarted: time, tags: z.array(tagValue), ...context }).strict();
const ref = z.object({ recordId: entityId, caption: z.string().default('') }).strict();
const choice = z.object({ text: z.string().default(''), recordId: entityId.nullable().optional(), templateId: entityId.nullable().optional() }).strict()
  .refine(v => !(v.recordId && v.templateId));
const unique = <T>(values: T[]): boolean => new Set(values).size === values.length;
const softIds = z.array(entityId).refine(unique).default([]);
export const photoSchema = z.object({ id: entityId, recordId: entityId.nullable(), runningRecordId: entityId.nullable(),
  relativePath: z.string().regex(/^photos\/[a-zA-Z0-9_-]+\.(png|jpg|jpeg|webp|gif|bmp|heic|heif|avif)$/),
  sha256: z.string().regex(/^[a-f0-9]{64}$/), position: z.number().int().min(0).max(2147483647),
  mimeType: z.string().regex(/^image\/[a-z0-9.+-]+$/), sizeBytes: z.number().int().min(1).max(50 * 1024 * 1024) }).strict();
export const snapshotSchema = z.object({ schemaVersion: z.literal(1), dataSetId: uuid, snapshotId: uuid, createdAt: time.min(0),
  recordTypes: z.array(z.object({ id: entityId, name: z.string(), icon: z.string(), color, defaultDuration: time.min(0), note: z.string().default(''), hidden: z.boolean() }).strict()),
  categories: z.array(z.object({ id: entityId, name: z.string(), color, note: z.string().default('') }).strict()).default([]),
  tags: z.array(z.object({ id: entityId, name: z.string(), icon: z.string().default(''), color, iconColorSource: zeroId,
    note: z.string().default(''), archived: z.boolean(), valueType: z.enum(['NONE', 'NUMERIC']), valueSuffix: z.string().default(''), purpose: z.enum(['GENERAL', 'PERSON', 'ENVIRONMENT']).default('GENERAL') }).strict()),
  recordTypeCategories: z.array(z.object({ recordTypeId: entityId, categoryId: entityId }).strict()).default([]),
  recordTypeTags: z.array(z.object({ recordTypeId: entityId, tagId: entityId }).strict()).default([]),
  defaultRecordTypeTags: z.array(z.object({ recordTypeId: entityId, tagId: entityId }).strict()).default([]),
  records: z.array(record), runningRecords: z.array(running).max(1),
  ratingTagIds: z.object({ happinessTagId: entityId, meaningTagId: entityId }).strict(), photos: z.array(photoSchema),
  templates: z.array(z.object({ id: entityId, typeId: entityId, name: z.string().default(''), tags: z.array(tagValue), ...context }).strict()).default([]),
  settingShortcuts: z.array(z.object({ id: entityId, action: z.enum(['Multitasking', 'RetroactiveMode', 'Categories', 'Archive', 'DataEdit', 'SortActivities']) }).strict()).default([]),
  weeklyReviews: z.array(z.object({ id: entityId, weekStart: z.string().regex(/^\d{4}-\d{2}-\d{2}$/), zoneId: zone,
    timeAbundance: z.string().nullable().optional(), pleasantRecords: z.array(ref).default([]), unpleasantRecords: z.array(ref).default([]),
    keep: choice.nullable().optional(), adjust: choice.nullable().optional(), attentionRecordIds: softIds, pinnedTemplateIds: softIds.refine(v => v.length <= 3) }).strict()).default([]),
  trials: z.array(z.object({ id: entityId, reviewId: entityId.nullable().optional(), source: ref.nullable().optional(),
    description: z.string().refine(v => v.trim().length > 0), relatedRecordIds: softIds }).strict()).default([]),
  businessSettings: z.record(z.string(), z.unknown()).default({}), repairRecordIds: softIds,
  upstreamData: z.object({ databaseVersion: z.union([z.literal(36), z.literal(37)]), tables: z.record(z.string(), z.array(z.record(z.string(), z.unknown()))) }).strict().optional(),
}).strict();
export type Snapshot = z.infer<typeof snapshotSchema>;
export type Experience = Snapshot['records'][number];
export type Photo = Snapshot['photos'][number];

export function check(condition: unknown, message: string): asserts condition { if (!condition) throw new Error(message); }
export function strictJson(text: string, integerStrings = false): unknown {
  const errors: ParseError[] = [];
  const tree = parseTree(text, errors, { allowTrailingComma: false, disallowComments: true });
  check(tree && !errors.length, 'Invalid JSON');
  function visit(node: JsonNode): unknown {
    if (node.type === 'object') {
      const result = Object.create(null) as Record<string, unknown>;
      for (const pair of node.children ?? []) {
        const [key, value] = pair.children!;
        check(!Object.hasOwn(result, key.value), 'Duplicate JSON key');
        result[key.value] = visit(value);
      }
      return result;
    }
    if (node.type === 'array') return node.children!.map(visit);
    if (integerStrings && node.type === 'number') {
      const raw = text.slice(node.offset, node.offset + node.length);
      check(/^-?\d+$/.test(raw), 'Invalid internal ID');
      return raw;
    }
    return node.value;
  }
  return visit(tree);
}

export function repairIds(s: Snapshot): Set<string> {
  const invalid = new Set<string>();
  const intervals = s.records.map(r => {
    if (r.timeEnded <= r.timeStarted || r.timeEnded > s.createdAt || r.reviewState === 'CONFIRMED' && (r.timeEnded - r.timeStarted) % 900000) invalid.add(r.id);
    return { id: r.id, start: r.timeStarted, end: r.timeEnded };
  });
  intervals.push(...s.runningRecords.map(r => ({ id: '0', start: r.timeStarted, end: s.createdAt })));
  let last: typeof intervals[number] | undefined;
  for (const current of intervals.filter(r => r.end > r.start).sort((a, b) => a.start - b.start)) {
    if (last && current.start < last.end) { if (current.id !== '0') invalid.add(current.id); if (last.id !== '0') invalid.add(last.id); }
    if (!last || current.end > last.end) last = current;
  }
  return invalid;
}

export function validateSnapshot(input: unknown): Snapshot {
  const s = snapshotSchema.parse(input);
  function ids(rows: { id: string }[]) { check(unique(rows.map(r => r.id)), 'Duplicate ID'); return new Set(rows.map(r => r.id)); }
  const types = ids(s.recordTypes), tags = ids(s.tags), categories = ids(s.categories), records = ids(s.records), running = ids(s.runningRecords), reviews = ids(s.weeklyReviews);
  const templates = ids(s.templates), shortcuts = ids(s.settingShortcuts); ids(s.photos); ids(s.trials);
  check([...templates].every(id => !shortcuts.has(id)), 'Conflicting shortcut ID');
  const rating = Object.values(s.ratingTagIds); check(unique(rating), 'Conflicting rating identities');
  check(rating.every(id => s.tags.some(t => t.id === id && t.valueType === 'NUMERIC')), 'Missing rating identity');
  for (const tag of s.tags) check(tag.iconColorSource === '0' || types.has(tag.iconColorSource), 'Missing icon source');
  for (const r of [...s.records, ...s.runningRecords, ...s.templates]) {
    check(unique(r.tags.map(t => t.tagId)), 'Duplicate record tag');
    for (const t of r.tags) {
      check(tags.has(t.tagId), 'Missing tag');
      if (rating.includes(t.tagId)) check(t.numericValue === null || Number.isInteger(t.numericValue) && t.numericValue >= 1 && t.numericValue <= 5, 'Invalid rating');
    }
  }
  for (const r of s.records) check(r.typeId === '0' || types.has(r.typeId), 'Missing type');
  for (const r of s.runningRecords) check(types.has(r.id) && r.timeStarted <= s.createdAt, 'Invalid running record');
  for (const t of s.templates) check(types.has(t.typeId) && !t.tags.some(v => rating.includes(v.tagId)), 'Invalid template');
  for (const relation of [...s.recordTypeTags, ...s.defaultRecordTypeTags]) check(types.has(relation.recordTypeId) && tags.has(relation.tagId), 'Invalid tag relation');
  for (const r of s.recordTypeCategories) check(types.has(r.recordTypeId) && categories.has(r.categoryId), 'Invalid category relation');
  for (const rows of [s.recordTypeTags, s.defaultRecordTypeTags, s.recordTypeCategories]) check(unique(rows.map(v => JSON.stringify(v))), 'Duplicate relation');
  const files = new Map<string, string>();
  for (const p of s.photos) {
    check(Boolean(p.recordId) !== Boolean(p.runningRecordId), 'Invalid photo owner');
    check(p.recordId ? records.has(p.recordId) : running.has(p.runningRecordId!), 'Missing photo owner');
    const signature = `${p.sha256}:${p.sizeBytes}:${p.mimeType}`;
    check(!files.has(p.relativePath) || files.get(p.relativePath) === signature, 'Conflicting photo'); files.set(p.relativePath, signature);
  }
  const weeks = new Set<string>();
  for (const r of s.weeklyReviews) {
    const day = DateTime.fromISO(r.weekStart, { zone: r.zoneId });
    check(day.isValid && day.toISODate() === r.weekStart && day.weekday === 1, 'Invalid review week');
    const key = `${r.weekStart}:${r.zoneId}`; check(!weeks.has(key), 'Duplicate review week'); weeks.add(key);
    check(unique(r.pleasantRecords.map(v => v.recordId)) && unique(r.unpleasantRecords.map(v => v.recordId)), 'Duplicate chosen experience');
  }
  for (const t of s.trials) check(!t.reviewId || reviews.has(t.reviewId), 'Missing review');
  const repairs = repairIds(s); check(repairs.size === s.repairRecordIds.length && s.repairRecordIds.every(id => repairs.has(id)), 'Incorrect repair declarations');
  for (const [key, value] of Object.entries(s.businessSettings)) {
    check(Object.hasOwn(businessKinds, key), 'Unknown business setting');
    const kind = businessKinds[key as keyof typeof businessKinds];
    check(kind === 'BOOLEAN' ? typeof value === 'boolean' : kind === 'STRING' ? typeof value === 'string' : kind === 'SET' ? Array.isArray(value) && value.every(v => typeof v === 'string') :
      typeof value === 'number' && Number.isSafeInteger(value) && (kind !== 'INT' || value >= -2147483648 && value <= 2147483647), 'Invalid business setting');
  }
  if (s.upstreamData) validateNative(s);
  return s;
}

function validateNative(s: Snapshot) {
  const tables = s.upstreamData!.tables;
  for (const [name, rows] of Object.entries(tables)) {
    check(Object.hasOwn(nativeSchema, name), 'Unknown native table');
    const columns = nativeSchema[name as keyof typeof nativeSchema] as Record<string, { type: string; required: boolean }>;
    for (const row of rows) {
      check(Object.keys(row).every(c => Object.hasOwn(columns, c)), 'Unknown native column');
      for (const [c, spec] of Object.entries(columns)) {
        check(Object.hasOwn(row, c), 'Missing native column');
        const v = row[c];
        check(v === null ? !spec.required : spec.type === 'INTEGER' ? typeof v === 'string' && /^-?\d+$/.test(v) && BigInt(v) >= -9223372036854775808n && BigInt(v) <= 9223372036854775807n :
          spec.type === 'REAL' ? typeof v === 'number' && Number.isFinite(v) : typeof v === 'string', 'Invalid native value');
      }
    }
  }
  const rows = (name: string) => tables[name] ?? [];
  const metadata = rows('lifeClockSettings');
  check(metadata.length === 1 && metadata[0].data_set_id === s.dataSetId && metadata[0].happiness_tag_id === s.ratingTagIds.happinessTagId && metadata[0].meaning_tag_id === s.ratingTagIds.meaningTagId, 'Conflicting native identity');
  const projection = (name: string, data: { id: string }[], map: (r: Record<string, unknown>) => Record<string, unknown>) => {
    const actual = rows(name).map(map); check(actual.length === data.length, 'Conflicting native collection');
    const indexed = new Map(actual.map(r => [r.id, r])); check(indexed.size === actual.length, 'Duplicate native ID');
    for (const item of data) { const value = indexed.get(item.id); check(value, 'Missing native row'); for (const [key, val] of Object.entries(value)) check(canonical(val) === canonical((item as Record<string, unknown>)[key]), `Conflicting native ${key}`); }
  };
  const nativeColor = (r: Record<string, unknown>) => ({ colorId: Number(r.color), colorInt: r.color_int });
  projection('recordTypes', s.recordTypes, r => ({ id: r.id, name: r.name, icon: r.icon, color: nativeColor(r), note: r.note, hidden: r.hidden === '1', defaultDuration: Number(r.instantDuration) }));
  projection('categories', s.categories, r => ({ id: r.id, name: r.name, color: nativeColor(r), note: r.note }));
  projection('recordTags', s.tags, r => ({ id: r.id, name: r.name, icon: r.icon, color: nativeColor(r), note: r.note, iconColorSource: r.icon_color_source,
    archived: r.archived === '1', valueType: r.value_type === '1' ? 'NUMERIC' : 'NONE', valueSuffix: r.value_suffix ?? '', purpose: ['GENERAL','PERSON','ENVIRONMENT'][Number(r.purpose)] }));
  const values = (table: string, key: string, id: unknown, template = false) => rows(table).filter(v => v[key] === id)
    .filter(v => !template || !Object.values(s.ratingTagIds).includes(v.record_tag_id as string))
    .map(v => ({ tagId: v.record_tag_id, numericValue: v.record_tag_numeric_value, ratedAt: template ? null : v.rated_at === null ? null : Number(v.rated_at) }));
  const context = (r: Record<string, unknown>, table: string, key: string, template = false) => ({ id: r.id, summary: r.summary, comment: r.comment,
    ...(template ? {} : { zoneId: r.zone_id, templateId: r.template_id }), tags: values(table, key, r.id, template) });
  projection('records', s.records, r => ({ ...context(r, 'recordToRecordTag', 'record_id'), typeId: r.type_id, timeStarted: Number(r.time_started), timeEnded: Number(r.time_ended), reviewState: r.review_state === '1' ? 'CONFIRMED' : 'PENDING' }));
  projection('runningRecords', s.runningRecords, r => ({ ...context(r, 'runningRecordToRecordTag', 'running_record_id'), timeStarted: Number(r.time_started) }));
  const nativeShortcuts = rows('recordShortcuts');
  check(nativeShortcuts.length === s.templates.length + s.settingShortcuts.length, 'Conflicting shortcuts');
  const original = tables.recordShortcuts;
  tables.recordShortcuts = nativeShortcuts.filter(r => r.target_type === '0');
  projection('recordShortcuts', s.templates, r => ({ ...context(r, 'recordShortcutToRecordTag', 'shortcut_id', true), typeId: r.type_id, name: r.name }));
  tables.recordShortcuts = original;
  const actions = ['Multitasking','RetroactiveMode','Categories','Archive','DataEdit','SortActivities'];
  for (const item of s.settingShortcuts) check(nativeShortcuts.some(r => r.id === item.id && r.target_type === '1' && actions[Number(r.setting_action)] === item.action), 'Conflicting setting shortcut');
  projection('recordPhotos', s.photos, r => ({ id: r.id, recordId: r.record_id, runningRecordId: r.running_record_id, relativePath: r.relative_path, sha256: r.sha256, position: Number(r.position) }));
  for (const [table, collection, left, right, a, b] of [
    ['recordTypeCategory','recordTypeCategories','record_type_id','category_id','recordTypeId','categoryId'],
    ['recordTypeToTag','recordTypeTags','record_type_id','record_tag_id','recordTypeId','tagId'],
    ['recordTypeToDefaultTag','defaultRecordTypeTags','record_type_id','record_tag_id','recordTypeId','tagId']] as const) {
    check(canonical(rows(table).map(r => ({ [a]: r[left], [b]: r[right] }))) === canonical(s[collection]), 'Conflicting native relation');
  }
  projection('weeklyReviews', s.weeklyReviews, r => {
    const content = strictJson(r.content_json as string, true) as Record<string, unknown>;
    const normalizeChoice = (v: unknown) => v ? { text: '', recordId: null, templateId: null, ...v as object } : null;
    return { timeAbundance: null, pleasantRecords: [], unpleasantRecords: [], attentionRecordIds: [], pinnedTemplateIds: [], ...content,
      keep: normalizeChoice(content.keep), adjust: normalizeChoice(content.adjust), id: r.id, weekStart: r.week_start, zoneId: r.zone_id };
  });
  projection('reviewTrials', s.trials, r => ({ source: null, relatedRecordIds: [], ...strictJson(r.content_json as string, true) as object, id: r.id, reviewId: r.review_id }));
}

export function canonical(value: unknown): string {
  if (Array.isArray(value)) return '[' + value.map(canonical).sort().join(',') + ']';
  if (value && typeof value === 'object') return '{' + Object.entries(value).sort(([a],[b]) => a.localeCompare(b)).map(([k,v]) => JSON.stringify(k) + ':' + canonical(v)).join(',') + '}';
  return JSON.stringify(value ?? null);
}
