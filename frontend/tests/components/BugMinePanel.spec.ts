import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import BugMinePanel from '@/components/bugmine/BugMinePanel.vue';
import { useLearningStore } from '@stores/learning';

vi.mock('@/api/bugMine', () => ({
  openBugMineSession: vi.fn(),
  getBugMineErrorBook: vi.fn(),
  getBugMineEchoes: vi.fn(),
  openBugMineEcho: vi.fn(),
  getBugMineSession: vi.fn(),
  revealBugMineCard: vi.fn(),
  retryBugMineSubmit: vi.fn(),
  variantBugMineSubmit: vi.fn(),
  closeBugMineSession: vi.fn(),
}));
vi.mock('@/api/quests', () => ({
  getTodayQuests: vi.fn(),
  listQuests: vi.fn(),
  startQuest: vi.fn(),
  completeQuest: vi.fn(),
  skipQuest: vi.fn(),
}));
vi.mock('@/api/problems', () => ({
  getPublicProblem: vi.fn(),
}));

import {
  getBugMineErrorBook,
  getBugMineEchoes,
  getBugMineSession,
  openBugMineEcho,
  revealBugMineCard,
  retryBugMineSubmit,
} from '@/api/bugMine';
import { getTodayQuests } from '@/api/quests';
import { getPublicProblem } from '@/api/problems';
import type {
  BugMineCardDto,
  BugMineDepthStepDto,
  BugMineEchoCandidateDto,
  BugMineErrorBookItemDto,
  BugMineSessionDto,
} from '@/api/dto/BugMine';

function buildDepthSteps(depthLayer = 3): BugMineDepthStepDto[] {
  return [1, 2, 3, 4, 5].map((layer) => ({
    layer,
    key: `L${layer}`,
    title: `L${layer}`,
    description: `step-${layer}`,
    unlocked: layer <= depthLayer,
  }));
}

function buildSession(overrides: Partial<BugMineSessionDto> = {}): BugMineSessionDto {
  const depthLayer = overrides.depthLayer ?? 3;
  const cards: BugMineCardDto[] = [
    {
      id: 1,
      sessionId: 100,
      cardType: 'COUNTER_EXAMPLE',
      payload: { stdin: '5', expectedStdout: '78.5375', yourStdout: '5', diffSummary: '形状不符' },
      revealed: false,
      createdAt: '2026-05-21T19:50:00Z',
    },
    {
      id: 2,
      sessionId: 100,
      cardType: 'VARIABLE_TRACE',
      payload: { steps: [], truncated: false },
      revealed: false,
      createdAt: '2026-05-21T19:50:00Z',
    },
    {
      id: 3,
      sessionId: 100,
      cardType: 'WRONG_ASSUMPTION',
      payload: { youThought: '你以为...', actually: '实际上...' },
      revealed: false,
      createdAt: '2026-05-21T19:50:00Z',
    },
    {
      id: 4,
      sessionId: 100,
      cardType: 'HINT_LAYER_1',
      payload: { headline: 'L1', body: '先想想' },
      revealed: false,
      createdAt: '2026-05-21T19:50:00Z',
    },
    {
      id: 5,
      sessionId: 100,
      cardType: 'HINT_LAYER_2',
      payload: { headline: 'L2', body: '关注第 2 行' },
      revealed: false,
      createdAt: '2026-05-21T19:50:00Z',
    },
    {
      id: 6,
      sessionId: 100,
      cardType: 'HINT_LAYER_3',
      payload: { headline: 'L3', body: '思路提示' },
      revealed: false,
      createdAt: '2026-05-21T19:50:00Z',
    },
  ];
  return {
    id: 100,
    userId: 7,
    submissionId: 200,
    problemId: 476,
    questId: null,
    status: 'OPEN',
    retryCount: 0,
    depthLayer,
    profileDepthTarget: 4,
    masteryConfirmed: false,
    variantSubmissionId: null,
    openedAt: '2026-05-21T19:50:00Z',
    closedAt: null,
    variantProblem: null,
    profileGuidance: '画像命中易错模式，建议逐层下探。',
    depthSteps: buildDepthSteps(depthLayer),
    cards,
    problem: null,
    ...overrides,
  };
}

function buildErrorBookItem(
  overrides: Partial<BugMineErrorBookItemDto> = {},
): BugMineErrorBookItemDto {
  return {
    sessionId: 100,
    submissionId: 200,
    problemId: 476,
    questId: null,
    kcCode: 'python.variables',
    problemTitle: '圆面积调试',
    status: 'OPEN',
    retryCount: 2,
    depthLayer: 3,
    profileDepthTarget: 4,
    profileGuidance: '掌握度中等，先 L1 独立重试。',
    masteryScore: 0.42,
    minePriority: 0.68,
    decayRisk: 0.72,
    recommendationReason: '遗忘回声 · 置信度低 · 建议目标 L4',
    mistakePatterns: ['变量覆盖', '输出格式'],
    patternMatched: true,
    openedAt: '2026-05-21T19:50:00Z',
    variantProblem: null,
    depthSteps: buildDepthSteps(3),
    ...overrides,
  };
}

function buildEchoCandidate(
  overrides: Partial<BugMineEchoCandidateDto> = {},
): BugMineEchoCandidateDto {
  return {
    kcCode: 'PY43-KC-016',
    problemId: 476,
    problemTitle: '圆面积调试',
    masteryScore: 0.72,
    confidence: 0.25,
    decayRisk: 0.86,
    minePriority: 0.71,
    recommendationReason: '遗忘回声 · 置信度低 · 建议入矿',
    ...overrides,
  };
}

function mountPanel() {
  const router: Router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/bug-mine', name: 'bug-mine', component: { template: '<div />' } },
      { path: '/training', name: 'training', component: { template: '<div />' } },
    ],
  });
  return mount(BugMinePanel, {
    global: { plugins: [createPinia(), router] },
  });
}

describe('BugMinePanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    vi.mocked(getBugMineErrorBook).mockResolvedValue([buildErrorBookItem()]);
    vi.mocked(getBugMineEchoes).mockResolvedValue([]);
    vi.mocked(openBugMineEcho).mockResolvedValue(buildSession({
      profileGuidance: '遗忘回声：久未复习，建议下探到 L4 再确认。',
    }));
    vi.mocked(getBugMineSession).mockImplementation(async () => buildSession({ depthLayer: 4 }));
    vi.mocked(revealBugMineCard).mockResolvedValue({
      id: 5,
      sessionId: 100,
      cardType: 'HINT_LAYER_2',
      payload: { headline: 'L2', body: '关注第 2 行' },
      revealed: true,
      createdAt: '2026-05-21T19:50:00Z',
    });
    vi.mocked(getTodayQuests).mockResolvedValue([]);
  });

  it('loads error book, shows profile guidance, and opens clicked session', async () => {
    const wrapper = mountPanel();
    await flushPromises();

    expect(getBugMineErrorBook).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain('错题矿洞');
    expect(wrapper.text()).toContain('圆面积调试');
    expect(wrapper.text()).toContain('遗忘回声');

    await wrapper.find('[data-testid="error-book-item-100"]').trigger('click');
    await flushPromises();

    expect(getBugMineSession).toHaveBeenCalledWith(100);
    expect(wrapper.text()).toContain('会话 #100');
    expect(wrapper.text()).toContain('画像命中易错模式');
  });

  it('shows compact recommendation reason without depth microcopy in the error book list', async () => {
    vi.mocked(getBugMineErrorBook).mockResolvedValue([
      buildErrorBookItem({
        recommendationReason: '遗忘回声 · 置信度低 · 建议目标 L4',
        depthSteps: buildDepthSteps(4),
      }),
    ]);

    const wrapper = mountPanel();
    await flushPromises();

    const item = wrapper.find('[data-testid="error-book-item-100"]');
    expect(item.text()).toContain('遗忘回声');
    expect(item.text()).toContain('推荐 68%');
    expect(wrapper.find('[data-testid="depth-steps-100"]').text()).not.toContain('step-1');
  });

  it('shows forgetting echoes as compact mine entries and opens one', async () => {
    vi.mocked(getBugMineEchoes).mockResolvedValue([buildEchoCandidate()]);

    const wrapper = mountPanel();
    await flushPromises();

    expect(getBugMineEchoes).toHaveBeenCalledTimes(1);
    expect(wrapper.find('[data-testid="echo-board"]').text()).toContain('遗忘回声');
    expect(wrapper.find('[data-testid="echo-candidate-PY43-KC-016"]').text()).toContain('推荐 71%');

    await wrapper.find('[data-testid="open-echo-PY43-KC-016"]').trigger('click');
    await flushPromises();

    expect(openBugMineEcho).toHaveBeenCalledWith({ kcCode: 'PY43-KC-016' });
    expect(wrapper.text()).toContain('会话 #100');
    expect(wrapper.text()).toContain('久未复习');
  });

  it('omits locked layers from the diagnostic dropdown (depth gating)', async () => {
    const wrapper = mountPanel();
    const learning = useLearningStore();
    learning.bugMine.session = buildSession({ depthLayer: 2 });
    await flushPromises();

    // depthLayer=2 → 诊断层下拉只列已解锁的 L1/L2，锁定的 L4 不可选、内容也不渲染。
    const options = wrapper.find('[data-testid="layer-select"]').findAll('option');
    expect(options).toHaveLength(2);
    expect(options.map((o) => o.text()).join(' ')).not.toContain('L4');
    expect(wrapper.find('[data-testid="card-assumption"]').exists()).toBe(false);
    // 默认停在系统判定的当前层（L2），展示反例卡。
    expect(wrapper.find('[data-testid="card-counter-example"]').exists()).toBe(true);
  });

  it('switches the visible diagnostic layer via the dropdown (unlocked only)', async () => {
    const wrapper = mountPanel();
    const learning = useLearningStore();
    learning.bugMine.session = buildSession({ depthLayer: 4 });
    await flushPromises();

    // 默认停在当前系统判定层 L4（分层提示）。
    expect(wrapper.find('[data-testid="card-assumption"]').exists()).toBe(true);
    // 下拉切回已解锁的 L2 → 显示反例卡、隐藏 L4 内容。
    await wrapper.find('[data-testid="layer-select"]').setValue('2');
    expect(wrapper.find('[data-testid="card-counter-example"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="card-assumption"]').exists()).toBe(false);
    // 作答框（原题重试）始终常驻。
    expect(wrapper.find('[data-testid="retry-area"]').exists()).toBe(true);
  });

  it('reveals hint layer 2 when depth allows and button clicked', async () => {
    const wrapper = mountPanel();
    const learning = useLearningStore();
    learning.bugMine.session = buildSession({ depthLayer: 4 });
    await flushPromises();

    const buttons = wrapper.findAll('button').filter((btn) => btn.text() === '解锁 Layer 2 提示');
    expect(buttons).toHaveLength(1);
    await buttons[0]!.trigger('click');
    await flushPromises();
    expect(revealBugMineCard).toHaveBeenCalledWith(100, 5);
    expect(getBugMineSession).toHaveBeenCalled();
  });

  it('navigates to training for L5 variant mastery confirmation', async () => {
    vi.mocked(getPublicProblem).mockResolvedValue({
      id: 501,
      packId: 43,
      kcCode: 'python.variables',
      title: '列表求和变式',
      statementMarkdown: '求和',
      inputDescription: '',
      outputDescription: '',
      difficulty: 'EASY',
      samples: [],
    });
    const wrapper = mountPanel();
    const learning = useLearningStore();
    const router = wrapper.vm.$router;
    const pushSpy = vi.spyOn(router, 'push');
    learning.bugMine.session = buildSession({
      depthLayer: 5,
      variantProblem: { id: 501, title: '列表求和变式' },
    });
    await flushPromises();

    await wrapper.find('[data-testid="variant-problem-button"]').trigger('click');
    await flushPromises();
    expect(getPublicProblem).toHaveBeenCalledWith(501);
    // S1：变式会话私有 id 改走 store（pendingVariantSessionId），不写进 URL（bugMineSessionId 已移除）。
    expect(learning.bugMine.pendingVariantSessionId).toBe(100);
    expect(pushSpy).toHaveBeenCalledWith({
      path: '/training',
      query: {
        problemId: '501',
        variant: '1',
      },
    });
  });

  it('retry button posts new code and refreshes session', async () => {
    vi.mocked(retryBugMineSubmit).mockResolvedValue({
      id: 999,
      userId: 7,
      problemId: 476,
      questId: null,
      source: 'BUG_MINE_RETRY',
      language: 'PYTHON_3_11',
      code: 'print("hi")',
      submittedAt: '2026-05-21T20:00:00Z',
      status: 'PASSED',
      runtimeMs: 8,
      memoryKb: null,
    });
    const wrapper = mountPanel();
    const learning = useLearningStore();
    learning.bugMine.session = buildSession();
    await flushPromises();
    await wrapper.find('textarea').setValue('print("hi")');
    await wrapper.find('[data-testid="retry-button"]').trigger('click');
    await flushPromises();
    expect(retryBugMineSubmit).toHaveBeenCalledWith(100, { code: 'print("hi")' });
    expect(getBugMineErrorBook).toHaveBeenCalled();
    expect(learning.bugMine.lastRetry?.status).toBe('PASSED');
  });
});
