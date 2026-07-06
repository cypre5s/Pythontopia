import { createPinia, setActivePinia } from 'pinia';
import { mount } from '@vue/test-utils';
import { beforeEach, expect, test, vi } from 'vitest';
import App from '@/App.vue';
import { router } from '@/app/router';
import { useWorldStore } from '@stores/world';

vi.mock('@/components/game/WorldCanvas.vue', () => ({
  default: { template: '<div class="world-canvas-mock" />' },
}));

vi.mock('@/pages/WorkshopPage.vue', () => ({
  default: { template: '<main>Workshop mock</main>' },
}));

beforeEach(async () => {
  localStorage.clear();
  if (router.currentRoute.value.path !== '/') {
    await router.push('/');
    await router.isReady();
  }
});

test('renders the login entry when unauthenticated', async () => {
  const pinia = createPinia();
  setActivePinia(pinia);
  const wrapper = mount(App, {
    global: {
      plugins: [pinia, router],
    },
  });
  await router.push('/');
  await router.isReady();

  expect(wrapper.text()).toContain('Pythontopia');
  expect(wrapper.text()).toContain('登录');
});

test('handles NPC dialog route navigation globally', async () => {
  const pinia = createPinia();
  setActivePinia(pinia);
  const pushSpy = vi.spyOn(router, 'push').mockResolvedValue(undefined);

  const wrapper = mount(App, {
    global: {
      plugins: [pinia, router],
    },
  });

  window.dispatchEvent(new CustomEvent('pythontopia:dialog-navigate', { detail: { route: '/workshop' } }));

  expect(pushSpy).toHaveBeenCalledWith('/workshop');
  pushSpy.mockRestore();
  wrapper.unmount();
});

test('routes bug mine dialog navigation straight to the /bug-mine page (R-14: no pendingTravel)', async () => {
  const pinia = createPinia();
  setActivePinia(pinia);
  const world = useWorldStore();
  const pushSpy = vi.spyOn(router, 'push').mockResolvedValue(undefined);

  const wrapper = mount(App, {
    global: {
      plugins: [pinia, router],
    },
  });

  window.dispatchEvent(new CustomEvent('pythontopia:dialog-navigate', { detail: { route: '/bug-mine' } }));

  // R-14：mine_south 风景地图已删，对话导航不再写 pendingTravel，直接 router.push 进页面。
  expect(world.pendingTravelMap).toBeNull();
  expect(world.pendingTravelTile).toBeNull();
  expect(pushSpy).toHaveBeenCalledWith('/bug-mine');
  pushSpy.mockRestore();
  wrapper.unmount();
});
