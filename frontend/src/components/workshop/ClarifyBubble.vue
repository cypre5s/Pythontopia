<script setup lang="ts">
/**
 * ClarifyBubble —— 低置信澄清气泡（assistant 左对齐）。
 *
 * 意图路由置信不足时不瞎猜：列出候选意图 chips（icon+label）让学生一点选定，
 * 点击 emit pick(intent)；reason 非空时以小字披露路由理由（可解释性）。
 */
interface ClarifyOption {
  intent: string;
  label: string;
  icon: string;
}

withDefaults(
  defineProps<{
    options: ClarifyOption[];
    reason?: string;
  }>(),
  { reason: '' },
);

const emit = defineEmits<{ (e: 'pick', intent: string): void }>();

function onPick(intent: string): void {
  emit('pick', intent);
}
</script>

<template>
  <div
    class="clarify"
    data-testid="clarify-bubble"
  >
    <p class="clarify__ask">
      🤔 我不太确定你想要什么——你是想？
    </p>
    <div class="clarify__chips">
      <button
        v-for="opt in options"
        :key="opt.intent"
        type="button"
        class="clarify__chip"
        :data-testid="`clarify-option-${opt.intent}`"
        @click="onPick(opt.intent)"
      >
        <span
          class="clarify__chip-icon"
          aria-hidden="true"
        >{{ opt.icon }}</span>
        {{ opt.label }}
      </button>
    </div>
    <p
      v-if="reason"
      class="clarify__reason"
      data-testid="clarify-reason"
    >
      路由理由：{{ reason }}
    </p>
  </div>
</template>

<style scoped>
.clarify {
  align-self: flex-start;
  max-width: 90%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 12px;
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
  background: #f5ecd4;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  animation: clarify-in 0.18s ease-out;
}
.clarify__ask {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.5;
}
.clarify__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.clarify__chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: inherit;
  font-size: 12px;
  padding: 3px 10px;
  background: #fff8df;
  border: 2px solid #8c6a23;
  border-radius: var(--pyt-radius-full);
  color: #2a1a08;
  cursor: pointer;
  box-shadow: 2px 2px 0 rgba(140, 106, 35, 0.5);
}
.clarify__chip:hover {
  background: #ffe098;
}
.clarify__chip:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}
.clarify__chip-icon {
  font-weight: 700;
  color: #8c6a23;
}
.clarify__reason {
  margin: 0;
  font-size: 10px;
  line-height: 1.4;
  color: var(--pyt-color-text-muted);
}
@keyframes clarify-in {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@media (prefers-reduced-motion: reduce) {
  .clarify {
    animation: none;
  }
}
</style>
