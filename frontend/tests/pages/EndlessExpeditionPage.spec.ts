/* eslint-disable vue/one-component-per-file -- local stubs isolate one page integration test. */
import { defineComponent, nextTick, reactive } from 'vue';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  run: {
    phase: 'map',
    nodes: [] as Array<Record<string, unknown>>,
    nodesByLayer: [] as Array<Array<Record<string, unknown>>>,
    layers: 1,
    currentNodeId: null as string | null,
    activeNode: null as Record<string, unknown> | null,
    activeProblem: null as Record<string, unknown> | null,
    activeDifficulty: 0,
    activeBossPhase: 2,
    bossPhasesTotal: 3,
    hp: 4,
    maxHp: 5,
    score: 0,
    momentum: 1,
    verifiedScore: 0,
    relics: [] as string[],
    elapsedSec: 0,
    challengeResult: null as Record<string, unknown> | null,
    settlementPending: false,
    loading: false,
    errorMessage: '',
    activeCode: '',
    dailyAvailable: true,
    loadLeaderboard: vi.fn(),
    tickSecond: vi.fn(),
    refreshSavedRun: vi.fn(),
    startRun: vi.fn(),
    startDailyRun: vi.fn(),
    enterNode: vi.fn(),
    submitChallenge: vi.fn(),
    retryChallenge: vi.fn(),
    continueAfterChallenge: vi.fn(),
    finishRun: vi.fn(),
    chooseEvent: vi.fn(),
    chooseReward: vi.fn(),
    saveCheckpoint: vi.fn(),
    abort: vi.fn(),
    leaderboard: null,
  },
  learning: { field: { masterySnapshot: [] as Array<Record<string, unknown>> } },
}));

vi.mock('@stores/rogueRun', async () => {
  const { reactive } = await import('vue');
  return { useRogueRunStore: () => reactive(mocks.run) };
});
vi.mock('@stores/learning', async () => {
  const { reactive } = await import('vue');
  return { useLearningStore: () => reactive(mocks.learning) };
});

const runState = reactive(mocks.run);
import EndlessExpeditionPage from '@/pages/EndlessExpeditionPage.vue';

const CANVAS_STUB = defineComponent({
  name: 'ExpeditionCanvas',
  props: {
    mode: { type: String, default: 'tower' },
    boss: { type: Object, default: null },
  },
  template: '<div data-testid="expedition-canvas" :data-mode="mode" :data-boss-status="boss?.status" :data-boss-hp="boss?.hp" :data-boss-phase="boss?.phase" :data-boss-attempt="boss?.attemptId" />',
});
const CHALLENGE_STUB = defineComponent({
  name: 'ChallengeWorkspace',
  template: '<div data-testid="challenge-workspace"><slot name="actions" /></div>',
});
const LAYOUT_STUB = defineComponent({
  name: 'AppLayout',
  template: '<main><slot /></main>',
});

function bossNode(): Record<string, unknown> {
  return {
    id: 'boss-1',
    kind: 'boss',
    kcCode: 'PY43-KC-031',
    challengeKind: 'multi_round',
    difficulty: 4,
    state: 'current',
    layer: 1,
    col: 0,
    next: [],
  };
}

function problem(): Record<string, unknown> {
  return {
    problemId: 'problem-boss-1',
    chapter: 4,
    stage: 2,
    difficulty: 4,
    kcCode: 'PY43-KC-031',
    statement: '编写一个 while 循环。',
    hints: [],
    sampleInput: '',
    sampleOutput: '',
    totalTestCases: 2,
    reviewed: true,
    aiGenerated: true,
  };
}

describe('EndlessExpeditionPage Boss scene integration', () => {
  afterEach(() => {
    vi.clearAllMocks();
    mocks.run.phase = 'map';
    mocks.run.nodes = [];
    mocks.run.nodesByLayer = [];
    mocks.run.activeNode = null;
    mocks.run.activeProblem = null;
    mocks.run.challengeResult = null;
    mocks.run.settlementPending = false;
    mocks.run.loading = false;
  });

  it('keeps the same stage beside the challenge and exposes settlement plus real HP', async () => {
    runState.nodes = [bossNode()];
    runState.nodesByLayer = [[bossNode()]];
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/expedition', component: EndlessExpeditionPage }],
    });
    await router.push('/expedition');
    await router.isReady();

    const wrapper = mount(EndlessExpeditionPage, {
      global: {
        plugins: [router],
        stubs: {
          AppLayout: LAYOUT_STUB,
          BossSprite: true,
          ChallengeWorkspace: CHALLENGE_STUB,
          ExpeditionCanvas: CANVAS_STUB,
          ExpeditionLeaderboard: true,
          KnowledgeConstellation: true,
        },
      },
    });
    await flushPromises();
    const canvas = wrapper.get('[data-testid="expedition-canvas"]');

    expect(canvas.attributes('data-mode')).toBe('tower');
    expect(wrapper.findAll('[data-testid="expedition-canvas"]')).toHaveLength(1);

    runState.phase = 'challenge';
    runState.activeNode = bossNode();
    runState.activeProblem = problem();
    await nextTick();

    expect(wrapper.findAll('[data-testid="expedition-canvas"]')).toHaveLength(1);
    expect(wrapper.get('[data-testid="expedition-canvas"]').attributes('data-mode')).toBe('boss');
    expect(wrapper.get('[data-testid="expedition-canvas"]').attributes('data-boss-phase')).toBe('2');
    expect(wrapper.get('[data-testid="expedition-challenge"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="challenge-workspace"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="expedition-submit"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="expedition-canvas"]').attributes('data-boss-status')).toBe('ready');

    runState.loading = true;
    await nextTick();
    expect(wrapper.get('[data-testid="expedition-canvas"]').attributes('data-boss-status')).toBe('judging');

    runState.challengeResult = {
      problemId: 'problem-boss-1',
      passed: true,
      passedTestCases: 2,
      totalTestCases: 2,
      score: 100,
      feedback: '通过',
    };
    runState.settlementPending = true;
    runState.loading = true;
    runState.hp = 3;
    await nextTick();
    expect(wrapper.get('[data-testid="expedition-canvas"]').attributes('data-boss-status')).toBe('settling');

    runState.settlementPending = false;
    runState.loading = false;
    await nextTick();
    expect(wrapper.get('[data-testid="expedition-canvas"]').attributes('data-boss-status')).toBe('passed');
    expect(wrapper.get('[data-testid="expedition-canvas"]').attributes('data-boss-attempt')).toBe('problem-boss-1:2');
    expect(wrapper.get('[data-testid="expedition-canvas"]').attributes('data-boss-hp')).toBe('3');

    runState.challengeResult = {
      problemId: 'problem-boss-2',
      passed: false,
      passedTestCases: 1,
      totalTestCases: 2,
      score: 50,
      feedback: '未通过',
    };
    runState.hp = 2;
    await nextTick();
    expect(wrapper.get('[data-testid="expedition-canvas"]').attributes('data-boss-status')).toBe('failed');
    expect(wrapper.get('[data-testid="expedition-canvas"]').attributes('data-boss-hp')).toBe('2');
    expect(wrapper.get('[data-testid="expedition-active-run"]').text()).not.toContain('Boss 受到命中');

    runState.activeNode = { ...bossNode(), kind: 'battle' };
    await nextTick();
    expect(wrapper.find('[data-testid="expedition-canvas"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="challenge-workspace"]').exists()).toBe(true);

    wrapper.unmount();
  });
});
