<template>
  <article ref="workbench" class="practice-question-card coding-workbench" :class="{ 'is-dragging': dragAxis }" :style="{ '--problem-fr': problemWidth + 'fr', '--code-fr': (100 - problemWidth) + 'fr', '--editor-fr': editorHeight + 'fr', '--cases-fr': (100 - editorHeight) + 'fr' }" :data-question-id="question.id">
    <section class="coding-pane coding-problem" aria-label="题目内容">
      <header class="coding-pane-title">题目描述</header>
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
      <header class="coding-pane-title">代码 <span>Python · 标准输入输出</span></header>
      <pre v-if="readOnly || pendingMode" class="question-readonly-code">{{ code }}</pre>
      <CodeEditor v-else :model-value="code" @update:model-value="$emit('update:code', $event)" />
    </section>
    <section class="coding-pane coding-tests" aria-label="用例与结果">
      <header class="coding-pane-title coding-tabs" role="group" aria-label="用例视图">
        <button :aria-pressed="tab === 'cases'" @click="tab = 'cases'">测试用例</button>
        <button :aria-pressed="tab === 'results'" @click="tab = 'results'">测试结果</button>
      </header>
      <div class="coding-pane-body">
        <div class="case-tabs" role="group" aria-label="选择公开用例"><button v-for="(_, i) in question.samples" :key="i" :aria-pressed="activeCase === i" @click="activeCase = i">Case {{ i + 1 }}</button></div>
        <template v-if="tab === 'cases' && selectedSample"><label>输入</label><pre>{{ selectedSample.input || '（空输入）' }}</pre><label>预期输出</label><pre>{{ selectedSample.output || '（无输出）' }}</pre></template>
        <div v-else-if="tab === 'results'" aria-live="polite">
          <p v-if="running">正在运行公开样例…</p><p v-if="runError" role="alert">{{ runError }}</p>
          <template v-if="selectedResult"><strong>样例 {{ activeCase + 1 }} · {{ selectedResult.passed ? '通过' : '未通过' }}</strong><pre>{{ selectedResult.stdout || '（无输出）' }}</pre><pre v-if="selectedResult.stderr">{{ selectedResult.stderr }}</pre></template>
          <p v-else-if="!running && !runError">运行后在这里查看结果。公开样例不计入成绩。</p>
        </div>
        <div v-if="result" class="submission-result" :data-status="result.status">
          <strong role="status">正式提交 · {{ submissionStatusName[result.status] }}</strong><p v-if="resultDetail">{{ resultDetail }}</p>
          <section v-for="(sample, i) in result.feedback.samples ?? []" :key="i" class="public-sample-feedback"><h4>公开样例 {{ i + 1 }} · {{ submissionStatusName[sample.status] }}</h4><pre>{{ sample.stdout || '（无输出）' }}</pre><pre v-if="sample.stderr">{{ sample.stderr }}</pre><pre v-if="sample.compile_output">{{ sample.compile_output }}</pre></section>
        </div>
      </div>
    </section>
    <Teleport :to="toolbarTarget || 'body'" :disabled="!toolbarTarget">
      <div v-if="!readOnly" class="coding-actions">
        <button :disabled="busy || !hasAnswer || Boolean(pendingMode)" @click="running ? controller?.abort() : runSamples()"><GameIcon :name="running ? 'stop' : 'play'" />{{ running ? '停止运行' : '运行公开样例' }}</button>
        <button v-if="showSubmit" class="primary" :disabled="busy || running || !hasAnswer" @click="$emit('submit', 'submit')"><GameIcon :name="busy ? 'refresh' : 'publish'" />{{ busy ? '等待判定…' : pendingMode ? '重试提交' : '提交代码' }}</button>
      </div>
    </Teleport>
  </article>
</template>
<script setup lang="ts">
import { computed, defineAsyncComponent, ref, onBeforeUnmount } from "vue";
import GameIcon from "../shared/GameIcon.vue";
import { runPython, sameOutput } from "../shared/pythonRunner";
import type { Question, Submission } from "../shared/types";
import { submissionStatusName } from "../shared/practicePresentation";
const CodeEditor = defineAsyncComponent(() => import("./CodeEditor.vue"));
const props = withDefaults(defineProps<{ question: Question; index: number; answer: string; code: string; readOnly?: boolean; busy?: boolean; showSubmit?: boolean; pendingMode?: 'submit'; toolbarTarget?: string; result?: Submission }>(), { readOnly: false, busy: false, showSubmit: true });
defineEmits<{ "update:answer": [value: string]; "update:code": [value: string]; submit: [mode: "submit"] }>();
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
const activeCase = ref(0), tab = ref<'cases' | 'results'>('cases');
const selectedSample = computed(()=>props.question.samples[activeCase.value]);
const selectedResult = computed(()=>localResults.value[activeCase.value]);
const running = ref(false), runError = ref('');
const localResults = ref<{stdout:string;stderr:string;passed:boolean}[]>([]);
let controller: AbortController | undefined;
async function runSamples() {
  if (running.value || props.busy || props.readOnly) return;
  tab.value='results'; running.value=true; runError.value=''; localResults.value=[];
  controller = new AbortController();
  const source = props.code;
  try {
    if (!props.question.samples.length) throw Error('此题尚未配置公开样例');
    for (const sample of props.question.samples) {
      const result = await runPython(source, sample.input, controller.signal);
      localResults.value.push({...result, passed:!result.stderr && sameOutput(result.stdout,sample.output)});
    }
  } catch(e) { runError.value=e instanceof Error ? e.message : '运行失败'; }
  finally { running.value=false; }
}
onBeforeUnmount(()=>controller?.abort());
const headingId = computed(() => `practice-question-${props.question.id}`);
const hasAnswer = computed(() => props.question.kind === "python" ? Boolean(props.code.trim()) : Boolean(props.answer.trim()));
const resultDetail = computed(() => {
  const detail = props.result?.feedback.detail?.trim() ?? "";
  return props.result && detail !== submissionStatusName[props.result.status] ? detail : "";
});
</script>
