<script setup lang="ts">
// 共享判题结果展示面板（纯展示，无副作用）。
// NFR-USE-003：结果容器常驻 DOM 并带 role="status" + aria-live="polite"，
// 让屏幕阅读器在判定出现/更新时自动播报 verdict，键盘/读屏用户无需主动查找。
import { computed } from 'vue';
import type { ChallengeMode, ChallengeResult } from '@/api/dto/CodeChallenge';

const props = defineProps<{
  result: ChallengeResult | null;
  mode: ChallengeMode;
}>();

const tone = computed<'idle' | 'success' | 'danger'>(() => {
  if (!props.result) return 'idle';
  return props.result.passed ? 'success' : 'danger';
});

const verdictLabel = computed<string>(() => props.result?.verdict ?? '');
const modeLabel = computed<string>(() => (props.mode === 'judge' ? '正式判题' : '本地预览'));
</script>

<template>
  <section
    class="judge-result"
    :class="`judge-result--${tone}`"
    role="status"
    aria-live="polite"
    data-testid="judge-result-panel"
  >
    <template v-if="result">
      <header
        class="judge-result__banner"
        data-testid="judge-result-banner"
      >
        <strong class="judge-result__verdict">
          {{ result.passed ? '通过' : '未通过' }}
          <span
            v-if="verdictLabel"
            class="judge-result__verdict-code"
            data-testid="judge-result-verdict"
          >{{ verdictLabel }}</span>
        </strong>
        <span class="judge-result__mode">{{ modeLabel }}</span>
      </header>

      <p
        class="judge-result__cases"
        data-testid="judge-result-cases"
      >
        通过用例 <strong>{{ result.passedCases }}/{{ result.totalCases }}</strong>
      </p>

      <p
        v-if="result.feedback"
        class="judge-result__feedback"
        data-testid="judge-result-feedback"
      >
        {{ result.feedback }}
      </p>

      <p
        v-if="result.firstHint"
        class="judge-result__hint"
        data-testid="judge-result-hint"
      >
        提示：{{ result.firstHint }}
      </p>
    </template>

    <p
      v-else
      class="judge-result__empty"
      data-testid="judge-result-empty"
    >
      尚未判题
    </p>
  </section>
</template>

<style scoped>
.judge-result {
  display: grid;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-3);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  background: var(--pyt-color-bg-soft);
  box-shadow: var(--pyt-shadow-card);
  color: var(--pyt-color-text-primary);
}

.judge-result--success {
  border-color: var(--pyt-color-success);
  background: var(--pyt-color-success-bg);
}

.judge-result--danger {
  border-color: var(--pyt-color-danger);
  background: var(--pyt-color-danger-bg);
}

.judge-result__banner {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--pyt-space-2);
}

.judge-result__verdict {
  display: inline-flex;
  align-items: baseline;
  gap: var(--pyt-space-2);
  font-family: var(--pyt-font-read);
  font-size: var(--pyt-font-size-xl);
  line-height: 1.2;
}

.judge-result--success .judge-result__verdict {
  color: var(--pyt-color-success);
}

.judge-result--danger .judge-result__verdict {
  color: var(--pyt-color-danger);
}

.judge-result__verdict-code {
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-hint);
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  padding: var(--pyt-space-1) var(--pyt-space-2);
}

.judge-result__mode {
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-hint);
}

.judge-result__cases {
  margin: 0;
  font-family: var(--pyt-font-read);
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-hint);
}

.judge-result__cases strong {
  color: var(--pyt-color-text-primary);
}

.judge-result__feedback {
  margin: 0;
  font-family: var(--pyt-font-read);
  font-size: var(--pyt-font-size-sm);
  line-height: 1.65;
  color: var(--pyt-color-text-primary);
}

.judge-result__hint {
  margin: 0;
  padding: var(--pyt-space-2) var(--pyt-space-3);
  border-left: 3px solid var(--pyt-color-warning);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-warning-bg);
  font-family: var(--pyt-font-read);
  font-size: var(--pyt-font-size-sm);
  line-height: 1.6;
  color: var(--pyt-color-text-hint);
}

.judge-result__empty {
  margin: 0;
  font-family: var(--pyt-font-read);
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-hint);
}
</style>
