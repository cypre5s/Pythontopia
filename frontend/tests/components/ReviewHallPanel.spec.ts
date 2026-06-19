import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import ReviewHallPanel from '@/components/review/ReviewHallPanel.vue';
import { useLearningStore } from '@stores/learning';
import type { EvaluationDto } from '@/api/dto/Evaluation';

vi.mock('@/api/evaluation', () => ({
  listEvaluations: vi.fn(),
  getEvaluation: vi.fn(),
  runEvaluation: vi.fn(),
  acceptEvaluationPath: vi.fn(),
  keepOldEvaluationPath: vi.fn(),
}));

vi.mock('@/api/path', () => ({
  planLearningPath: vi.fn(),
  getActiveLearningPath: vi.fn().mockResolvedValue(null),
  getArchivedLearningPaths: vi.fn().mockResolvedValue([]),
}));

vi.mock('@/api/quests', () => ({
  getTodayQuests: vi.fn().mockResolvedValue([]),
  startQuest: vi.fn(),
  completeQuest: vi.fn(),
  skipQuest: vi.fn(),
}));

import {
  listEvaluations,
  getEvaluation,
  runEvaluation,
  acceptEvaluationPath,
  keepOldEvaluationPath,
} from '@/api/evaluation';

const SAMPLE: EvaluationDto = {
  id: 101,
  userId: 7,
  packId: 43,
  periodStart: '2026-06-06T00:00:00Z',
  periodEnd: '2026-06-13T00:00:00Z',
  narrativeSummary: '# 本周复盘\n- KC 总掌握度：3.21score\n- PY43-KC-001 进步明显',
  pathDiffSummary: '新路径会调整节奏',
  draftPathId: 999,
  activePathId: 888,
  createdAt: '2026-06-13T10:00:00Z',
  actedStatus: 'PENDING',
  metrics: [
    {
      metricKey: 'kc_mastery_total',
      numerator: 3.21,
      denominator: 0,
      value: 3.21,
      displayUnit: 'score',
      notes: 'demo',
    },
    {
      metricKey: 'practice_pass_rate',
      numerator: 3,
      denominator: 4,
      value: 0.75,
      displayUnit: 'ratio',
      notes: null,
    },
  ],
  draftPath: {
    id: 999,
    userId: 7,
    packId: 43,
    version: 4,
    status: 'DRAFT',
    goalSummary: 'draft demo',
    generatedAt: '2026-06-13T10:00:00Z',
    nextReviewAt: '2026-06-20T10:00:00Z',
    sourceRunId: null,
    nodes: [
      {
        id: 9001,
        pathId: 999,
        dayIndex: 1,
        orderInDay: 1,
        kcCode: 'PY43-KC-001',
        nodeType: 'READ_RESOURCE',
        reasonSummary: '复习',
        evidenceSummary: 'evidence',
        suggestedResourceType: 'HANDOUT',
        suggestedProblemId: null,
        sourceClassroomQuestId: null,
      },
    ],
  },
};

describe('ReviewHallPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.mocked(listEvaluations).mockResolvedValue([SAMPLE]);
    vi.mocked(getEvaluation).mockResolvedValue(SAMPLE);
    vi.mocked(runEvaluation).mockResolvedValue({ ...SAMPLE, id: 102 });
    vi.mocked(acceptEvaluationPath).mockResolvedValue({ ...SAMPLE, actedStatus: 'ACCEPTED' });
    vi.mocked(keepOldEvaluationPath).mockResolvedValue({ ...SAMPLE, actedStatus: 'KEPT_OLD' });
  });

  function mountWithSample() {
    const pinia = createPinia();
    setActivePinia(pinia);
    const learning = useLearningStore();
    learning.evaluation.list = [SAMPLE];
    learning.evaluation.current = SAMPLE;
    return mount(ReviewHallPanel, { global: { plugins: [pinia] } });
  }

  // 新 UX：详情改为点击周报某一天的行后弹窗展示，先打开弹窗再断言。
  async function openDetail(wrapper: ReturnType<typeof mountWithSample>) {
    await wrapper.find('[data-testid="eval-row"]').trigger('click');
    await flushPromises();
    await wrapper.vm.$nextTick();
  }

  it('shows the weekly report row and opens metrics modal on click', async () => {
    const wrapper = mountWithSample();
    await flushPromises();
    await wrapper.vm.$nextTick();

    expect(wrapper.find('[data-testid="review-hall-panel"]').exists()).toBe(true);
    // 点击前弹窗不展示
    expect(wrapper.find('[data-testid="evaluation-modal"]').exists()).toBe(false);

    await openDetail(wrapper);

    expect(wrapper.find('[data-testid="evaluation-modal"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('KC 总掌握');
    expect(wrapper.text()).toContain('练习通过率');
  });

  it('accept-new-path triggers acceptEvaluationPath API', async () => {
    const wrapper = mountWithSample();
    await flushPromises();
    await openDetail(wrapper);
    await wrapper.find('[data-testid="accept-new-path"]').trigger('click');
    expect(acceptEvaluationPath).toHaveBeenCalledWith(101);
  });

  it('keep-old triggers keepOldEvaluationPath API', async () => {
    const wrapper = mountWithSample();
    await flushPromises();
    await openDetail(wrapper);
    await wrapper.find('[data-testid="keep-old"]').trigger('click');
    expect(keepOldEvaluationPath).toHaveBeenCalledWith(101);
  });

  it('runs a new evaluation when 立即评估 is clicked', async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const wrapper = mount(ReviewHallPanel, {
      global: { plugins: [pinia] },
    });
    await flushPromises();
    await wrapper.find('[data-testid="run-evaluation"]').trigger('click');
    await flushPromises();
    expect(runEvaluation).toHaveBeenCalledWith({ periodDays: 7 });
  });
});
