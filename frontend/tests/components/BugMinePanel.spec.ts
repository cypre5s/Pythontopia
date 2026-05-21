import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import BugMinePanel from '@/components/bugmine/BugMinePanel.vue';
import { useLearningStore } from '@stores/learning';

vi.mock('@/api/bugMine', () => ({
  openBugMineSession: vi.fn(),
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

import {
  getBugMineSession,
  revealBugMineCard,
  retryBugMineSubmit,
} from '@/api/bugMine';
import { getTodayQuests } from '@/api/quests';
import type {
  BugMineCardDto,
  BugMineSessionDto,
} from '@/api/dto/BugMine';

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
    openedAt: '2026-05-21T19:50:00Z',
    closedAt: null,
    cards,
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
    expect(learning.bugMine.lastRetry?.status).toBe('PASSED');
  });
});
