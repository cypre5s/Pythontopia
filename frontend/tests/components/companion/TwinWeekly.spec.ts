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
});
