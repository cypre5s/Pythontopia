/* eslint-disable vue/one-component-per-file -- These stubs are local to this isolated test. */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';

const appHarness = vi.hoisted(() => ({
  mounts: 0,
  router: {
    onError: vi.fn(),
    push: vi.fn(),
  },
}));

vi.mock('vue-router', async () => {
  const { defineComponent, h } = await import('vue');
  const AccountBoundPage = defineComponent({
    name: 'WorkshopPage',
    setup() {
      appHarness.mounts += 1;
      const instance = appHarness.mounts;
      return () => h('div', { 'data-testid': 'account-bound-page' }, String(instance));
    },
  });
  return {
    useRouter: () => appHarness.router,
    RouterView: defineComponent({
      name: 'RouterView',
      setup(_props, { slots }) {
        return () => slots.default?.({
          Component: AccountBoundPage,
          route: { name: 'workshop', fullPath: '/workshop' },
        });
      },
    }),
  };
});

vi.mock('@/components/system/AppToast.vue', async () => {
  const { defineComponent } = await import('vue');
  return { default: defineComponent({ name: 'AppToast', template: '<div />' }) };
});
vi.mock('@/components/game/DialogTree.vue', async () => {
  const { defineComponent } = await import('vue');
  return { default: defineComponent({ name: 'DialogTree', template: '<div />' }) };
});
vi.mock('@/components/hud/PerfHud.vue', async () => {
  const { defineComponent } = await import('vue');
  return { default: defineComponent({ name: 'PerfHud', template: '<div />' }) };
});

import App from '@/App.vue';
import { useSessionStore } from '@/stores/session';

describe('App · KeepAlive 账号隔离', () => {
  beforeEach(() => {
    localStorage.clear();
    appHarness.mounts = 0;
    appHarness.router.onError.mockClear();
    const pinia = createPinia();
    setActivePinia(pinia);
  });

  it('同一路由在账号世代变化后销毁旧页面并创建新实例', async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const session = useSessionStore();
    session.user = { id: 1, username: 'alice', displayName: 'A', role: 'STUDENT' };

    const wrapper = mount(App, { global: { plugins: [pinia] } });
    await flushPromises();
    expect(wrapper.get('[data-testid="account-bound-page"]').text()).toBe('1');

    session.user = { id: 2, username: 'bob', displayName: 'B', role: 'STUDENT' };
    session.accountEpoch += 1;
    await flushPromises();

    expect(wrapper.get('[data-testid="account-bound-page"]').text()).toBe('2');
    expect(appHarness.mounts).toBe(2);
  });
});
