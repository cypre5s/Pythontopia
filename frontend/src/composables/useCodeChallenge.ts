// 共享「代码挑战」状态机（Wave 1 基座）。
//
// 同时服务无尽闯关与代码舞台两条链路，统一「预览 → 提交」流程，但不绑定任何具体判题 API：
//   - runPreview()：走共享 Pyodide 单例本地跑首个样例，trim 比对 stdout，仅供学生自测（非权威）；
//   - submit()：委托注入的 judge() 做权威判题，结果直接决定 passed/failed；
//   - 本地预览通过 ≠ 提交通过：后端 judge 返回 failed 时一律以 judge 为准（与 boss 链路一致）。

import { isRef, ref, type Ref } from 'vue';
import { getPyodideRunner } from '@/game/adapters/pyodide';
import type { ChallengeResult, CodeChallenge } from '@/api/dto/CodeChallenge';

export type ChallengePhase =
  | 'idle'
  | 'running'
  | 'evaluating'
  | 'passed'
  | 'failed'
  | 'error';

/** 本地预览结果（非权威，仅供学生自测；权威判定以 submit() 的 judge 结果为准）。 */
export interface ChallengePreview {
  /** 首个样例 stdout trim 后是否匹配期望输出。 */
  matched: boolean;
  stdout: string;
  stderr: string;
  durationMs: number;
}

export interface UseCodeChallengeOptions {
  /** 题目，支持响应式 ref 或静态对象。 */
  challenge: Ref<CodeChallenge> | CodeChallenge;
  /** 注入式判题：两端各传自己的后端判题实现，composable 不感知 boss / submissions 等具体 API。 */
  judge: (code: string) => Promise<ChallengeResult>;
  /** 判题返回（权威结果）后的回调。 */
  onResult?: (result: ChallengeResult) => void;
}

export interface UseCodeChallengeReturn {
  code: Ref<string>;
  phase: Ref<ChallengePhase>;
  result: Ref<ChallengeResult | null>;
  error: Ref<string | null>;
  /** 最近一次本地预览结果（非权威）。 */
  preview: Ref<ChallengePreview | null>;
  /** 归一后的题目 ref。 */
  challenge: Ref<CodeChallenge>;
  runPreview: () => Promise<ChallengePreview>;
  submit: () => Promise<ChallengeResult | null>;
  reset: () => void;
}

function toMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}

export function useCodeChallenge(options: UseCodeChallengeOptions): UseCodeChallengeReturn {
  const challenge: Ref<CodeChallenge> = isRef(options.challenge)
    ? options.challenge
    : ref(options.challenge);

  const code = ref<string>(challenge.value.starterCode ?? '');
  const phase = ref<ChallengePhase>('idle');
  const result = ref<ChallengeResult | null>(null);
  const error = ref<string | null>(null);
  const preview = ref<ChallengePreview | null>(null);

  async function runPreview(): Promise<ChallengePreview> {
    error.value = null;
    phase.value = 'running';
    const sample = challenge.value.samples[0];
    try {
      const runner = getPyodideRunner();
      const run = await runner.run(code.value, sample?.input ?? '');
      const actual = (run.stdout ?? '').trim();
      const expected = (sample?.output ?? '').trim();
      const matched = sample != null && !run.isError && actual === expected;
      const next: ChallengePreview = {
        matched,
        stdout: run.stdout ?? '',
        stderr: run.stderr ?? '',
        durationMs: run.durationMs,
      };
      preview.value = next;
      // 预览不主张权威：跑完回到 idle，绝不置 passed/failed（那是 submit 的职责）。
      phase.value = 'idle';
      return next;
    } catch (err) {
      const message = toMessage(err);
      error.value = message;
      phase.value = 'error';
      const next: ChallengePreview = {
        matched: false,
        stdout: '',
        stderr: message,
        durationMs: 0,
      };
      preview.value = next;
      return next;
    }
  }

  async function submit(): Promise<ChallengeResult | null> {
    error.value = null;
    phase.value = 'evaluating';
    try {
      const judged = await options.judge(code.value);
      result.value = judged;
      // 权威判定：本地预览即便通过，后端 judged.passed=false 也一律以 judge 为准。
      phase.value = judged.passed ? 'passed' : 'failed';
      options.onResult?.(judged);
      return judged;
    } catch (err) {
      error.value = toMessage(err);
      phase.value = 'error';
      return null;
    }
  }

  function reset(): void {
    code.value = challenge.value.starterCode ?? '';
    phase.value = 'idle';
    result.value = null;
    error.value = null;
    preview.value = null;
  }

  return {
    code,
    phase,
    result,
    error,
    preview,
    challenge,
    runPreview,
    submit,
    reset,
  };
}
