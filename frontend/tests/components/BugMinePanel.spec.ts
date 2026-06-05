import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import BugMinePanel from '@/components/bugmine/BugMinePanel.vue';
import { useLearningStore } from '@stores/learning';

vi.mock('@/api/bugMine', () => ({
  openBugMineSession: vi.fn(),
  getBugMineErrorBook: vi.fn(),
  getBugMineSession: vi.fn(),
  revealBugMineCard: vi.fn(),
  retryBugMineSubmit: vi.fn(),
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
  getBugMineSession,
  revealBugMineCard,
  retryBugMineSubmit,
} from '@/api/bugMine';
import { getTodayQuests } from '@/api/quests';
import { getPublicProblem } from '@/api/problems';
import type {
  BugMineCardDto,
  BugMineDepthStepDto,
  BugMineErrorBookItemDto,
  BugMineSessionDto,
} from '@/api/dto/BugMine';

function buildDepthSteps(): BugMineDepthStepDto[] {
  return [
    {
      layer: 1,
      key: 'NAKED_RETRY',
      title: 'L1 裸重试',
      description: '先独立改一次。',
      unlocked: true,
    },
    {
      layer: 2,
      key: 'COUNTER_EXAMPLE',
      title: 'L2 反例',
      description: '看一组打脸输入。',
      unlocked: true,
    },
    {
      layer: 3,
      key: 'VARIABLE_TRACE',
      title: 'L3 变量轨迹',
      description: '追踪关键变量。',
      unlocked: true,
    },
    {
      layer: 4,
      key: 'LAYERED_HINTS',
      title: 'L4 分层提示',
      description: '逐层拿提示。',
      unlocked: false,
    },
    {
      layer: 5,
      key: 'KC_VARIANT',
      title: 'L5 同 KC 变式',
      description: '确认能迁移。',
      unlocked: false,
    },
  ];
}

function buildSession(overrides: Partial<BugMineSessionDto> = {}): BugMineSessionDto {
  const cards: BugMineCardDto[] = [
    {
      id: 1,
      sessionId: 100,
      cardType: 'COUNTER_EXAMPLE',
      payload: {
        stdin: '5',
        expectedStdout: '78.5375',
        yourStdout: '5',
        diffSummary: '形状不符',
      },
      revealed: true,
      createdAt: '2026-05-21T19:50:00Z',
    },
    {
      id: 2,
      sessionId: 100,
      cardType: 'VARIABLE_TRACE',
      payload: { steps: [], truncated: false },
      revealed: true,
      createdAt: '2026-05-21T19:50:00Z',
    },
    {
      id: 3,
      sessionId: 100,
      cardType: 'WRONG_ASSUMPTION',
      payload: { youThought: '你以为...', actually: '实际上...' },
      revealed: true,
      createdAt: '2026-05-21T19:50:00Z',
    },
    {
      id: 4,
      sessionId: 100,
      cardType: 'HINT_LAYER_1',
      payload: { headline: 'L1', body: '先想想' },
      revealed: true,
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
    depthLayer: 3,
    openedAt: '2026-05-21T19:50:00Z',
    closedAt: null,
    variantProblem: null,
    cards,
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
    masteryScore: 0.42,
    mistakePatterns: ['变量覆盖', '输出格式'],
    patternMatched: true,
    openedAt: '2026-05-21T19:50:00Z',
    variantProblem: null,
    depthSteps: buildDepthSteps(),
    ...overrides,
  };
}

function mountPanel() {
  const router: Router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/bug-mine', name: 'bug-mine', component: { template: '<div />' } }],
  });
  return mount(BugMinePanel, {
    global: { plugins: [createPinia(), router] },
  });
}

describe('BugMinePanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.mocked(getBugMineErrorBook).mockResolvedValue([buildErrorBookItem()]);
    vi.mocked(getBugMineSession).mockResolvedValue(buildSession());
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

  it('loads the error book list, renders five depth steps, and opens a clicked session', async () => {
    const wrapper = mountPanel();
    await flushPromises();

    expect(getBugMineErrorBook).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain('错题矿洞');
    expect(wrapper.text()).toContain('圆面积调试');
    expect(wrapper.text()).toContain('python.variables');
    expect(wrapper.text()).toContain('L1 裸重试');
    expect(wrapper.text()).toContain('L2 反例');
    expect(wrapper.text()).toContain('L3 变量轨迹');
    expect(wrapper.text()).toContain('L4 分层提示');
    expect(wrapper.text()).toContain('L5 同 KC 变式');

    await wrapper.find('[data-testid="error-book-item-100"]').trigger('click');
    await flushPromises();

    expect(getBugMineSession).toHaveBeenCalledWith(100);
    expect(wrapper.text()).toContain('会话 #100');
  });

  it('renders 6 cards with layer 1 unlocked by default', async () => {
    const wrapper = mountPanel();
    const learning = useLearningStore();
    learning.bugMine.session = buildSession();
    await flushPromises();
    expect(wrapper.find('[data-testid="card-counter-example"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="card-trace"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="card-assumption"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="card-hint-1"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('Layer 2 （未解锁）');
    expect(wrapper.text()).toContain('Layer 3 （未解锁）');
  });

  it('reveals layer 2 when clicking unlock button', async () => {
    const wrapper = mountPanel();
    const learning = useLearningStore();
    learning.bugMine.session = buildSession();
    await flushPromises();
    const buttons = wrapper.findAll('button').filter((btn) => btn.text() === '解锁 Layer 2 提示');
    expect(buttons).toHaveLength(1);
    await buttons[0]!.trigger('click');
    await flushPromises();
    expect(revealBugMineCard).toHaveBeenCalledWith(100, 5);
    expect(learning.bugMine.session?.cards.find((card) => card.id === 5)?.revealed).toBe(true);
  });

  it('shows L5 variant action when depth reaches layer 5', async () => {
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
    learning.bugMine.session = buildSession({
      depthLayer: 5,
      variantProblem: { id: 501, title: '列表求和变式' },
    });
    await flushPromises();

    expect(wrapper.find('[data-testid="card-kc-variant"]').exists()).toBe(true);
    await wrapper.find('[data-testid="variant-problem-button"]').trigger('click');
    await flushPromises();
    expect(getPublicProblem).toHaveBeenCalledWith(501);
    expect(learning.training.problem?.title).toBe('列表求和变式');
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
    const textarea = wrapper.find('textarea');
    await textarea.setValue('print("hi")');
    await wrapper.find('[data-testid="retry-button"]').trigger('click');
    await flushPromises();
    expect(retryBugMineSubmit).toHaveBeenCalledWith(100, { code: 'print("hi")' });
    expect(getBugMineErrorBook).toHaveBeenCalled();
    expect(learning.bugMine.lastRetry?.status).toBe('PASSED');
  });
});
