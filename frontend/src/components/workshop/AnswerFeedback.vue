<script setup lang="ts">
/**
 * AnswerFeedback —— 👍/👎 轻反馈（低摩擦常显，hover 提亮）。
 *
 * 点击 emit feedback(up)；随后进入已反馈态：所点按钮变实心 + 「已记录」小字，
 * 两个按钮一并失效（再点不再 emit）。messageId 仅用于 key / 测试定位。
 */
import { ref } from 'vue';

defineProps<{ messageId: string }>();

const emit = defineEmits<{ (e: 'feedback', up: boolean): void }>();

const voted = ref<'up' | 'down' | null>(null);

function vote(up: boolean): void {
  if (voted.value !== null) return;
  voted.value = up ? 'up' : 'down';
  emit('feedback', up);
}
</script>

<template>
  <div
    class="feedback"
    data-testid="answer-feedback"
    :data-message-id="messageId"
  >
    <button
      type="button"
      class="feedback__btn"
      :class="{ 'feedback__btn--active': voted === 'up', 'feedback__btn--mute': voted === 'down' }"
      :disabled="voted !== null"
      title="这条回答有帮助"
      data-testid="feedback-up"
      @click="vote(true)"
    >
      👍
    </button>
    <button
      type="button"
      class="feedback__btn"
      :class="{ 'feedback__btn--active': voted === 'down', 'feedback__btn--mute': voted === 'up' }"
      :disabled="voted !== null"
      title="这条回答没帮上"
      data-testid="feedback-down"
      @click="vote(false)"
    >
      👎
    </button>
    <span
      v-if="voted !== null"
      class="feedback__done"
      data-testid="feedback-done"
    >已记录</span>
  </div>
</template>

<style scoped>
.feedback {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: var(--pyt-font-pixel);
}
.feedback__btn {
  font-family: inherit;
  font-size: 12px;
  line-height: 1;
  padding: 2px 6px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--pyt-radius-md);
  cursor: pointer;
  opacity: 0.7;
  transition:
    opacity var(--pyt-motion-base) ease,
    background var(--pyt-motion-base) ease;
}
.feedback__btn:hover:not(:disabled) {
  opacity: 1;
  background: #ffe098;
  border-color: #8c6a23;
}
.feedback__btn:disabled {
  cursor: default;
}
.feedback__btn--active {
  opacity: 1;
  background: #f7d774;
  border-color: #8c6a23;
  box-shadow: 1px 1px 0 rgba(140, 106, 35, 0.5);
}
.feedback__btn--mute {
  opacity: 0.35;
}
.feedback__done {
  font-size: 10px;
  font-weight: 700;
  color: var(--pyt-color-success);
}
@media (prefers-reduced-motion: reduce) {
  .feedback__btn {
    transition: none;
  }
}
</style>
