import { defineStore } from 'pinia';
import {
  huntFeynmanBug,
  judgeFeynmanSession,
  startFeynmanSession,
  teachFeynmanSession,
  type FeynmanBugHuntDto,
  type FeynmanJudgeResultDto,
  type FeynmanSessionDto,
  type FeynmanTurnDto,
} from '@/api/feynman';

interface FeynmanState {
  currentSession: FeynmanSessionDto | null;
  turns: FeynmanTurnDto[];
  loading: boolean;
  teaching: boolean;
  judging: boolean;
  hunting: boolean;
  error: string | null;
  latestProducedCode: string;
  latestJudgeResult: FeynmanJudgeResultDto | null;
  bugHunt: FeynmanBugHuntDto | null;
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
    judging: false,
    hunting: false,
    error: null,
    latestProducedCode: '',
    latestJudgeResult: null,
    bugHunt: null,
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
        this.bugHunt = null;
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
        // 后端 TurnResponse 用 taughtFix 承载学弟产出的代码；前端 DTO 另有可选 producedCode。
        const produced = turn.producedCode ?? turn.taughtFix;
        if (produced !== undefined && produced !== null) {
          this.latestProducedCode = produced;
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
    async startBugHunt(): Promise<FeynmanBugHuntDto | null> {
      const sessionId = this.currentSession?.id;
      if (sessionId === undefined) {
        throw new Error('请先开始费曼反演会话');
      }

      this.hunting = true;
      this.error = null;
      try {
        const hunt = await huntFeynmanBug(sessionId);
        this.bugHunt = hunt;
        return hunt;
      } catch (error) {
        this.error = errorMessage(error);
        return null;
      } finally {
        this.hunting = false;
      }
    },
    async judge(): Promise<FeynmanJudgeResultDto> {
      const sessionId = this.currentSession?.id;
      if (sessionId === undefined) {
        throw new Error('请先开始费曼反演会话');
      }

      this.judging = true;
      this.error = null;
      try {
        const verdict = await judgeFeynmanSession(sessionId);
        const result: FeynmanJudgeResultDto = {
          // 后端 status 是权威：ERROR（判题系统故障）与 NA（概念模式不判题）都不是「学生失败」，
          // 只在旧后端不返回 status 时才回落到 passed 的布尔投影。
          status: verdict.status ?? (verdict.passed ? 'PASSED' : 'FAILED'),
          messageZh: verdict.message,
          // 判题证据回链到学弟当前误解的真实 event_id（零合成）。
          evidenceEventIds: this.misconceptions.flatMap((item) => item.evidenceEventIds),
        };
        this.latestJudgeResult = result;
        return result;
      } catch (error) {
        this.error = errorMessage(error);
        throw error;
      } finally {
        this.judging = false;
      }
    },
  },
});
