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
/* 学习通考试倒计时白纸风：浅底细线，紧急/截止时浅红。 */
.ct-countdown {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 14px;
  background: #f7f8fa;
  border: 1px solid #e6e8ec;
  border-radius: 4px;
  font-family: var(--pyt-font-mono);
  color: #333;
}
.ct-countdown__icon { font-size: 15px; }
.ct-countdown__time { font-size: 18px; font-weight: 700; letter-spacing: 1px; }
.ct-countdown__hint { font-size: 12px; color: #9aa0a8; }
.ct-countdown--urgent {
  border-color: #f3c4bc;
  color: #e25c4a;
  background: #fdf3f2;
}
.ct-countdown--urgent .ct-countdown__hint { color: #e25c4a; }
.ct-countdown--expired {
  border-color: #dcdfe5;
  color: #9aa0a8;
  background: #f0f1f3;
}
</style>
