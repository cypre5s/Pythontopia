import { describe, it, expect, beforeEach, vi } from 'vitest';
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
    setActivePinia(createPinia());
    window.localStorage.removeItem('pyt_main_quest_progress_v1');
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
