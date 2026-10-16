import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TodayQuestsTab from '../TodayQuestsTab.vue';
import ClassroomQuestsTab from '../ClassroomQuestsTab.vue';

const api = vi.hoisted(() => ({
  getTodayQuests: vi.fn(),
  startQuest: vi.fn(),
  completeQuest: vi.fn(),
  skipQuest: vi.fn(),
  getMyClassroomQuests: vi.fn(),
}));

vi.mock('@/api/quests', () => ({
  getTodayQuests: api.getTodayQuests,
  startQuest: api.startQuest,
  completeQuest: api.completeQuest,
  skipQuest: api.skipQuest,
}));
vi.mock('@/api/classroomQuests', () => ({
  getMyClassroomQuests: api.getMyClassroomQuests,
}));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));

describe('board quest loading states', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
    api.getTodayQuests.mockResolvedValue([]);
    api.getMyClassroomQuests.mockResolvedValue([]);
  });

  it('shows loading before deciding that there are no today quests', async () => {
    let resolveRequest!: (quests: never[]) => void;
    api.getTodayQuests.mockReturnValue(new Promise((resolve) => {
      resolveRequest = resolve;
    }));

    const wrapper = mount(TodayQuestsTab);
    await wrapper.vm.$nextTick();

    expect(wrapper.get('[role="status"]').text()).toContain('正在加载今日任务');
    expect(wrapper.text()).not.toContain('今日暂无待办任务');

    resolveRequest([]);
    await flushPromises();
    expect(wrapper.text()).toContain('今日暂无待办任务');
  });

  it('shows today quest load errors and retries to the real empty response', async () => {
    api.getTodayQuests
      .mockRejectedValueOnce(new Error('暂时无法连接'))
      .mockResolvedValueOnce([]);

    const wrapper = mount(TodayQuestsTab);
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('暂时无法连接');
    expect(wrapper.text()).not.toContain('今日暂无待办任务');

    await wrapper.get('[data-testid="today-quests-retry"]').trigger('click');
    await flushPromises();

    expect(api.getTodayQuests).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('今日暂无待办任务');
  });

  it('shows loading before deciding that there are no classroom quests', async () => {
    let resolveRequest!: (quests: never[]) => void;
    api.getMyClassroomQuests.mockReturnValue(new Promise((resolve) => {
      resolveRequest = resolve;
    }));

    const wrapper = mount(ClassroomQuestsTab);
    await wrapper.vm.$nextTick();

    expect(wrapper.get('[role="status"]').text()).toContain('正在加载班级任务');
    expect(wrapper.text()).not.toContain('暂时没有班级任务');

    resolveRequest([]);
    await flushPromises();
    expect(wrapper.text()).toContain('暂时没有班级任务');
  });

  it('shows classroom quest load errors and retries to the real empty response', async () => {
    api.getMyClassroomQuests
      .mockRejectedValueOnce(new Error('班级服务暂不可用'))
      .mockResolvedValueOnce([]);

    const wrapper = mount(ClassroomQuestsTab);
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('班级服务暂不可用');
    expect(wrapper.text()).not.toContain('暂时没有班级任务');

    await wrapper.get('[data-testid="classroom-quests-retry"]').trigger('click');
    await flushPromises();

    expect(api.getMyClassroomQuests).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('暂时没有班级任务');
  });
});
