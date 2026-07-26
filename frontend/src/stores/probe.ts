// 世界内认知探针的本地 UI 状态 store（纯前端 + 一个 fire-and-forget 提交）。
//
// 探针是「中性、可跳过、低风险」的世界内选择卡（非表单问卷），从玩家选择推断维度
// （如 cog_intro → cognitive_style）。设计铁律：探针失败绝不阻塞玩家——
// 网络/未登录/后端 400 时一律本地标记「已答」并静默降级。
//
// 持久化：已答 probeKey 集合写 localStorage（pyt_probe_answered，pyt_* 约定），
// 避免刷新后重复打扰；shown 仅为单次会话内「已弹出」标记，不持久化。
//
// 键按登录用户分命名空间（`pyt_probe_answered@u:<username>`）：公用机房同一浏览器换账号时，
// 不带用户标识的键会让 B 永远不再被问到 A 答过的探针，B 的 8 维画像永久缺这些被动信号。
// 用户切换后由 session.logout/login 的全局 store 复位触发本 store 重建，从而读到新用户的键。

import { defineStore } from 'pinia';
import { submitProbe } from '@api/learner';
import { userScopedKey } from '@stores/session';

const PERSIST_KEY_BASE = 'pyt_probe_answered';

function persistKey(): string {
  return userScopedKey(PERSIST_KEY_BASE);
}

export interface ProbeState {
  /** 本次会话内已弹出过的 probeKey（不持久化，避免同一会话内重复弹）。 */
  shown: Set<string>;
  /** 已回答（含跳过）的 probeKey（持久化到 localStorage，跨刷新生效）。 */
  answered: Set<string>;
  /**
   * 已被外部事件请求弹出、但尚未在世界内展示的 probeKey（仅会话内，不持久化）。
   * 用于跨页面/路由传递触发信号：如练习失败发生在训练页（learning.submitToServer），
   * 而探针卡只挂在世界页 ProbeCard——置 requested 标记后，玩家回到世界即被监听弹出。
   */
  requested: Set<string>;
}

function readAnswered(): Set<string> {
  if (typeof window === 'undefined') return new Set();
  try {
    const raw = window.localStorage.getItem(persistKey());
    if (!raw) return new Set();
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return new Set();
    return new Set(parsed.filter((x): x is string => typeof x === 'string'));
  } catch {
    return new Set();
  }
}

function persistAnswered(answered: Set<string>): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(persistKey(), JSON.stringify([...answered]));
  } catch {
    // 隐私模式 localStorage 不可用：忽略写入失败，内存状态仍生效。
  }
}

function defaultState(): ProbeState {
  return {
    shown: new Set<string>(),
    answered: readAnswered(),
    requested: new Set<string>(),
  };
}

export const useProbeStore = defineStore('probe', {
  state: (): ProbeState => defaultState(),
  getters: {
    /** 是否应弹出该探针：尚未回答 且 本会话尚未弹出过。 */
    shouldShow(state): (probeKey: string) => boolean {
      return (probeKey: string): boolean =>
        !state.answered.has(probeKey) && !state.shown.has(probeKey);
    },
    /** 是否有外部事件请求过弹出该探针（如练习失败请求 fail_react）。 */
    isRequested(state): (probeKey: string) => boolean {
      return (probeKey: string): boolean => state.requested.has(probeKey);
    },
  },
  actions: {
    /** 标记探针已弹出（防止 watcher 反复触发同一张卡）。 */
    markShown(probeKey: string): void {
      this.shown.add(probeKey);
    },
    /**
     * 由外部事件（如练习失败）请求弹出某探针。仅置标记——实际是否弹由 ProbeCard
     * 结合 shouldShow 决定（已答/本会话已弹则不弹）；幂等，重复请求无副作用。
     */
    request(probeKey: string): void {
      this.requested.add(probeKey);
    },
    /** 标记探针已回答（含跳过）并持久化，之后 shouldShow 永远返回 false。 */
    markAnswered(probeKey: string): void {
      this.answered.add(probeKey);
      persistAnswered(this.answered);
    },
    /**
     * 提交一次探针选择。成功后标记已答；失败（网络/未登录/400）也只在本地标记已答
     * 并吞掉异常——探针是锦上添花的画像信号，绝不能阻塞玩家主线。
     */
    async submit(probeKey: string, choiceId: string): Promise<void> {
      try {
        await submitProbe(probeKey, choiceId);
      } catch {
        // 静默降级：探针失败不打断玩家，本地照常记为已答（见 finally）。
      } finally {
        this.markAnswered(probeKey);
      }
    },
  },
});
