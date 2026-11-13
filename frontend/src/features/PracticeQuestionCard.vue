<template>
  <article class="practice-question-card" :class="{ 'programming-card': question.kind === 'python' }" :data-question-id="question.id" :aria-labelledby="headingId">
    <header class="practice-question-heading">
      <span class="question-kind">{{ kindName[question.kind] }}</span>
      <h3 :id="headingId">{{ index + 1 }}. {{ question.title }}</h3>
    </header>
    <section class="problem-description"><p class="question-prompt">{{ question.prompt }}</p><details v-for="(hint, i) in question.hints ?? []" :key="i"><summary>提示 {{ i + 1 }}</summary><p>{{ hint }}</p></details></section>
    <fieldset v-if="question.kind === 'single_choice' || question.kind === 'boolean'" class="question-options" :disabled="readOnly || busy || Boolean(pendingMode)" :aria-labelledby="headingId">
      <label v-for="(option, optionIndex) in question.options?.length ? question.options : ['正确', '错误']" :key="optionIndex" class="answer-option">
        <input :checked="answer === optionValue(option, optionIndex)" :disabled="readOnly || busy || Boolean(pendingMode)" type="radio" :name="`answer-${question.id}`" :value="optionValue(option, optionIndex)" @change="$emit('update:answer', optionValue(option, optionIndex))" />
        <span>{{ option }}</span>
      </label>
    </fieldset>
    <label v-else-if="question.kind === 'fill'" class="question-fill" :for="`answer-${question.id}`">填写答案
      <input :id="`answer-${question.id}`" :value="answer" :disabled="readOnly || busy || Boolean(pendingMode)" placeholder="输入你的答案" autocomplete="off" @input="$emit('update:answer', ($event.target as HTMLInputElement).value)" />
    </label>
    <section v-else class="programming-editor">
      <h4>Python · 标准输入输出</h4>
      <pre v-if="readOnly || pendingMode" class="question-readonly-code" tabindex="0" :aria-label="readOnly ? '已提交的代码' : '待确认的代码'">{{ code }}</pre>
      <CodeEditor v-else :model-value="code" @update:model-value="$emit('update:code', $event)" />
      <div v-for="(sample, sampleIndex) in question.samples ?? []" :key="sampleIndex" class="sample">
        <strong>公开样例 {{ sampleIndex + 1 }}</strong><pre>输入：{{ sample.input }}
输出：{{ sample.output }}</pre>
      </div>
      <section class="local-results" aria-live="polite"><p v-if="running">正在加载 Python 并运行公开样例…</p><p v-if="runError" role="alert">{{ runError }}</p><section v-for="(item, i) in localResults" :key="i"><strong>样例 {{ i + 1 }} · {{ item.passed ? '通过' : '未通过' }}</strong><pre>{{ item.stdout || '（无输出）' }}</pre><pre v-if="item.stderr">{{ item.stderr }}</pre></section></section>
    </section>
    <div v-if="!readOnly && (showSubmit || question.kind === 'python')" class="submission-actions">
      <button v-if="question.kind === 'python'" :disabled="busy || !hasAnswer || Boolean(pendingMode)" @click="running ? controller?.abort() : runSamples()">{{ running ? '停止运行' : '运行公开样例' }}</button>
      <button v-if="showSubmit" class="primary" :disabled="busy || running || !hasAnswer" @click="$emit('submit', 'submit')">{{ busy ? "等待判定…" : pendingMode === 'submit' ? '重试提交' : question.kind === 'python' ? '提交代码' : "提交答案" }}</button>
    </div>
    <div v-if="result" class="submission-result" :data-status="result.status">
      <header class="submission-result-heading">
        <GameIcon :name="result.status === 'accepted' ? 'check' : pendingSubmissionStates.includes(result.status) ? 'refresh' : 'close'" class="result-symbol" />
        <strong role="status">{{ result.mode === 'run' ? `公开样例 · ${submissionStatusName[result.status]}` : submissionStatusName[result.status] }}</strong>
      </header>
      <p v-if="resultDetail">{{ resultDetail }}</p>
      <section v-for="(sample, sampleIndex) in result.feedback.samples ?? []" :key="sampleIndex" class="public-sample-feedback" aria-label="公开样例反馈">
        <h4>公开样例 {{ sampleIndex + 1 }} · {{ publicStatusName(sample.status) }}</h4>
        <h5>标准输出</h5><pre tabindex="0">{{ sample.stdout || "（无输出）" }}</pre>
        <template v-if="sample.stderr"><h5>错误输出</h5><pre tabindex="0">{{ sample.stderr }}</pre></template>
        <template v-if="sample.compile_output"><h5>编译反馈</h5><pre tabindex="0">{{ sample.compile_output }}</pre></template>
      </section>
    </div>
  </article>
</template>
<script setup lang="ts">
import { computed, defineAsyncComponent, ref, onBeforeUnmount } from "vue";
import { runPython, sameOutput } from "../shared/pythonRunner";
import GameIcon from "../shared/GameIcon.vue";
import type { Question, Submission, SubmissionStatus } from "../shared/types";
import { pendingSubmissionStates, submissionStatusName } from "../shared/practicePresentation";
const CodeEditor = defineAsyncComponent(() => import("./CodeEditor.vue"));
const props = withDefaults(defineProps<{ question: Question; index: number; answer: string; code: string; readOnly?: boolean; busy?: boolean; showSubmit?: boolean; pendingMode?: 'submit'; result?: Submission }>(), { readOnly: false, busy: false, showSubmit: true });
defineEmits<{ "update:answer": [value: string]; "update:code": [value: string]; submit: [mode: "submit"] }>();
const running = ref(false), runError = ref('');
const localResults = ref<{stdout:string;stderr:string;passed:boolean}[]>([]);
let controller: AbortController | undefined;
async function runSamples() {
  running.value=true; runError.value=''; localResults.value=[];
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
const kindName = { single_choice: "单选题", boolean: "判断题", fill: "填空题", python: "Python 编程题" };
function optionValue(option: string, index: number) { return props.question.kind === "boolean" ? index === 0 ? "true" : "false" : option; }
function publicStatusName(status: SubmissionStatus) { return status === "accepted" ? "通过" : status === "wrong_answer" ? "答案未通过" : submissionStatusName[status]; }
</script>

<style scoped>
.programming-card { display:grid; grid-template-columns:minmax(180px, 0.8fr) minmax(250px, 1.2fr); gap:16px; align-content:start; }
.programming-card > header, .programming-card > .submission-actions, .programming-card > .submission-result { grid-column:1 / -1; }
.problem-description { min-width:0; white-space:pre-wrap; overflow-wrap:anywhere; }
.programming-editor { min-width:0; }
.programming-editor pre { white-space:pre-wrap; overflow-wrap:anywhere; }
.local-results { max-height:260px; overflow:auto; }
@media(max-width:1100px) { .programming-card { grid-template-columns: minmax(0,1fr); } }
</style>
