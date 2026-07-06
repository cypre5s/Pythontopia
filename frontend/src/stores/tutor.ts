import { defineStore } from 'pinia';
import { listConversation, listTutorSessions, type TutorSessionDto } from '@/api/tutor';

/** 生成会话窗口 id；优先 crypto.randomUUID，环境缺失时退回时间戳+随机串。 */
function newConversationId(): string {
  const c = (globalThis as { crypto?: { randomUUID?: () => string } }).crypto;
  if (c?.randomUUID) {
    return c.randomUUID();
  }
  return `conv-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export const useTutorStore = defineStore('tutor', {
  state: () => ({
    sessions: [] as TutorSessionDto[],
    currentSession: null as TutorSessionDto | null,
    // 当前聊天窗口的会话 id（一次打开 / 一次「新对话」共享）；多轮记忆按它分组并回拉历史。
    conversationId: null as string | null,
    // 当前会话窗口内「当前轮之前」已完成的问答（按时间升序），用于 thread 展示。
    turns: [] as TutorSessionDto[],
    streamingAnswer: '',
    status: 'idle' as 'idle' | 'streaming' | 'done' | 'error',
    lastError: null as string | null,
  }),
  actions: {
    async loadSessions(): Promise<void> {
      try {
        this.sessions = await listTutorSessions();
      } catch (error) {
        this.lastError = error instanceof Error ? error.message : String(error);
      }
    },
    /** 确保当前窗口有 conversationId（无则新建），返回它。 */
    ensureConversationId(): string {
      if (!this.conversationId) {
        this.conversationId = newConversationId();
      }
      return this.conversationId;
    },
    /** 选中侧栏历史会话：按 conversationId 回拉整段多轮 thread（旧数据无 id 时退回单条）。 */
    async loadConversation(session: TutorSessionDto): Promise<void> {
      this.streamingAnswer = '';
      this.lastError = null;
      if (session.conversationId) {
        this.conversationId = session.conversationId;
        try {
          const all = await listConversation(session.conversationId);
          if (all.length > 0) {
            const last = all[all.length - 1] ?? session;
            this.turns = all.slice(0, -1);
            this.currentSession = last;
            this.streamingAnswer = last.answerMd ?? '';
            this.status = 'done';
            return;
          }
        } catch (error) {
          this.lastError = error instanceof Error ? error.message : String(error);
        }
      }
      // 旧数据 / 拉取失败：退回只展示这一条。
      this.conversationId = session.conversationId ?? newConversationId();
      this.turns = [];
      this.currentSession = session;
      this.streamingAnswer = session.answerMd ?? '';
      this.status = session.answerMd ? 'done' : 'idle';
    },
    startNewSession(): void {
      this.currentSession = null;
      this.streamingAnswer = '';
      this.status = 'idle';
      this.lastError = null;
      this.conversationId = newConversationId();
      this.turns = [];
    },
    setStreamingAnswer(text: string): void {
      this.streamingAnswer = text;
    },
    setStatus(status: 'idle' | 'streaming' | 'done' | 'error'): void {
      this.status = status;
    },
    onStreamComplete(session: TutorSessionDto): void {
      // 上一轮（若有且非同一条）转入历史 thread，再把本轮设为当前。
      const prev = this.currentSession;
      if (prev && prev.id !== session.id && !this.turns.some((t) => t.id === prev.id)) {
        this.turns = [...this.turns, prev];
      }
      this.currentSession = session;
      this.status = 'done';
      if (!this.sessions.some((s) => s.id === session.id)) {
        this.sessions = [session, ...this.sessions];
      }
    },
  },
});
