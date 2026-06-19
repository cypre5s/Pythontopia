// 单题闭环：按 (KC, 题型, 难度) 出题（5 重审查 + golden 兜底）→ 真判题入库。
// 无尽远征各战斗节点与旧无尽模式共用，避免逻辑各写一份。

import { requestBossProblem, submitBossAttempt } from '@/api/boss';
import { useProfileSignals } from '@/composables/useProfileSignals';
import { kcChapter } from '@/data/rogueConfig';
import type { BossAttemptResultDto, BossProblemDto } from '@/api/dto/Boss';

const UNAVAILABLE_HINT = '后端未连接';

/** 后端判题不可达时（boss.ts 返回 mock 兜底）：不应据此扣血/计分，交由调用方提示重试。 */
export function isJudgeUnavailable(result: BossAttemptResultDto): boolean {
  return !result.passed && result.score === 0 && result.feedback.includes(UNAVAILABLE_HINT);
}

export interface UseChallengeJudgeReturn {
  fetchProblem: (args: { kc: string; kind: string | null; difficulty: number }) => Promise<BossProblemDto>;
  judge: (problem: BossProblemDto, code: string, elapsedSec: number) => Promise<BossAttemptResultDto>;
}

export function useChallengeJudge(): UseChallengeJudgeReturn {
  const { signals, hasAnySignal } = useProfileSignals();

  async function fetchProblem(args: {
    kc: string;
    kind: string | null;
    difficulty: number;
  }): Promise<BossProblemDto> {
    const chapter = kcChapter(args.kc);
    const stage = Math.max(1, Math.min(4, Math.round(args.difficulty)));
    return requestBossProblem(chapter, {
      stage,
      preferredKc: args.kc,
      ...(args.kind ? { preferredKind: args.kind } : {}),
      difficulty: args.difficulty,
      ...(hasAnySignal.value ? { profileSignals: signals.value } : {}),
    });
  }

  async function judge(
    problem: BossProblemDto,
    code: string,
    elapsedSec: number,
  ): Promise<BossAttemptResultDto> {
    return submitBossAttempt({
      problemId: problem.problemId,
      chapter: problem.chapter,
      stage: problem.stage,
      code,
      elapsedSec,
      ...(hasAnySignal.value ? { profileSignals: signals.value } : {}),
    });
  }

  return { fetchProblem, judge };
}
