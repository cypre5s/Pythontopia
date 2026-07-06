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
  /** 本轮编排 trace（喂事务所动画）。 */
  traces?: OrchestratorTraceStep[];
  done?: boolean;
  streaming?: boolean;
  failed?: boolean;
}

export type GuideStatus = 'idle' | 'streaming' | 'error';

/** 最近一轮用户输入的标记（重试只重发这一轮，不重复历史）。 */
export interface GuideLastTurn {
  userMessageId: string;
  text: string;
}

/** beginRetry 返回给调用方的「重发载荷」：新占位 id + 原文 + 截到该轮之前的历史。 */
export interface GuideRetryTurn {
  assistantId: string;
  text: string;
  history: OrchestratorTurnMessage[];
}

function genId(): string {
  try {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
  } catch {
    /* 忽略 */
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
  /** 最近一轮用户输入；该轮失败时支撑「只重发本轮」的重试。 */
  lastTurn: GuideLastTurn | null;
}

/** 过滤出可发给后端的多轮历史（剔除欢迎语与正在流式/失败的占位）。 */
function toRequestHistory(messages: GuideMessage[]): OrchestratorTurnMessage[] {
  return messages
    .filter((m) => !m.streaming && !m.failed && m.id !== 'welcome' && m.content.trim().length > 0)
    .map((m) => ({ role: m.role, content: m.content }));
}

const WELCOME: GuideMessage = {
  id: 'welcome',
  role: 'assistant',
  content:
    '我是你的 **AI 助教**。要不要我带你做第 1 章第一步？'
    + '也可以直接说目标，比如「我想学循环」「这题报错怎么办」。',
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
    lastTurn: null,
  }),
  getters: {
    isStreaming: (state): boolean => state.status === 'streaming',
    /** 仅最近一轮失败时可重试（idle/streaming 下重试无意义，且保证单飞行）。 */
    canRetry: (state): boolean => state.status === 'error' && state.lastTurn !== null,
    /** 转成发给后端的多轮历史（剔除欢迎语与正在流式/失败的占位）。 */
    historyForRequest(): OrchestratorTurnMessage[] {
      return toRequestHistory(this.messages);
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
      this.lastTurn = { userMessageId: id, text: content };
      return id;
    },
    /** 开启一轮助手流式占位。同一时刻只允许一个进行中请求：streaming 中拒绝并返回 null。 */
    beginAssistant(): string | null {
      if (this.status === 'streaming') return null;
      const id = genId();
      this.messages = [
        ...this.messages,
        { id, role: 'assistant', content: '', streaming: true },
      ];
      this.status = 'streaming';
      this.lastError = null;
      return id;
    },
    /**
     * 重试最近一轮失败的请求：只重发该轮用户输入，历史截到该轮之前（避免 message 与
     * history 重复）。失败占位被替换为新的流式占位，不在线程里堆积。
     * 仅 canRetry（最近一轮失败）时可用；streaming/idle 下返回 null。
     */
    beginRetry(): GuideRetryTurn | null {
      if (!this.canRetry || this.lastTurn === null) return null;
      const { userMessageId, text } = this.lastTurn;
      const turnIndex = this.messages.findIndex((m) => m.id === userMessageId);
      if (turnIndex < 0) return null;
      const history = toRequestHistory(this.messages.slice(0, turnIndex));
      // 移除本轮（含此前各次重试）的失败占位，再挂新的流式占位。
      this.messages = this.messages.filter(
        (m, idx) => !(idx > turnIndex && m.role === 'assistant' && m.failed),
      );
      const assistantId = genId();
      this.messages = [
        ...this.messages,
        { id: assistantId, role: 'assistant', content: '', streaming: true },
      ];
      this.status = 'streaming';
      this.lastError = null;
      return { assistantId, text, history };
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
          ? { ...m, streaming: false, failed: true, content: m.content || '抱歉，AI 助教暂时没法回应，过一会再试。' }
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
      this.lastTurn = null;
    },
  },
});
