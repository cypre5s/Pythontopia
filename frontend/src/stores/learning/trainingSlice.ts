import { ref } from 'vue';
import { getPublicProblem } from '@/api/problems';
import { createSubmission, getSubmission } from '@/api/submissions';
import { getPyodideRunner, type PyodideRunResult } from '@/game/adapters/pyodide';
import {
  notifyMainQuestSubmission,
  notifyMoodRefresh,
  notifyProbeRequest,
} from './notify';
import type {
  PublicProblemDto,
  SubmissionCreateRequest,
  SubmissionDetailDto,
} from '@/api/dto/Submission';

export type TrainingPhase = 'idle' | 'previewing' | 'submitting' | 'done' | 'error';

export interface TrainingState {
  problem: PublicProblemDto | null;
  code: string;
  preview: PyodideRunResult | null;
  submission: SubmissionDetailDto | null;
  phase: TrainingPhase;
  lastError: string | null;
}

export interface TrainingSliceDeps {
  /** 正式判题落库后刷新今日任务（练题型任务的 evidence 由后端校验）。 */
  loadTodayQuests: () => Promise<void>;
  /** AC 后刷新掌握度快照并联动主线「测」（fieldSlice.refreshAfterEvent）。 */
  refreshAfterEvent: (kcCode: string) => Promise<void>;
}

/**
 * 训练场（「练」环）切片：双轨执行 ——
 * Pyodide 仅本地即时预览；正式提交必须 submitToServer 走服务端判题并入库。
 */
export function createTrainingSlice(deps: TrainingSliceDeps) {
  const training = ref<TrainingState>({
    problem: null,
    code: '',
    preview: null,
    submission: null,
    phase: 'idle',
    lastError: null,
  });

  async function loadTrainingProblem(problemId: number): Promise<void> {
    training.value.lastError = null;
    try {
      const problem = await getPublicProblem(problemId);
      training.value.problem = problem;
      training.value.preview = null;
      training.value.submission = null;
      training.value.phase = 'idle';
      if (!training.value.code) {
        training.value.code = '# 在这里编写 Python 代码\n';
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      training.value.lastError = message;
      training.value.phase = 'error';
    }
  }

  function setTrainingCode(code: string): void {
    training.value.code = code;
  }

  async function runPyodidePreview(stdin: string = ''): Promise<void> {
    training.value.phase = 'previewing';
    training.value.lastError = null;
    try {
      const runner = getPyodideRunner();
      await runner.init();
      const result = await runner.run(training.value.code, stdin);
      training.value.preview = result;
      training.value.phase = 'done';
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      training.value.lastError = message;
      training.value.phase = 'error';
    }
  }

  async function submitToServer(
    payload: SubmissionCreateRequest,
  ): Promise<SubmissionDetailDto | null> {
    training.value.phase = 'submitting';
    training.value.lastError = null;
    try {
      const created = await createSubmission(payload);
      const detail = await getSubmission(created.id);
      training.value.submission = detail;
      training.value.phase = 'done';
      // 判题结果回来即刷新情绪氛围（streak 同时计入 AC/WA，故无论通过与否都刷）。
      notifyMoodRefresh();
      await deps.loadTodayQuests();
      // 真题判定通过 → 推进主线「练」子任务，并刷新掌握度联动「测」子任务。
      const passed = detail.judgeResult?.verdict === 'AC';
      if (passed) {
        const kcCode = training.value.problem?.kcCode;
        notifyMainQuestSubmission(
          kcCode ? { problemId: payload.problemId, kcCode } : { problemId: payload.problemId },
        );
        try {
          // 后端在 AC 时按难度加权掌握度；刷新快照后 refreshAfterEvent 会触发 kc_mastery 联动。
          await deps.refreshAfterEvent(kcCode ?? '');
        } catch {
          /* 掌握度刷新失败不阻断提交结果展示 */
        }
      } else if (
        // 服务端判题为「失败」（FAILED/ERROR 或有非 AC 判定）→ 请求 fail_react
        // 探针。仅置标记、不阻塞；排除 PENDING/RUNNING（尚未判定）误触。
        detail.submission.status === 'FAILED' ||
        detail.submission.status === 'ERROR' ||
        (detail.judgeResult !== null && detail.judgeResult.verdict !== 'AC')
      ) {
        notifyProbeRequest('fail_react');
      }
      return detail;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      training.value.lastError = message;
      training.value.phase = 'error';
      return null;
    }
  }

  return {
    training,
    loadTrainingProblem,
    setTrainingCode,
    runPyodidePreview,
    submitToServer,
  };
}
