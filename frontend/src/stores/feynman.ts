import { defineStore } from 'pinia';
import {
  startFeynmanSession,
  teachFeynmanSession,
  type FeynmanJudgeResultDto,
  type FeynmanSessionDto,
  type FeynmanTurnDto,
} from '@/api/feynman';

interface FeynmanState {
  currentSession: FeynmanSessionDto | null;
  turns: FeynmanTurnDto[];
  loading: boolean;
  teaching: boolean;
  error: string | null;
  latestProducedCode: string;
  latestJudgeResult: FeynmanJudgeResultDto | null;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export const useFeynmanStore = defineStore('feynman', {
  state: (): FeynmanState => ({
    currentSession: null,
    turns: [],
    loading: false,
    teaching: false,
    error: null,
    latestProducedCode: '',
    latestJudgeResult: null,
  }),
  getters: {
    misconceptions(state) {
      return state.currentSession?.tuteeState.misconceptions ?? [];
    },
  },
  actions: {
    async startSession(kcCode: string): Promise<FeynmanSessionDto> {
      this.loading = true;
      this.error = null;
      try {
        const session = await startFeynmanSession(kcCode);
        this.currentSession = session;
        this.turns = [];
        this.latestProducedCode = session.tuteeState.producedCode ?? '';
        this.latestJudgeResult = session.tuteeState.judgeResult ?? null;
        return session;
      } catch (error) {
        this.error = errorMessage(error);
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async teach(explanation: string): Promise<FeynmanTurnDto> {
      const sessionId = this.currentSession?.id;
      if (sessionId === undefined) {
        throw new Error('请先开始费曼反演会话');
      }

      this.teaching = true;
      this.error = null;
      try {
        const turn = await teachFeynmanSession(sessionId, explanation);
        this.turns = [...this.turns, turn];
        if (turn.producedCode !== undefined && turn.producedCode !== null) {
          this.latestProducedCode = turn.producedCode;
        }
        if (turn.judgeResult !== undefined) {
          this.latestJudgeResult = turn.judgeResult;
        }
        return turn;
      } catch (error) {
        this.error = errorMessage(error);
        throw error;
      } finally {
        this.teaching = false;
      }
    },
  },
});
