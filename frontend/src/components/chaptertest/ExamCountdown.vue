<script setup lang="ts">
// 倒计时：以服务端下发的 deadlineAt 为锚（每 tick 用 Date.now() 重算，抗漂移），到点 emit('expire')。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

const props = withDefaults(
  defineProps<{
    deadlineAt: string;
    active?: boolean;
  }>(),
  { active: true },
);

const emit = defineEmits<{ (e: 'expire'): void }>();

function computeRemaining(): number {
  const deadline = Date.parse(props.deadlineAt);
  if (Number.isNaN(deadline)) return 0;
  return Math.max(deadline - Date.now(), 0);
}

// 用截止时间初始化，避免首帧闪现 00:00（computeRemaining 为函数声明，已提升）。
const remainingMs = ref<number>(computeRemaining());
let timer: number | null = null;
let fired = false;

function stop(): void {
  if (timer !== null) {
    window.clearInterval(timer);
    timer = null;
  }
}

function tick(): void {
  remainingMs.value = computeRemaining();
  if (remainingMs.value <= 0 && !fired) {
    fired = true;
    stop();
    emit('expire');
  }
}

function start(): void {
  stop();
  tick();
  if (props.active && !fired && typeof window !== 'undefined') {
    timer = window.setInterval(tick, 500);
  }
}

const totalSeconds = computed<number>(() => Math.ceil(remainingMs.value / 1000));
const minutes = computed<number>(() => Math.floor(totalSeconds.value / 60));
const seconds = computed<number>(() => totalSeconds.value % 60);
const display = computed<string>(
  () => `${String(minutes.value).padStart(2, '0')}:${String(seconds.value).padStart(2, '0')}`,
);
const urgent = computed<boolean>(() => remainingMs.value > 0 && remainingMs.value <= 60_000);
const expired = computed<boolean>(() => remainingMs.value <= 0);

onMounted(() => {
  if (props.active) start();
  else tick();
});

watch(
  () => props.active,
  (active) => {
    if (active) start();
    else stop();
  },
);

watch(
  () => props.deadlineAt,
  () => {
    fired = false;
    start();
  },
);

onBeforeUnmount(stop);
</script>

<template>
  <div
    class="ct-countdown"
    :class="{ 'ct-countdown--urgent': urgent, 'ct-countdown--expired': expired }"
    data-testid="exam-countdown"
    role="timer"
    aria-live="polite"
  >
    <span class="ct-countdown__icon">⏱</span>
    <span class="ct-countdown__time">{{ display }}</span>
    <span class="ct-countdown__hint">
      {{ expired ? '已截止' : urgent ? '即将截止' : '剩余时间' }}
    </span>
  </div>
</template>

<style scoped>
.ct-countdown {
  display: inline-flex;
  align-items: center;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-2) var(--pyt-space-4);
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-md);
  font-family: var(--pyt-font-mono);
  color: var(--pyt-color-text-primary);
  box-shadow: var(--pyt-shadow-card);
}
.ct-countdown__icon { font-size: var(--pyt-font-size-lg); }
.ct-countdown__time { font-size: var(--pyt-font-size-xl); font-weight: 700; letter-spacing: 1px; }
.ct-countdown__hint { font-size: var(--pyt-font-size-xs); color: var(--pyt-color-text-muted); }
.ct-countdown--urgent {
  border-color: var(--pyt-color-danger);
  color: var(--pyt-color-danger);
  background: var(--pyt-color-danger-bg);
}
.ct-countdown--urgent .ct-countdown__hint { color: var(--pyt-color-danger); }
.ct-countdown--expired {
  border-color: var(--pyt-color-text-muted);
  color: var(--pyt-color-text-muted);
  background: var(--pyt-color-bg-sunken);
}
</style>
