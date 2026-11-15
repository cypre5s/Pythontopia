<template>
  <section class="experience-tools">
    <div class="experience-tabs" role="tablist" aria-label="体验开发工具">
      <button v-for="item in tabs" :key="item.key" role="tab" :aria-selected="tab === item.key" @click="tab = item.key; confirmation = undefined"><GameIcon :name="item.icon" />{{ item.title }}</button>
    </div>
    <p v-if="error" class="notice" role="alert">{{ error }}</p><p v-if="message" class="notice" role="status">{{ message }}</p>
    <p v-if="loading">正在加载体验工具…</p>
    <template v-else-if="data">
      <section v-if="tab === 'content'" class="experience-section" aria-label="内容定位">
        <label>章节<select v-model="chapterId"><option v-for="chapter in data.chapters" :key="chapter.id" :value="chapter.id">{{ chapter.title }} · {{ chapter.available ? '可进入' : chapter.status === 'published' ? '需定位或等待开放' : '未发布' }}</option></select></label>
        <div class="experience-actions"><button :disabled="busy || !chapter || chapter.status !== 'published'" @click="switchChapter"><GameIcon name="compass" />定位本章</button><small>只调整本账号的前置限制，不修改班级发布安排。</small></div>
        <div class="experience-grid">
          <section><h3 class="game-card-title"><GameIcon name="book" />资料</h3>
            <label>选择资料<select v-model="materialId"><option value="">请选择</option><option v-for="item in materials" :key="item.id" :value="String(item.id)">{{ item.title }}</option></select></label>
            <label v-if="material?.kind === 'video'">视频时间（秒）<input v-model.number="videoTime" type="number" min="0" max="86400"></label>
            <label v-else>页码<input v-model.number="pageNo" type="number" min="1" :max="material?.pages ?? 1"></label>
            <button :disabled="busy || !chapter?.available || !material || material.status !== 'ready'" @click="openContent('material', materialId)">打开资料</button>
          </section>
          <section><h3 class="game-card-title"><GameIcon name="practice" />学习活动</h3>
            <label>选择活动<select v-model="activityId" aria-label="选择活动"><option value="">请选择</option><option v-for="item in activities" :key="item.id" :value="item.id">{{ item.title }}{{ item.published ? '' : ' · 未发布' }}</option></select></label>
            <label v-if="activity && ['practice', 'assessment'].includes(activity.kind)">题号<input v-model.number="questionNo" type="number" min="1" :max="activity.questions"></label>
            <button :disabled="busy || !chapter?.available || !activity?.published" @click="openContent('activity', activityId)">打开活动</button>
          </section>
        </div>
        <p class="notice">打开内容不会直接记为完成。未发布或未到开放时间的章节仍不可进入。</p>
        <button :disabled="busy" @click="spawn">回到小镇出生点</button>
      </section>
      <section v-else-if="tab === 'stage'" class="experience-section" aria-label="任务阶段">
        <label>任务<select v-model="questId" aria-label="任务"><option value="">请选择任务</option><option v-for="item in data.quests" :key="item.id" :value="item.id">{{ item.title }}</option></select></label>
        <p v-if="!data.quests.length" class="notice">本班没有已配置任务。可通过内容定位查看课程，不会自动创建章节任务。</p>
        <template v-if="quest">
          <label>目标阶段<select v-model="nodeId" aria-label="目标阶段"><option value="">请选择阶段</option><option v-for="node in quest.nodes" :key="node.id" :value="node.id">{{ node.title }} · {{ stateLabels[node.status] ?? node.status }}</option></select></label>
          <p v-if="node" class="notice">前置：{{ (node.dependencies ?? []).map(titleOf).join('、') || '无' }} · {{ node.aggregation ?? 'ALL' }}{{ node.condition ? ` · 需要分支 ${titleOf(node.condition.node)} / ${node.condition.choice}` : '' }}</p>
          <div class="experience-grid">
            <label v-for="entry in quest.nodes.filter(n => n.choices?.length)" :key="entry.id">{{ entry.title }} · 分支<select v-model="choices[entry.id]"><option value="">按目标推导</option><option v-for="choice in entry.choices" :key="choice.id" :value="choice.id">{{ choice.label }}</option></select></label>
            <label v-for="entry in quest.nodes.filter(n => n.aggregation === 'ANY' && (n.dependencies?.length ?? 0) > 1)" :key="`any-${entry.id}`">{{ entry.title }} · ANY 前置<select v-model="anyDependencies[entry.id]"><option value="">请选择</option><option v-for="dep in entry.dependencies" :key="dep" :value="dep">{{ titleOf(dep) }}</option></select></label>
          </div>
          <button :disabled="busy || !nodeId" @click="previewStage">预览阶段调整</button>
          <p class="notice">将当前任务定位到目标阶段的开始，其他阶段按所选前置路径重新排列，结束当前对白。不会生成成绩或学习证明；建议先保存检查点。</p>
        </template>
      </section>
      <section v-else class="experience-section" aria-label="体验检查点">
        <div class="experience-actions"><label>检查点名称<input v-model="checkpointName" maxlength="80" placeholder="例如：第一章练习前"></label><button :disabled="busy || !checkpointName.trim()" @click="save"><GameIcon name="save" />保存当前体验</button></div>
        <p class="notice">保存任务、对白、阅读位置、当前活动与角色位置。正式提交、成绩和助教会话保留，不随检查点回退。每班最多 20 个。</p>
        <p v-if="!data.checkpoints.length" class="notice">还没有检查点。</p>
        <div v-for="item in data.checkpoints" :key="item.id" class="checkpoint-row"><div><strong>{{ item.name }}</strong><small>{{ new Date(item.created_at).toLocaleString() }}{{ item.valid ? '' : ' · 课程已变化，检查点失效' }}</small></div><button :disabled="busy || !item.valid" @click="previewRestore(item.id)">恢复</button><button :disabled="busy" @click="confirmation = { kind: 'delete', id: item.id, title: `删除检查点“${item.name}”` }">删除</button></div>
      </section>
      <section v-if="confirmation" class="experience-confirm" aria-label="确认体验调整">
        <h3 class="game-card-title"><GameIcon name="settings" />{{ confirmation.title }}</h3>
        <p v-if="confirmation.completed">标记为体验前置：{{ confirmation.completed.map(item => item.title).join('、') || '无' }}</p>
        <p v-if="confirmation.kind === 'restore'">恢复检查点中的体验进度和阅读位置，当前对白会重新建立。当前状态将被替换。</p>
        <div class="experience-actions"><button class="town-primary" :disabled="busy" @click="confirm">{{ busy ? '处理中…' : '确认' }}</button><button :disabled="busy" @click="confirmation = undefined">取消</button></div>
      </section>
    </template>
    <button v-if="!data && !loading" :disabled="busy" @click="load">重新加载</button>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import GameIcon from '../shared/GameIcon.vue';
import { api } from '../shared/api';
import { captureExperience } from '../shared/devTools';
import { randomUUID } from '../shared/uuid';
import { useSession } from '../shared/session';
import type { Workspace } from '../shared/types';
type Node = { id: string; title: string; status: string; dependencies?: string[]; aggregation?: string; condition?: {node: string; choice: string}; choices?: {id: string; label: string}[] };
type Overview = { chapters: {id: number; title: string; available: boolean; status: string}[]; materials: {id: number; title: string; kind: string; status: string; pages: number; chapters: number[]}[]; activities: {id: string; chapter_id: number; title: string; kind: string; published: boolean; questions: number}[]; quests: {id: string; title: string; chapter_id: number; nodes: Node[]}[]; checkpoints: {id: string; name: string; created_at: string; valid: boolean}[] };
type Confirmation = { kind: 'stage' | 'restore' | 'delete'; id?: string; title: string; expected?: string; completed?: {title: string}[]; body?: Record<string, unknown> };
const tabs = [{key:'content',title:'内容定位',icon:'book'}, {key:'stage',title:'任务阶段',icon:'journal'}, {key:'checkpoints',title:'体验检查点',icon:'save'}] as const;
const stateLabels: Record<string,string> = {ready:'可开始',locked:'前置未完成',complete:'已完成',skipped:'另一分支'};
const session = useSession(), tab = ref<string>('content'), data = ref<Overview>(), loading = ref(false), busy = ref(false), error = ref(''), message = ref('');
const chapterId = ref<number>(), materialId = ref(''), activityId = ref(''), pageNo = ref(1), questionNo = ref(1), videoTime = ref(0), questId = ref(''), nodeId = ref(''), checkpointName = ref('');
const choices = ref<Record<string,string>>({}), anyDependencies = ref<Record<string,string>>({}), confirmation = ref<Confirmation>();
const base = computed(() => `${session.user?.town_preview ? '/teacher-town' : '/dev'}/experience`);
const chapter = computed(() => data.value?.chapters.find(c => c.id === chapterId.value));
const materials = computed(() => data.value?.materials.filter(m => m.chapters.includes(chapterId.value!)) ?? []);
const activities = computed(() => data.value?.activities.filter(a => a.chapter_id === chapterId.value) ?? []);
const material = computed(() => materials.value.find(m => String(m.id) === materialId.value));
const activity = computed(() => activities.value.find(a => a.id === activityId.value));
const quest = computed(() => data.value?.quests.find(q => q.id === questId.value));
const node = computed(() => quest.value?.nodes.find(n => n.id === nodeId.value));
const titleOf = (id: string) => quest.value?.nodes.find(n => n.id === id)?.title ?? id;
let generation = 0, disposed = false;
let saveRequestId: string | undefined;
const scope = () => ({classroom_id: session.classroomId});
async function load() {
  const ticket = ++generation; loading.value = true; error.value = '';
  try { const result = await api.get<Overview>(`${base.value}?classroom_id=${session.classroomId}`); if (!disposed && ticket === generation) { data.value = result; chapterId.value ??= result.chapters[0]?.id; } }
  catch (cause) { if (!disposed && ticket === generation) error.value = cause instanceof Error ? cause.message : '加载失败'; }
  finally { if (!disposed && ticket === generation) loading.value = false; }
}
async function act(task: (current: () => boolean) => Promise<void>) {
  if (busy.value || session.resetBusy) return;
  const owner = session.user?.id, classroom = session.classroomId, ticket = generation;
  const current = () => !disposed && ticket === generation && owner === session.user?.id && classroom === session.classroomId;
  busy.value = true; error.value = ''; message.value = '';
  try { await task(current); } catch (cause) { if (current()) error.value = cause instanceof Error ? cause.message : '操作失败'; }
  finally { if (!disposed && owner === session.user?.id && classroom === session.classroomId) busy.value = false; }
}
const clean = (value: Record<string,string>) => Object.fromEntries(Object.entries(value).filter(([,v]) => v));
function switchChapter() { return act(async current => { await api.post(`${base.value}/chapter`, {...scope(), chapter_id: chapterId.value}); if (current()) { await session.refresh(); await load(); } }); }
function openContent(kind: string, target: string) { return act(async current => {
  const result = await api.post<{workspace: Workspace}>(`${base.value}/open`, {...scope(), kind, target, page_no: kind === 'material' ? pageNo.value : 1, question_no: questionNo.value, video_time: kind === 'material' ? videoTime.value : 0});
  if (current() && result.workspace) { session.dismissStory(); session.navigate(result.workspace); }
}); }
function previewStage() { return act(async current => {
  await session.prepareExperience(); if (!current()) return;
  const body = {...scope(), quest_id: questId.value, node_id: nodeId.value, choices: clean(choices.value), any_dependencies: clean(anyDependencies.value)};
  const result = await api.post<{expected: string; completed: {title: string}[]; target: string}>(`${base.value}/stage/preview`, body);
  if (current()) confirmation.value = {kind:'stage', title:`定位到“${result.target}”的开始`, ...result, body};
}); }
function save() { return act(async current => {
  await session.prepareExperience(); if (!current()) return;
  let workspace: Workspace = session.previousWorkspace ? {...session.previousWorkspace} : null;
  try { workspace = JSON.parse(sessionStorage.getItem(`read:${session.user!.id}:${session.classroomId}:workspace`) ?? 'null') as Workspace ?? workspace; } catch { /* Ignore a malformed local hint. */ }
  if (workspace && 'page_no' in workspace) delete workspace.page_no;
  if (workspace && 'video_time' in workspace) delete workspace.video_time;
  if (workspace && 'question_no' in workspace) delete workspace.question_no;
  if (workspace && 'story_session_id' in workspace) delete workspace.story_session_id;
  saveRequestId ??= randomUUID();
  await api.post(`${base.value}/checkpoints`, {...scope(), request_id: saveRequestId, name: checkpointName.value, client_state: captureExperience(session.user!.id, session.classroomId!, workspace)});
  if (current()) { saveRequestId = undefined; checkpointName.value = ''; message.value = '检查点已保存。'; await load(); }
}); }
function previewRestore(id: string) { return act(async current => {
  await session.prepareExperience(); if (!current()) return;
  const result = await api.get<{expected: string; name: string}>(`${base.value}/checkpoints/${id}/preview?classroom_id=${session.classroomId}`);
  if (current()) confirmation.value = {kind:'restore', id, expected: result.expected, title:`恢复“${result.name}”`};
}); }
function confirm() { return act(async current => {
  const item = confirmation.value; if (!item) return;
  if (item.kind === 'delete') { await api.request(`${base.value}/checkpoints/${item.id}?classroom_id=${session.classroomId}`, {method:'DELETE'}); if (current()) {confirmation.value = undefined; await load();} return; }
  await session.restartCurrentClassroom({path: item.kind === 'stage' ? `${base.value}/stage` : `${base.value}/checkpoints/${item.id}/restore`, body: {...(item.body ?? scope()), expected: item.expected}, restore: item.kind === 'restore'});
}); }
function spawn() { return act(async () => { await session.restartCurrentClassroom({path: `${base.value}/spawn`, body: scope()}); }); }
watch(chapterId, () => {materialId.value = activityId.value = ''; pageNo.value = questionNo.value = 1; videoTime.value = 0;});
watch(checkpointName, () => {saveRequestId = undefined;});
watch(questId, () => {nodeId.value = ''; choices.value = {}; anyDependencies.value = {}; confirmation.value = undefined;});
watch([nodeId, choices, anyDependencies], () => {confirmation.value = undefined;}, {deep:true});
watch([() => session.user?.id, () => session.classroomId], () => {generation++; data.value = undefined; chapterId.value = undefined; questId.value = ''; confirmation.value = undefined; saveRequestId = undefined; busy.value = false; if (session.classroomId && session.user?.dev_tools_enabled) void load();}, {immediate:true});
onBeforeUnmount(() => {disposed = true; generation++;});
</script>
<style scoped>
.experience-tools { margin-bottom:16px; }
.experience-tabs { display:flex; border-bottom:1px solid var(--game-line); gap:12px; }
.experience-tabs button { display:flex; align-items:center; gap:6px; background:transparent; border:0; border-radius:0; font-size:var(--game-caption-size); }
.experience-tabs button[aria-selected=true] { border-bottom:2px solid var(--game-forest); color:var(--game-forest); font-weight:600; }
.experience-section { padding:14px 0; }
.experience-section label { display:flex; flex-direction:column; gap:6px; margin-bottom:12px; font-size:var(--game-caption-size); }
.experience-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px; margin-top:14px; }
.experience-actions { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
.experience-actions label { flex:1; margin:0; }
.notice,small { color:var(--game-muted); font-size:var(--game-caption-size); line-height:1.6; }
.checkpoint-row { display:flex; align-items:center; gap:8px; padding:10px 0; border-bottom:1px solid var(--game-line); }
.checkpoint-row>div { flex:1; min-width:0; }.checkpoint-row strong { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:var(--game-body-size); }.checkpoint-row small { display:block; }
.experience-confirm { border-top:1px solid var(--game-line); padding:12px 0; font-size:var(--game-caption-size); }
@media(max-width:700px) { .experience-grid {grid-template-columns:1fr;} }
</style>
