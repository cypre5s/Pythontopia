<template>
  <ProgrammingQuestion v-if="question.kind === 'python'" v-bind="$props" @running-change="$emit('runningChange', $event)" @update:code="$emit('update:code', $event)" @submit="$emit('submit', $event)" />
  <article v-else class="practice-question-card" :data-question-id="question.id" :aria-labelledby="headingId">
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
    <div v-if="!readOnly && showSubmit" class="submission-actions">
      <button v-if="showSubmit" class="primary" :disabled="busy || !hasAnswer" @click="$emit('submit', 'submit')">{{ busy ? "等待判定…" : pendingMode === 'submit' ? '重试提交' : "提交答案" }}</button>
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
import { computed } from "vue";
import ProgrammingQuestion from "./ProgrammingQuestion.vue";
import GameIcon from "../shared/GameIcon.vue";
import type { Question, Submission, SubmissionStatus } from "../shared/types";
import { pendingSubmissionStates, submissionStatusName } from "../shared/practicePresentation";
const props = withDefaults(defineProps<{ question: Question; index: number; answer: string; code: string; readOnly?: boolean; busy?: boolean; showSubmit?: boolean; pendingMode?: 'submit'; toolbarTarget?: string; result?: Submission }>(), { readOnly: false, busy: false, showSubmit: true });
defineEmits<{ runningChange: [running: boolean]; "update:answer": [value: string]; "update:code": [value: string]; submit: [mode: "submit"] }>();
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
