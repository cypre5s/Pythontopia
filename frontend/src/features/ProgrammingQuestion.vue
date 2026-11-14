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
            <div class="coding-result-summary">
              <h4 class="coding-outcome" :data-tone="outcomeTone" role="status">{{ outcomeTitle }}</h4>
              <span v-if="resultSource === 'submission' && submissionCounts" class="coding-pass-count">{{ submissionCounts.passed }} / {{ submissionCounts.total }} 个测试用例通过</span>
              <span v-if="resultSource === 'local' && runDuration !== undefined" class="coding-run-time">用时 {{ runDuration }} ms</span>
              <button v-if="resultSource === 'local' && selectedResult?.status === 'wrong_answer'" class="coding-diff-toggle" :aria-pressed="diffEnabled" @click="diffEnabled = !diffEnabled">Diff {{ diffEnabled ? '开' : '关' }}</button>
            </div>
            <p v-if="resultSource === 'submission' && resultDetail">{{ resultDetail }}</p>
            <pre v-if="resultSource === 'submission' && publicDiagnostic" class="coding-error" role="alert">{{ publicDiagnostic }}</pre>
            <section v-if="resultSource === 'submission' && failureHints.length" class="coding-failure-hints" aria-label="排查建议">
              <h5>{{ outcome === 'wrong_answer' ? '为什么运行正确，提交却错误？' : '可以检查这些地方' }}</h5>
              <ul><li v-for="hint in failureHints" :key="hint">{{ hint }}</li></ul>
              <p>以上是排查方向，不代表已确定的错误原因。</p>
            </section>
            <pre v-if="runError && resultSource === 'local'" class="coding-error" role="alert">{{ runError }}</pre>
          </template>
          <template v-if="tab === 'cases' || resultSource === 'local'">
          <div class="case-tabs" role="group" aria-label="选择公开用例"><button v-for="(_, i) in question.samples" :key="i" :aria-pressed="activeCase === i" @click="activeCase = i"><GameIcon v-if="tab === 'results' && displayedResults[i]" :name="displayedResults[i]?.status === 'accepted' ? 'check' : 'circleX'" :class="displayedResults[i]?.status === 'accepted' ? 'case-pass' : 'case-fail'" />Case {{ i + 1 }}</button></div>
          <template v-if="tab === 'cases' && selectedSample"><label>输入</label><pre>{{ selectedSample.input || '（空输入）' }}</pre><label>预期输出</label><pre>{{ selectedSample.output || '（无输出）' }}</pre></template>
          <template v-else-if="tab === 'results'">
            <pre v-if="selectedResult?.error" class="coding-error" role="alert">{{ selectedResult.error }}</pre>
            <template v-if="selectedResult || (runError && resultSource === 'local')">
              <label>{{ selectedResult?.error || runError ? '执行时的输入' : '输入' }}</label><pre>{{ selectedSample?.input || '（空输入）' }}</pre>
              <template v-if="selectedResult">
                <label>实际输出 <span v-if="showDiff" class="diff-legend">红色：多余或不匹配的内容</span></label>
                <pre v-if="showDiff" class="output-diff"><template v-if="comparedOutput.actual.length"><span v-for="(part, i) in comparedOutput.actual" :key="i" :class="{ 'diff-extra': part.changed }">{{ visibleDiffText(part) }}</span></template><template v-else>（无输出）</template></pre>
                <pre v-else>{{ selectedResult.stdout || '（无输出）' }}</pre>
                <label>预期输出 <span v-if="showDiff" class="diff-legend">绿色：缺少或需要替换的内容</span></label>
                <pre v-if="showDiff" class="output-diff"><template v-if="comparedOutput.expected.length"><span v-for="(part, i) in comparedOutput.expected" :key="i" :class="{ 'diff-missing': part.changed }">{{ visibleDiffText(part) }}</span></template><template v-else>（无输出）</template></pre>
                <pre v-else>{{ selectedSample?.output || '（无输出）' }}</pre>
              </template>
            </template>
          </template>
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
import { computed, defineAsyncComponent, ref, watch, onMounted, onBeforeUnmount } from "vue";
import GameIcon from "../shared/GameIcon.vue";
import { randomUUID } from '../shared/uuid';
import type { AssistantFeedback, AssistantLocalRun } from '../shared/assistantState';
import { createPythonRunner, sameOutput } from "../shared/pythonRunner";
import { outputDiff, visibleDiffText } from "../shared/outputDiff";
import type { Question, Submission, SubmissionStatus } from "../shared/types";
import { pendingSubmissionStates, submissionStatusName } from "../shared/practicePresentation";
const CodeEditor = defineAsyncComponent(() => import("./CodeEditor.vue"));
const props = withDefaults(defineProps<{ question: Question; index: number; answer: string; code: string; readOnly?: boolean; busy?: boolean; showSubmit?: boolean; pendingMode?: 'submit'; toolbarTarget?: string; result?: Submission; assistantFeedback?: AssistantFeedback }>(), { readOnly: false, busy: false, showSubmit: true });
const emit = defineEmits<{ runningChange: [running: boolean]; "update:answer": [value: string]; "update:code": [value: string]; submit: [mode: "submit"]; assistantFeedback: [feedback: AssistantFeedback] }>();
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
const localResults = ref<CaseResult[]>(props.assistantFeedback?.local_run?.cases ?? []);
const latestRun = ref<AssistantLocalRun | undefined>(props.assistantFeedback?.local_run);
const resultSource = ref<'local' | 'submission'>(props.assistantFeedback?.displayed === 'local_run' ? 'local' : props.result ? 'submission' : 'local');
const judging = computed(() => running.value || props.busy || Boolean(props.result && pendingSubmissionStates.includes(props.result.status)));
const displayedResults = computed<CaseResult[]>(() => resultSource.value === 'local' ? localResults.value : (props.result?.feedback.samples ?? []).map(sample => ({ stdout: sample.stdout ?? '', error: [sample.stderr, sample.compile_output].filter(Boolean).join('\n'), status: sample.status })));
const selectedResult = computed(() => displayedResults.value[activeCase.value]);
const publicDiagnostic = computed(() => ['runtime_error', 'compile_error'].includes(props.result?.status ?? '')
  ? (props.result?.feedback.samples ?? []).map(sample => [sample.stderr, sample.compile_output].filter(Boolean).join('\n')).find(Boolean) : undefined);
const diffEnabled = ref(true), runDuration = ref<number>();
const showDiff = computed(() => diffEnabled.value && selectedResult.value?.status === 'wrong_answer');
const comparedOutput = computed(() => outputDiff(selectedResult.value?.stdout ?? '', selectedSample.value?.output ?? ''));
const submissionCounts = computed(() => {
  const feedback = props.result?.feedback;
  if (!props.result || pendingSubmissionStates.includes(props.result.status) || ['infrastructure_error', 'submission_unknown'].includes(props.result.status)) return undefined;
  const passed = feedback?.passed_count, total = feedback?.total_count;
  return Number.isInteger(passed) && Number.isInteger(total) && total! > 0 && passed! >= 0 && passed! <= total! ? {passed, total} : undefined;
});
const failureHints = computed(() => {
  switch (outcome.value) {
    case 'wrong_answer': return ['公开样例只覆盖部分输入，正式提交还会检查其他数据和边界情况。', '核对空格、换行、中英文标点；不要输出 input() 提示、调试文字或多余说明。', '检查最小值、零、负数、重复值及多行输入，避免只针对示例写死答案。'];
    case 'runtime_error': case 'compile_error': return ['检查缩进、语法、变量名，以及输入是否按题目要求读取。', '考虑空输入、下标越界、除零或类型转换失败；浏览器与判题环境的 Python 版本、可用库也可能不同。'];
    case 'time_limit': return ['检查循环是否能够结束，是否读取了超出题目范围的输入。', '较大的测试数据可能使重复计算超时，尝试减少循环和重复工作。'];
    case 'memory_limit': return ['检查是否创建了过大的列表、字符串，或不断向容器中添加数据。'];
    default: return [];
  }
});
const outcome = computed<SubmissionStatus | undefined>(() => {
  if (resultSource.value === 'submission') return props.result?.status;
  if (runError.value) return 'runtime_error';
  if (!localResults.value.length) return undefined;
  return localResults.value.find(item => item.error)?.status ?? localResults.value.find(item => item.status !== 'accepted')?.status ?? 'accepted';
});
const outcomeTitle = computed(() => runStopped.value && resultSource.value === 'local' ? '已停止运行' : outcome.value === 'accepted' ? '通过' : outcome.value === 'wrong_answer' ? '解答错误' : outcome.value === 'runtime_error' || outcome.value === 'compile_error' ? '执行出错' : outcome.value ? submissionStatusName[outcome.value] : '');
const outcomeTone = computed(() => runStopped.value && resultSource.value === 'local' ? 'warning' : outcome.value === 'accepted' ? 'success' : ['submission_unknown', 'infrastructure_error'].includes(outcome.value ?? '') ? 'warning' : 'error');
watch([latestRun, resultSource, activeCase], () => emit('assistantFeedback', {
  local_run: latestRun.value,
  displayed: resultSource.value === 'local' ? 'local_run' : 'submission',
  selected_case: activeCase.value,
}), { deep: true });
watch(() => [props.result, props.busy], () => { resultSource.value = 'submission'; runError.value = ''; tab.value = 'results';
  if (props.result && !pendingSubmissionStates.includes(props.result.status)) activeCase.value = Math.max(0, (props.result.feedback.samples ?? []).findIndex(sample => sample.status !== 'accepted'));
});
let controller: AbortController | undefined;
let runVersion = 0;
let pythonRunner: ReturnType<typeof createPythonRunner> | undefined;
onMounted(() => { if (!props.readOnly && props.question.samples.length) pythonRunner = createPythonRunner(props.question.samples.length); });
watch(() => props.question.id, () => {
  runVersion++; running.value=false; emit('runningChange', false);
  controller?.abort(); pythonRunner?.dispose(); pythonRunner = undefined;
  localResults.value=[]; runError.value=''; runStopped.value=false; runDuration.value=undefined;
  activeCase.value=0; diffEnabled.value=true; resultSource.value=props.result ? 'submission' : 'local';
  tab.value=props.result ? 'results' : 'cases';
  if (!props.readOnly && props.question.samples.length) pythonRunner=createPythonRunner(props.question.samples.length);
});
async function runSamples() {
  if (running.value || props.busy || props.readOnly) return;
  resultSource.value='local'; runStopped.value=false; activeCase.value=0; tab.value='results'; running.value=true; emit('runningChange', true); runError.value=''; localResults.value=[];
  const currentController = new AbortController(), version = ++runVersion;
  controller = currentController;
  const source = props.code, started = performance.now();
  runDuration.value = undefined;
  try {
    if (!props.question.samples.length) throw Error('此题尚未配置公开样例');
    for (const [i, sample] of props.question.samples.entries()) {
      activeCase.value = i;
      pythonRunner ??= createPythonRunner(props.question.samples.length);
      const result = await pythonRunner.run(source, sample.input, currentController.signal);
      if (version !== runVersion) return;
      localResults.value.push({stdout:result.stdout,error:result.stderr,status:result.stderr ? 'runtime_error' : sameOutput(result.stdout,sample.output) ? 'accepted' : 'wrong_answer'});
    }
  } catch(e) { if (version === runVersion) { runStopped.value=currentController.signal.aborted; runError.value=e instanceof Error ? e.message : '运行失败'; } }
  finally { if (version === runVersion) { runDuration.value=Math.round(performance.now()-started); running.value=false; emit('runningChange', false); if (!runError.value) activeCase.value=Math.max(0,localResults.value.findIndex(item=>item.status !== 'accepted'));
    const cap = Math.min(16000, Math.floor(60000 / Math.max(1, localResults.value.length)));
    const bounded = (value: string) => value.length > cap ? value.slice(0, cap - 20) + '\n（输出过长，已省略）' : value;
    latestRun.value = { question_id: props.question.id, run_id: randomUUID(), code: source, completed_at: new Date().toISOString(), duration_ms: runDuration.value,
      cases: localResults.value.map(item => ({ ...item, stdout: bounded(item.stdout), error: bounded(item.error) })), error: runError.value.slice(0, 2000), stopped: runStopped.value };
  } }
}
onBeforeUnmount(()=>{ runVersion++; controller?.abort(); pythonRunner?.dispose(); emit('runningChange', false); });
const headingId = computed(() => `practice-question-${props.question.id}`);
const hasAnswer = computed(() => props.question.kind === "python" ? Boolean(props.code.trim()) : Boolean(props.answer.trim()));
const resultDetail = computed(() => {
  const detail = props.result?.feedback.detail?.trim() ?? "";
  return props.result && detail !== submissionStatusName[props.result.status] ? detail : "";
});
</script>
