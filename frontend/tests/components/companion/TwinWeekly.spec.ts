import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import TwinWeekly from '@/components/companion/TwinWeekly.vue';
import type { WeeklyReport } from '@/api/twin';

const twinMocks = vi.hoisted(() => ({ fetchWeeklyReport: vi.fn() }));
vi.mock('@/api/twin', () => twinMocks);

const report: WeeklyReport = {
  periodStart: '2026-10-01',
  periodEnd: '2026-10-07',
  memoryBrief: {
    totalEvents: 0,
    failedPractices: 0,
    succeededPractices: 0,
    topKcCodes: [],
    highlights: [],
  },
  insights: [],
  masteryDeltas: [],
  resourceRuns: [],
};

describe('TwinWeekly', () => {
  beforeEach(() => {
    twinMocks.fetchWeeklyReport.mockReset();
  });

  it('retries a failed weekly report request', async () => {
    twinMocks.fetchWeeklyReport
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce(report);
    const wrapper = mount(TwinWeekly);
    await flushPromises();

    expect(wrapper.text()).toContain('暂时无法加载周报');
    await wrapper.find('[data-testid="twin-weekly-retry"]').trigger('click');
    await flushPromises();

    expect(twinMocks.fetchWeeklyReport).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('Learner Twin');
  });

  it('shows recent failed-practice summaries from the weekly report', async () => {
    twinMocks.fetchWeeklyReport.mockResolvedValue({
      ...report,
      memoryBrief: {
        ...report.memoryBrief,
        failedPractices: 1,
        highlights: ['循环边界条件练习未通过'],
      },
    });

    const wrapper = mount(TwinWeekly);
    await flushPromises();

    expect(wrapper.get('[data-testid="twin-weekly-highlights"]').text()).toContain(
      '循环边界条件练习未通过',
    );
  });

  it('labels mastery deltas in percentage points', async () => {
    twinMocks.fetchWeeklyReport.mockResolvedValue({
      ...report,
      masteryDeltas: [
        { kcCode: 'KC-001', currentScore: 0.62, confidence: 0.8, weeklyDelta: 0.12, evidenceCount: 2, evidenceSources: ['PRACTICE_PASS'] },
        { kcCode: 'KC-002', currentScore: 0.38, confidence: 0.7, weeklyDelta: -0.08, evidenceCount: 1, evidenceSources: ['PRACTICE_FAIL'] },
        { kcCode: 'KC-003', currentScore: null, confidence: null, weeklyDelta: null, evidenceCount: 0, evidenceSources: [] },
      ],
    });

    const wrapper = mount(TwinWeekly);
    await flushPromises();

    expect(wrapper.text()).toContain('本周 +12 个百分点');
    expect(wrapper.text()).toContain('本周 -8 个百分点');
    expect(wrapper.text()).toContain('本周 暂无可比数据');
  });
});
