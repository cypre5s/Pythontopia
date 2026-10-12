import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import MainlineProgressTab from '@/components/teacher/MainlineProgressTab.vue';

const apiMocks = vi.hoisted(() => ({ fetchMainlineProgress: vi.fn() }));
vi.mock('@/api/teacher', () => ({ fetchMainlineProgress: apiMocks.fetchMainlineProgress }));

beforeEach(() => {
  apiMocks.fetchMainlineProgress.mockReset();
});
afterEach(() => {
  vi.restoreAllMocks();
});

function payload() {
  return {
    totalStudents: 2,
    totalStages: 7,
    chapterStageCounts: [
      { chapter: 1, stageCount: 3 },
      { chapter: 2, stageCount: 4 },
    ],
    members: [
      {
        studentId: 11,
        displayName: 'Stu A',
        completedStages: 3,
        totalStars: 5,
        chapters: [
          { chapter: 1, completedStages: 2, totalStages: 3 },
          { chapter: 2, completedStages: 1, totalStages: 4 },
        ],
      },
      {
        studentId: 12,
        displayName: 'Stu B',
        completedStages: 0,
        totalStars: 0,
        chapters: [
          { chapter: 1, completedStages: 0, totalStages: 3 },
          { chapter: 2, completedStages: 0, totalStages: 4 },
        ],
      },
    ],
  };
}

describe('MainlineProgressTab', () => {
  it('按 classroomId 取数并渲染成员 × 章节完成度矩阵', async () => {
    apiMocks.fetchMainlineProgress.mockResolvedValueOnce(payload());
    const wrapper = mount(MainlineProgressTab, { props: { classroomId: 9 } });
    await flushPromises();

    expect(apiMocks.fetchMainlineProgress).toHaveBeenCalledWith(9);
    expect(wrapper.find('[data-testid="progress-matrix"]').exists()).toBe(true);
    const rowA = wrapper.find('[data-testid="progress-member-11"]');
    expect(rowA.exists()).toBe(true);
    expect(rowA.text()).toContain('Stu A');
    expect(rowA.text()).toContain('2/3');
    expect(rowA.text()).toContain('1/4');
    expect(wrapper.find('[data-testid="progress-member-12"]').exists()).toBe(true);
  });

  it('无成员 → 空态', async () => {
    apiMocks.fetchMainlineProgress.mockResolvedValueOnce({
      totalStudents: 0,
      totalStages: 7,
      chapterStageCounts: [],
      members: [],
    });
    const wrapper = mount(MainlineProgressTab, { props: { classroomId: 9 } });
    await flushPromises();
    expect(wrapper.find('[data-testid="progress-empty"]').exists()).toBe(true);
  });

  it('接口失败 → 错误态文案', async () => {
    apiMocks.fetchMainlineProgress.mockRejectedValueOnce(new Error('进度聚合异常'));
    const wrapper = mount(MainlineProgressTab, { props: { classroomId: 9 } });
    await flushPromises();
    expect(wrapper.find('[data-testid="progress-error"]').text()).toContain('进度聚合异常');
  });

  it('失败后可在本页重试主线进度请求', async () => {
    apiMocks.fetchMainlineProgress
      .mockRejectedValueOnce(new Error('进度聚合异常'))
      .mockResolvedValueOnce(payload());
    const wrapper = mount(MainlineProgressTab, { props: { classroomId: 9 } });
    await flushPromises();

    await wrapper.get('[data-testid="progress-retry"]').trigger('click');
    await flushPromises();

    expect(apiMocks.fetchMainlineProgress).toHaveBeenCalledTimes(2);
    expect(apiMocks.fetchMainlineProgress).toHaveBeenLastCalledWith(9);
    expect(wrapper.find('[data-testid="progress-error"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="progress-matrix"]').exists()).toBe(true);
  });
});
