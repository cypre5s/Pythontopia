// 小镇氛围兼容 store（拉取 + 派发 + 可关闭开关）；后端不推断学生情绪。
//
// 职责：
//   - refresh()：拉 GET /api/learner/world-state，写入中性 mood 及客观 streak；
//     成功且未关闭氛围时，向 window 派发事件，由 WorldScene 重应用兼容氛围层。
//   - 失败一律降级到 'sunny'，绝不阻塞玩家或翻车（沿用 world store 的容错风格）。
//   - 玩家可通过「关闭情绪氛围」开关（localStorage 'pyt_mood_disabled'）关停氛围；
//     关闭后 refresh 不再派发，并立即派发一次 'sunny' 让 WorldScene 清场。
//
// 设计取舍：disabled 以 localStorage 为持久化真源，但同时镜像到一个 reactive state
// 字段，避免 Pinia getter「无 reactive 依赖 → 计算缓存永不失效」导致开关状态读旧值。
import { defineStore } from 'pinia';
import { fetchWorldState } from '@api/learner';

export type Mood = 'sunny' | 'cloudy' | 'rainy';

const DISABLED_KEY = 'pyt_mood_disabled';
const MOOD_EVENT = 'pythontopia:mood-changed';

function readDisabled(): boolean {
  if (typeof window === 'undefined') return false;
  try {
    return window.localStorage.getItem(DISABLED_KEY) === '1';
  } catch {
    return false;
  }
}

function persistDisabled(value: boolean): void {
  if (typeof window === 'undefined') return;
  try {
    if (value) {
      window.localStorage.setItem(DISABLED_KEY, '1');
    } else {
      window.localStorage.removeItem(DISABLED_KEY);
    }
  } catch {
    // 隐私模式 localStorage 不可用：忽略写入失败，内存态仍生效。
  }
}

function dispatchMood(mood: Mood): void {
  if (typeof window === 'undefined') return;
  try {
    window.dispatchEvent(new CustomEvent(MOOD_EVENT, { detail: { mood } }));
  } catch {
    // CustomEvent 不可用（极端环境）时静默：氛围是锦上添花，不阻断主流程。
  }
}

export const useMoodStore = defineStore('mood', {
  state: () => ({
    mood: 'sunny' as Mood,
    streak: 0,
    emotionScore: 1,
    loading: false,
    // localStorage 的 reactive 镜像（见文件顶部说明）。
    ambienceDisabled: readDisabled(),
  }),
  getters: {
    /** 玩家是否已关闭情绪氛围（读 'pyt_mood_disabled' 的 reactive 镜像）。 */
    disabled(state): boolean {
      return state.ambienceDisabled;
    },
  },
  actions: {
    async refresh(): Promise<void> {
      this.loading = true;
      try {
        const worldState = await fetchWorldState();
        this.mood = worldState.mood;
        this.streak = worldState.streak;
        this.emotionScore = worldState.emotionScore;
        if (!this.disabled) {
          dispatchMood(this.mood);
        }
      } catch {
        // 拉取失败：降级到 sunny，绝不阻塞玩家（不派发，避免误清/误染氛围）。
        this.mood = 'sunny';
      } finally {
        this.loading = false;
      }
    },
    /**
     * 切换情绪氛围开关并持久化。关闭→立即派发 'sunny' 让 WorldScene 清掉叠加层；
     * 重新开启→按当前 mood 立即恢复氛围。
     */
    setDisabled(value: boolean): void {
      this.ambienceDisabled = value;
      persistDisabled(value);
      dispatchMood(value ? 'sunny' : this.mood);
    },
  },
});
