import { createPinia, setActivePinia } from 'pinia';
import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { beforeEach, expect, test, vi } from 'vitest';
import App from '@/App.vue';
import { router } from '@/app/router';
import { useSessionStore } from '@stores/session';
import { useWorldStore } from '@stores/world';

vi.mock('@/components/game/WorldCanvas.vue', () => ({
  default: { template: '<div class="world-canvas-mock" />' },
}));

vi.mock('@/pages/WorkshopPage.vue', () => ({
  default: { name: 'WorkshopPage', template: '<main>Workshop mock</main>' },
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

test('rebuilds KeepAlive account-local pages when the authenticated user changes', async () => {
  let workshopMountCount = 0;
  const AccountLocalWorkshopPage = defineComponent({
    name: 'WorkshopPage',
    setup() {
      workshopMountCount += 1;
      return {};
    },
    template: '<main>Account-local workshop</main>',
  });
  const accountRouter = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/workshop',
        name: 'workshop',
        component: AccountLocalWorkshopPage,
      },
    ],
  });
  const pinia = createPinia();
  setActivePinia(pinia);
  const session = useSessionStore();
  session.token = 'token-a';
  session.expiresAt = '2099-01-01T00:00:00Z';
  session.user = {
    id: 101,
    username: 'student-a',
    displayName: '学生 A',
    role: 'STUDENT',
  };
  await accountRouter.push('/workshop');
  await accountRouter.isReady();
  const wrapper = mount(App, {
    global: {
      plugins: [pinia, accountRouter],
    },
  });

  await flushPromises();
  expect(workshopMountCount).toBe(1);

  session.user = {
    id: 202,
    username: 'student-b',
    displayName: '学生 B',
    role: 'STUDENT',
  };
  await nextTick();
  await flushPromises();

  expect(workshopMountCount).toBe(2);
  wrapper.unmount();
});
