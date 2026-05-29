import { createPinia, setActivePinia } from 'pinia';
import { mount } from '@vue/test-utils';
import { beforeEach, expect, test, vi } from 'vitest';
import App from '@/App.vue';
import { router } from '@/app/router';
import { useWorldStore } from '@stores/world';

vi.mock('@/components/game/GameCanvas.vue', () => ({
  default: { template: '<div class="game-canvas-mock" />' },
}));

vi.mock('@/components/game/FieldCanvas.vue', () => ({
  default: { template: '<div class="field-canvas-mock" />' },
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

test('routes legacy bug mine dialog navigation into the mine map', async () => {
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

  expect(world.pendingTravelMap).toBe('mine_south');
  expect(world.pendingTravelTile).toEqual({ x: 15, y: 2 });
  expect(pushSpy).toHaveBeenCalledWith({ path: '/world', query: { goto: 'mine_south' } });
  pushSpy.mockRestore();
  wrapper.unmount();
});
