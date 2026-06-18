/**
 * PauseMenu 学习分组导航入口测试。
 *
 * 覆盖「代码剧场」入口（id=code-theater）：点击后 router.push('/code-theater') 并关闭暂停菜单，
 * 与「训练场」「百晓工坊」「复盘大厅」等既有学习入口写法一致。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { nextTick } from 'vue';

vi.mock('@/composables/useStardewFx', () => ({
  playUiClick: vi.fn(),
}));

import PauseMenu from '@/components/hud/PauseMenu.vue';
import { useGameSettingsStore } from '@stores/gameSettings';

function createPauseRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', redirect: '/world' },
      { path: '/world', name: 'world', component: { template: '<div />' } },
      { path: '/code-theater', name: 'code-theater', component: { template: '<div />' } },
    ],
  });
}

beforeEach(() => {
  setActivePinia(createPinia());
  localStorage.clear();
});

describe('PauseMenu 代码剧场入口', () => {
  it('「代码剧场」(id=code-theater) 点击后跳转 /code-theater 并关闭菜单', async () => {
    const router = createPauseRouter();
    await router.push('/world');
    await router.isReady();
    const pushSpy = vi.spyOn(router, 'push');

    const settings = useGameSettingsStore();
    settings.pauseMenuOpen = true;

    const wrapper = mount(PauseMenu, {
      global: { plugins: [router] },
    });
    await nextTick();

    const button = wrapper.get('[data-testid="pause-code-theater"]');
    expect(button.text()).toContain('代码剧场');

    pushSpy.mockClear();
    await button.trigger('click');
    await flushPromises();

    expect(pushSpy).toHaveBeenCalledWith('/code-theater');
    expect(settings.pauseMenuOpen).toBe(false);
  });
});
