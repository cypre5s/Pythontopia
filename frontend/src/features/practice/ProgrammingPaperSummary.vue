<template>
  <article class="practice-question-card programming-paper-summary" :data-question-id="question.id" :aria-labelledby="headingId">
    <header class="practice-question-heading">
      <span class="question-kind">编程题</span>
      <h3 :id="headingId">{{ index + 1 }}. {{ question.title }}</h3>
    </header>
    <p class="question-prompt">{{ question.prompt }}</p>
    <div class="coding-summary-footer">
      <div class="coding-summary-status" role="status">
        <GameIcon :name="result?.status === 'accepted' ? 'check' : 'code'" />
        <span>{{ readOnly ? '答案已锁定' : edited ? '已作答' : '未作答' }}</span>
        <p>{{ outcome }}</p>
      </div>
      <button class="town-secondary coding-summary-edit" @click="$emit('open')"><GameIcon :name="readOnly ? 'code' : 'practice'" /><span>{{ readOnly ? '查看单题详情' : '进入单题编辑' }}</span></button>
    </div>
  </article>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import GameIcon from '../../shared/GameIcon.vue';
import type { Question, Submission } from '../../shared/types';
import type { AssistantFeedback } from '../../shared/assistantState';
import { pendingSubmissionStates, submissionStatusName } from './practicePresentation';

const props = defineProps<{ question: Question; index: number; code: string; readOnly?: boolean; result?: Submission; feedback?: AssistantFeedback }>();
defineEmits<{ open: [] }>();
const headingId = computed(() => `practice-question-${props.question.id}`);
const edited = computed(() => Boolean(props.code.trim()) && props.code.trim() !== (props.question.starter_code ?? '').trim());
function counts(passed: number, total: number) {
  return passed === 0 ? `全部未通过（0 / ${total}）` : `通过 ${passed} / ${total}`;
}
const outcome = computed(() => {
  const result = props.result;
  if (result) {
    const label = result.mode === 'submit' ? '正式判题' : '公开样例';
    const { passed_count: passed, total_count: total } = result.feedback;
    const pending = [...pendingSubmissionStates, 'infrastructure_error', 'submission_unknown'].includes(result.status);
    const validCounts = Number.isInteger(passed) && Number.isInteger(total) && total! > 0 && passed! >= 0 && passed! <= total!;
    return `${label} · ${!pending && validCounts ? counts(passed!, total!) : submissionStatusName[result.status]}`;
  }
  if (props.readOnly) return '等待正式判题';
  const run = props.feedback?.local_run;
  if (!run || run.question_id !== props.question.id) return '尚未运行公开样例';
  const stale = run.code !== props.code;
  const prefix = stale ? '上次公开样例（代码已修改）' : '公开样例';
  if (run.stopped) return `${prefix} · 运行已停止`;
  if (run.error) return `${prefix} · 执行出错`;
  const passed = run.cases.filter(item => item.status === 'accepted').length;
  return `${prefix} · ${run.cases.length ? counts(passed, props.question.samples.length) : '暂无结果'}`;
});
</script>
