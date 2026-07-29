import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import TrainingPanel from '@/components/training/TrainingPanel.vue';
import { useLearningStore } from '@stores/learning';
import { setPyodideRunner } from '@/game/adapters/pyodide';

vi.mock('@/api/problems', () => ({
  getPublicProblem: vi.fn(),
}));
vi.mock('@/api/submissions', () => ({
  createSubmission: vi.fn(),
  getSubmission: vi.fn(),
  listSubmissions: vi.fn(),
}));
vi.mock('@/api/path', () => ({
  planLearningPath: vi.fn(),
  getActiveLearningPath: vi.fn(),
  getArchivedLearningPaths: vi.fn(),
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
vi.mock('@/api/field', () => ({
  fetchMastery: vi.fn(async () => []),
  bootstrapCurriculum: vi.fn(),
}));
vi.mock('@/api/tutorWorkflow', () => ({
  getActiveWorkflowSession: vi.fn(async () => ({
    sessionKey: 'wf-test',
    problemId: 476,
    phase: 'READING',
    seq: 0,
    cards: [],
  })),
}));

import { getPublicProblem } from '@/api/problems';
import { createSubmission, getSubmission } from '@/api/submissions';
import { getTodayQuests } from '@/api/quests';
import { useToastStore } from '@stores/toast';
import { loadCodeDraft, saveCodeDraft } from '@/composables/useCodeDraft';
import { DEFAULT_TRAINING_CODE } from '@stores/learning/trainingSlice';
import type { SubmissionDto } from '@/api/dto/Submission';

function mountPanel(options: { stubTutor?: boolean } = {}) {
  const router: Router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/training', name: 'training', component: { template: '<div />' } },
      { path: '/bug-mine', name: 'bug-mine', component: { template: '<div />' } },
    ],
  });
  return mount(TrainingPanel, {
    global: {
      plugins: [createPinia(), router],
      stubs: options.stubTutor
        ? {
            TutorWorkflowPanel: {
              props: ['problemId', 'kcCodes', 'submissionId', 'initialEventRequest'],
              template:
                '<div data-testid="stub-tutor-workflow" :data-initial-event="initialEventRequest?.event || \'\'" />',
            },
          }
        : {},
    },
  });
}

describe('TrainingPanel', () => {
  beforeEach(() => {
    // 每个挂载实例都会创建 500ms 草稿去抖定时器。全量套件负载较高时，上一用例的
    // 真实定时器可能跨过下一次 localStorage.clear() 后才回写，造成跨用例污染。
    vi.useFakeTimers();
    setActivePinia(createPinia());
    window.localStorage.clear();
    vi.mocked(getPublicProblem).mockResolvedValue({
      id: 476,
      packId: 43,
      kcCode: 'PY43-KC-016',
      title: '圆面积',
      statementMarkdown: '### 题目描述\n\n读取半径并使用 `print()` 输出面积。',
      inputDescription: 'r',
      outputDescription: 'area',
      difficulty: 'basic',
      samples: [{ input: '5', output: '78.5375' }],
    });
    vi.mocked(getTodayQuests).mockResolvedValue([]);
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('preview button does NOT call /api/submissions', async () => {
    const fakeRunner = {
      init: vi.fn(async () => {}),
      run: vi.fn(async () => ({
        stdout: '78.5375\n',
        stderr: '',
        durationMs: 5,
        isError: false,
      })),
      dispose: vi.fn(),
    };
    setPyodideRunner(fakeRunner);

    const wrapper = mountPanel();
    const learning = useLearningStore();
    await learning.loadTrainingProblem(476);
    learning.setTrainingCode('print("hi")');
    await flushPromises();

    await wrapper.find('[data-testid="preview-button"]').trigger('click');
    await flushPromises();
    expect(fakeRunner.run).toHaveBeenCalledTimes(1);
    expect(createSubmission).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="preview-output"]').exists()).toBe(true);
    setPyodideRunner(null);
  });

  it('formal submit posts to /api/submissions and shows verdict', async () => {
    vi.mocked(createSubmission).mockResolvedValue({
      id: 1,
      userId: 7,
      problemId: 476,
      questId: null,
      source: 'TRAINING',
      language: 'PYTHON_3_11',
      code: 'print(1)',
      submittedAt: '2026-05-21T19:00:00Z',
      status: 'PASSED',
      runtimeMs: 12,
      memoryKb: null,
    });
    vi.mocked(getSubmission).mockResolvedValue({
      submission: {
        id: 1,
        userId: 7,
        problemId: 476,
        questId: null,
        source: 'TRAINING',
        language: 'PYTHON_3_11',
        code: 'print(1)',
        submittedAt: '2026-05-21T19:00:00Z',
        status: 'PASSED',
        runtimeMs: 12,
        memoryKb: null,
      },
      judgeResult: {
        id: 11,
        submissionId: 1,
        verdict: 'AC',
        passedCases: 3,
        totalCases: 3,
        failureReason: null,
        trace: {},
        createdAt: '2026-05-21T19:00:00Z',
      },
    });

    const wrapper = mountPanel();
    const learning = useLearningStore();
    await learning.loadTrainingProblem(476);
    learning.setTrainingCode('print(1)');
    await flushPromises();

    await wrapper.find('[data-testid="submit-button"]').trigger('click');
    await flushPromises();

    expect(createSubmission).toHaveBeenCalledTimes(1);
    expect(getSubmission).toHaveBeenCalledWith(1);
    expect(wrapper.find('[data-testid="submission-result"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('AC');
  });

  it('shows a short AC nudge and waits for click before opening tutor workflow', async () => {
    vi.mocked(createSubmission).mockResolvedValue({
      id: 21,
      userId: 7,
      problemId: 476,
      questId: null,
      source: 'TRAINING',
      language: 'PYTHON_3_11',
      code: 'print(1)',
      submittedAt: '2026-05-21T19:00:00Z',
      status: 'PASSED',
      runtimeMs: 12,
      memoryKb: null,
    });
    vi.mocked(getSubmission).mockResolvedValue({
      submission: {
        id: 21,
        userId: 7,
        problemId: 476,
        questId: null,
        source: 'TRAINING',
        language: 'PYTHON_3_11',
        code: 'print(1)',
        submittedAt: '2026-05-21T19:00:00Z',
        status: 'PASSED',
        runtimeMs: 12,
        memoryKb: null,
      },
      judgeResult: {
        id: 31,
        submissionId: 21,
        verdict: 'AC',
        passedCases: 3,
        totalCases: 3,
        failureReason: null,
        trace: {},
        createdAt: '2026-05-21T19:00:00Z',
      },
    });

    const wrapper = mountPanel({ stubTutor: true });
    const learning = useLearningStore();
    await learning.loadTrainingProblem(476);
    learning.setTrainingCode('print(1)');
    await flushPromises();

    await wrapper.find('[data-testid="submit-button"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="guide-nudge"]').text()).toContain('这题过了');
    expect(wrapper.find('[data-testid="stub-tutor-workflow"]').exists()).toBe(false);

    await wrapper.get('[data-testid="guide-nudge-ac-review"]').trigger('click');
    await flushPromises();

    const tutor = wrapper.get('[data-testid="stub-tutor-workflow"]');
    expect(tutor.attributes('data-initial-event')).toBe('AC_REVIEW');
  });

  it('offers bug mine entry after failed official training submission', async () => {
    vi.mocked(createSubmission).mockResolvedValue({
      id: 3,
      userId: 7,
      problemId: 476,
      questId: null,
      source: 'TRAINING',
      language: 'PYTHON_3_11',
      code: 'print(0)',
      submittedAt: '2026-05-21T19:00:00Z',
      status: 'FAILED',
      runtimeMs: 12,
      memoryKb: null,
    });
    vi.mocked(getSubmission).mockResolvedValue({
      submission: {
        id: 3,
        userId: 7,
        problemId: 476,
        questId: null,
        source: 'TRAINING',
        language: 'PYTHON_3_11',
        code: 'print(0)',
        submittedAt: '2026-05-21T19:00:00Z',
        status: 'FAILED',
        runtimeMs: 12,
        memoryKb: null,
      },
      judgeResult: {
        id: 13,
        submissionId: 3,
        verdict: 'WA',
        passedCases: 0,
        totalCases: 3,
        failureReason: '答案错误',
        trace: {},
        createdAt: '2026-05-21T19:00:00Z',
      },
    });

    const wrapper = mountPanel();
    const router = wrapper.vm.$router;
    const pushSpy = vi.spyOn(router, 'push');
    const learning = useLearningStore();
    await learning.loadTrainingProblem(476);
    learning.setTrainingCode('print(0)');
    await flushPromises();

    await wrapper.find('[data-testid="submit-button"]').trigger('click');
    await flushPromises();

    const bugMineButton = wrapper.get('[data-testid="bug-mine-button"]');
    expect(bugMineButton.text()).toContain('进入 Bug 矿洞');

    await bugMineButton.trigger('click');
    await flushPromises();

    // S1：私有 submissionId 改走 store 入口（不写进 URL），仅按路径导航。
    expect(learning.bugMine.pendingEntrySubmissionId).toBe(3);
    expect(pushSpy).toHaveBeenCalledWith('/bug-mine');
  });

  it('shows WA verdict for a non-AC judge result', async () => {
    vi.mocked(createSubmission).mockResolvedValue({
      id: 2,
      userId: 7,
      problemId: 476,
      questId: null,
      source: 'TRAINING',
      language: 'PYTHON_3_11',
      code: 'print(0)',
      submittedAt: '2026-05-21T19:00:00Z',
      status: 'PASSED',
      runtimeMs: 12,
      memoryKb: null,
    });
    vi.mocked(getSubmission).mockResolvedValue({
      submission: {
        id: 2,
        userId: 7,
        problemId: 476,
        questId: null,
        source: 'TRAINING',
        language: 'PYTHON_3_11',
        code: 'print(0)',
        submittedAt: '2026-05-21T19:00:00Z',
        status: 'PASSED',
        runtimeMs: 12,
        memoryKb: null,
      },
      judgeResult: {
        id: 12,
        submissionId: 2,
        verdict: 'WA',
        passedCases: 0,
        totalCases: 3,
        failureReason: '答案错误',
        trace: {},
        createdAt: '2026-05-21T19:00:00Z',
      },
    });

    const wrapper = mountPanel();
    const learning = useLearningStore();
    await learning.loadTrainingProblem(476);
    learning.setTrainingCode('print(0)');
    await flushPromises();

    await wrapper.find('[data-testid="submit-button"]').trigger('click');
    await flushPromises();
    await flushPromises();

    expect(wrapper.text()).toContain('WA');
  });

  it('renders problem statement as markdown instead of raw text', async () => {
    const wrapper = mountPanel();
    const learning = useLearningStore();
    await learning.loadTrainingProblem(476);
    await flushPromises();

    const statement = wrapper.get('[data-testid="problem-statement"]');
    expect(statement.find('h3').text()).toBe('题目描述');
    expect(statement.find('code').text()).toBe('print()');
    expect(statement.text()).not.toContain('### 题目描述');
  });

  it('resets the editor to the template when switching to a problem without a draft', async () => {
    const wrapper = mountPanel();
    const learning = useLearningStore();
    await learning.loadTrainingProblem(476);
    await flushPromises();
    learning.setTrainingCode('print("上一题的代码")');
    await flushPromises();

    vi.mocked(getPublicProblem).mockResolvedValue({
      id: 477,
      packId: 43,
      kcCode: 'PY43-KC-017',
      title: '下一题',
      statementMarkdown: '题干',
      inputDescription: null,
      outputDescription: null,
      difficulty: 'basic',
      samples: [],
    });
    await learning.loadTrainingProblem(477);
    await flushPromises();

    // 上一题的代码绝不能带过来（否则去抖保存会把它写成新题草稿）。
    expect(learning.training.code).toBe(DEFAULT_TRAINING_CODE);
    expect(learning.training.code).not.toContain('上一题的代码');
    expect(loadCodeDraft(477)).not.toBe('print("上一题的代码")');
    expect(wrapper.get('[data-testid="code-editor"]').text()).not.toContain('上一题的代码');
  });

  it('restores the target problem draft when switching problems', async () => {
    saveCodeDraft(477, 'print("477 的草稿")');
    const wrapper = mountPanel();
    const learning = useLearningStore();
    await learning.loadTrainingProblem(476);
    await flushPromises();
    learning.setTrainingCode('print("上一题的代码")');
    await flushPromises();

    vi.mocked(getPublicProblem).mockResolvedValue({
      id: 477,
      packId: 43,
      kcCode: 'PY43-KC-017',
      title: '下一题',
      statementMarkdown: '题干',
      inputDescription: null,
      outputDescription: null,
      difficulty: 'basic',
      samples: [],
    });
    await learning.loadTrainingProblem(477);
    await flushPromises();

    expect(learning.training.code).toBe('print("477 的草稿")');
    wrapper.unmount();
  });

  it('keeps submit disabled while judging even after a local run finishes', async () => {
    const fakeRunner = {
      init: vi.fn(async () => {}),
      run: vi.fn(async () => ({ stdout: '1\n', stderr: '', durationMs: 4, isError: false })),
      dispose: vi.fn(),
    };
    setPyodideRunner(fakeRunner);
    let releaseCreate: ((value: SubmissionDto) => void) | null = null;
    vi.mocked(createSubmission).mockImplementation(
      () =>
        new Promise<SubmissionDto>((resolve) => {
          releaseCreate = resolve;
        }),
    );
    vi.mocked(createSubmission).mockClear();

    const wrapper = mountPanel();
    const learning = useLearningStore();
    await learning.loadTrainingProblem(476);
    learning.setTrainingCode('print(1)');
    await flushPromises();

    await wrapper.get('[data-testid="submit-button"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="submit-button"]').attributes('disabled')).toBeDefined();

    // 判题在途时点「运行」：旧实现共用 phase，运行结束会把提交按钮重新放开 → 可重复提交。
    await wrapper.get('[data-testid="preview-button"]').trigger('click');
    await flushPromises();
    expect(fakeRunner.run).toHaveBeenCalledTimes(1);
    expect(wrapper.get('[data-testid="submit-button"]').attributes('disabled')).toBeDefined();

    await wrapper.get('[data-testid="submit-button"]').trigger('click');
    await flushPromises();
    expect(createSubmission).toHaveBeenCalledTimes(1);

    releaseCreate?.({
      id: 5,
      userId: 7,
      problemId: 476,
      questId: null,
      source: 'TRAINING',
      language: 'PYTHON_3_11',
      code: 'print(1)',
      submittedAt: '2026-05-21T19:00:00Z',
      status: 'FAILED',
      runtimeMs: 12,
      memoryKb: null,
    });
    setPyodideRunner(null);
  });

  it('does not translate a submission failure as a Python runtime error hint', async () => {
    vi.mocked(createSubmission).mockRejectedValue(
      new Error("NameError: name 'total' is not defined"),
    );

    const wrapper = mountPanel();
    const learning = useLearningStore();
    await learning.loadTrainingProblem(476);
    learning.setTrainingCode('print(1)');
    await flushPromises();

    await wrapper.get('[data-testid="submit-button"]').trigger('click');
    await flushPromises();

    // 提交失败的报错不属于本地运行轨道，不得被翻译成 Output 区的 Python 运行提示。
    expect(wrapper.find('[data-testid="py-error-hint"]').exists()).toBe(false);
  });

  it('announces AC without quoting a hardcoded mastery delta', async () => {
    vi.mocked(createSubmission).mockResolvedValue({
      id: 41,
      userId: 7,
      problemId: 476,
      questId: null,
      source: 'TRAINING',
      language: 'PYTHON_3_11',
      code: 'print(1)',
      submittedAt: '2026-05-21T19:00:00Z',
      status: 'PASSED',
      runtimeMs: 12,
      memoryKb: null,
    });
    vi.mocked(getSubmission).mockResolvedValue({
      submission: {
        id: 41,
        userId: 7,
        problemId: 476,
        questId: null,
        source: 'TRAINING',
        language: 'PYTHON_3_11',
        code: 'print(1)',
        submittedAt: '2026-05-21T19:00:00Z',
        status: 'PASSED',
        runtimeMs: 12,
        memoryKb: null,
      },
      judgeResult: {
        id: 51,
        submissionId: 41,
        verdict: 'AC',
        passedCases: 3,
        totalCases: 3,
        failureReason: null,
        trace: {},
        createdAt: '2026-05-21T19:00:00Z',
      },
    });

    const wrapper = mountPanel();
    const learning = useLearningStore();
    const toast = useToastStore();
    await learning.loadTrainingProblem(476);
    learning.setTrainingCode('print(1)');
    saveCodeDraft(476, 'print(1)');
    await flushPromises();

    await wrapper.get('[data-testid="submit-button"]').trigger('click');
    await flushPromises();

    // 后端掌握度增量按事件 × 难度系数（0.14/0.20/0.30），前端不复述数字。
    expect(toast.message).not.toContain('+0.10');
    expect(toast.message).toContain('掌握进度');
    // AC 后清掉本题草稿（此前 clearCodeDraft 从未被调用）。
    expect(loadCodeDraft(476)).toBeNull();
  });

  it('uses a CodeMirror 6 editor surface for Python code', async () => {
    const wrapper = mountPanel();
    const learning = useLearningStore();
    await learning.loadTrainingProblem(476);
    await flushPromises();

    const editor = wrapper.get('[data-testid="code-editor"]');
    expect(editor.find('.cm-editor').exists()).toBe(true);
    expect(editor.find('textarea').exists()).toBe(false);
  });
});
