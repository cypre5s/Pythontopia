<script setup lang="ts">
import { computed } from 'vue';
import type { WorkflowEvent } from '@/api/tutorWorkflow';

const props = defineProps<{
  verdict: string;
  disabled?: boolean;
}>();

const emit = defineEmits<{
  (e: 'continue', event: WorkflowEvent): void;
}>();

const isAccepted = computed(() => props.verdict === 'AC');
const copy = computed(() => {
  if (props.verdict === 'AC') {
    return {
      title: '这题过了。',
      body: '要不要我帮你做 30 秒复盘，再给一道迁移练习方向？',
      button: '30 秒复盘',
      testId: 'guide-nudge-ac-review',
      event: 'AC_REVIEW' as WorkflowEvent,
    };
  }
  if (props.verdict === 'CE') {
    return {
      title: '代码还没跑起来。',
      body: '我可以先看静态检查结果，帮你定位第一处语法或结构问题。',
      button: '开始分析',
      testId: 'guide-nudge-error-analysis',
      event: 'ERROR_FEEDBACK' as WorkflowEvent,
    };
  }
  if (props.verdict === 'RE' || props.verdict === 'TLE') {
    return {
      title: '程序运行时卡住了。',
      body: '我可以结合代码和判题结果，先给一层运行错误/超时分析。',
      button: '开始分析',
      testId: 'guide-nudge-error-analysis',
      event: 'ERROR_FEEDBACK' as WorkflowEvent,
    };
  }
  return {
    title: '这次没过。',
    body: '我可以先看代码和判题结果，给第一层错因分析。',
    button: '开始分析',
    testId: 'guide-nudge-error-analysis',
    event: 'ERROR_FEEDBACK' as WorkflowEvent,
  };
});

function continueGuide(): void {
  emit('continue', copy.value.event);
}
</script>

<template>
  <section
    class="guide-nudge"
    :class="{ 'guide-nudge--ac': isAccepted }"
    data-testid="guide-nudge"
  >
    <div>
      <strong>{{ copy.title }}</strong>
      <p>{{ copy.body }}</p>
    </div>
    <button
      type="button"
      class="guide-nudge__button"
      :data-testid="copy.testId"
      :disabled="disabled"
      @click="continueGuide"
    >
      {{ copy.button }}
    </button>
  </section>
</template>

<style scoped>
.guide-nudge {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  padding: 10px 12px;
  border: 2px solid var(--pyt-color-border);
  border-radius: 8px;
  background: rgba(255, 244, 214, 0.94);
  box-shadow: 0 3px 0 rgba(74, 54, 29, 0.24);
}
.guide-nudge--ac {
  background: rgba(226, 247, 215, 0.94);
}
.guide-nudge strong {
  display: block;
  font-family: var(--pyt-font-read);
  font-size: 14px;
}
.guide-nudge p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.55;
  color: var(--pyt-color-text-muted);
}
.guide-nudge__button {
  flex: 0 0 auto;
  font-family: inherit;
  font-size: 12px;
  padding: 6px 12px;
  cursor: pointer;
  color: #fff;
  background: var(--pyt-color-info, #2a6f97);
  border: 2px solid var(--pyt-color-border-strong, #2a1a05);
  border-radius: 4px;
}
.guide-nudge__button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
