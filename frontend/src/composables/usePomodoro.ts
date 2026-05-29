import { ref, computed, watch, onUnmounted } from 'vue';
import { useGameSettingsStore } from '@stores/gameSettings';
import { useToastStore } from '@stores/toast';

export type PomodoroPhase = 'idle' | 'work' | 'break';

export function usePomodoro() {
  const settings = useGameSettingsStore();
  const toast = useToastStore();

  const phase = ref<PomodoroPhase>('idle');
  const secondsLeft = ref(0);
  let timer: number | null = null;

  const isRunning = computed(() => phase.value !== 'idle');
  const display = computed(() => {
    const m = Math.floor(secondsLeft.value / 60);
    const s = secondsLeft.value % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  });
  const progress = computed(() => {
    const total = phase.value === 'work'
      ? settings.education.pomodoroWorkMin * 60
      : settings.education.pomodoroBreakMin * 60;
    if (total <= 0) return 0;
    return Math.round(((total - secondsLeft.value) / total) * 100);
  });

  function clearTimer(): void {
    if (timer !== null) { clearInterval(timer); timer = null; }
  }

  function startTick(): void {
    clearTimer();
    timer = window.setInterval(() => {
      if (secondsLeft.value <= 0) {
        onPhaseEnd();
        return;
      }
      secondsLeft.value--;
    }, 1000);
  }

  function start(): void {
    if (!settings.education.pomodoroEnabled) return;
    phase.value = 'work';
    secondsLeft.value = settings.education.pomodoroWorkMin * 60;
    startTick();
  }

  function onPhaseEnd(): void {
    clearTimer();
    if (phase.value === 'work') {
      toast.push({ kind: 'success', text: '学习时间到! 休息一下吧' });
      phase.value = 'break';
      secondsLeft.value = settings.education.pomodoroBreakMin * 60;
      startTick();
    } else if (phase.value === 'break') {
      toast.push({ kind: 'info', text: '休息结束，继续学习!' });
      phase.value = 'work';
      secondsLeft.value = settings.education.pomodoroWorkMin * 60;
      startTick();
    }
  }

  function stop(): void {
    clearTimer();
    phase.value = 'idle';
    secondsLeft.value = 0;
  }

  function toggle(): void {
    if (isRunning.value) stop();
    else start();
  }

  watch(() => settings.education.pomodoroEnabled, (enabled) => {
    if (!enabled && isRunning.value) stop();
  });

  onUnmounted(() => clearTimer());

  return { phase, secondsLeft, isRunning, display, progress, start, stop, toggle };
}
