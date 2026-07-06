<script setup lang="ts">
/**
 * QuizModeBar —— 考核模式驻顶条（ASKME 多轮口头小测）。
 *
 * 「✓ 考核模式 · 第 {round}/{total} 轮」+ KC 徽章（可空）+ 轮次进度点（●●○）
 * + 右侧「退出考核」按钮 emit exit。醒目暖橙底色但不刺眼；定位由调用方（WP5）负责。
 */
import { computed } from 'vue';

const props = withDefaults(
  defineProps<{
    round: number;
    total: number;
    kcLabel?: string;
  }>(),
  { kcLabel: '' },
);

const emit = defineEmits<{ (e: 'exit'): void }>();

/** 轮次进度点：已完成/进行中的轮 ●，其余 ○（round/total 防御性取整钳 0）。 */
const dots = computed<boolean[]>(() => {
  const total = Math.max(0, Math.floor(props.total));
  const round = Math.max(0, Math.floor(props.round));
  return Array.from({ length: total }, (_, i) => i < round);
});

function onExit(): void {
  emit('exit');
}
</script>

<template>
  <div
    class="quizbar"
    data-testid="quiz-mode-bar"
    role="status"
  >
    <span class="quizbar__mode">✓ 考核模式 · 第 {{ round }}/{{ total }} 轮</span>
    <span
      v-if="kcLabel"
      class="quizbar__kc"
      data-testid="quiz-kc-badge"
    >{{ kcLabel }}</span>
    <span
      class="quizbar__dots"
      aria-hidden="true"
    >
      <span
        v-for="(filled, i) in dots"
        :key="i"
        class="quizbar__dot"
        :class="{ 'quizbar__dot--filled': filled }"
        data-testid="quiz-dot"
      >{{ filled ? '●' : '○' }}</span>
    </span>
    <span class="quizbar__spacer" />
    <button
      type="button"
      class="quizbar__exit"
      data-testid="quiz-exit"
      @click="onExit"
    >
      退出考核
    </button>
  </div>
</template>

<style scoped>
.quizbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 6px 10px;
  font-family: var(--pyt-font-pixel);
  font-size: 12px;
  color: #6b3a00;
  background: linear-gradient(180deg, #ffe8c2 0%, #f9d9a4 100%);
  border: 2px solid #c2871f;
  border-radius: var(--pyt-radius-lg);
  box-shadow: 2px 2px 0 rgba(140, 106, 35, 0.45);
  animation: quizbar-in 0.18s ease-out;
}
.quizbar__mode {
  font-weight: 700;
  white-space: nowrap;
}
.quizbar__kc {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 8px;
  background: #fff8df;
  border: 1px solid #8c6a23;
  border-radius: var(--pyt-radius-full);
  color: #2a1a08;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.quizbar__dots {
  display: inline-flex;
  gap: 2px;
  font-size: 10px;
  line-height: 1;
  color: rgba(140, 106, 35, 0.55);
}
.quizbar__dot--filled {
  color: #c2871f;
}
.quizbar__spacer {
  flex: 1 1 auto;
}
.quizbar__exit {
  flex: none;
  font-family: inherit;
  font-size: 11px;
  padding: 2px 10px;
  background: #fff8df;
  border: 1px solid #8c6a23;
  border-radius: var(--pyt-radius-full);
  color: #2a1a08;
  cursor: pointer;
}
.quizbar__exit:hover {
  background: #ffe098;
}
@keyframes quizbar-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@media (prefers-reduced-motion: reduce) {
  .quizbar {
    animation: none;
  }
}
</style>
