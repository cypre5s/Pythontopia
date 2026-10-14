// 无尽模式状态机。
//
// 玩家进入 /endless 后系统连续出题（来自 graph /agents/boss/problem）。
// 自适应规则（简化版）：
//   * 连胜 1 → 难度 +0.5；连胜 2 → 难度 +1；
//   * 失败 1 → 难度 -1，下一题 KC 不变；
//   * 难度 clamp 到 1..5；
//   * 章节按用户选择（focusChapter）或随机抽 1..7；
//   * 总分 = 通过题数 × 难度 × 10；
//   * 失败结束当前局，玩家可继续无尽，但 streak 清零。

import { computed, ref, type Ref } from 'vue';
import {
  fetchBossPrefetchStatus,
  newBossProblemRequestId,
  prefetchBossProblem,
  requestBossProblem,
  submitBossAttempt,
} from '@/api/boss';
import { getPyodideRunner } from '@/game/adapters/pyodide';
import { useProfileSignals, computeAdaptiveDifficulty } from '@/composables/useProfileSignals';
import { userScopedKey } from '@stores/session';
import type {
  BossAttemptResultDto,
  BossProblemDto,
  BossProblemRequest,
} from '@/api/dto/Boss';

export interface EndlessHistoryEntry {
  problemId: string;
  chapter: number;
  kcCode: string;
  difficulty: number;
  passed: boolean;
  elapsedSec: number;
  score: number;
  timestamp: string;
}

export type EndlessPhase =
  | 'idle'
  | 'loading'
  | 'playing'
  | 'evaluating'
  | 'passed'
  | 'failed'
  | 'error';

export interface UseEndlessRunOptions {
  /** 锁定章节 1..7；空 = 随机抽章 */
  focusChapter?: Ref<number | null>;
  /** 锁定 KC（来自 ArenaPage 关卡）；空 = 按 chapter 随机 / 画像 weakKcs 优先 */
  focusKc?: Ref<string | null>;
  /** 偏好题型（来自 ArenaPage 关卡 kind） */
  preferredKind?: Ref<string | null>;
  /** 闯关塔单关 id（形如 "1-3"）：作为预生成 / 复用题的维度之一 */
  arenaLevel?: Ref<string | null>;
}

export interface UseEndlessRunReturn {
  phase: Ref<EndlessPhase>;
  problem: Ref<BossProblemDto | null>;
  code: Ref<string>;
  lastResult: Ref<BossAttemptResultDto | null>;
  streak: Ref<number>;
  bestStreak: Ref<number>;
  totalScore: Ref<number>;
  attempts: Ref<number>;
  currentDifficulty: Ref<number>;
  history: Ref<EndlessHistoryEntry[]>;
  elapsedSec: Ref<number>;
  errorMessage: Ref<string>;
  retryAction: Ref<'start' | 'next' | 'submit' | null>;
  failureRate: Ref<number>;
  startRun: () => Promise<void>;
  submit: () => Promise<void>;
  nextProblem: () => Promise<void>;
  retry: () => Promise<void>;
  abort: () => void;
  tickSecond: () => void;
}

interface PendingProblemRequest {
  chapter: number;
  request: BossProblemRequest;
  prefetchPromise?: ReturnType<typeof prefetchBossProblem>;
}

const PERSIST_KEY = 'pyt_endless_history_v1';
const MAX_HISTORY = 50;
const PREFETCH_POLL_INTERVAL_MS = 5_000;
const PREFETCH_MAX_POLLS = 36;

function loadHistory(): EndlessHistoryEntry[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(userScopedKey(PERSIST_KEY));
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(
      (e): e is EndlessHistoryEntry =>
        typeof e === 'object' && e !== null && typeof (e as EndlessHistoryEntry).problemId === 'string',
    );
  } catch {
    return [];
  }
}

function saveHistory(history: EndlessHistoryEntry[]): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(
      userScopedKey(PERSIST_KEY),
      JSON.stringify(history.slice(0, MAX_HISTORY)),
    );
  } catch {
    /* 隐私模式禁止写入，忽略 */
  }
}

function pickChapter(focus: number | null): number {
  if (focus && focus >= 1 && focus <= 7) return focus;
  return 1 + Math.floor(Math.random() * 7);
}

function clampDifficulty(d: number): number {
  return Math.max(1, Math.min(5, Math.round(d)));
}

/**
 * 从画像 weakKcs 中按 chapter 过滤出命中本章的 KC（PY43-KC-XXX）。
 * 章 1 = KC-001..007，章 2 = KC-008..016，…，章 7 = KC-054..065，与 mainQuests boss kcRange 一致。
 */
function pickWeakKcForChapter(
  weakKcs: readonly string[] | undefined,
  chapter: number,
): string | undefined {
  if (!weakKcs || weakKcs.length === 0) return undefined;
  // 按章节编号区间匹配本章 KC
  const ranges: readonly [number, number, number][] = [
    [1, 1, 7], [2, 8, 16], [3, 17, 30], [4, 31, 38], [5, 39, 47], [6, 48, 53], [7, 54, 65],
  ];
  const range = ranges.find(([c]) => c === chapter);
  if (!range) return undefined;
  const [, lo, hi] = range;
  return weakKcs.find((code) => {
    const m = /PY43-KC-(\d+)/.exec(code);
    if (!m) return false;
    const num = Number(m[1]);
    return num >= lo && num <= hi;
  });
}

export function useEndlessRun(opts: UseEndlessRunOptions = {}): UseEndlessRunReturn {
  const phase = ref<EndlessPhase>('idle');
  const problem = ref<BossProblemDto | null>(null);
  const code = ref<string>('');
  const lastResult = ref<BossAttemptResultDto | null>(null);
  const streak = ref<number>(0);
  const bestStreak = ref<number>(0);
  const totalScore = ref<number>(0);
  const attempts = ref<number>(0);
  const currentDifficulty = ref<number>(2);
  const elapsedSec = ref<number>(0);
  const errorMessage = ref<string>('');
  const retryAction = ref<'start' | 'next' | 'submit' | null>('start');
  const history = ref<EndlessHistoryEntry[]>(loadHistory());
  const profileSignals = useProfileSignals();
  let pendingNext: PendingProblemRequest | null = null;
  let pendingCurrent: PendingProblemRequest | null = null;

  const failureRate = computed<number>(() => {
    if (attempts.value === 0) return 0;
    // 用近 10 题统计失败率（仅作自适应难度的辅助展示）。
    const recent = history.value.slice(0, 10);
    if (recent.length === 0) return 0;
    const failedCount = recent.filter((h) => !h.passed).length;
    return failedCount / recent.length;
  });

  function buildProblemRequest(chapter: number): BossProblemRequest {
    const recent = history.value.slice(0, 3);
    const firstPass = recent.findIndex((entry) => entry.passed);
    const recentFailures = firstPass < 0 ? recent.length : firstPass;
    const adaptiveDiff = computeAdaptiveDifficulty({
      baseDifficulty: currentDifficulty.value,
      recentFailures,
      recentStreak: streak.value,
    });
    const preferredKc =
      opts.focusKc?.value
      ?? pickWeakKcForChapter(profileSignals.weakKcs.value, chapter);
    return {
      requestId: newBossProblemRequestId(),
      stage: 1 + Math.floor(Math.random() * 4),
      difficulty: clampDifficulty(adaptiveDiff),
      ...(preferredKc ? { preferredKc } : {}),
      ...(opts.preferredKind?.value ? { preferredKind: opts.preferredKind.value } : {}),
      ...(opts.arenaLevel?.value ? { arenaLevel: opts.arenaLevel.value } : {}),
      ...(profileSignals.hasAnySignal.value ? { profileSignals: profileSignals.signals.value } : {}),
    };
  }

  async function _fetchAndStart(pending?: PendingProblemRequest): Promise<void> {
    phase.value = 'loading';
    problem.value = null;
    lastResult.value = null;
    code.value = '';
    elapsedSec.value = 0;
    try {
      let target = pending ?? pendingCurrent;
      if (!target) {
        const chapter = pickChapter(opts.focusChapter?.value ?? null);
        target = { chapter, request: buildProblemRequest(chapter) };
      }
      const chapter = target.chapter;
      const request = target.request;
      pendingCurrent = { chapter, request };
      currentDifficulty.value = clampDifficulty(request.difficulty ?? currentDifficulty.value);
      if (target.prefetchPromise) await waitForPrefetch(target);
      // 远征只接受服务端审查通过的 AI 题；golden / 本地模板不能冒充画像自适应关卡。
      const fetched = await requestBossProblem(chapter, request, { allowGoldenFallback: false });
      problem.value = fetched;
      currentDifficulty.value = clampDifficulty(fetched.difficulty);
      pendingCurrent = null;
      if (pendingNext === target) pendingNext = null;
      phase.value = 'playing';
      retryAction.value = null;
    } catch (error) {
      phase.value = 'error';
      errorMessage.value = error instanceof Error ? error.message : String(error);
    }
  }

  async function startRun(): Promise<void> {
    retryAction.value = 'start';
    pendingNext = null;
    pendingCurrent = null;
    streak.value = 0;
    totalScore.value = 0;
    attempts.value = 0;
    currentDifficulty.value = 2;
    await _fetchAndStart();
  }

  async function _runPyodide(p: BossProblemDto, source: string) {
    try {
      const runner = getPyodideRunner();
      const result = await runner.run(source, p.sampleInput);
      const actual = (result.stdout ?? '').trimEnd();
      const expected = (p.sampleOutput ?? '').trimEnd();
      return { ok: actual === expected, actual, stderr: result.stderr ?? '' };
    } catch (error) {
      return { ok: false, actual: '', stderr: error instanceof Error ? error.message : String(error) };
    }
  }

  async function submit(): Promise<void> {
    const p = problem.value;
    if (!p) return;
    // 并发守卫（同步置位，不可省）：BugMineCard 的 30s 定时器直接 emit('timeout') → onSubmit，
    // 绕过按钮上的 :disabled。t≈29s 点提交 + 定时器触发 = 同一题两次正式判题、两条历史、双倍计分。
    if (phase.value === 'evaluating') return;
    phase.value = 'evaluating';
    const localEval = await _runPyodide(p, code.value);
    let backendResult: BossAttemptResultDto;
    try {
      backendResult = await submitBossAttempt({
        problemId: p.problemId,
        chapter: p.chapter,
        stage: p.stage,
        code: code.value,
        elapsedSec: elapsedSec.value,
        // 上传画像信号供后端 PassiveProfileService 被动更新弱项 KC
        ...(profileSignals.hasAnySignal.value ? { profileSignals: profileSignals.signals.value } : {}),
      });
    } catch (error) {
      // 判题服务不可用：绝不拿本地公开样例冒充正式判题（否则后端宕机时仅凭可见样例即可「过关」，
      // 闯关塔还会 markCleared 离线刷关）。本次不计分、不入历史、不动连胜，玩家可重试提交。
      errorMessage.value =
        `判题服务暂不可用，本次不计分，请稍后重试。（${error instanceof Error ? error.message : String(error)}）`;
      lastResult.value = null;
      retryAction.value = 'submit';
      phase.value = 'error';
      return;
    }
    retryAction.value = null;
    const passed = backendResult.passed;
    const finalResult: BossAttemptResultDto = {
      ...backendResult,
      passed,
      feedback: passed
        ? backendResult.feedback || '通过！下一题难度自适应提升。'
        : backendResult.feedback || localEval.stderr || '未通过，难度自适应下调。',
    };
    lastResult.value = finalResult;
    attempts.value += 1;

    const localScore = passed ? p.difficulty * 10 + Math.max(0, 30 - elapsedSec.value) : 0;
    totalScore.value += localScore;

    if (passed) {
      streak.value += 1;
      if (streak.value > bestStreak.value) bestStreak.value = streak.value;
      phase.value = 'passed';
    } else {
      streak.value = 0;
      phase.value = 'failed';
    }
    // 难度自适应统一由 computeAdaptiveDifficulty 在下一次取题时按更新后的 streak / 近期连续失败
    // 计算（单一真源，NFR-MNT）；此处不再叠加 "+0.5 / -1"，避免双重难度系统互相打架。

    const entry: EndlessHistoryEntry = {
      problemId: p.problemId,
      chapter: p.chapter,
      kcCode: p.kcCode,
      difficulty: p.difficulty,
      passed,
      elapsedSec: elapsedSec.value,
      score: localScore,
      timestamp: new Date().toISOString(),
    };
    history.value = [entry, ...history.value].slice(0, MAX_HISTORY);
    saveHistory(history.value);
    void prefetchNext().catch((error) => {
      if (typeof console !== 'undefined') {
        console.warn('[endless] prefetch failed:', error instanceof Error ? error.message : String(error));
      }
    });
  }

  /** 结果已知后冻结完整下一题请求，确保预取维度和超时重试完全一致。 */
  async function prefetchNext(): Promise<void> {
    if (!problem.value || pendingNext) return;
    const chapter = pickChapter(opts.focusChapter?.value ?? null);
    const target: PendingProblemRequest = { chapter, request: buildProblemRequest(chapter) };
    target.prefetchPromise = prefetchBossProblem(target.chapter, target.request);
    pendingNext = target;
    await target.prefetchPromise;
  }

  async function waitForPrefetch(target: PendingProblemRequest): Promise<void> {
    if (!target.prefetchPromise) return;
    let status: Awaited<ReturnType<typeof prefetchBossProblem>>;
    try {
      status = await target.prefetchPromise;
    } catch {
      // A lost POST response may still have created a server job; inspect it before starting a duplicate.
      status = await fetchBossPrefetchStatus(target.chapter, target.request);
    }
    for (let attempt = 0; status.status === 'PENDING' && attempt < PREFETCH_MAX_POLLS; attempt += 1) {
      await new Promise<void>((resolve) => setTimeout(resolve, PREFETCH_POLL_INTERVAL_MS));
      status = await fetchBossPrefetchStatus(target.chapter, target.request);
    }
    if (status.status === 'PENDING') throw new Error('题目仍在生成，请稍后重试');
  }

  async function nextProblem(): Promise<void> {
    // 「就绪预取」存于后端 pregenerated_problem (READY)：_fetchAndStart → 后端 fetchProblem 优先
    // claimReady，命中即瞬时返回（省去等 LLM），未命中自然回退现场出题。无需前端缓存题目本体。
    retryAction.value = 'next';
    if (!pendingNext) {
      const chapter = pickChapter(opts.focusChapter?.value ?? null);
      pendingNext = { chapter, request: buildProblemRequest(chapter) };
    }
    await _fetchAndStart(pendingNext);
  }

  async function retry(): Promise<void> {
    if (retryAction.value === 'submit') {
      await submit();
    } else if (retryAction.value === 'next') {
      if (!pendingNext) {
        const chapter = pickChapter(opts.focusChapter?.value ?? null);
        pendingNext = { chapter, request: buildProblemRequest(chapter) };
      }
      await _fetchAndStart(pendingNext);
    } else if (retryAction.value === 'start' && pendingCurrent) {
      await _fetchAndStart(pendingCurrent);
    } else {
      await startRun();
    }
  }

  function abort(): void {
    phase.value = 'idle';
    problem.value = null;
    retryAction.value = null;
    pendingNext = null;
    pendingCurrent = null;
  }

  function tickSecond(): void {
    if (phase.value === 'playing') {
      elapsedSec.value += 1;
    }
  }

  return {
    phase,
    problem,
    code,
    lastResult,
    streak,
    bestStreak,
    totalScore,
    attempts,
    currentDifficulty,
    history,
    elapsedSec,
    errorMessage,
    retryAction,
    failureRate,
    startRun,
    submit,
    nextProblem,
    retry,
    abort,
    tickSecond,
  };
}
