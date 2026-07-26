import { ref } from 'vue';
import { getPublicProblem } from '@/api/problems';
import { createSubmission, getSubmission } from '@/api/submissions';
import { getPyodideRunner, type PyodideRunResult } from '@/game/adapters/pyodide';
import {
  notifyMoodRefresh,
  notifyProbeRequest,
} from './notify';
import type {
  PublicProblemDto,
  SubmissionCreateRequest,
  SubmissionDetailDto,
} from '@/api/dto/Submission';

export type TrainingPhase = 'idle' | 'previewing' | 'submitting' | 'done' | 'error';

/**
 * 切题后的默认代码模板：由 TrainingPanel 在「无草稿」时落盘（store 只负责清空，
 * 保证「草稿 ?? 模板」的赋值发生在唯一一处，不会与去抖草稿保存打架）。
 */
export const DEFAULT_TRAINING_CODE = '# 在这里编写 Python 代码\n';

export interface TrainingState {
  problem: PublicProblemDto | null;
  code: string;
  preview: PyodideRunResult | null;
  submission: SubmissionDetailDto | null;
  /** 本地 Pyodide 预览轨道相位（「运行」按钮只看它）。 */
  previewPhase: TrainingPhase;
  /** 服务端判题轨道相位（「提交」按钮只看它，运行不会把它重置成可点）。 */
  submitPhase: TrainingPhase;
  /** 本地运行错误：只喂给 Output 区的 Python 报错翻译。 */
  previewError: string | null;
  /** 提交/判题错误：只喂给提交结果与 toast。 */
  submitError: string | null;
  /**
   * 题目加载错误；同时镜像最近一次运行/提交错误，兼容仅读 lastError 的旧调用方
   * （工坊 CodeLabView / PracticeList 在 runPyodidePreview 后读它）。
   */
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
    previewPhase: 'idle',
    submitPhase: 'idle',
    previewError: null,
    submitError: null,
    lastError: null,
  });

  async function loadTrainingProblem(problemId: number): Promise<void> {
    training.value.lastError = null;
    try {
      const problem = await getPublicProblem(problemId);
      // 切题必须清空上一题代码：随后由 TrainingPanel 的 problemId watch 用
      // 「草稿 ?? 模板」做一次确定性赋值。否则上一题代码会被去抖草稿保存写成新题草稿，
      // 且模板从第二题起永远不再出现。
      training.value.code = '';
      training.value.problem = problem;
      training.value.preview = null;
      training.value.submission = null;
      training.value.previewPhase = 'idle';
      training.value.submitPhase = 'idle';
      training.value.previewError = null;
      training.value.submitError = null;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      training.value.lastError = message;
    }
  }

  function setTrainingCode(code: string): void {
    training.value.code = code;
  }

  async function runPyodidePreview(stdin: string = ''): Promise<void> {
    training.value.previewPhase = 'previewing';
    training.value.previewError = null;
    training.value.lastError = null;
    // 刻意不清 submission/submitError：本地运行与服务端判题是两个独立卡片（Output vs 判题结果），
    // 清掉判题结果会连带抹掉「进入 Bug 矿洞」入口 —— 而判题失败后先本地跑一遍正是最常见的调试动作。
    // 两轨的错误已由 previewError / submitError 分开承载，不存在串味。
    try {
      const runner = getPyodideRunner();
      await runner.init();
      const result = await runner.run(training.value.code, stdin);
      training.value.preview = result;
      training.value.previewPhase = 'done';
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      training.value.previewError = message;
      training.value.lastError = message;
      training.value.previewPhase = 'error';
    }
  }

  async function submitToServer(
    payload: SubmissionCreateRequest,
  ): Promise<SubmissionDetailDto | null> {
    // 提交进行中再次进来（多入口/回车）直接拒绝，避免同一份代码重复入库、重复计掌握度与任务证据。
    if (training.value.submitPhase === 'submitting') return null;
    training.value.submitPhase = 'submitting';
    training.value.submitError = null;
    training.value.lastError = null;
    // 进入服务端判题轨道：清掉本地预览结果，避免「本地跑通了」被当成判题依据。
    // 同样只清「结果」，运行在途时不改 previewPhase。
    training.value.preview = null;
    training.value.previewError = null;
    try {
      const created = await createSubmission(payload);
      const detail = await getSubmission(created.id);
      training.value.submission = detail;
      training.value.submitPhase = 'done';
      // 判题结果回来即刷新情绪氛围（streak 同时计入 AC/WA，故无论通过与否都刷）。
      notifyMoodRefresh();
      await deps.loadTodayQuests();
      // 真题判定通过 → 刷新掌握度快照（供训练页提示 / 闯关塔 / 画像即时反映）。
      // R-15：主线「练」进度改由后端 stage 三件套 example-done API 记录，不再走客户端钩子。
      const passed = detail.judgeResult?.verdict === 'AC';
      if (passed) {
        const kcCode = training.value.problem?.kcCode;
        try {
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
      training.value.submitError = message;
      training.value.lastError = message;
      training.value.submitPhase = 'error';
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
