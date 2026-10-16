import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { mount, flushPromises } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import type { Component } from 'vue';

describe('AdminLayout mobile navigation', () => {
  let AdminLayout: Component;

  beforeEach(async () => {
    vi.resetModules();
    const mediaQuery = {
      matches: true,
      media: '(max-width: 767px)',
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    } as unknown as MediaQueryList;
    vi.stubGlobal('matchMedia', vi.fn(() => mediaQuery));

    ({ default: AdminLayout } = await import('../AdminLayout.vue'));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.resetModules();
    document.body.innerHTML = '';
  });

  async function mountLayout() {
    const pinia = createPinia();
    setActivePinia(pinia);
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/admin/users', name: 'admin-users', component: { template: '<div>用户管理内容</div>' } },
        { path: '/admin/operations', name: 'admin-operations', component: { template: '<div />' } },
        { path: '/admin/problem-reviews', name: 'admin-problem-reviews', component: { template: '<div />' } },
      ],
    });
    await router.push('/admin/users');
    await router.isReady();

    const wrapper = mount(AdminLayout, {
      global: {
        plugins: [pinia, router],
        stubs: {
          AppLayout: { template: '<div><slot /></div>' },
          RouterLink: {
            props: ['to'],
            template: '<a href="#" v-bind="$attrs"><slot /></a>',
          },
          RouterView: { template: '<div />' },
        },
      },
      attachTo: document.body,
    });
    await flushPromises();
    return wrapper;
  }

  it('opens as a modal drawer, traps keyboard focus, and restores focus on Escape', async () => {
    const wrapper = await mountLayout();
    const toggle = wrapper.get('.admin-shell__navigation-toggle');
    const sidebar = wrapper.get('#admin-navigation');

    expect(toggle.attributes('aria-expanded')).toBe('false');
    expect(sidebar.attributes('aria-hidden')).toBe('true');
    expect(sidebar.attributes('inert')).toBeDefined();

    await toggle.trigger('click');
    await flushPromises();

    expect(sidebar.attributes('role')).toBe('dialog');
    expect(sidebar.attributes('aria-modal')).toBe('true');
    expect(wrapper.get('.admin-shell__main').attributes('inert')).toBeDefined();
    expect(wrapper.find('.admin-shell__scrim').exists()).toBe(true);

    const focusable = Array.from(sidebar.element.querySelectorAll<HTMLElement>('a[href], button:not(:disabled)'));
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    expect(first).toBeDefined();
    expect(last).toBeDefined();

    last?.focus();
    await sidebar.trigger('keydown', { key: 'Tab' });
    expect(document.activeElement).toBe(first);

    first?.focus();
    await sidebar.trigger('keydown', { key: 'Tab', shiftKey: true });
    expect(document.activeElement).toBe(last);

    await sidebar.trigger('keydown', { key: 'Escape' });
    await flushPromises();

    expect(toggle.attributes('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(toggle.element);
    expect(wrapper.find('.admin-shell__scrim').exists()).toBe(false);
    wrapper.unmount();
  });
});
