import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ChapterTestsTab from '@/components/teacher/ChapterTestsTab.vue';

const apiMocks = vi.hoisted(() => ({ fetchChapterTests: vi.fn() }));
vi.mock('@/api/teacher', () => ({ fetchChapterTests: apiMocks.fetchChapterTests }));

beforeEach(() => {
  apiMocks.fetchChapterTests.mockReset();
});
afterEach(() => {
  vi.restoreAllMocks();
});

describe('ChapterTestsTab', () => {
  it('按 classroomId 取数并渲染通过率 + 分数箱线', async () => {
    apiMocks.fetchChapterTests.mockResolvedValueOnce({
      totalStudents: 4,
      chapters: [
        {
          chapter: 1,
          participantCount: 3,
          attemptCount: 4,
          passedCount: 2,
          passRate: 0.667,
          scoreDistribution: { count: 3, min: 50, q1: 60, median: 70, q3: 80, max: 90 },
        },
        {
          chapter: 2,
          participantCount: 0,
          attemptCount: 0,
          passedCount: 0,
          passRate: 0,
          scoreDistribution: { count: 0, min: null, q1: null, median: null, q3: null, max: null },
        },
      ],
    });
    const wrapper = mount(ChapterTestsTab, { props: { classroomId: 9 } });
    await flushPromises();

    expect(apiMocks.fetchChapterTests).toHaveBeenCalledWith(9);
    const row1 = wrapper.find('[data-testid="test-row-1"]');
    expect(row1.exists()).toBe(true);
    expect(row1.text()).toContain('66.7%');
    expect(row1.find('.boxplot').exists()).toBe(true);
    expect(row1.text()).toContain('中位 70');
    // 无提交章：显示占位、不渲染箱线。
    const row2 = wrapper.find('[data-testid="test-row-2"]');
    expect(row2.find('.boxplot').exists()).toBe(false);
    expect(row2.text()).toContain('本章暂无提交');
  });

  it('全章零提交 → 空态提示', async () => {
    apiMocks.fetchChapterTests.mockResolvedValueOnce({
      totalStudents: 4,
      chapters: [
        {
          chapter: 1,
          participantCount: 0,
          attemptCount: 0,
          passedCount: 0,
          passRate: 0,
          scoreDistribution: { count: 0, min: null, q1: null, median: null, q3: null, max: null },
        },
      ],
    });
    const wrapper = mount(ChapterTestsTab, { props: { classroomId: 9 } });
    await flushPromises();
    expect(wrapper.find('[data-testid="tests-empty"]').exists()).toBe(true);
  });

  it('接口失败 → 错误态文案', async () => {
    apiMocks.fetchChapterTests.mockRejectedValueOnce(new Error('章测聚合异常'));
    const wrapper = mount(ChapterTestsTab, { props: { classroomId: 9 } });
    await flushPromises();
    expect(wrapper.find('[data-testid="tests-error"]').text()).toContain('章测聚合异常');
  });
});
