import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { LearnerTwinState, WeeklyReport } from '@/api/twin';
import ProfileRoomPage from '../ProfileRoomPage.vue';

const { fetchTwinState, fetchWeeklyReport, routerPush, setPendingPrompt } = vi.hoisted(() => ({
  fetchTwinState: vi.fn(),
  fetchWeeklyReport: vi.fn(),
  routerPush: vi.fn(),
  setPendingPrompt: vi.fn(),
}));

vi.mock('@/api/twin', () => ({ fetchLearnerTwinState: fetchTwinState, fetchWeeklyReport }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: routerPush }) }));
vi.mock('@/stores/learning', () => ({
  useLearningStore: () => ({ profileInterview: { sessionId: 'interview-1' }, openInterview: vi.fn() }),
}));
vi.mock('@/stores/session', () => ({ useSessionStore: () => ({ displayName: 'Ada' }) }));
vi.mock('@/stores/workshopUi', () => ({ useWorkshopUiStore: () => ({ setPendingPrompt }) }));

const state: LearnerTwinState = {
  assessedKcCount: 2,
  totalKcCount: 65,
  masteredKcCount: 0,
  growthStage: 'Python 见习学徒',
  kcs: [
    {
      kcCode: 'PY43-KC-001', kcName: '变量', chapter: 1, chapterName: '基础', assessed: true,
      masteryScore: 0.4, confidence: 0.5, reviewDue: true, dueAt: '2026-10-13T00:00:00Z',
      lastPracticedAt: '2026-10-12T00:00:00Z', consecutiveFailures: 0,
      evidenceSources: ['PRACTICE_PASS'],
    },
    {
      kcCode: 'PY43-KC-002', kcName: '表达式', chapter: 2, chapterName: '运算', assessed: true,
      masteryScore: 0.5, confidence: 0.5, reviewDue: false, dueAt: null,
      lastPracticedAt: '2026-10-12T00:00:00Z', consecutiveFailures: 2,
      evidenceSources: ['PRACTICE_FAIL'],
    },
  ],
  nextSteps: [
    {
      kcCode: 'PY43-KC-001', kcName: '变量', chapter: 1, chapterName: '基础', difficulty: 2,
      action: 'REVIEW', reason: '复习已到期', remediationRequired: false,
    },
    {
      kcCode: 'PY43-KC-002', kcName: '表达式', chapter: 2, chapterName: '运算', difficulty: 2,
      action: 'REMEDIATION', reason: '连续练习未通过', remediationRequired: true,
    },
  ],
};

const weeklyReport: WeeklyReport = {
  periodStart: '2026-10-06',
  periodEnd: '2026-10-13',
  memoryBrief: { totalEvents: 2, failedPractices: 1, succeededPractices: 1, topKcCodes: ['PY43-KC-002'], highlights: [] },
  insights: [{ id: 1, text: '巩固表达式', dimensionKey: 'error_pattern', tags: [], createdAt: '2026-10-12T00:00:00Z' }],
  masteryDeltas: [{
    kcCode: 'PY43-KC-002', currentScore: 0.5, confidence: 0.6, weeklyDelta: null,
    evidenceCount: 2, evidenceSources: ['PRACTICE_FAIL'],
  }],
  resourceRuns: [],
};

function mountPage() {
  return mount(ProfileRoomPage, {
    global: {
      stubs: {
        AppLayout: { template: '<div><slot /></div>' },
        ProfileInterviewPanel: true,
        AgentNotebook: true,
        KnowledgeConstellation: true,
      },
    },
  });
}

describe('ProfileRoomPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fetchTwinState.mockResolvedValue(state);
    fetchWeeklyReport.mockResolvedValue(weeklyReport);
  });

  it('routes both server recommendations to their real learning actions', async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('[data-testid="twin-next-primary"]').trigger('click');
    await wrapper.get('[data-testid="twin-next-alternative"]').trigger('click');

    expect(routerPush).toHaveBeenNthCalledWith(1, { path: '/bug-mine', query: { review: 'PY43-KC-001' } });
    expect(routerPush).toHaveBeenNthCalledWith(2, { path: '/expedition', query: { focus: '2', kcCode: 'PY43-KC-002' } });
  });

  it('shows the verified evidence sources for each assessed knowledge point', async () => {
    fetchTwinState.mockResolvedValueOnce({
      ...state,
      kcs: [{
        ...state.kcs[0],
        evidenceSources: ['PRACTICE_PASS', 'MICRO_CHECK_FAIL'],
      }],
    } as LearnerTwinState);

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.get('[data-testid="twin-evidence-list"]').text())
      .toContain('依据：练习通过、小测未过');
  });

  it('routes an assessed chapter-one recommendation back to the mainline map', async () => {
    fetchTwinState.mockResolvedValueOnce({
      ...state,
      kcs: [{
        kcCode: 'PY43-KC-001', kcName: '变量', chapter: 1, chapterName: '基础', assessed: true,
        masteryScore: 0.4, confidence: 0.5, reviewDue: false, dueAt: null,
        lastPracticedAt: '2026-10-12T00:00:00Z', consecutiveFailures: 0,
        evidenceSources: ['PRACTICE_PASS'],
      }],
      nextSteps: [{
        kcCode: 'PY43-KC-001', kcName: '变量', chapter: 1, chapterName: '基础', difficulty: 2,
        action: 'PRACTICE', reason: '继续巩固', remediationRequired: false,
      }],
    });

    const wrapper = mountPage();
    await flushPromises();
    await wrapper.get('[data-testid="twin-next-primary"]').trigger('click');

    expect(wrapper.get('[data-testid="twin-next-primary"]').text()).toContain('前往主线巩固');
    expect(routerPush).toHaveBeenCalledWith({
      path: '/mainline-map',
      query: { chapter: '1', kcCode: 'PY43-KC-001' },
    });
  });

  it('routes an unassessed recommendation to the mainline map', async () => {
    fetchTwinState.mockResolvedValueOnce({
      ...state,
      kcs: [{
        kcCode: 'PY43-KC-003', kcName: '循环', chapter: 1, chapterName: '基础', assessed: false,
        masteryScore: null, confidence: null, reviewDue: false, dueAt: null,
        lastPracticedAt: null, consecutiveFailures: 0, evidenceSources: [],
      }],
      nextSteps: [{
        kcCode: 'PY43-KC-003', kcName: '循环', chapter: 1, chapterName: '基础', difficulty: 1,
        action: 'PRACTICE', reason: '尚未评估，先从基础题建立证据', remediationRequired: false,
      }],
    });
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('[data-testid="twin-next-primary"]').trigger('click');

    expect(routerPush).toHaveBeenCalledWith({
      path: '/mainline-map',
      query: { chapter: '1', kcCode: 'PY43-KC-003' },
    });
  });

  it('shows evidence sufficiency separately from mastery on assessed knowledge points', async () => {
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.get('[data-testid="twin-evidence-list"]').text()).toContain('证据充分度 50%');
  });

  it('hands weekly insight prompts to the workshop and navigates there', async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('[data-testid="twin-insight-prompt-1"]').trigger('click');

    expect(setPendingPrompt).toHaveBeenCalledWith(expect.stringContaining('巩固表达式'));
    expect(routerPush).toHaveBeenCalledWith('/workshop');
  });

  it('routes the weekly mastery reinforce action to the workshop with its KC context', async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('[data-testid="twin-mastery-prompt-PY43-KC-002"]').trigger('click');

    expect(setPendingPrompt).toHaveBeenCalledWith(expect.stringContaining('PY43-KC-002'));
    expect(setPendingPrompt).toHaveBeenCalledWith(expect.stringContaining('2 条证据'));
    expect(routerPush).toHaveBeenCalledWith('/workshop');
  });
});
