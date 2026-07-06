import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import MainlineStarsTab from '@/components/teacher/MainlineStarsTab.vue';

const apiMocks = vi.hoisted(() => ({ fetchMainlineStars: vi.fn() }));
vi.mock('@/api/teacher', () => ({ fetchMainlineStars: apiMocks.fetchMainlineStars }));

beforeEach(() => {
  apiMocks.fetchMainlineStars.mockReset();
});
afterEach(() => {
  vi.restoreAllMocks();
});

function payload() {
  return {
    totalStudents: 3,
    chapters: [
      { chapter: 1, zeroStar: 0, oneStar: 1, twoStar: 1, threeStar: 1, avgStars: 2.0 },
      { chapter: 2, zeroStar: 1, oneStar: 2, twoStar: 0, threeStar: 0, avgStars: 0.67 },
    ],
    topStudents: [
      { studentId: 11, displayName: 'Stu A', totalStars: 7, starredChapters: 3 },
    ],
  };
}

describe('MainlineStarsTab', () => {
  it('按 classroomId 取数并渲染星级矩阵 + 总星 TopN', async () => {
    apiMocks.fetchMainlineStars.mockResolvedValueOnce(payload());
    const wrapper = mount(MainlineStarsTab, { props: { classroomId: 9 } });
    await flushPromises();

    expect(apiMocks.fetchMainlineStars).toHaveBeenCalledWith(9);
    expect(wrapper.find('[data-testid="star-row-1"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="star-row-2"]').exists()).toBe(true);
    const topn = wrapper.find('[data-testid="star-topn"]');
    expect(topn.exists()).toBe(true);
    expect(topn.text()).toContain('Stu A');
    expect(topn.text()).toContain('7');
  });

  it('无成员/无星级数据 → 空态', async () => {
    apiMocks.fetchMainlineStars.mockResolvedValueOnce({
      totalStudents: 0,
      chapters: [],
      topStudents: [],
    });
    const wrapper = mount(MainlineStarsTab, { props: { classroomId: 9 } });
    await flushPromises();
    expect(wrapper.find('[data-testid="stars-empty"]').exists()).toBe(true);
  });

  it('接口失败 → 错误态文案', async () => {
    apiMocks.fetchMainlineStars.mockRejectedValueOnce(new Error('班级聚合异常'));
    const wrapper = mount(MainlineStarsTab, { props: { classroomId: 9 } });
    await flushPromises();
    expect(wrapper.find('[data-testid="stars-error"]').text()).toContain('班级聚合异常');
  });

  it('classroomId 变化时重新取数', async () => {
    apiMocks.fetchMainlineStars.mockResolvedValue(payload());
    const wrapper = mount(MainlineStarsTab, { props: { classroomId: 9 } });
    await flushPromises();
    await wrapper.setProps({ classroomId: 12 });
    await flushPromises();
    expect(apiMocks.fetchMainlineStars).toHaveBeenLastCalledWith(12);
  });
});
