import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import EndlessActivityTab from '@/components/teacher/EndlessActivityTab.vue';

const apiMocks = vi.hoisted(() => ({ fetchEndlessActivity: vi.fn() }));
vi.mock('@/api/teacher', () => ({ fetchEndlessActivity: apiMocks.fetchEndlessActivity }));

beforeEach(() => {
  apiMocks.fetchEndlessActivity.mockReset();
});
afterEach(() => {
  vi.restoreAllMocks();
});

function payload(accuracyPct: number | null) {
  return {
    periodStart: '2026-06-04',
    periodEnd: '2026-06-10',
    totalStudents: 5,
    activeStudentCount: 2,
    recoveryRoundTotal: 3,
    recoveryStarTotal: 3,
    accuracyPct,
    daily: [
      { day: '2026-06-09', recoveryRounds: 1, recoveryStars: 1 },
      { day: '2026-06-10', recoveryRounds: 2, recoveryStars: 2 },
    ],
    chapters: [
      { chapter: 1, recoveryRounds: 2, recoveryStars: 2, studentCount: 2 },
      { chapter: 2, recoveryRounds: 0, recoveryStars: 0, studentCount: 0 },
    ],
  };
}

describe('EndlessActivityTab', () => {
  it('按 classroomId 取数并渲染补星汇总 + 逐日柱 + 分章表', async () => {
    apiMocks.fetchEndlessActivity.mockResolvedValueOnce(payload(null));
    const wrapper = mount(EndlessActivityTab, { props: { classroomId: 9 } });
    await flushPromises();

    expect(apiMocks.fetchEndlessActivity).toHaveBeenCalledWith(9);
    expect(wrapper.find('[data-testid="endless-daybars"]').exists()).toBe(true);
    const chapters = wrapper.find('[data-testid="endless-chapters"]');
    expect(chapters.exists()).toBe(true);
    // 仅渲染有补星活动的章节（第 2 章 rounds=0 被过滤）。
    expect(chapters.text()).toContain('第 1 章');
    expect(chapters.text()).not.toContain('第 2 章');
    // accuracyPct=null → 显示「暂未采集」（§3.2 无无尽轮次明细表）。
    expect(wrapper.find('[data-testid="endless-accuracy"]').text()).toContain('暂未采集');
  });

  it('accuracyPct 有值时渲染百分比', async () => {
    apiMocks.fetchEndlessActivity.mockResolvedValueOnce(payload(0.82));
    const wrapper = mount(EndlessActivityTab, { props: { classroomId: 9 } });
    await flushPromises();
    expect(wrapper.find('[data-testid="endless-accuracy"]').text()).toContain('82.0%');
  });

  it('近 7 日无补星 → 分章空态', async () => {
    apiMocks.fetchEndlessActivity.mockResolvedValueOnce({
      ...payload(null),
      recoveryRoundTotal: 0,
      recoveryStarTotal: 0,
      activeStudentCount: 0,
      chapters: [{ chapter: 1, recoveryRounds: 0, recoveryStars: 0, studentCount: 0 }],
    });
    const wrapper = mount(EndlessActivityTab, { props: { classroomId: 9 } });
    await flushPromises();
    expect(wrapper.find('[data-testid="endless-empty"]').exists()).toBe(true);
  });

  it('接口失败 → 错误态文案', async () => {
    apiMocks.fetchEndlessActivity.mockRejectedValueOnce(new Error('无尽聚合异常'));
    const wrapper = mount(EndlessActivityTab, { props: { classroomId: 9 } });
    await flushPromises();
    expect(wrapper.find('[data-testid="endless-error"]').text()).toContain('无尽聚合异常');
  });
});
