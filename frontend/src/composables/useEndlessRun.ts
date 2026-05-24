// R-13 §5 Phase 8 续：无尽模式状态机。
//
// 玩家进入 /endless 后系统连续出题（来自 graph /agents/boss/problem，回落 golden）。
// 自适应规则（简化版，R-14 可接学习画像后端）：
//   * 连胜 1 → 难度 +0.5；连胜 2 → 难度 +1；
//   * 失败 1 → 难度 -1，下一题 KC 不变；
//   * 难度 clamp 到 1..5；
//   * 章节按用户选择（focusChapter）或随机抽 1..7；
//   * 总分 = 通过题数 × 难度 × 10；
//   * 失败结束当前局，玩家可继续无尽，但 streak 清零。

import { computed, ref, type Ref } from 'vue';
import { requestBossProblem, submitBossAttempt } from '@/api/boss';
import { getPyodideRunner } from '@/game/adapters/pyodide';
import type {
  BossAttemptResultDto,
  BossProblemDto,
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
  failureRate: Ref<number>;
  startRun: () => Promise<void>;
  submit: () => Promise<void>;
  nextProblem: () => Promise<void>;
  abort: () => void;
  tickSecond: () => void;
}

const PERSIST_KEY = 'pyt_endless_history_v1';
const MAX_HISTORY = 50;

function loadHistory(): EndlessHistoryEntry[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(PERSIST_KEY);
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
    window.localStorage.setItem(PERSIST_KEY, JSON.stringify(history.slice(0, MAX_HISTORY)));
  } catch {
    /* private mode */
  }
}

function pickChapter(focus: number | null): number {
  if (focus && focus >= 1 && focus <= 7) return focus;
  return 1 + Math.floor(Math.random() * 7);
}

function clampDifficulty(d: number): number {
  return Math.max(1, Math.min(5, Math.round(d)));
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
  const history = ref<EndlessHistoryEntry[]>(loadHistory());

  const failureRate = computed<number>(() => {
    if (attempts.value === 0) return 0;
    const failed = attempts.value - streak.value - bestStreak.value;
    // 简化：用近 10 题统计
    const recent = history.value.slice(0, 10);
    if (recent.length === 0) return 0;
    const failedCount = recent.filter((h) => !h.passed).length;
    void failed;
    return failedCount / recent.length;
  });

  async function _fetchAndStart(): Promise<void> {
    phase.value = 'loading';
    lastResult.value = null;
    code.value = '';
    elapsedSec.value = 0;
    try {
      const chapter = pickChapter(opts.focusChapter?.value ?? null);
      const stage = 1 + Math.floor(Math.random() * 4);
      const fetched = await requestBossProblem(chapter, stage);
      problem.value = {
        ...fetched,
        difficulty: clampDifficulty(currentDifficulty.value),
      };
      phase.value = 'playing';
    } catch (error) {
      phase.value = 'error';
      errorMessage.value = error instanceof Error ? error.message : String(error);
    }
  }

  async function startRun(): Promise<void> {
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
    phase.value = 'evaluating';
    const localEval = await _runPyodide(p, code.value);
    let backendResult: BossAttemptResultDto | null = null;
    try {
      backendResult = await submitBossAttempt({
        problemId: p.problemId,
        chapter: p.chapter,
        stage: p.stage,
        code: code.value,
        elapsedSec: elapsedSec.value,
      });
    } catch (error) {
      backendResult = {
        problemId: p.problemId,
        passed: localEval.ok,
        passedTestCases: localEval.ok ? p.totalTestCases : 0,
        totalTestCases: p.totalTestCases,
        score: localEval.ok ? 60 : 0,
        feedback: `后端联通失败，按本地 Pyodide 判定：${error instanceof Error ? error.message : String(error)}`,
      };
    }
    const passed = backendResult.passed || localEval.ok;
    const finalResult: BossAttemptResultDto = {
      ...backendResult,
      passed,
      passedTestCases: passed
        ? Math.max(backendResult.passedTestCases, p.totalTestCases)
        : backendResult.passedTestCases,
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
      // 连胜自适应：连 1 → +0.5；连 2+ → +1
      const bump = streak.value >= 2 ? 1 : 0.5;
      currentDifficulty.value = clampDifficulty(currentDifficulty.value + bump);
      phase.value = 'passed';
    } else {
      streak.value = 0;
      currentDifficulty.value = clampDifficulty(currentDifficulty.value - 1);
      phase.value = 'failed';
    }

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
  }

  async function nextProblem(): Promise<void> {
    await _fetchAndStart();
  }

  function abort(): void {
    phase.value = 'idle';
    problem.value = null;
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
    failureRate,
    startRun,
    submit,
    nextProblem,
    abort,
    tickSecond,
  };
}
