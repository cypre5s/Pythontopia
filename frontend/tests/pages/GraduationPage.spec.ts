// 阶段3 终局演出：GraduationPage 守卫与渲染测试。
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import GraduationPage from '@/pages/GraduationPage.vue';
import { useMainQuestStore } from '@/stores/mainQuest';
import { useLearningStore } from '@/stores/learning';

vi.mock('@/utils/celebrate', () => ({
  celebrateLevelUp: vi.fn(),
}));

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/world', name: 'world', component: { template: '<div />' } },
      { path: '/profile-room', name: 'profile-room', component: { template: '<div />' } },
      { path: '/graduation', name: 'graduation', component: GraduationPage },
    ],
  });
}

describe('GraduationPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
  });

  it('boss7 未通关时经 URL 直入 → 重定向回 /world', async () => {
    const router = createTestRouter();
    await router.push('/graduation');
    await router.isReady();

    const wrapper = mount(GraduationPage, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.find('[data-testid="graduation-page"]').exists()).toBe(false);
    expect(router.currentRoute.value.path).toBe('/world');
  });

  it('boss7 通关后渲染毕业典礼与学习数据回顾', async () => {
    const router = createTestRouter();
    await router.push('/graduation');
    await router.isReady();

    const mainQuest = useMainQuestStore();
    mainQuest.progress.defeatedBosses = [1, 2, 3, 4, 5, 6, 7];
    mainQuest.progress.completedSubQuests = ['1-1', '1-2', '1-3'];
    const learning = useLearningStore();
    learning.field.masterySnapshot = [
      { kcCode: 'PY43-KC-001', masteryScore: 0.6 },
      { kcCode: 'PY43-KC-002', masteryScore: 0.2 },
    ] as never;

    const wrapper = mount(GraduationPage, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.find('[data-testid="graduation-page"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('毕业典礼');
    expect(wrapper.find('[data-testid="graduation-quests"]').text()).toBe('3 / 35');
    expect(wrapper.find('[data-testid="graduation-bosses"]').text()).toBe('7 / 7');
    expect(wrapper.find('[data-testid="graduation-kcs"]').text()).toBe('1 / 65');

    await wrapper.find('[data-testid="graduation-back"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/world');
  });
});
