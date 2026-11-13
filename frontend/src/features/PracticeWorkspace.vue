<template>
  <Teleport :to="toolbarTarget || 'body'" :disabled="!toolbarTarget">
    <nav v-if="questions.length && viewMode === 'single'" class="practice-navigation" aria-label="答题翻页">
      <button :disabled="questionIndex <= 0 || requestBusy" aria-label="上一题" title="上一题" @click="selectQuestion(questions[questionIndex - 1]!.id)"><GameIcon name="arrowLeft" /><span class="navigation-label">上一题</span></button>
      <div id="practice-question-actions" class="practice-question-actions">
        <span v-if="current?.kind !== 'python'" class="practice-question-location" :title="`${questionIndex + 1}/${questions.length} ${current?.title ?? ''}`"><span>{{ questionIndex + 1 }}/{{ questions.length }}</span><span class="practice-question-name">{{ current?.title }}</span></span>
      </div>
      <button :disabled="questionIndex >= questions.length - 1 || requestBusy" aria-label="下一题" title="下一题" @click="selectQuestion(questions[questionIndex + 1]!.id)"><span class="navigation-label">下一题</span><GameIcon name="arrowRight" /></button>
    </nav>
  </Teleport>
  <LearningSplitLayout class="practice-workspace" :has-support="Boolean(activity)" :compact="compact">
    <nav v-if="activity && compact" class="workspace-view-switch game-segmented" aria-label="练习与助教视图">
      <button :aria-pressed="!showAssistant" aria-controls="practice-content-region" @click="switchView(false)">题目与代码</button>
      <button :aria-pressed="showAssistant" aria-controls="practice-assistant-region" @click="switchView(true)">目标与目录</button>
    </nav>
    <section id="practice-content-region" class="practice-content" :class="{ 'coding-content': current?.kind === 'python' && viewMode === 'single' }" tabindex="-1" aria-label="题目与代码" :hidden="compact && showAssistant" :inert="compact && showAssistant" :aria-busy="loading">
      <header class="content-heading practice-heading" :class="{ 'assessment-heading': isAssessment }">
        <h2>{{ activity?.title ?? "正在读取题目" }}</h2>
        <div v-if="isAssessment" class="game-segmented assessment-view-modes" role="group" aria-label="答题模式">
          <button :aria-pressed="viewMode === 'single'" @click="setViewMode('single')">单题模式</button>
          <button :aria-pressed="viewMode === 'paper'" @click="setViewMode('paper')">试卷模式</button>
        </div>
      </header>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-if="draftError" class="error" role="alert">{{ draftError }} <button v-if="draftConflict" @click="load">重新载入试卷</button><button v-else-if="!paper?.locked" @click="retryDraft">重试保存</button></p>
      <p v-if="loading" class="learning-empty" role="status">正在读取题目…</p>
      <div v-else-if="questions.length" ref="questionRegion" class="question-body" :data-coding="current?.kind === 'python' && viewMode === 'single'" :class="{ 'question-paper': isAssessment && viewMode === 'paper' }" @scroll.passive="syncPaperSelection">
        <PracticeQuestionCard v-for="question in visibleQuestions" :key="question.id" :question="question" :toolbar-target="actionTarget" :index="questions.findIndex(item => item.id === question.id)" :answer="drafts[question.id]?.answer ?? ''" :code="drafts[question.id]?.code ?? ''" :read-only="Boolean(isAssessment && (paper?.locked || paperSubmissionPending))" :busy="questionBusy(question.id)" :show-submit="!isAssessment" :pending-mode="drafts[question.id]?.pending?.mode" :result="questionResult(question.id)" @running-change="localRunning[question.id] = $event" @update:answer="updateAnswer(question.id, $event)" @update:code="updateCode(question.id, $event)" @submit="submitQuestion(question.id, $event)" />
      </div>
      <p v-else-if="!loading && activity" class="learning-empty">此活动尚未绑定公开题目，请联系教师。</p>

      <button v-if="storySessionId && (isAssessment ? paper?.completed : activityProgress?.completed)" class="primary practice-complete" @click="$emit('complete')">{{ isAssessment ? '完成测验' : '完成练习' }}</button>
    </section>
    <LearningSupportPanel v-if="activity" :key="activity.id" id="practice-assistant-region" tabindex="-1" :hidden="compact && !showAssistant" :inert="compact && !showAssistant" :aria-hidden="compact && !showAssistant" :activity-id="activity.id" :allowed="activity.assistant_allowed" :code="drafts[current?.id ?? '']?.code ?? ''" :role-name="roleName" :portrait-url="rolePortrait" :goals="learningGoals" :question-outline="questionOutline" @cite="cite" @select-question="selectQuestion">
      <template #question-actions>
        <div v-if="isAssessment" class="assessment-actions">
          <template v-if="paper?.locked">
            <template v-if="paper.status === 'graded' && paper.score !== null">
              <strong class="assessment-score" role="status">{{ paper.score }} / {{ paper.max_score }} 分</strong>
              <span class="assessment-answer-count">答对 {{ paper.correct_count }} / {{ paper.total }} 题</span>
            </template>
            <p v-else class="assessment-status" role="status">{{ paper.status === 'attention' ? '判定需要教师处理' : '正在判定试卷…' }}</p>
            <p class="assessment-lock-state"><GameIcon name="check" /> 已交卷，答案已锁定</p>
          </template>
          <template v-else>
            <Teleport :to="actionTarget || 'body'" :disabled="!(actionTarget && current?.kind === 'python')"><button v-if="!requestBusy && !publicRunBusy || current?.kind !== 'python' || viewMode === 'paper'" class="primary assessment-top-submit" :disabled="requestBusy || publicRunBusy || !allAnswered || loading" @click="submitPaper">{{ requestBusy ? '正在交卷…' : paperSubmissionPending ? '重试交卷' : '提交试卷' }}</button></Teleport>
            <span class="assessment-answer-count">已作答 {{ answeredCount }} / {{ questions.length }} 题</span>
            <p class="assessment-submit-note">交卷后不可修改</p>
          </template>
        </div>
      </template>
    </LearningSupportPanel>
  </LearningSplitLayout>
</template>
<script setup lang="ts">
import { randomUUID } from "../shared/uuid";
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { api, ApiError, notify } from "../shared/api";
import { useSession } from "../shared/session";
import type { Activity, ActivityProgress, AssessmentAnswer, AssessmentAttempt, Question, Submission } from "../shared/types";
import { pendingSubmissionStates } from "../shared/practicePresentation";
import { createAssessmentDraftQueue } from "../shared/assessmentDraft";
import { useWorkspaceSidebar } from "../shared/workspaceSidebar";
import GameIcon from "../shared/GameIcon.vue";
import LearningSupportPanel from "./LearningSupportPanel.vue";
import LearningSplitLayout from "./LearningSplitLayout.vue";
import PracticeQuestionCard from "./PracticeQuestionCard.vue";
import "./learningUi.css";
import "./practiceUi.css";
import "./programmingUi.css";
interface Draft { answer: string; code: string; submission_id?: string; pending?: { key: string; mode: 'submit'; answer: string | boolean; code: string } }
const props = defineProps<{ activityId: string; storySessionId?: string; toolbarTarget?: string }>();
const emit = defineEmits<{ complete: []; activityKind: [kind: string] }>();
const session = useSession(), { collapsed: sidebarCollapsed } = useWorkspaceSidebar();
const activity = ref<Activity>(), activityProgress = ref<ActivityProgress>(), paper = ref<AssessmentAttempt>();
const drafts = ref<Record<string, Draft>>({}), results = ref<Record<string, Submission>>({});
const questionIndex = ref(0), viewMode = ref<'single' | 'paper'>('single'), questionRegion = ref<HTMLElement>();
const loading = ref(true), error = ref(''), draftError = ref(''), draftConflict = ref(false), requestBusy = ref(false), busyQuestionId = ref('');
const compact = ref(false), showAssistant = ref(false), paperSubmissionPending = ref(false);
const contextUserId = session.user?.id, contextClassroomId = session.classroomId, contextToken = api.token();
const polls = new Map<string, ReturnType<typeof setTimeout>>();
let closed = false, generation = 0, saveTimer: ReturnType<typeof setTimeout> | undefined, paperPoll: ReturnType<typeof setTimeout> | undefined;
let compactMedia: MediaQueryList | undefined, draftQueue: ReturnType<typeof createAssessmentDraftQueue> | undefined;
const draftTasks = new Set<Promise<void>>();
const mutationTasks = new Set<Promise<unknown>>();
function trackMutation<T>(task: Promise<T>) { mutationTasks.add(task); void task.then(() => mutationTasks.delete(task), () => mutationTasks.delete(task)); return task; }
let unregisterPreparation: (() => void) | undefined;
const ownerActive = () => session.user?.id === contextUserId && session.classroomId === contextClassroomId && api.token() === contextToken;
const currentOwner = () => !closed && ownerActive();
const isAssessment = computed(() => activity.value?.kind === 'assessment');
const questions = computed(() => {
  if (!isAssessment.value || !paper.value) return activity.value?.questions ?? [];
  const available = activity.value?.questions ?? [];
  if (!paper.value.locked) return paper.value.question_ids.map(id => available.find(question => question.id === id)).filter((question): question is Question => Boolean(question));
  return paper.value.question_ids.map(id => {
    const known = available.find(question => question.id === id);
    if (known) return known;
    const submitted = paper.value?.answers.find(answer => answer.question_id === id);
    return { id, title: '题目已调整', kind: submitted?.code ? 'python' : typeof submitted?.answer === 'boolean' ? 'boolean' : 'fill', prompt: '原题内容已调整，以下保留已提交答案和判定结果。', options: [], samples: [] } as Question;
  });
});
const actionTarget = computed(() => viewMode.value === 'single' && props.toolbarTarget ? '#practice-question-actions' : undefined);
const localRunning = ref<Record<string, boolean>>({});
const publicRunBusy = computed(() => Object.values(localRunning.value).some(Boolean));
const current = computed(() => questions.value[questionIndex.value]);
const visibleQuestions = computed(() => isAssessment.value && viewMode.value === 'paper' ? questions.value : current.value ? [current.value] : []);
const role = computed(() => activity.value?.role);
const roleName = computed(() => role.value?.name ?? '练习助教'), rolePortrait = computed(() => role.value?.portrait);
const containerKey = computed(() => `draft:${contextUserId}:${contextClassroomId}:assessment-${props.activityId}`);
const questionKey = (id: string) => `draft:${contextUserId}:${contextClassroomId}:${id}`;
const answeredCount = computed(() => questions.value.filter(question => hasAnswer(question)).length);
const allAnswered = computed(() => questions.value.length > 0 && answeredCount.value === questions.value.length);
const questionOutline = computed(() => questions.value.map((question, index) => {
  const result = questionResult(question.id);
  return { id: question.id, index: index + 1, title: question.title, kind: question.kind, current: index === questionIndex.value,
    state: result && (isAssessment.value ? paper.value?.locked : result.mode === 'submit') ? result.status === 'accepted' ? 'accepted' as const : [...pendingSubmissionStates, 'infrastructure_error', 'submission_unknown'].includes(result.status) ? 'pending' as const : 'wrong' as const : !isAssessment.value && activityProgress.value?.accepted_question_ids?.includes(question.id) ? 'accepted' as const : hasAnswer(question) ? 'answered' as const : 'unanswered' as const };
}));
const markedCount = computed(() => paper.value?.results.filter(result => ['accepted', 'wrong_answer', 'compile_error', 'runtime_error', 'time_limit', 'memory_limit'].includes(result.status)).length ?? 0);
const learningGoals = computed(() => {
  const total = questions.value.length;
  if (isAssessment.value) return [{ title: '完成测验', completed: paper.value?.completed ?? false,
    progress: { value: paper.value?.locked ? markedCount.value : answeredCount.value, max: total,
      label: paper.value?.locked ? `已判定 ${markedCount.value} / ${total} 题` : `已作答 ${answeredCount.value} / ${total} 题` } }];
  const passed = questions.value.filter(question => activityProgress.value?.accepted_question_ids?.includes(question.id));
  return [{ title: '完成练习', completed: activityProgress.value?.completed ?? false, progress: { value: passed.length, max: total, label: `已完成 ${passed.length} / ${total} 题` } }];
});
function hasAnswer(question: Question) { const draft = drafts.value[question.id]; return Boolean(question.kind === 'python' ? draft?.code.trim() : draft?.answer.trim()); }
function questionResult(id: string) { return isAssessment.value && paper.value?.locked ? paper.value.results.find(result => result.question_id === id) : results.value[id]; }
function questionBusy(id: string) { const result = results.value[id]; return isAssessment.value && requestBusy.value || requestBusy.value && busyQuestionId.value === id || !isAssessment.value && Boolean(result && pendingSubmissionStates.includes(result.status)); }
function normalizeAnswer(question: Question): AssessmentAnswer { const draft = drafts.value[question.id] ?? { answer: '', code: '' }; return question.kind === 'python' ? { question_id: question.id, code: draft.code } : { question_id: question.id, answer: question.kind === 'boolean' ? draft.answer ? draft.answer === 'true' : null : draft.answer }; }
function allAnswers() { return questions.value.map(normalizeAnswer); }
function applyAnswers(answers: AssessmentAnswer[]) { for (const answer of answers) { const draft = drafts.value[answer.question_id]; if (draft) { draft.answer = typeof answer.answer === 'boolean' ? String(answer.answer) : typeof answer.answer === 'string' ? answer.answer : answer.answer && typeof answer.answer === 'object' ? JSON.stringify(answer.answer) : ''; draft.code = answer.code ?? ''; } } }
function localJson(key: string) { try { return JSON.parse(localStorage.getItem(key) ?? 'null'); } catch { return null; } }
function persistLocal() {
  if (!ownerActive()) return;
  try {
    if (isAssessment.value) localStorage.setItem(containerKey.value, JSON.stringify({ revision: draftQueue?.revision() ?? paper.value?.revision ?? 0, answers: allAnswers(), mode: viewMode.value, submission: pendingPaper }));
    else for (const [id, draft] of Object.entries(drafts.value)) localStorage.setItem(questionKey(id), JSON.stringify(draft));
  } catch { draftError.value = '本机草稿保存失败，请保持页面打开并重试。'; }
}
function edit(id: string, key: 'answer' | 'code', value: string) {
  if (!currentOwner() || drafts.value[id]?.pending || isAssessment.value && (paper.value?.locked || paperSubmissionPending.value || requestBusy.value) || !drafts.value[id]) return;
  drafts.value[id]![key] = value;
  persistLocal();
  if (isAssessment.value) { draftQueue?.set(allAnswers()); clearTimeout(saveTimer); saveTimer = setTimeout(() => void flushDrafts(), 450); }
}
function updateAnswer(id: string, value: string) { edit(id, 'answer', value); }
function updateCode(id: string, value: string) { edit(id, 'code', value); }
function trackDraftTask(task: Promise<void>) { draftTasks.add(task); void task.finally(() => draftTasks.delete(task)); return task; }
async function flushDrafts() { clearTimeout(saveTimer); if (draftQueue) await trackDraftTask(draftQueue.flush()); }
async function retryDraft() { draftError.value = ''; await flushDrafts(); }
async function prepareRestart() { clearTimeout(saveTimer); await flushDrafts(); await Promise.all([...draftTasks]); await Promise.allSettled([...mutationTasks]); if (draftQueue?.hasPending()) throw new Error(draftError.value || '试卷草稿尚未保存，请重试。'); }
function updateCompact() { if (!compactMedia) return; if (compactMedia.matches && !compact.value) showAssistant.value = Boolean(document.getElementById('practice-assistant-region')?.contains(document.activeElement)); compact.value = compactMedia.matches; }
async function switchView(support: boolean) { if (support) sidebarCollapsed.value = false; showAssistant.value = support; await nextTick(); document.getElementById(support ? 'practice-assistant-region' : 'practice-content-region')?.focus({ preventScroll: true }); }
async function selectQuestion(id: string) {
  const index = questions.value.findIndex(question => question.id === id);
  if (index < 0) return;
  questionIndex.value = index;
  if (compact.value) await switchView(false);
  await nextTick();
  if (viewMode.value === 'paper' && isAssessment.value) Array.from(questionRegion.value?.querySelectorAll<HTMLElement>('.practice-question-card') ?? []).find(element => element.dataset.questionId === id)?.scrollIntoView?.({ block: 'start', behavior: 'auto' });
  else if (questionRegion.value) questionRegion.value.scrollTop = 0;
}
function syncPaperSelection() { if (!isAssessment.value || viewMode.value !== 'paper' || !questionRegion.value) return; const top = questionRegion.value.getBoundingClientRect().top; const cards = Array.from(questionRegion.value.querySelectorAll<HTMLElement>('.practice-question-card')); const selected = cards.find(card => card.getBoundingClientRect().bottom > top + 36); if (selected) questionIndex.value = Math.max(0, questions.value.findIndex(question => question.id === selected.dataset.questionId)); }
async function setViewMode(mode: 'single' | 'paper') { if (!isAssessment.value) return; viewMode.value = mode; persistLocal(); await nextTick(); await selectQuestion(current.value?.id ?? ''); }
let pendingPaper: { key: string; answers: AssessmentAnswer[] } | undefined;
function configureDraftQueue(saved: AssessmentAttempt, ticket: number, activityId: string) {
  if (!currentOwner() || ticket !== generation || saved.locked) return;
  draftQueue = createAssessmentDraftQueue({ revision: saved.revision, initial: saved.answers, active: ownerActive,
    send: (revision, answers) => api.put<AssessmentAttempt>(`/activities/${activityId}/assessment`, { classroom_id: contextClassroomId, revision, answers }),
    saved: next => { if (currentOwner() && ticket === generation) { paper.value = next; draftError.value = ''; persistLocal(); } },
    failed: cause => { if (currentOwner() && ticket === generation) { draftError.value = cause instanceof Error ? cause.message : '试卷草稿保存失败，请重试。'; draftConflict.value = cause instanceof ApiError && cause.status === 409; } } });
  draftQueue.set(allAnswers());
}
async function load() {
  const ticket = ++generation;
  clearTimeout(saveTimer); clearTimeout(paperPoll); for (const timer of polls.values()) clearTimeout(timer); polls.clear();
  if (draftQueue) trackDraftTask(draftQueue.flush());
  draftQueue = undefined; pendingPaper = undefined; paperSubmissionPending.value = false; paper.value = undefined; loading.value = true; error.value = ''; draftError.value = ''; draftConflict.value = false; results.value = {}; drafts.value = {};
  try {
    const loaded = await session.activity(props.activityId);
    if (!currentOwner() || ticket !== generation) return;
    activity.value = loaded; emit('activityKind', loaded.kind); activityProgress.value = loaded.progress; questionIndex.value = 0; viewMode.value = 'single';
    for (const question of loaded.questions) { const old = localJson(questionKey(question.id)); drafts.value[question.id] = { answer: typeof old?.answer === 'string' ? old.answer : '', code: typeof old?.code === 'string' ? old.code : question.starter_code ?? '', submission_id: old?.submission_id, pending: old?.pending }; }
    if (loaded.kind === 'assessment') {
      const saved = await api.get<AssessmentAttempt>(`/activities/${loaded.id}/assessment?classroom_id=${contextClassroomId}`);
      if (!currentOwner() || ticket !== generation) return;
      paper.value = saved; for (const id of saved.question_ids) drafts.value[id] ??= { answer: '', code: '' }; applyAnswers(saved.answers);
      const local = localJson(containerKey.value);
      if (!saved.locked && local?.revision === saved.revision && Array.isArray(local.answers)) { applyAnswers(local.answers); pendingPaper = local.submission; paperSubmissionPending.value = Boolean(pendingPaper); }
      if (local?.mode === 'paper') viewMode.value = 'paper';
      if (saved.locked) { persistLocal(); schedulePaperPoll(); if (saved.completed && !props.storySessionId && saved.results.length) await advanceTask(saved.results[saved.results.length - 1]!.id); }
      else {
        configureDraftQueue(saved, ticket, loaded.id);
        if (pendingPaper) error.value = '上次交卷结果尚未确认，请重试交卷。';
      }
    } else for (const [id, draft] of Object.entries(drafts.value)) if (draft.submission_id) void pollQuestion(id, draft.submission_id, ticket);
  } catch (cause) { if (currentOwner() && ticket === generation) error.value = cause instanceof Error ? cause.message : '题目加载失败'; }
  finally { if (currentOwner() && ticket === generation) loading.value = false; }
}
async function submitQuestion(id: string, mode: 'submit') {
  const question = questions.value.find(item => item.id === id), draft = drafts.value[id];
  if (!question || !draft || !activity.value || !hasAnswer(question) || requestBusy.value || isAssessment.value && (paper.value?.locked || mode === 'submit')) return;
  const ticket = generation; requestBusy.value = true; busyQuestionId.value = id; error.value = '';
  const body = { answer: question.kind === 'boolean' ? draft.answer === 'true' : draft.answer, code: draft.code, mode };
  const pending = draft.pending ?? { ...body, key: randomUUID() };
  draft.pending = pending; persistLocal();
  try {
    const received = await trackMutation(api.post<Submission>('/submissions', { classroom_id: contextClassroomId, activity_id: activity.value.id, question_id: id, idempotency_key: pending.key, answer: pending.answer, code: pending.code, mode: pending.mode }));
    if (!currentOwner() || ticket !== generation) return;
    results.value[id] = received; draft.submission_id = received.id; draft.pending = undefined; persistLocal();
    await handleQuestionResult(id, received, ticket);
  } catch (cause) { if (currentOwner() && ticket === generation) { error.value = cause instanceof Error ? cause.message : '提交失败，请重试。'; if (cause instanceof ApiError && cause.status === 422) { draft.pending = undefined; persistLocal(); } } }
  finally { if (currentOwner() && ticket === generation) { requestBusy.value = false; busyQuestionId.value = ''; } }
}
async function handleQuestionResult(id: string, result: Submission, ticket: number) {
  if (!currentOwner() || ticket !== generation) return;
  if (pendingSubmissionStates.includes(result.status)) { polls.set(id, setTimeout(() => void pollQuestion(id, result.id, ticket), 1500)); return; }
  if (!isAssessment.value && result.mode === 'submit' && result.status === 'accepted') {
    const progress = await api.get<ActivityProgress>(`/activities/${props.activityId}/progress?classroom_id=${contextClassroomId}`);
    if (!currentOwner() || ticket !== generation) return;
    activityProgress.value = progress;
    if (!props.storySessionId && progress.completed) await advanceTask(result.id);
  }
}
async function pollQuestion(id: string, submissionId: string, ticket: number) { try { const received = await api.get<Submission>(`/submissions/${submissionId}?classroom_id=${contextClassroomId}`); if (!currentOwner() || ticket !== generation || received.question_id !== id) return; results.value[id] = received; await handleQuestionResult(id, received, ticket); } catch (cause) { if (currentOwner() && ticket === generation) error.value = cause instanceof Error ? cause.message : '结果读取失败'; } }
async function advanceTask(submissionId: string) {
  if (!activity.value || !currentOwner()) return;
  let advanced = false;
  for (const quest of session.world?.quests ?? []) for (const node of quest.nodes) {
    if (node.status !== 'ready' || !['COMPLETE_PRACTICE', 'PASS_ASSESSMENT'].includes(node.type) || node.activity_id !== activity.value.id) continue;
    try { await session.event(quest, node, { submission_id: submissionId }); advanced = true; }
    catch (cause) { notify(cause instanceof Error ? cause.message : '仍有待完成题目'); }
  }
  if (advanced && currentOwner()) await session.refresh();
}
async function submitPaper() {
  if (!activity.value || !isAssessment.value || paper.value?.locked || requestBusy.value || !allAnswered.value) return;
  const ticket = generation; requestBusy.value = true; error.value = '';
  try {
    await flushDrafts();
    if (!currentOwner() || ticket !== generation) return;
    pendingPaper ??= { key: randomUUID(), answers: allAnswers() }; paperSubmissionPending.value = true; applyAnswers(pendingPaper.answers); persistLocal();
    const saved = await trackMutation(api.post<AssessmentAttempt>(`/activities/${activity.value.id}/assessment/submit`, { classroom_id: contextClassroomId, idempotency_key: pendingPaper.key, answers: pendingPaper.answers }));
    if (!currentOwner() || ticket !== generation) return;
    paper.value = saved; pendingPaper = undefined; paperSubmissionPending.value = false; draftQueue = undefined; applyAnswers(saved.answers); persistLocal();
    if (saved.question_ids.some(id => !activity.value?.questions.some(question => question.id === id))) error.value = '试卷中的部分题目已调整，请联系教师查看。';
    if (saved.completed && !props.storySessionId && saved.results.length) await advanceTask(saved.results[saved.results.length - 1]!.id);
    schedulePaperPoll();
  } catch (cause) { if (currentOwner() && ticket === generation) { error.value = cause instanceof Error ? cause.message : '交卷失败，请重试。'; if (cause instanceof ApiError && cause.status === 422) { pendingPaper = undefined; paperSubmissionPending.value = false; persistLocal(); } if (cause instanceof ApiError && cause.status === 409 || cause instanceof Error && /锁定|已提交|已交卷/.test(cause.message)) await refreshPaper(ticket); } }
  finally { if (currentOwner() && ticket === generation) requestBusy.value = false; }
}
function schedulePaperPoll() { clearTimeout(paperPoll); if (paper.value?.locked && paper.value.status === 'grading') paperPoll = setTimeout(() => void refreshPaper(generation), 1500); }
async function refreshPaper(ticket: number) {
  try {
    const saved = await api.get<AssessmentAttempt>(`/activities/${props.activityId}/assessment?classroom_id=${contextClassroomId}`);
    if (!currentOwner() || ticket !== generation) return;
    const wasCompleted = paper.value?.completed, wasLocked = paper.value?.locked;
    paper.value = saved;
    if (saved.locked) {
      draftQueue = undefined;
      applyAnswers(saved.answers);
      persistLocal();
    } else if (wasLocked) {
      for (const id of saved.question_ids) drafts.value[id] ??= { answer: '', code: '' };
      applyAnswers(saved.answers);
      configureDraftQueue(saved, ticket, props.activityId);
      persistLocal();
    }
    if (saved.completed && !wasCompleted && !props.storySessionId && saved.results.length) await advanceTask(saved.results[saved.results.length - 1]!.id);
    schedulePaperPoll();
  } catch (cause) {
    if (currentOwner() && ticket === generation) error.value = cause instanceof Error ? cause.message : '试卷结果读取失败';
  }
}
function cite(id: number, page: number) { session.navigate({ kind: 'material', material_id: id, page_no: page }); }
watch(() => props.activityId, load);
watch(sidebarCollapsed, value => { if (value) showAssistant.value = false; });
onMounted(() => { compactMedia = window.matchMedia?.('(max-width: 900px), (max-height: 540px)'); updateCompact(); compactMedia?.addEventListener('change', updateCompact); unregisterPreparation = session.registerRestartPreparation(prepareRestart); void load(); });
onBeforeUnmount(() => { clearTimeout(saveTimer); persistLocal(); const finalSave = flushDrafts(); closed = true; clearTimeout(paperPoll); for (const timer of polls.values()) clearTimeout(timer); compactMedia?.removeEventListener('change', updateCompact); void Promise.allSettled([finalSave, ...draftTasks, ...mutationTasks]).finally(() => unregisterPreparation?.()); });
</script>
