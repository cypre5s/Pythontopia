// R-13 Phase 6 迭代 26：NotFoundPage 关键行为单测。
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import NotFoundPage from '@/pages/NotFoundPage.vue';

const APP_LAYOUT_STUB = { template: '<div data-testid="layout-stub"><slot /></div>' };

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/world', name: 'world', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' } },
      { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundPage },
    ],
  });
}

describe('NotFoundPage', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('挂载后显示当前 path + 三个按钮 + 倒计时', async () => {
    const router = createTestRouter();
    await router.push('/this-route-not-exist');
    await router.isReady();

    const wrapper = mount(NotFoundPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    const page = wrapper.find('[data-testid="not-found-page"]');
    expect(page.exists()).toBe(true);
    expect(wrapper.find('[data-testid="not-found-path"]').text()).toContain(
      '/this-route-not-exist',
    );
    expect(wrapper.find('[data-testid="not-found-countdown"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="not-found-countdown"]').text()).toContain('8');
  });

  it('每过 1s 倒计时减 1', async () => {
    const router = createTestRouter();
    await router.push('/x');
    await router.isReady();
    const wrapper = mount(NotFoundPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();
    expect(wrapper.find('[data-testid="not-found-countdown"]').text()).toContain('8');

    vi.advanceTimersByTime(1000);
    await flushPromises();
    expect(wrapper.find('[data-testid="not-found-countdown"]').text()).toContain('7');

    vi.advanceTimersByTime(3000);
    await flushPromises();
    expect(wrapper.find('[data-testid="not-found-countdown"]').text()).toContain('4');
  });
});
