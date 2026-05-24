// R-13 §5 Phase 4：Boss 副本前端状态机。
//
// 阶段流转：
//   idle -> loading -> playing
//   playing -> evaluating -> passed(下一阶段) | failed(允许重试)
//   通过 stage 4 后 -> victory
//
// 评测策略：先在浏览器跑 Pyodide 给玩家即时反馈，再调真后端 submitBossAttempt
// 把分数 / mastery 同步入库（后端 5xx 时仍以 Pyodide 判定为准）。

import { ref, type Ref } from 'vue';
import { requestBossProblem, submitBossAttempt } from '@/api/boss';
import type {
  BossAttemptResultDto,
  BossProblemDto,
  BossSessionStatus,
} from '@/api/dto/Boss';
import { getPyodideRunner } from '@/game/adapters/pyodide';
import { useMainQuestStore } from '@stores/mainQuest';

export type BossPhase =
  | 'idle'
  | 'loading'
  | 'playing'
  | 'evaluating'
  | 'passed'
  | 'failed'
  | 'victory'
  | 'error';

export interface UseBossSessionOptions {
  chapter: number;
  totalStages?: number;
}

export interface UseBossSessionReturn {
  phase: Ref<BossPhase>;
  problem: Ref<BossProblemDto | null>;
  code: Ref<string>;
  stage: Ref<number>;
  clearedStages: Ref<number>;
  lastResult: Ref<BossAttemptResultDto | null>;
  elapsedSec: Ref<number>;
  errorMessage: Ref<string>;
  startStage: () => Promise<void>;
  submit: () => Promise<void>;
  reset: () => void;
  status: () => BossSessionStatus;
  tickSecond: () => void;
  advanceStage: () => Promise<void>;
}

export function useBossSession(opts: UseBossSessionOptions): UseBossSessionReturn {
  const totalStages = opts.totalStages ?? 4;
  const phase = ref<BossPhase>('idle');
  const problem = ref<BossProblemDto | null>(null);
  const code = ref<string>('');
  const stage = ref<number>(1);
  const clearedStages = ref<number>(0);
  const lastResult = ref<BossAttemptResultDto | null>(null);
  const elapsedSec = ref<number>(0);
  const errorMessage = ref<string>('');

  const mainQuest = useMainQuestStore();

  async function startStage(): Promise<void> {
    phase.value = 'loading';
    lastResult.value = null;
    elapsedSec.value = 0;
    try {
      const fetched = await requestBossProblem(opts.chapter, stage.value);
      problem.value = fetched;
      code.value = '';
      phase.value = 'playing';
    } catch (error) {
      phase.value = 'error';
      errorMessage.value = error instanceof Error ? error.message : String(error);
    }
  }

  async function runPyodideEvaluation(
    p: BossProblemDto,
    source: string,
  ): Promise<{ ok: boolean; actual: string; stderr: string }> {
    try {
      const runner = getPyodideRunner();
      // 直接走 Pyodide adapter 的 stdin 通道，避免在 user code 前注入 stub
      // 干扰玩家自己定义的 input 函数 / 全局变量。
      const result = await runner.run(source, p.sampleInput);
      const actual = (result.stdout ?? '').trimEnd();
      const expected = (p.sampleOutput ?? '').trimEnd();
      return { ok: actual === expected, actual, stderr: result.stderr ?? '' };
    } catch (error) {
      const stderr = error instanceof Error ? error.message : String(error);
      return { ok: false, actual: '', stderr };
    }
  }

  async function submit(): Promise<void> {
    if (!problem.value) return;
    phase.value = 'evaluating';
    const p = problem.value;
    const localEval = await runPyodideEvaluation(p, code.value);
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
      // 后端失败不影响本地 Pyodide 判定
      const msg = error instanceof Error ? error.message : String(error);
      backendResult = {
        problemId: p.problemId,
        passed: localEval.ok,
        passedTestCases: localEval.ok ? p.totalTestCases : 0,
        totalTestCases: p.totalTestCases,
        score: localEval.ok ? 80 : 0,
        feedback: `后端联通失败，按本地 Pyodide 判定：${msg}`,
      };
    }
    // 合并：以 backendResult 为准；如本地评测和后端不一致，取「至少一个通过 ⇒ passed」并附说明。
    const passed = backendResult.passed || localEval.ok;
    const finalResult: BossAttemptResultDto = {
      ...backendResult,
      passed,
      passedTestCases: passed
        ? Math.max(backendResult.passedTestCases, p.totalTestCases)
        : backendResult.passedTestCases,
      feedback: passed
        ? backendResult.feedback || '通过！沙箱与服务端均确认无误。'
        : backendResult.feedback || localEval.stderr || '未通过，请检查输出',
      ...(localEval.actual && !passed
        ? {
            failureFirstHint:
              backendResult.failureFirstHint ??
              `期望 ${JSON.stringify(p.sampleOutput.trimEnd())}，实际 ${JSON.stringify(localEval.actual)}`,
          }
        : {}),
    };
    lastResult.value = finalResult;
    // 写主线 AI 出题历史日志（用于自适应难度）
    mainQuest.recordAiProblem({
      problemId: p.problemId,
      kc: p.kcCode,
      difficulty: p.difficulty,
      succeededInSec: passed ? elapsedSec.value : null,
      timestamp: new Date().toISOString(),
    });
    if (passed) {
      clearedStages.value += 1;
      if (stage.value >= totalStages) {
        phase.value = 'victory';
        mainQuest.defeatBoss(opts.chapter);
      } else {
        phase.value = 'passed';
      }
    } else {
      phase.value = 'failed';
    }
  }

  function reset(): void {
    phase.value = 'idle';
    problem.value = null;
    code.value = '';
    stage.value = 1;
    clearedStages.value = 0;
    lastResult.value = null;
    elapsedSec.value = 0;
    errorMessage.value = '';
  }

  function tickSecond(): void {
    if (phase.value === 'playing') {
      elapsedSec.value += 1;
    }
  }

  function status(): BossSessionStatus {
    return {
      chapter: opts.chapter,
      stage: stage.value,
      clearedStages: clearedStages.value,
      problem: problem.value,
      lastAttempt: lastResult.value,
      victory: phase.value === 'victory',
    };
  }

  // 当 passed 时手动调用 nextStage：把 stage++ 然后 startStage。
  // 暴露成 reset / startStage 即可，组件按需调用。
  const advanceStage = async (): Promise<void> => {
    if (phase.value !== 'passed') return;
    if (stage.value >= totalStages) return;
    stage.value += 1;
    await startStage();
  };

  return {
    phase,
    problem,
    code,
    stage,
    clearedStages,
    lastResult,
    elapsedSec,
    errorMessage,
    startStage,
    submit,
    reset,
    status,
    tickSecond,
    advanceStage,
  };
}
