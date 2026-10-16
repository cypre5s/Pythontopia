import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { LearnerTwinState } from '@/api/twin';
import ReviewHallPanel from '../ReviewHallPanel.vue';

const { fetchTwinState, routerPush } = vi.hoisted(() => ({
  fetchTwinState: vi.fn(),
  routerPush: vi.fn(),
}));

vi.mock('@/api/twin', () => ({ fetchLearnerTwinState: fetchTwinState }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: routerPush }) }));
vi.mock('@stores/learning', () => ({
  useLearningStore: () => ({
    evaluation: { list: [], current: null, loading: false, lastError: '' },
    pathState: { active: null },
    loadEvaluations: vi.fn(),
    openEvaluation: vi.fn(),
    runEvaluationNow: vi.fn(),
    acceptEvaluationPath: vi.fn(),
    keepCurrentEvaluationPath: vi.fn(),
  }),
}));
vi.mock('@stores/toast', () => ({ useToastStore: () => ({ show: vi.fn() }) }));

const state: LearnerTwinState = {
  assessedKcCount: 1,
  totalKcCount: 65,
  masteredKcCount: 0,
  growthStage: '启程练习',
  kcs: [{
    kcCode: 'PY43-KC-009', kcName: '循环', chapter: 2, chapterName: '流程控制', assessed: true,
    masteryScore: 0.42, confidence: 0.6, reviewDue: true, dueAt: '2026-10-13T00:00:00Z',
    lastPracticedAt: '2026-10-12T00:00:00Z', consecutiveFailures: 0,
    evidenceSources: ['PRACTICE_PASS'],
  }],
  nextSteps: [{
    kcCode: 'PY43-KC-009', kcName: '循环', chapter: 2, chapterName: '流程控制', difficulty: 2,
    action: 'REVIEW', reason: 'FSRS-lite 复习已到期', remediationRequired: false,
  }],
};

describe('ReviewHallPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fetchTwinState.mockResolvedValue(state);
  });

  it('uses the shared twin recommendation and opens its due review', async () => {
    const wrapper = mount(ReviewHallPanel, {
      global: {
        stubs: {
          PyButton: { props: ['testId'], template: '<button :data-testid="testId"><slot /></button>' },
          MetricCard: true,
          KcGrowthList: true,
          PathDiffView: true,
        },
      },
    });
    await flushPromises();

    expect(wrapper.get('[data-testid="review-twin-recommendation"]').text())
      .toContain('FSRS-lite 复习已到期');
    await wrapper.get('[data-testid="review-twin-next"]').trigger('click');

    expect(routerPush).toHaveBeenCalledWith({
      path: '/bug-mine',
      query: { review: 'PY43-KC-009' },
    });
  });

  it('routes an unassessed recommendation to the mainline', async () => {
    fetchTwinState.mockResolvedValue({
      ...state,
      assessedKcCount: 0,
      kcs: [{
        kcCode: 'PY43-KC-001', kcName: '计算思维', chapter: 1, chapterName: '计算思维',
        assessed: false, masteryScore: null, confidence: null, reviewDue: false, dueAt: null,
        lastPracticedAt: null, consecutiveFailures: 0,
        evidenceSources: [],
      }],
      nextSteps: [{
        kcCode: 'PY43-KC-001', kcName: '计算思维', chapter: 1, chapterName: '计算思维',
        difficulty: 1, action: 'PRACTICE', reason: '尚未评估，先从基础题建立证据',
        remediationRequired: false,
      }],
    });
    const wrapper = mount(ReviewHallPanel, {
      global: {
        stubs: {
          PyButton: { props: ['testId'], template: '<button :data-testid="testId"><slot /></button>' },
          MetricCard: true,
          KcGrowthList: true,
          PathDiffView: true,
        },
      },
    });
    await flushPromises();

    await wrapper.get('[data-testid="review-twin-next"]').trigger('click');

    expect(routerPush).toHaveBeenCalledWith({
      path: '/mainline-map',
      query: { chapter: '1', kcCode: 'PY43-KC-001' },
    });
  });
});
