// 单题闭环：按 (KC, 题型, 难度) 出题（5 重审查 + golden 兜底）→ 真判题入库。
// 无尽远征各战斗节点与旧无尽模式共用，避免逻辑各写一份。

import { ref, type Ref } from 'vue';
import {
  fetchBossPrefetchStatus,
  newBossProblemRequestId,
  prefetchBossProblem,
  requestBossProblem,
  submitBossAttempt,
} from '@/api/boss';
import { useProfileSignals } from '@/composables/useProfileSignals';
import { kcChapter } from '@/data/rogueConfig';
import type { BossAttemptRequest, BossAttemptResultDto, BossProblemDto } from '@/api/dto/Boss';

/**
 * 「本次没有真正判定」的后端 feedback 前缀集合。
 *
 * Older backend versions returned missing problems, empty test cases, or evaluator outages as
 * **200 + passed:false + score:0**, without emitting a LearningEvent. Current evaluator outages
 * return 503; keep the legacy hints so a staggered Web/API rollout still cannot penalize students.
 *
 * The stable prefixes below only bridge older API responses until all Web instances are updated.
 */
const JUDGE_UNAVAILABLE_HINTS: readonly string[] = [
  '题目缓存过期且无持久化记录',
  '题目缓存数据异常',
  'graph 评测服务不可用',
  // 历史前端 mock 兜底文案（已废弃，保留以兼容旧缓存/旧后端）
  '后端未连接',
];

/** 后端返回「未判定」结果时：不应据此扣血/计分/入库，交由调用方提示重试。 */
export function isJudgeUnavailable(result: BossAttemptResultDto): boolean {
  if (result.passed || result.score !== 0) {
    return false;
  }
  const feedback = result.feedback ?? '';
  return JUDGE_UNAVAILABLE_HINTS.some((hint) => feedback.includes(hint));
}

export interface ChallengeProblemArgs {
  kc: string;
  kind: string | null;
  difficulty: number;
  expeditionRunId?: string | null;
  expeditionNodeId?: string | null;
}

export type ChallengePreloadPhase = 'idle' | 'pending' | 'ready' | 'failed';

export interface ChallengePreloadState {
  phase: ChallengePreloadPhase;
  attempts: number;
  errorMessage: string | null;
  updatedAt: string | null;
}

const IDLE_PRELOAD: ChallengePreloadState = {
  phase: 'idle', attempts: 0, errorMessage: null, updatedAt: null,
};
const PREFETCH_POLL_INTERVAL_MS = 5_000;
const PREFETCH_MAX_POLLS = 36; // Boss Graph generation can take up to 180 seconds.

export interface UseChallengeJudgeReturn {
  fetchProblem: (args: ChallengeProblemArgs) => Promise<BossProblemDto>;
  /** 开始或复用服务端 PENDING 任务；只有服务端确认后才会进入 ready。 */
  prefetchProblem: (args: ChallengeProblemArgs) => Promise<ChallengePreloadState>;
  /** FAILED 任务按相同完整维度重试。 */
  retryPrefetch: (args: ChallengeProblemArgs) => Promise<ChallengePreloadState>;
  /** 查询服务端持久化状态。 */
  refreshPrefetch: (args: ChallengeProblemArgs) => Promise<ChallengePreloadState>;
  getPrefetchState: (args: ChallengeProblemArgs) => ChallengePreloadState;
  preloadStates: Ref<Record<string, ChallengePreloadState>>;
  /** 清空本局的状态视图（服务端 READY 题保留，仍可跨会话复用）。 */
  clearPrefetch: () => void;
  judge: (
    problem: BossProblemDto,
    code: string,
    elapsedSec: number,
    answers?: string[],
  ) => Promise<BossAttemptResultDto>;
}

export function useChallengeJudge(): UseChallengeJudgeReturn {
  const { signals, hasAnySignal } = useProfileSignals();

  // 客户端只缓存「状态」，题目本体与 hidden tests 始终留在后端 pregenerated_problem。
  const preloadStates = ref<Record<string, ChallengePreloadState>>({});
  const problemRequestIds = new Map<string, string>();
  // 浏览器超时无法判断服务端是否完成生成，重试时必须保留原请求 ID。
  const uncertainProblemRequests = new Set<string>();
  function keyOf(args: ChallengeProblemArgs): string {
    return `${args.kc}|${args.kind ?? ''}|${args.difficulty}|${args.expeditionNodeId ?? ''}`;
  }

  function problemRequestKey(args: ChallengeProblemArgs): string {
    return JSON.stringify([
      args.expeditionRunId ?? null,
      args.expeditionNodeId ?? null,
      args.kc,
      args.kind,
    ]);
  }

  function requestPayload(args: ChallengeProblemArgs) {
    const payload = {
      stage: Math.max(1, Math.min(4, Math.round(args.difficulty))),
      preferredKc: args.kc,
      ...(args.kind ? { preferredKind: args.kind } : {}),
      difficulty: args.difficulty,
      ...(args.expeditionRunId ? { expeditionRunId: args.expeditionRunId } : {}),
      ...(args.expeditionNodeId ? { expeditionNodeId: args.expeditionNodeId } : {}),
      ...(hasAnySignal.value ? { profileSignals: signals.value } : {}),
    };
    const key = problemRequestKey(args);
    let requestId = problemRequestIds.get(key);
    if (!requestId) {
      requestId = newBossProblemRequestId();
      problemRequestIds.set(key, requestId);
    }
    return { ...payload, requestId };
  }

  async function request(args: ChallengeProblemArgs): Promise<BossProblemDto> {
    const chapter = kcChapter(args.kc);
    const key = problemRequestKey(args);
    try {
      const problem = await requestBossProblem(
        chapter,
        requestPayload(args),
        { allowGoldenFallback: false },
      );
      problemRequestIds.delete(key);
      uncertainProblemRequests.delete(key);
      return problem;
    } catch (error) {
      uncertainProblemRequests.add(key);
      throw error;
    }
  }

  async function fetchProblem(args: ChallengeProblemArgs): Promise<BossProblemDto> {
    let preload = getPrefetchState(args);
    if (preload.phase !== 'idle') {
      // 点击节点时最后确认一次；PENDING/FAILED 不会被客户端乐观改成 READY。
      preload = await refreshPrefetch(args).catch(() => getPrefetchState(args));
    }
    for (let attempt = 0; preload.phase === 'pending' && attempt < PREFETCH_MAX_POLLS; attempt += 1) {
      await new Promise<void>((resolve) => setTimeout(resolve, PREFETCH_POLL_INTERVAL_MS));
      preload = await refreshPrefetch(args);
    }
    if (preload.phase === 'pending') throw new Error('题目仍在生成，请稍后重试');
    return request(args);
  }

  function applyServerState(
    args: ChallengeProblemArgs,
    status: Awaited<ReturnType<typeof prefetchBossProblem>>,
  ): ChallengePreloadState {
    const key = keyOf(args);
    const phase: ChallengePreloadPhase =
      status.status === 'READY'
        ? 'ready'
        : status.status === 'FAILED'
          ? 'failed'
          : status.status === 'PENDING'
            ? 'pending'
            : 'idle';
    const next: ChallengePreloadState = {
      phase,
      attempts: status.attempts,
      errorMessage: status.errorMessage,
      updatedAt: status.updatedAt,
    };
    preloadStates.value = { ...preloadStates.value, [key]: next };
    return next;
  }

  function applyClientFailure(args: ChallengeProblemArgs, error: unknown): ChallengePreloadState {
    const key = keyOf(args);
    const previous = preloadStates.value[key] ?? IDLE_PRELOAD;
    const next: ChallengePreloadState = {
      phase: 'failed',
      attempts: Math.max(1, previous.attempts),
      errorMessage: error instanceof Error ? error.message : '预加载失败，请重试',
      updatedAt: new Date().toISOString(),
    };
    preloadStates.value = { ...preloadStates.value, [key]: next };
    return next;
  }

  async function prefetchProblem(args: ChallengeProblemArgs): Promise<ChallengePreloadState> {
    const key = keyOf(args);
    const previous = preloadStates.value[key];
    if (previous?.phase === 'pending' || previous?.phase === 'ready') return previous;
    preloadStates.value = {
      ...preloadStates.value,
      [key]: { phase: 'pending', attempts: previous?.attempts ?? 0, errorMessage: null, updatedAt: null },
    };
    try {
      const status = await prefetchBossProblem(kcChapter(args.kc), requestPayload(args));
      return applyServerState(args, status);
    } catch (error) {
      return applyClientFailure(args, error);
    }
  }

  async function retryPrefetch(args: ChallengeProblemArgs): Promise<ChallengePreloadState> {
    // 现场请求结果不确定时，不能另起后台生成，后续现场请求会沿用原 ID。
    if (uncertainProblemRequests.has(problemRequestKey(args))) {
      return getPrefetchState(args);
    }
    preloadStates.value = { ...preloadStates.value, [keyOf(args)]: IDLE_PRELOAD };
    return prefetchProblem(args);
  }

  async function refreshPrefetch(args: ChallengeProblemArgs): Promise<ChallengePreloadState> {
    try {
      const status = await fetchBossPrefetchStatus(kcChapter(args.kc), requestPayload(args));
      return applyServerState(args, status);
    } catch (error) {
      return applyClientFailure(args, error);
    }
  }

  function getPrefetchState(args: ChallengeProblemArgs): ChallengePreloadState {
    return preloadStates.value[keyOf(args)] ?? IDLE_PRELOAD;
  }

  function clearPrefetch(): void {
    preloadStates.value = {};
    problemRequestIds.clear();
    uncertainProblemRequests.clear();
  }

  async function judge(
    problem: BossProblemDto,
    code: string,
    elapsedSec: number,
    answers?: string[],
  ): Promise<BossAttemptResultDto> {
    const submission: BossAttemptRequest = {
      problemId: problem.problemId,
      chapter: problem.chapter,
      stage: problem.stage,
      elapsedSec,
      ...(answers === undefined ? { code } : { answers }),
      ...(hasAnySignal.value ? { profileSignals: signals.value } : {}),
    };
    return submitBossAttempt(submission);
  }

  return {
    fetchProblem,
    prefetchProblem,
    retryPrefetch,
    refreshPrefetch,
    getPrefetchState,
    preloadStates,
    clearPrefetch,
    judge,
  };
}
