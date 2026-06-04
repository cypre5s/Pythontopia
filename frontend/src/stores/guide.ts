// 伴学向导（学习总管前端态）。
//
// 与静态 NPC 对话树（stores/dialog.ts，脚本剧情）明确区分：
//   - 向导 = LLM 多轮对话，会话级记忆 + 澄清追问 + 计划协商 + 对话抽画像；
//   - NPC 树 = 固定分支脚本。
//
// 本 store 只持有「多轮对话线程 + 会话 id + 开关」；真正的流式 IO 在 GuideOverlay.vue
// 里用 useSseStream 驱动，逐轮把结果写回这里，保证关闭重开仍能看到历史。

import { defineStore } from 'pinia';
import type {
  OrchestratorDispatchResult,
  OrchestratorPlanStep,
  OrchestratorProfileSignal,
  OrchestratorResult,
  OrchestratorTraceStep,
  OrchestratorTurnMessage,
} from '@/api/orchestrator';

const SESSION_KEY = 'pyt_guide_session_v1';

export interface GuideMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  /** 助手轮：规范化的看/学/练/测计划。 */
  plan?: OrchestratorPlanStep[];
  dispatchResults?: OrchestratorDispatchResult[];
  clarifyingQuestion?: string | null;
  needsUserInput?: boolean;
  profileUpdates?: OrchestratorProfileSignal[];
  /** 本轮编排 trace（喂 P3 事务所动画）。 */
  traces?: OrchestratorTraceStep[];
  done?: boolean;
  /** 正在流式生成中。 */
  streaming?: boolean;
  /** 本轮失败。 */
  failed?: boolean;
}

export type GuideStatus = 'idle' | 'streaming' | 'error';

function genId(): string {
  try {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
  } catch {
    /* ignore */
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function readSessionId(): string {
  if (typeof window === 'undefined') return genId();
  try {
    const existing = window.localStorage.getItem(SESSION_KEY);
    if (existing) return existing;
    const fresh = genId();
    window.localStorage.setItem(SESSION_KEY, fresh);
    return fresh;
  } catch {
    return genId();
  }
}

export interface GuideStoreState {
  open: boolean;
  sessionId: string;
  messages: GuideMessage[];
  status: GuideStatus;
  lastError: string | null;
  /** 最近做题失败的 KC（触发总管动态重规划 → DebugAgent）。 */
  recentFailures: string[];
  /** 是否已展示过欢迎语（避免重复 seed）。 */
  greeted: boolean;
}

const WELCOME: GuideMessage = {
  id: 'welcome',
  role: 'assistant',
  content:
    '我是你的**学习总管**。说一句你的目标（比如「我想学循环」「第 3 章太难了」「这题报错怎么办」），'
    + '我会帮你拆成「看 → 学 → 练 → 测」的步骤，并直接带你到对应面板。',
  done: true,
};

export const useGuideStore = defineStore('guide', {
  state: (): GuideStoreState => ({
    open: false,
    sessionId: readSessionId(),
    messages: [],
    status: 'idle',
    lastError: null,
    recentFailures: [],
    greeted: false,
  }),
  getters: {
    isStreaming: (state): boolean => state.status === 'streaming',
    /** 转成发给后端的多轮历史（剔除欢迎语与正在流式/失败的占位）。 */
    historyForRequest(): OrchestratorTurnMessage[] {
      return this.messages
        .filter((m) => !m.streaming && !m.failed && m.id !== 'welcome' && m.content.trim().length > 0)
        .map((m) => ({ role: m.role, content: m.content }));
    },
    /** 最近一条助手消息（用于渲染计划 / 追问建议）。 */
    lastAssistant(): GuideMessage | null {
      for (let i = this.messages.length - 1; i >= 0; i -= 1) {
        const m = this.messages[i];
        if (m && m.role === 'assistant') return m;
      }
      return null;
    },
  },
  actions: {
    ensureGreeting(): void {
      if (!this.greeted && this.messages.length === 0) {
        this.messages = [{ ...WELCOME }];
        this.greeted = true;
      }
    },
    openPanel(): void {
      this.open = true;
      this.ensureGreeting();
    },
    closePanel(): void {
      this.open = false;
    },
    toggle(): void {
      if (this.open) this.closePanel();
      else this.openPanel();
    },
    pushUserMessage(content: string): string {
      const id = genId();
      this.messages = [...this.messages, { id, role: 'user', content }];
      return id;
    },
    beginAssistant(): string {
      const id = genId();
      this.messages = [
        ...this.messages,
        { id, role: 'assistant', content: '', streaming: true },
      ];
      this.status = 'streaming';
      this.lastError = null;
      return id;
    },
    updateAssistantStream(id: string, content: string, traces: OrchestratorTraceStep[]): void {
      this.messages = this.messages.map((m) =>
        m.id === id ? { ...m, content, traces } : m,
      );
    },
    finalizeAssistant(id: string, result: OrchestratorResult, fallbackContent: string): void {
      const content = result.reply_md?.trim().length ? result.reply_md : fallbackContent;
      this.messages = this.messages.map((m) =>
        m.id === id
          ? {
              ...m,
              content,
              streaming: false,
              plan: result.plan ?? [],
              dispatchResults: result.dispatch_results ?? [],
              clarifyingQuestion: result.clarifying_question ?? null,
              needsUserInput: result.needs_user_input ?? false,
              profileUpdates: result.profile_updates ?? [],
              traces: result.trace ?? m.traces ?? [],
              done: result.done ?? false,
            }
          : m,
      );
      this.status = 'idle';
    },
    failAssistant(id: string, message: string): void {
      this.messages = this.messages.map((m) =>
        m.id === id
          ? { ...m, streaming: false, failed: true, content: m.content || '抱歉，学习总管暂时无法回应，请稍后重试。' }
          : m,
      );
      this.status = 'error';
      this.lastError = message;
    },
    setRecentFailures(kcCodes: string[]): void {
      this.recentFailures = [...new Set(kcCodes)].slice(0, 8);
    },
    reset(): void {
      this.messages = [];
      this.status = 'idle';
      this.lastError = null;
      this.greeted = false;
    },
  },
});
