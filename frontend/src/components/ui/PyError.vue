<script setup lang="ts">
/**
 * PyError：全站统一错误态（统一标准 §3 三态之一）。原因 + 可选「重试」动作，
 * 配合 PySkeleton（加载）/ PyEmptyState（空）覆盖异步区三态，避免白屏或裸 alert。
 */
import { computed } from 'vue';
import PyButton from './PyButton.vue';

const props = withDefaults(
  defineProps<{
    message?: string;
    retryable?: boolean;
  }>(),
  { message: '出了点问题，请稍后重试。', retryable: true },
);

const emit = defineEmits<{ (event: 'retry'): void }>();

const showRetry = computed<boolean>(() => props.retryable === true);
</script>

<template>
  <div
    class="py-error"
    role="alert"
    data-testid="py-error"
  >
    <span
      class="py-error__icon"
      aria-hidden="true"
    >⚠</span>
    <p class="py-error__msg">
      {{ message }}
    </p>
    <PyButton
      v-if="showRetry"
      variant="secondary"
      size="sm"
      @click="emit('retry')"
    >
      重试
    </PyButton>
  </div>
</template>

<style scoped>
.py-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--pyt-space-2);
  min-height: 120px;
  padding: var(--pyt-space-4);
  border: 2px dashed var(--pyt-color-danger);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-danger-bg);
  color: var(--pyt-color-danger);
  text-align: center;
  font-family: var(--pyt-font-read);
}
.py-error__icon {
  font-size: 28px;
  line-height: 1;
}
.py-error__msg {
  margin: 0;
  font-size: var(--pyt-font-size-sm);
  max-width: 320px;
  line-height: 1.5;
}
</style>
