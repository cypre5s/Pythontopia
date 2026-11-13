<template>
  <article ref="workbench" class="practice-question-card coding-workbench" :class="{ 'is-dragging': dragAxis }" :style="{ '--problem-fr': problemWidth + 'fr', '--code-fr': (100 - problemWidth) + 'fr', '--editor-fr': editorHeight + 'fr', '--cases-fr': (100 - editorHeight) + 'fr' }" :data-question-id="question.id">
    <section class="coding-pane coding-problem" aria-label="题目内容">
      <header class="coding-pane-title"><GameIcon name="description" />题目描述</header>
      <div class="coding-pane-body">
        <h3 :id="headingId">{{ index + 1 }}. {{ question.title }}</h3>
        <p class="question-prompt">{{ question.prompt }}</p>
        <section v-for="(sample, i) in question.samples" :key="i" class="coding-example">
          <h4>示例 {{ i + 1 }}</h4><label>输入</label><pre>{{ sample.input || '（空输入）' }}</pre><label>输出</label><pre>{{ sample.output || '（无输出）' }}</pre>
        </section>
        <details v-for="(hint, i) in question.hints ?? []" :key="i" class="coding-hint"><summary><GameIcon name="hint" /><span>提示 {{ i + 1 }}</span><GameIcon name="chevronDown" class="hint-chevron" /></summary><p>{{ hint }}</p></details>
      </div>
    </section>
    <div class="coding-divider coding-divider-vertical"><div class="pane-resize-handle" role="separator" tabindex="0" aria-label="调整题目与代码宽度" aria-orientation="vertical" :aria-valuenow="problemWidth" :aria-valuemin="25" :aria-valuemax="70" @pointerdown="startResize($event, 'width')" @pointermove="resize" @pointerup="finishResize" @pointercancel="finishResize" @lostpointercapture="finishResize" @keydown="resizeKeys($event, 'width')" @dblclick="resetSize('width')"><span></span></div></div>
    <div class="coding-divider coding-divider-horizontal"><div class="pane-resize-handle" role="separator" tabindex="0" aria-label="调整代码与用例高度" aria-orientation="horizontal" :aria-valuenow="editorHeight" :aria-valuemin="25" :aria-valuemax="75" @pointerdown="startResize($event, 'height')" @pointermove="resize" @pointerup="finishResize" @pointercancel="finishResize" @lostpointercapture="finishResize" @keydown="resizeKeys($event, 'height')" @dblclick="resetSize('height')"><span></span></div></div>
    <section class="coding-pane coding-source" aria-label="代码编辑器">
      <header class="coding-pane-title"><GameIcon name="code" />代码 <span>Python · 标准输入输出</span></header>
      <pre v-if="readOnly || pendingMode" class="question-readonly-code">{{ code }}</pre>
      <CodeEditor v-else :model-value="code" @update:model-value="$emit('update:code', $event)" />
    </section>
    <section class="coding-pane coding-tests" aria-label="用例与结果">
      <header class="coding-pane-title coding-tabs" role="group" aria-label="用例视图">
        <button :aria-pressed="tab === 'cases'" @click="tab = 'cases'"><GameIcon name="cases" />测试用例</button>
        <button :aria-pressed="tab === 'results'" @click="tab = 'results'"><GameIcon :name="judging ? 'spinner' : 'terminal'" :class="{ 'is-spinning': judging }" />测试结果</button>
      </header>
      <div class="coding-pane-body" :class="{ 'is-empty': tab === 'results' && !judging && !outcome }" :aria-busy="judging">
        <template v-if="tab === 'results' && judging">
          <div class="coding-run-progress" role="status">正在判题…<button v-if="running" class="stop-run" @click="controller?.abort()"><GameIcon name="stop" />停止运行</button></div>
          <div class="result-skeleton" aria-hidden="true"><i class="skeleton-heading"></i><div class="skeleton-tabs"><i></i><i></i><i></i></div><template v-for="n in 3" :key="n"><i class="skeleton-label"></i><i class="skeleton-block"></i></template></div>
        </template>
        <p v-else-if="tab === 'results' && !outcome" class="coding-empty-result">请先执行代码</p>
        <template v-else>
          <template v-if="tab === 'results' && outcome">
            <p class="result-origin">{{ resultSource === 'submission' ? '正式提交' : '公开样例 · 不计成绩' }}</p>
            <h4 class="coding-outcome" :data-tone="outcomeTone" role="status">{{ outcomeTitle }}</h4>
            <p v-if="resultSource === 'submission' && resultDetail">{{ resultDetail }}</p>
            <pre v-if="runError && resultSource === 'local'" class="coding-error" role="alert">{{ runError }}</pre>
          </template>
          <div class="case-tabs" role="group" aria-label="选择公开用例"><button v-for="(_, i) in question.samples" :key="i" :aria-pressed="activeCase === i" @click="activeCase = i"><GameIcon v-if="tab === 'results' && displayedResults[i]" :name="displayedResults[i]?.status === 'accepted' ? 'check' : 'circleX'" :class="displayedResults[i]?.status === 'accepted' ? 'case-pass' : 'case-fail'" />Case {{ i + 1 }}</button></div>
          <template v-if="tab === 'cases' && selectedSample"><label>输入</label><pre>{{ selectedSample.input || '（空输入）' }}</pre><label>预期输出</label><pre>{{ selectedSample.output || '（无输出）' }}</pre></template>
          <template v-else-if="tab === 'results'">
            <pre v-if="selectedResult?.error" class="coding-error" role="alert">{{ selectedResult.error }}</pre>
            <template v-if="selectedResult || (runError && resultSource === 'local')">
              <label>{{ selectedResult?.error || runError ? '执行时的输入' : '输入' }}</label><pre>{{ selectedSample?.input || '（空输入）' }}</pre>
              <template v-if="selectedResult"><label>实际输出</label><pre :class="{ 'coding-error': selectedResult.status === 'wrong_answer' }">{{ selectedResult.stdout || '（无输出）' }}</pre><label>预期输出</label><pre>{{ selectedSample?.output || '（无输出）' }}</pre></template>
            </template>
            <p v-else-if="resultSource === 'submission'">此用例没有可展示的评测反馈。</p>
          </template>
        </template>
      </div>
    </section>
    <Teleport :to="toolbarTarget || 'body'" :disabled="!toolbarTarget">
      <div v-if="!readOnly || judging" class="coding-actions">
        <button v-if="judging" class="judging-action" disabled><GameIcon name="spinner" class="is-spinning" />正在判题…</button>
        <template v-else>
          <button :disabled="!hasAnswer || Boolean(pendingMode)" @click="runSamples()"><GameIcon name="play" />运行公开样例</button>
          <button v-if="showSubmit" class="primary" :disabled="!hasAnswer" @click="$emit('submit', 'submit')"><GameIcon name="publish" />{{ pendingMode ? '重试提交' : '提交代码' }}</button>
        </template>
      </div>
    </Teleport>
  </article>
</template>
<script setup lang="ts">
import { computed, defineAsyncComponent, ref, watch, onBeforeUnmount } from "vue";
import GameIcon from "../shared/GameIcon.vue";
import { runPython, sameOutput } from "../shared/pythonRunner";
import type { Question, Submission, SubmissionStatus } from "../shared/types";
import { pendingSubmissionStates, submissionStatusName } from "../shared/practicePresentation";
const CodeEditor = defineAsyncComponent(() => import("./CodeEditor.vue"));
const props = withDefaults(defineProps<{ question: Question; index: number; answer: string; code: string; readOnly?: boolean; busy?: boolean; showSubmit?: boolean; pendingMode?: 'submit'; toolbarTarget?: string; result?: Submission }>(), { readOnly: false, busy: false, showSubmit: true });
const emit = defineEmits<{ runningChange: [running: boolean]; "update:answer": [value: string]; "update:code": [value: string]; submit: [mode: "submit"] }>();
const workbench = ref<HTMLElement>();
const problemWidth = ref(47), editorHeight = ref(57);
const dragAxis = ref<'width' | 'height' | null>(null);
const sizeKey = 'pythontopia-coding-layout';
try { const saved = JSON.parse(localStorage.getItem(sizeKey) ?? 'null');
  if (Number.isFinite(saved?.width)) problemWidth.value = Math.max(25, Math.min(70, saved.width));
  if (Number.isFinite(saved?.height)) editorHeight.value = Math.max(25, Math.min(75, saved.height));
} catch { /* Layout preferences are optional. */ }
function saveSize() { try { localStorage.setItem(sizeKey, JSON.stringify({width:problemWidth.value,height:editorHeight.value})); } catch { /* Optional preference. */ } }
function startResize(event: PointerEvent, axis: 'width' | 'height') {
  if (event.button !== 0) return;
  dragAxis.value = axis;
  (event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId);
  event.preventDefault();
}
function resize(event: PointerEvent) {
  if (!dragAxis.value || !workbench.value) return;
  const rect = workbench.value.getBoundingClientRect();
  if (dragAxis.value === 'width' && rect.width > 0) problemWidth.value = Math.max(25, Math.min(70, Math.round((event.clientX - rect.left) / rect.width * 100)));
  if (dragAxis.value === 'height' && rect.height > 0) editorHeight.value = Math.max(25, Math.min(75, Math.round((event.clientY - rect.top) / rect.height * 100)));
}
function finishResize() { if (dragAxis.value) { dragAxis.value = null; saveSize(); } }
function resetSize(axis: 'width' | 'height') { if(axis === 'width') problemWidth.value = 47; else editorHeight.value = 57; saveSize(); }
function resizeKeys(event: KeyboardEvent, axis: 'width' | 'height') {
  const decrease = axis === 'width' ? 'ArrowLeft' : 'ArrowUp';
  const increase = axis === 'width' ? 'ArrowRight' : 'ArrowDown';
  if (![decrease, increase, 'Home', 'End'].includes(event.key)) return;
  event.preventDefault(); event.stopPropagation();
  const value = axis === 'width' ? problemWidth : editorHeight, max = axis === 'width' ? 70 : 75;
  value.value = event.key === 'Home' ? 25 : event.key === 'End' ? max : Math.max(25, Math.min(max,value.value + (event.key === decrease ? -2 : 2)));
  saveSize();
}
const activeCase = ref(0), tab = ref<'cases' | 'results'>(props.result || props.busy ? 'results' : 'cases');
const selectedSample = computed(()=>props.question.samples[activeCase.value]);

const running = ref(false), runError = ref(''), runStopped = ref(false);
type CaseResult = { stdout: string; error: string; status: SubmissionStatus };
const localResults = ref<CaseResult[]>([]);
const resultSource = ref<'local' | 'submission'>(props.result ? 'submission' : 'local');
const judging = computed(() => running.value || props.busy || Boolean(props.result && pendingSubmissionStates.includes(props.result.status)));
const displayedResults = computed<CaseResult[]>(() => resultSource.value === 'local' ? localResults.value : (props.result?.feedback.samples ?? []).map(sample => ({ stdout: sample.stdout ?? '', error: [sample.stderr, sample.compile_output].filter(Boolean).join('\n'), status: sample.status })));
const selectedResult = computed(() => displayedResults.value[activeCase.value]);
const outcome = computed<SubmissionStatus | undefined>(() => {
  if (resultSource.value === 'submission') return props.result?.status;
  if (runError.value) return 'runtime_error';
  if (!localResults.value.length) return undefined;
  return localResults.value.find(item => item.error)?.status ?? localResults.value.find(item => item.status !== 'accepted')?.status ?? 'accepted';
});
const outcomeTitle = computed(() => runStopped.value && resultSource.value === 'local' ? '已停止运行' : outcome.value === 'accepted' ? '通过' : outcome.value === 'wrong_answer' ? '解答错误' : outcome.value === 'runtime_error' || outcome.value === 'compile_error' ? '执行出错' : outcome.value ? submissionStatusName[outcome.value] : '');
const outcomeTone = computed(() => runStopped.value && resultSource.value === 'local' ? 'warning' : outcome.value === 'accepted' ? 'success' : ['submission_unknown', 'infrastructure_error'].includes(outcome.value ?? '') ? 'warning' : 'error');
watch(() => [props.result, props.busy], () => { resultSource.value = 'submission'; runError.value = ''; tab.value = 'results';
  if (props.result && !pendingSubmissionStates.includes(props.result.status)) activeCase.value = Math.max(0, (props.result.feedback.samples ?? []).findIndex(sample => sample.status !== 'accepted'));
});
let controller: AbortController | undefined;
async function runSamples() {
  if (running.value || props.busy || props.readOnly) return;
  resultSource.value='local'; runStopped.value=false; activeCase.value=0; tab.value='results'; running.value=true; emit('runningChange', true); runError.value=''; localResults.value=[];
  controller = new AbortController();
  const source = props.code;
  try {
    if (!props.question.samples.length) throw Error('此题尚未配置公开样例');
    for (const [i, sample] of props.question.samples.entries()) {
      activeCase.value = i;
      const result = await runPython(source, sample.input, controller.signal);
      localResults.value.push({stdout:result.stdout,error:result.stderr,status:result.stderr ? 'runtime_error' : sameOutput(result.stdout,sample.output) ? 'accepted' : 'wrong_answer'});
    }
  } catch(e) { runStopped.value=controller.signal.aborted; runError.value=e instanceof Error ? e.message : '运行失败'; }
  finally { running.value=false; emit('runningChange', false); if (!runError.value) activeCase.value=Math.max(0,localResults.value.findIndex(item=>item.status !== 'accepted')); }
}
onBeforeUnmount(()=>{ controller?.abort(); emit('runningChange', false); });
const headingId = computed(() => `practice-question-${props.question.id}`);
const hasAnswer = computed(() => props.question.kind === "python" ? Boolean(props.code.trim()) : Boolean(props.answer.trim()));
const resultDetail = computed(() => {
  const detail = props.result?.feedback.detail?.trim() ?? "";
  return props.result && detail !== submissionStatusName[props.result.status] ? detail : "";
});
</script>
