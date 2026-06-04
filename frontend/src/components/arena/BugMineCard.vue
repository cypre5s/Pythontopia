<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, computed } from 'vue';
import type { BossProblemDto } from '@/api/dto/Boss';

defineProps<{ problem: BossProblemDto }>();
const code = defineModel<string>('code', { required: true });
const emit = defineEmits<{ (e: 'timeout'): void }>();

const TOTAL_SECONDS = 30;
const remaining = ref(TOTAL_SECONDS);
const started = ref(false);
const expired = ref(false);
let timer: ReturnType<typeof setInterval> | null = null;

const progress = computed(() => remaining.value / TOTAL_SECONDS);

const urgency = computed<'safe' | 'warn' | 'danger'>(() => {
  if (remaining.value > 15) return 'safe';
  if (remaining.value > 5) return 'warn';
  return 'danger';
});

function startTimer(): void {
  if (started.value) return;
  started.value = true;
  remaining.value = TOTAL_SECONDS;
  timer = setInterval(() => {
    remaining.value -= 1;
    if (remaining.value <= 0) {
      remaining.value = 0;
      expired.value = true;
      if (timer) clearInterval(timer);
      timer = null;
      emit('timeout');
    }
  }, 1000);
}

onMounted(() => {
  startTimer();
});

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
});
</script>

<template>
  <div class="bug-mine-card" :class="{ expired }" data-testid="bug-mine-card">
    <header class="card-head">
      <span class="card-icon">[B]</span>
      <h3>Bug 矿洞</h3>
    <div
        :class="['timer', `timer--${urgency}`]"
        role="timer"
        :aria-label="`剩余 ${remaining} 秒`"
    >
        <div class="timer-bar" :style="{ width: `${progress * 100}%` }" />
        <span class="timer-text">{{ remaining }}s</span>
    </div>
    </header>

    <div v-if="expired" class="expired-overlay">
      <p>时间到！</p>
      <p class="expired-hint">已自动提交你的当前代码。</p>
    </div>

    <pre class="question">{{ problem.statement }}</pre>

    <textarea
      v-model="code"
      data-testid="bug-mine-code"
      rows="8"
      :disabled="expired"
      placeholder="# 快速修复 Bug！30 秒倒计时已开始…"
      spellcheck="false"
      class="mine-editor"
    />

    <p class="hint">
      限时 30 秒！找到 Bug 并修复代码。时间到自动提交。
    </p>
  </div>
</template>

<style scoped>
.bug-mine-card { display: grid; gap: 12px; position: relative; }
.bug-mine-card.expired { opacity: 0.7; }
.card-head { display: flex; align-items: center; gap: 8px; }
.card-icon { font-size: 20px; }
.card-head h3 { margin: 0; font-size: 16px; color: var(--pyt-color-gold-bright); }
.timer { position: relative; margin-left: auto; width: 120px; height: 24px; background: rgba(10,20,40,0.8); border: 2px solid #4a90e2; border-radius: 3px; overflow: hidden; }
.timer-bar { position: absolute; inset: 0; background: #6bbf59; transition: width 1s linear; }
.timer--warn .timer-bar { background: var(--pyt-color-gold); }
.timer--danger .timer-bar { background: #ff7466; animation: pulse 0.5s infinite alternate; }
.timer-text { position: relative; z-index: 1; display: flex; align-items: center; justify-content: center; height: 100%; font-size: 13px; color: #fff; font-weight: bold; }
.expired-overlay { position: absolute; inset: 0; background: rgba(10,10,20,0.8); display: flex; flex-direction: column; align-items: center; justify-content: center; z-index: 2; border-radius: 6px; }
.expired-overlay p { margin: 0; font-size: 20px; color: #ff7466; }
.expired-hint { font-size: 13px !important; color: #93c5fd !important; margin-top: 8px !important; }
.question { margin: 0; padding: 10px; background: #0a1428; border: 2px solid #b3402a; white-space: pre-wrap; font-size: 13px; line-height: 1.7; color: #e5e7eb; }
.mine-editor { font-family: 'JetBrains Mono', monospace; font-size: 13px; background: #0a1428; color: #f0f9ff; border: 2px solid var(--pyt-color-gold); padding: 8px; resize: vertical; min-height: 120px; }
.mine-editor:disabled { opacity: 0.5; }
.hint { font-size: 12px; color: rgba(255,247,230,0.5); margin: 0; }
@keyframes pulse { from { opacity: 0.7; } to { opacity: 1; } }
@media (prefers-reduced-motion: reduce) {
  .timer-bar { transition: none; }
  .timer--danger .timer-bar { animation: none; }
}
</style>
