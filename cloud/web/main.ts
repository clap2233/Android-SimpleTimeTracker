import './style.css';
import { DateTime } from 'luxon';
import { validateSnapshot, type Snapshot, type Experience } from '../shared/snapshot.js';
import { weekStart, weekStats, rating, usableComparisons } from '../shared/statistics.js';
import { experienceScores, rangeStats, unionMillis } from '../shared/analysis.js';
import { occurrences } from '../shared/planning.js';

const app = document.querySelector<HTMLElement>('#app')!;
let token = '', snapshot: Snapshot | undefined, week = '', zone = '', people: string[] = [], environments: string[] = [];
let selectedDay = 0;
let photoUrls: string[] = [];
let session = 0;
const palette = ['#000000','#f53639','#e91e63','#9c27b0','#673ab7','#3f51b5','#2196f3','#03a9f4','#00bcd4','#009688','#4caf50','#8bc34a','#cddc39','#ffeb3b','#ffc107','#ff9800','#ff5722','#795548','#607d8b'];
function element<K extends keyof HTMLElementTagNameMap>(tag: K, text = '', className = ''): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag); el.textContent = text; el.className = className; return el;
}
function button(text: string, action: () => void, className = '') { const el = element('button', text, className); el.type = 'button'; el.addEventListener('click', action); return el; }
function duration(ms: number) { const hours = Math.floor(ms / 3600000), minutes = Math.floor(ms % 3600000 / 60000), seconds = Math.floor(ms % 60000 / 1000); return `${hours} 小时 ${minutes} 分${seconds ? ` ${seconds} 秒` : ''}`; }
function color(type: Snapshot['recordTypes'][number] | undefined) {
  if (!type) return '#aeb3ae';
  const argb = (Number(type.color.colorInt) >>> 0).toString(16).padStart(8, '0');
  return type.color.colorInt ? '#' + argb.slice(2) + argb.slice(0, 2) : palette[type.color.colorId] ?? '#aeb3ae';
}
function title(r: Experience) { return r.summary || snapshot!.recordTypes.find(t => t.id === r.typeId)?.name || '未分类'; }
function clearMedia() { photoUrls.forEach(URL.revokeObjectURL); photoUrls = []; }
async function api(path: string) {
  const response = await fetch(`/api/backups/${path}`, { headers: { authorization: `Bearer ${token}` }, cache: 'no-store' });
  if (!response.ok) throw new Error(response.status === 401 || response.status === 403 ? '只读凭据不正确，请重新输入。' : '暂时无法读取备份，请稍后重试。');
  return response;
}
function login(message = '') {
  session++; clearMedia(); snapshot = undefined; app.replaceChildren();
  const cover = element('section', '', 'cover');
  cover.append(element('span', 'ANDROID → WINDOWS', 'eyebrow'), element('h1', '生活时间钟'), element('p', '翻阅时间，也翻阅体验。', 'lead'),
    element('p', '电脑只读最近一次完整备份。记录、评分与照片，仍在手机上填写。', 'muted'));
  const form = element('form', '', 'login'); const key = element('input'); key.type = 'password'; key.required = true; key.autocomplete = 'off'; key.id = 'read-key';
  const label = element('label', '电脑只读凭据'); label.htmlFor = key.id;
  const submit = element('button', '打开生活回顾'); submit.type = 'submit';
  const status = element('p', message, 'status'); status.setAttribute('role', 'status');
  form.append(element('span', 'READ ONLY', 'eyebrow'), label, key, submit, status);
  form.addEventListener('submit', async event => {
    event.preventDefault(); token = key.value.trim(); key.value = ''; submit.disabled = true; status.textContent = '正在读取最近一次完整备份…';
    try { await load(); } catch (error) { status.textContent = (error as Error).message; submit.disabled = false; }
  });
  cover.append(form); app.append(cover);
}
async function load() {
  const current = ++session;
  const latest = (await (await api('latest')).json()).latest as { snapshotId: string; createdAt: number; dataSetId: string } | null;
  if (current !== session) return;
  if (!latest) { login('还没有云备份。请在手机「设置 → 手动云备份」完成首次上传。'); return; }
  const next = validateSnapshot(await (await api(latest.snapshotId)).json());
  if (current !== session) return;
  if (next.snapshotId !== latest.snapshotId || next.dataSetId !== latest.dataSetId || next.createdAt !== latest.createdAt) throw new Error('备份版本不一致，请重新读取。');
  clearMedia(); snapshot = next;
  zone = next.weeklyReviews.find(r => r.weekStart === weekStart(next.createdAt, r.zoneId))?.zoneId
    ?? next.records.find(r => r.zoneId)?.zoneId ?? Intl.DateTimeFormat().resolvedOptions().timeZone;
  week = weekStart(next.createdAt, zone); people = []; environments = [];
  selectedDay = DateTime.fromMillis(next.createdAt, { zone }).weekday - 1; render();
}
function render() {
  if (!snapshot) return;
  const s = snapshot, stats = weekStats(s, week, zone, people, environments);
  app.replaceChildren();
  const header = element('header', '', 'masthead');
  header.append(element('h1', '生活时间钟'), element('span', '电脑只读', 'badge'));
  const actions = element('div', '', 'actions');
  const refresh = button('读取最新备份', () => { refresh.disabled = true; void load().catch(e => { refresh.disabled = false; showMessage((e as Error).message); }); });
  actions.append(refresh, button('退出', () => { token = ''; login(); })); header.append(actions); app.append(header);
  const stamp = DateTime.fromMillis(s.createdAt, { zone }).toFormat('yyyy-MM-dd HH:mm:ss');
  app.append(element('p', `备份于 ${stamp} · ${zone}。统计截止于这个时刻，之后的时间不算漏记。`, 'backup-note'));
  const weekbar = element('section', '', 'weekbar');
  weekbar.append(button('← 上一周', () => { week = DateTime.fromISO(week, { zone }).minus({ weeks: 1 }).toISODate()!; render(); }),
    element('h2', `${week} — ${stats.days[6].date}`), button('下一周 →', () => { week = DateTime.fromISO(week, { zone }).plus({ weeks: 1 }).toISODate()!; render(); }));
  const zoneLabel = element('label', '查看时区 '); const zoneInput = element('input'); zoneInput.value = zone; zoneInput.setAttribute('aria-label', '查看时区');
  zoneInput.addEventListener('change', () => { if (DateTime.now().setZone(zoneInput.value).isValid) { zone = zoneInput.value; week = weekStart(stats.days[0].start + 12 * 3600000, zone); render(); } else { zoneInput.value = zone; showMessage('请输入有效时区，例如 Asia/Shanghai。'); } });
  zoneLabel.append(zoneInput); weekbar.append(zoneLabel); app.append(weekbar);
  const filters = element('section', '', 'filters');
  for (const [purpose, label, selected] of [['PERSON', '人物', people], ['ENVIRONMENT', '环境', environments]] as const) {
    const group = element('fieldset'); group.append(element('legend', label));
    for (const tag of s.tags.filter(t => t.purpose === purpose)) {
      const wrapper = element('label', '', 'chip'), input = element('input'); input.type = 'checkbox'; input.checked = selected.includes(tag.id);
      input.addEventListener('change', () => { const data = selected.filter(id => id !== tag.id); if (input.checked) data.push(tag.id); if (purpose === 'PERSON') people = data; else environments = data; render(); });
      wrapper.append(input, document.createTextNode(tag.name)); group.append(wrapper);
    }
    filters.append(group);
  }
  filters.append(button('清除筛选', () => { people = []; environments = []; render(); })); app.append(filters);
  const summary = element('section', '', 'summary');
  for (const [label, value] of [['已确认', stats.confirmedMs], ['待整理', stats.pendingMs], ['进行中', stats.runningMs], [people.length || environments.length ? '未匹配筛选' : '未记录', stats.unrecordedMs]] as const) {
    const item = element('div'); item.append(element('span', label, 'eyebrow'), element('strong', duration(value))); summary.append(item);
  }
  app.append(summary);
  const spread = element('section', '', 'spread'); const time = element('section', '', 'timeline-panel');
  time.append(element('h2', '一周色带'), element('p', '实色已确认 · 虚线待整理 · 实边框进行中 · 淡底色为未来。点击色块查看经历。', 'muted'));
  const grid = element('div', '', 'week-grid'), labels = element('div', '', 'hour-labels');
  for (let i = 0; i <= 24; i++) { const label = element('span', `${String(i).padStart(2,'0')}:00`); label.style.top = `${i / 24 * 100}%`; labels.append(label); }
  grid.append(labels);
  stats.days.forEach((day, index) => {
    const column = element('div', '', 'day-column'); const heading = button(day.date.slice(5), () => { selectedDay = index; render(); document.querySelector('#day-records')?.scrollIntoView({ block: 'nearest' }); }, selectedDay === index ? 'day selected' : 'day');
    column.append(heading); const track = element('div', '', 'track');
    const point = (timestamp: number) => { const d = DateTime.fromMillis(timestamp, { zone }); return d.hour * 60 + d.minute + d.second / 60 + d.millisecond / 60000; };
    if (stats.cutoff < day.end) { const future = element('div', '', 'future'); future.style.top = `${stats.cutoff <= day.start ? 0 : point(stats.cutoff) / 14.4}%`; track.append(future); }
    if (day.end - day.start !== 86400000) heading.title = '夏令时切换日：时长按真实经过时间计算，重复时段分列。';
    for (const block of stats.blocks.filter(b => b.day === index)) {
      const segments: [number, number][] = []; let part = block.start;
      for (let t = block.start; t < block.end; t = Math.min(t + 60000, block.end)) {
        const next = Math.min(t + 60000, block.end);
        if (next < block.end && DateTime.fromMillis(t, { zone }).offset !== DateTime.fromMillis(next, { zone }).offset) { segments.push([part, next]); part = next; }
      }
      segments.push([part, block.end]);
      for (const [from, to] of segments) {
        const record = s.records.find(r => r.id === block.id), active = s.runningRecords.find(r => r.id === block.id);
        const caption = block.state === 'RUNNING' ? active?.summary || '进行中' : record ? title(record) : '经历';
        const tile = button('', () => detail(block.id, block.state === 'RUNNING'), `block ${block.state.toLowerCase()}`);
        tile.setAttribute('aria-label', `${day.date} ${caption} ${duration(to - from)}`); tile.title = `${caption} · ${duration(to - from)}`;
        const a = point(from), b = to === day.end ? 1440 : point(to - 1) + 1 / 60000;
        tile.style.top = `${a / 14.4}%`; tile.style.height = `${Math.max(.2, (b - a) / 14.4)}%`; tile.style.backgroundColor = color(s.recordTypes.find(t => t.id === block.typeId));
        if (day.end - day.start > 86400000) { tile.style.width = '47%'; tile.style.left = DateTime.fromMillis(from, { zone }).offset === DateTime.fromMillis(day.start, { zone }).offset ? '2%' : '51%'; }
        track.append(tile);
      }
    }
    column.append(track); grid.append(column);
  });
  time.append(grid); spread.append(time);
  const aside = element('aside', '', 'analysis'); aside.append(element('h2', '时间花在哪里'), element('p', `已确认 ${duration(stats.confirmedMs)}；待整理与进行中单列。`, 'muted'));
  for (const [id] of Object.entries(stats.byType).sort(([a],[b]) => (stats.confirmedByType[b] ?? 0) - (stats.confirmedByType[a] ?? 0))) {
    const type = s.recordTypes.find(t => t.id === id), item = element('div', '', 'category');
    const heading = element('strong', type?.name ?? '未分类'); heading.style.borderLeftColor = color(type);
    const amount = stats.confirmedByType[id] ?? 0, bar = element('div', '', 'bar'); bar.style.width = `${stats.confirmedMs ? amount / stats.confirmedMs * 100 : 0}%`; bar.style.backgroundColor = color(type);
    const pending = stats.blocks.filter(b => b.typeId === id && b.state === 'PENDING').reduce((n,b) => n+b.end-b.start,0), running = stats.blocks.filter(b => b.typeId === id && b.state === 'RUNNING').reduce((n,b) => n+b.end-b.start,0);
    item.append(heading, element('span', duration(amount)), bar, element('small', `待整理 ${duration(pending)} · 进行中 ${duration(running)}`)); aside.append(item);
  }
  for (const [name, scores] of [['快乐', stats.happiness], ['意义', stats.meaning]] as const) {
    const group = element('section', '', 'scores'); group.append(element('h3', name), element('p', `${scores.ratedCount} 条已评分 · ${scores.unratedCount} 条未评分`, 'muted'));
    const bins = element('div', '', 'bins'); scores.distribution.forEach((count,i) => { const bin = element('div'); bin.append(element('strong', String(count)), element('span', `${i + (name === '意义' ? -5 : 1)} 分`)); bins.append(bin); }); group.append(bins); aside.append(group);
  }
  spread.append(aside); app.append(spread);
  const dayRecords = element('section', '', 'day-records'); dayRecords.id = 'day-records'; dayRecords.append(element('h2', `${stats.days[selectedDay].date} 的经历`));
  const records = stats.records.filter(r => r.timeStarted < stats.days[selectedDay].end && r.timeEnded > stats.days[selectedDay].start).sort((a,b) => a.timeStarted-b.timeStarted);
  if (!records.length) dayRecords.append(element('p', '这一天没有匹配的已记录经历。', 'muted'));
  records.forEach(r => dayRecords.append(button(`${DateTime.fromMillis(r.timeStarted, { zone }).toFormat('HH:mm')}  ${title(r)} · ${duration(r.timeEnded-r.timeStarted)} · ${r.reviewState === 'CONFIRMED' ? '已确认' : '待整理'}`, () => detail(r.id), 'record-row'))); app.append(dayRecords);
  if (stats.repairs.length) app.append(element('p', `另有 ${stats.repairs.length} 条待修复区间未计入统计，请在手机查看和整理。`, 'notice'));
  const review = s.weeklyReviews.find(r => r.weekStart === week && r.zoneId === zone);
  const reflection = element('section', '', 'reflection'); reflection.append(element('span', '本周的选择', 'eyebrow'), element('h2', '下周想保留什么？'));
  if (!review) reflection.append(element('p', '手机尚未保存这周的回顾。回顾可以全部跳过，也可以只写一件事。', 'muted'));
  else {
    for (const [label, text] of [['时间够用吗', review.timeAbundance], ['想保留', review.keep?.text], ['想减少或调整', review.adjust?.text]] as const) { const entry = element('div', '', 'review-choice'); entry.append(element('h3', label), element('p', text || '尚未填写')); reflection.append(entry); }
    for (const [label, refs] of [['愉快经历',review.pleasantRecords],['不愉快经历',review.unpleasantRecords]] as const) { const group = element('div'); group.append(element('h3', label)); refs.forEach(ref => { const r = s.records.find(v => v.id === ref.recordId); group.append(r ? button(ref.caption || title(r), () => detail(r.id), 'memory') : element('p', `${ref.caption || '经历'}（原记录已删除，回顾文字保留）`)); }); if (!refs.length) group.append(element('p','未选择','muted')); reflection.append(group); }
    if (review.attentionRecordIds.length) { const group = element('div'); group.append(element('h3','想多留意的经历')); review.attentionRecordIds.forEach(id => { const r = s.records.find(v => v.id === id); group.append(r ? button(title(r),()=>detail(id),'memory') : element('p','原经历已删除')); }); reflection.append(group); }
    const priorities = element('div', '', 'priorities'); priorities.append(element('h3','最近希望留出时间的事'));
    for (const id of review.pinnedTemplateIds) {
      const template = s.templates.find(t=>t.id===id); const actual = stats.records.filter(r=>r.templateId===id), ids=actual.map(r=>r.id);
      const confirmed = actual.filter(r=>r.reviewState==='CONFIRMED'); const spent=(state:string)=>stats.blocks.filter(b=>b.state===state && (state==='RUNNING'? s.runningRecords.some(r=>r.id===b.id&&r.templateId===id):ids.includes(b.id))).reduce((n,b)=>n+b.end-b.start,0);
      priorities.append(element('strong',template?.name||template?.summary||'已删除的模板'), element('p',`已确认 ${confirmed.length} 次 ${duration(spent('CONFIRMED'))} · 待整理 ${actual.length-confirmed.length} 次 ${duration(spent('PENDING'))} · 进行中 ${duration(spent('RUNNING'))}`));
    }
    if (!review.pinnedTemplateIds.length) priorities.append(element('p','没有置顶活动','muted')); reflection.append(priorities);
    const trials=element('div','','trials');trials.append(element('h3','换一种方式试一次'));
    const usable=usableComparisons(s);
    for(const trial of s.trials.filter(t=>t.reviewId===review.id)){
      trials.append(element('strong',trial.description)); const baseline=usable.find(r=>r.id===trial.source?.recordId);
      if(trial.source) trials.append(element('p',`原经历：${trial.source.caption||baseline&&title(baseline)||'来源已删除'}`));
      if(baseline) trials.append(button(`原体验 · ${scores(baseline)} · ${context(baseline)} · ${duration(baseline.timeEnded-baseline.timeStarted)}`,()=>detail(baseline.id),'memory'));
      const linked=usable.filter(r=>trial.relatedRecordIds.includes(r.id)); linked.forEach(r=>trials.append(button(`尝试 · ${title(r)} · ${scores(r)} · ${context(r)} · ${duration(r.timeEnded-r.timeStarted)}`,()=>detail(r.id),'memory')));
      if(!linked.length)trials.append(element('p','还没有关联实际经历；这不算失败。','muted'));
    }
    if(!s.trials.some(t=>t.reviewId===review.id))trials.append(element('p','没有新增尝试','muted'));reflection.append(trials);
  }
  app.append(reflection);
  renderPlanning(s, stats.days[0].start, stats.days[6].end);
  app.append(element('footer', '此页只供查看。修改与下一次记录，请回到手机。'));
}
function scores(r: Experience) {
  const { happiness, meaning, combined, recovery } = experienceScores(snapshot!, r);
  const total = combined === null ? '未计算' : combined > 0 ? `+${combined}` : String(combined);
  return `快乐 ${happiness ?? '未评分'} / 意义 ${meaning ?? '未评分'} / 合计 ${total} / 恢复感 ${recovery === 'MORE_ENERGY' ? '更有精神' : recovery === 'UNCHANGED' ? '差不多' : recovery === 'MORE_TIRED' ? '更累' : '未填写'}`;
}
function renderPlanning(s: Snapshot, from: number, until: number) {
  const stats = rangeStats(s, from, until), panel = element('section', '', 'reflection');
  panel.append(element('h2', '恢复感与个人节奏'), element('p', '统计本周已确认经历，不随人物／环境筛选变化；未填写保持为空。', 'muted'));
  const summary = (r: typeof stats.recovery) => `更有精神 ${r.MORE_ENERGY} · 差不多 ${r.UNCHANGED} · 更累 ${r.MORE_TIRED} · 未填写 ${r.unrated}`;
  panel.append(element('p', summary(stats.recovery)));
  for (const item of stats.byTemplate) {
    const name = s.templates.find(t => t.id === item.templateId)?.name || (item.templateId ? '已删除的模板' : '未使用模板');
    panel.append(element('p', `${name}：${summary(item.recovery)}`));
  }
  panel.append(element('h2', '计划与实际经历'), element('p', `计划占用 ${duration(stats.plannedMillis)} · 缓冲 ${duration(stats.bufferMillis)} · 未安排 ${duration(stats.unplannedMillis)}。重叠按区间去重，缓冲单列；空白不等于休息。`, 'muted'));
  const status = { OPEN: '待进行', DONE: '已完成', SKIPPED: '已跳过', CANCELLED: '已取消' };
  for (const plan of occurrences(s.planSchedules, s.planExceptions, from, until)) {
    const row = element('div', '', 'review-choice'), links = s.planRecordLinks.filter(l => l.occurrenceId === plan.id && l.recordId);
    const actual = unionMillis(links.map(l => ({ start: l.timeStarted, end: l.timeEnded! })), Number.MIN_SAFE_INTEGER, Number.MAX_SAFE_INTEGER);
    const kind = plan.schedule.kind === 'BUFFER' ? '缓冲' : plan.schedule.kind === 'FREE' ? '自由活动' : '计划';
    row.append(element('h3', `${plan.date} ${DateTime.fromMillis(plan.start, { zone }).toFormat('HH:mm')} · ${plan.schedule.title}`),
      element('p', `${kind} · ${status[plan.status]} · 预留 ${duration(plan.end - plan.start)} · 关联实际 ${duration(actual)}`));
    for (const id of [...new Set(links.map(l => l.recordId))]) {
      const record = s.records.find(r => r.id === id);
      if (record) row.append(button(`${title(record)} · ${scores(record)}`, () => detail(record.id), 'memory'));
    }
    panel.append(row);
  }
  panel.append(element('h3', '待办清单'));
  for (const task of s.planTasks.filter(t => !t.archived)) panel.append(element('p', `${task.important ? '★ ' : ''}${task.title} · ${status[task.status]}${task.deadline ? ` · 截止 ${task.deadline}` : ''}`));
  app.append(panel);
}
function context(r: Experience) { return r.tags.map(t=>snapshot!.tags.find(v=>v.id===t.tagId&&v.purpose!=='GENERAL')?.name).filter(Boolean).join('、'); }
function showMessage(text: string) { const dialog=element('dialog');dialog.append(element('p',text),button('关闭',()=>dialog.close()));document.body.append(dialog);dialog.addEventListener('close',()=>dialog.remove());dialog.showModal(); }
function detail(id: string, running = false) {
  const s=snapshot!;const r=running?s.runningRecords.find(r=>r.id===id):s.records.find(r=>r.id===id);if(!r)return;
  const dialog=element('dialog','','experience');const current=session;
  dialog.append(element('span',running?'进行中（截至备份）':(r as Experience).reviewState==='CONFIRMED'?'已确认':'待整理','eyebrow'),
    element('h2',r.summary||s.recordTypes.find(t=>t.id===(running?r.id:(r as Experience).typeId))?.name||'未分类'),
    element('p',`${DateTime.fromMillis(r.timeStarted,{zone}).toFormat('MM-dd HH:mm:ss')} → ${DateTime.fromMillis(running?s.createdAt:(r as Experience).timeEnded,{zone}).toFormat('MM-dd HH:mm:ss')} · ${duration((running?s.createdAt:(r as Experience).timeEnded)-r.timeStarted)}`),
    element('p',running?'运行中的评分不参与已确认分布':scores(r as Experience)),element('p',r.comment||'没有备注'),element('p',`记录原时区：${r.zoneId??'未知'}`));
  for(const purpose of ['PERSON','ENVIRONMENT','GENERAL'] as const){const tags=r.tags.map(t=>s.tags.find(v=>v.id===t.tagId&&v.purpose===purpose&&!Object.values(s.ratingTagIds).includes(v.id))).filter(Boolean);dialog.append(element('p',`${purpose==='PERSON'?'人物':purpose==='ENVIRONMENT'?'环境':'附加标签'}：${tags.map(t=>t!.name).join('、')||'未填写'}`));}
  const gallery=element('div','','gallery');const photos=s.photos.filter(p=>running?p.runningRecordId===id:p.recordId===id);if(!photos.length)gallery.append(element('p','没有照片','muted'));
  for(const photo of photos){const holder=element('figure');holder.append(element('span','正在读取照片…'));gallery.append(holder);void api(`${s.snapshotId}/photos/${photo.sha256}`).then(r=>r.blob()).then(blob=>{
    if(current!==session||!dialog.open)return;const url=URL.createObjectURL(blob);photoUrls.push(url);const img=element('img');img.alt='这条经历的照片';img.src=url;img.addEventListener('error',()=>{holder.replaceChildren(element('p','浏览器不支持此照片格式，请在手机查看。'));});holder.replaceChildren(img);
  }).catch(()=>holder.replaceChildren(element('p','照片暂时不可读取，请稍后重试。')));}
  dialog.append(gallery,button('关闭',()=>dialog.close()));document.body.append(dialog);dialog.addEventListener('close',()=>{dialog.remove();clearMedia();});dialog.showModal();
}
login();
