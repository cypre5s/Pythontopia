import { defineStore } from 'pinia';
import { listTutorSessions, type TutorSessionDto } from '@/api/tutor';

export const useTutorStore = defineStore('tutor', {
  state: () => ({
    sessions: [] as TutorSessionDto[],
    currentSession: null as TutorSessionDto | null,
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
    selectSession(session: TutorSessionDto): void {
      this.currentSession = session;
      this.streamingAnswer = session.answerMd ?? '';
      this.status = session.answerMd ? 'done' : 'idle';
    },
    startNewSession(): void {
      this.currentSession = null;
      this.streamingAnswer = '';
      this.status = 'idle';
      this.lastError = null;
    },
    setStreamingAnswer(text: string): void {
      this.streamingAnswer = text;
    },
    setStatus(status: 'idle' | 'streaming' | 'done' | 'error'): void {
      this.status = status;
    },
    onStreamComplete(session: TutorSessionDto): void {
      this.currentSession = session;
      this.status = 'done';
      if (!this.sessions.some((s) => s.id === session.id)) {
        this.sessions = [session, ...this.sessions];
      }
    },
  },
});
