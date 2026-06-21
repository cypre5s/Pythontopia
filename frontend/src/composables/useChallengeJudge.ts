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

export interface ChallengeProblemArgs {
  kc: string;
  kind: string | null;
  difficulty: number;
}

export interface UseChallengeJudgeReturn {
  fetchProblem: (args: ChallengeProblemArgs) => Promise<BossProblemDto>;
  /** 后台预取一道题并缓存（按 kc|kind|difficulty）；fetchProblem 命中即秒开。fire-and-forget。 */
  prefetchProblem: (args: ChallengeProblemArgs) => void;
  /** 清空预取缓存（新开一局时调用，避免跨局残留）。 */
  clearPrefetch: () => void;
  judge: (problem: BossProblemDto, code: string, elapsedSec: number) => Promise<BossAttemptResultDto>;
}

export function useChallengeJudge(): UseChallengeJudgeReturn {
  const { signals, hasAnySignal } = useProfileSignals();

  // 一次性预取缓存：key = kc|kind|difficulty → 进行中/已完成的出题 Promise。
  // fetchProblem 命中即消费（删除）→ 同一道不会被重复用（重试自然现取换新题）。
  const cache = new Map<string, Promise<BossProblemDto>>();
  function keyOf(args: ChallengeProblemArgs): string {
    return `${args.kc}|${args.kind ?? ''}|${args.difficulty}`;
  }

  function request(args: ChallengeProblemArgs): Promise<BossProblemDto> {
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

  async function fetchProblem(args: ChallengeProblemArgs): Promise<BossProblemDto> {
    const key = keyOf(args);
    const cached = cache.get(key);
    if (cached) {
      cache.delete(key);
      try {
        return await cached;
      } catch {
        // 预取失败：落回现取，绝不把预取错误抛给调用方。
      }
    }
    return request(args);
  }

  function prefetchProblem(args: ChallengeProblemArgs): void {
    const key = keyOf(args);
    if (cache.has(key)) return;
    const p = request(args);
    cache.set(key, p);
    // 防未处理 rejection；预取失败即移除，留待 fetchProblem 现取。
    void p.catch(() => {
      cache.delete(key);
    });
  }

  function clearPrefetch(): void {
    cache.clear();
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

  return { fetchProblem, prefetchProblem, clearPrefetch, judge };
}
