import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useLearningStore } from '@stores/learning';
import { setPyodideRunner } from '@/game/adapters/pyodide';
import type { PublicProblemDto, SubmissionDto } from '@/api/dto/Submission';

vi.mock('@/api/problems', () => ({
  getPublicProblem: vi.fn(),
}));
vi.mock('@/api/submissions', () => ({
  createSubmission: vi.fn(),
  getSubmission: vi.fn(),
  listSubmissions: vi.fn(),
}));
vi.mock('@/api/quests', () => ({
  getTodayQuests: vi.fn(),
  listQuests: vi.fn(),
  startQuest: vi.fn(),
  completeQuest: vi.fn(),
  skipQuest: vi.fn(),
}));
vi.mock('@/api/classroomQuests', () => ({
  getMyClassroomQuests: vi.fn(),
}));
vi.mock('@/api/path', () => ({
  planLearningPath: vi.fn(),
  getActiveLearningPath: vi.fn(),
  getArchivedLearningPaths: vi.fn(),
}));

import { getPublicProblem } from '@/api/problems';
import { createSubmission, getSubmission } from '@/api/submissions';
import { getTodayQuests } from '@/api/quests';

function problemOf(id: number): PublicProblemDto {
  return {
    id,
    packId: 43,
    kcCode: 'PY43-KC-016',
    title: `题目 ${id}`,
    statementMarkdown: '题干',
    inputDescription: 'r',
    outputDescription: 'area',
    difficulty: 'basic',
    samples: [{ input: '5', output: '78.5375' }],
  };
}

const FAILED_SUBMISSION: SubmissionDto = {
  id: 9,
  userId: 7,
  problemId: 1,
  questId: null,
  source: 'TRAINING',
  language: 'PYTHON_3_11',
  code: 'print(1)',
  submittedAt: '2026-06-01T10:00:00Z',
  status: 'FAILED',
  runtimeMs: 3,
  memoryKb: null,
};

describe('learning store training slice', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.mocked(getTodayQuests).mockResolvedValue([]);
    vi.mocked(getPublicProblem).mockImplementation(async (id: number) => problemOf(id));
  });

  afterEach(() => {
    setPyodideRunner(null);
    vi.clearAllMocks();
  });

  it('clears the editor code when switching problems so the next problem never inherits it', async () => {
    const store = useLearningStore();
    await store.loadTrainingProblem(1);
    store.setTrainingCode('print("A 题的代码")');

    await store.loadTrainingProblem(2);

    // 清空是「切题确定性赋值」的前置：由 TrainingPanel 用「草稿 ?? 模板」补上，
    // 否则 A 的代码会被去抖草稿保存写成 B 的草稿。
    expect(store.training.code).toBe('');
    expect(store.training.problem?.id).toBe(2);
  });

  it('keeps run and submit phases independent so a local run cannot re-enable submit', async () => {
    let releaseCreate: ((value: SubmissionDto) => void) | null = null;
    vi.mocked(createSubmission).mockImplementation(
      () =>
        new Promise<SubmissionDto>((resolve) => {
          releaseCreate = resolve;
        }),
    );
    vi.mocked(getSubmission).mockResolvedValue({
      submission: FAILED_SUBMISSION,
      judgeResult: {
        id: 91,
        submissionId: 9,
        verdict: 'WA',
        passedCases: 0,
        totalCases: 3,
        failureReason: '答案错误',
        trace: {},
        createdAt: '2026-06-01T10:00:00Z',
      },
    });
    setPyodideRunner({
      init: vi.fn(async () => {}),
      run: vi.fn(async () => ({ stdout: '1\n', stderr: '', durationMs: 2, isError: false })),
      dispose: vi.fn(),
    });

    const store = useLearningStore();
    await store.loadTrainingProblem(1);
    store.setTrainingCode('print(1)');

    const pending = store.submitToServer({ problemId: 1, code: 'print(1)', source: 'TRAINING' });
    expect(store.training.submitPhase).toBe('submitting');

    // 判题在途时点「运行」：本地预览走自己的相位，不得把提交相位重置成可再次提交。
    await store.runPyodidePreview('5');
    expect(store.training.previewPhase).toBe('done');
    expect(store.training.submitPhase).toBe('submitting');

    const secondAttempt = await store.submitToServer({
      problemId: 1,
      code: 'print(1)',
      source: 'TRAINING',
    });
    expect(secondAttempt).toBeNull();
    expect(createSubmission).toHaveBeenCalledTimes(1);

    releaseCreate?.(FAILED_SUBMISSION);
    await pending;
    expect(store.training.submitPhase).toBe('done');
  });

  it('routes submit failures to submitError and leaves the local run error slot clean', async () => {
    vi.mocked(createSubmission).mockRejectedValue(new Error('判题服务不可用'));

    const store = useLearningStore();
    await store.loadTrainingProblem(1);
    const detail = await store.submitToServer({
      problemId: 1,
      code: 'print(1)',
      source: 'TRAINING',
    });

    expect(detail).toBeNull();
    expect(store.training.submitError).toContain('判题服务不可用');
    // 提交失败不是 Python 运行错误：不能流进 Output 区的报错翻译。
    expect(store.training.previewError).toBeNull();
    expect(store.training.lastError).toContain('判题服务不可用');
  });

  it('clears the stale judge result when the local run starts', async () => {
    vi.mocked(createSubmission).mockResolvedValue(FAILED_SUBMISSION);
    vi.mocked(getSubmission).mockResolvedValue({
      submission: FAILED_SUBMISSION,
      judgeResult: {
        id: 91,
        submissionId: 9,
        verdict: 'WA',
        passedCases: 0,
        totalCases: 3,
        failureReason: '答案错误',
        trace: {},
        createdAt: '2026-06-01T10:00:00Z',
      },
    });
    setPyodideRunner({
      init: vi.fn(async () => {}),
      run: vi.fn(async () => ({ stdout: '1\n', stderr: '', durationMs: 2, isError: false })),
      dispose: vi.fn(),
    });

    const store = useLearningStore();
    await store.loadTrainingProblem(1);
    await store.submitToServer({ problemId: 1, code: 'print(1)', source: 'TRAINING' });
    expect(store.training.submission).not.toBeNull();

    await store.runPyodidePreview('5');
    // 本地运行不得抹掉判题结果：面板据此渲染「进入 Bug 矿洞」入口，
    // 而判题失败后先本地跑一遍恰恰是最常见的调试路径。
    expect(store.training.submission).not.toBeNull();
    expect(store.training.submission?.judgeResult?.verdict).toBe('WA');
    expect(store.training.preview?.stdout).toBe('1\n');
  });
});
