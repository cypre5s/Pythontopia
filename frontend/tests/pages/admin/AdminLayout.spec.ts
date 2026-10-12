import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import AdminLayout from '@/pages/admin/AdminLayout.vue';
import { useSessionStore } from '@stores/session';

const route = vi.hoisted(() => ({
  current: { name: 'admin-operations', fullPath: '/admin/operations' },
}));
const router = vi.hoisted(() => ({ replace: vi.fn() }));
const media = vi.hoisted(() => ({
  query: {
    matches: true,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  },
}));

vi.mock('vue-router', () => ({
  RouterLink: { props: ['to'], template: '<a href="/mock-route"><slot /></a>' },
  RouterView: { template: '<div data-testid="admin-route-view" />' },
  useRoute: () => route.current,
  useRouter: () => router,
}));
vi.mock('@/app/AppLayout.vue', () => ({
  default: { template: '<div data-testid="app-layout"><slot /></div>' },
}));

describe('AdminLayout', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn(() => media.query),
    });
  });

  afterEach(() => {
    Object.defineProperty(window, 'matchMedia', { configurable: true, value: undefined });
  });

  it('lists only real features in grouped navigation and opens the narrow-screen drawer', async () => {
    const wrapper = mount(AdminLayout);
    await flushPromises();

    expect(wrapper.findAll('.admin-nav__group')).toHaveLength(3);
    expect(wrapper.text()).toContain('平台运行');
    expect(wrapper.text()).toContain('内容审核');
    expect(wrapper.text()).toContain('AI 运维概览');
    expect(wrapper.text()).toContain('AI 出题审核');
    expect(wrapper.text()).toContain('账号管理');
    expect(wrapper.text()).toContain('用户管理');
    expect(wrapper.find('aside').attributes('aria-hidden')).toBe('true');

    expect(wrapper.find('.admin-topbar').exists()).toBe(false);
    await wrapper.get('.admin-shell__navigation-toggle').trigger('click');
    expect(wrapper.get('.admin-shell__navigation-toggle').attributes('aria-expanded')).toBe('true');
    expect(wrapper.find('aside').attributes('aria-hidden')).toBe('false');
    expect(wrapper.find('.admin-shell__scrim').exists()).toBe(true);

    await wrapper.get('.admin-shell__scrim').trigger('click');
    expect(wrapper.get('.admin-shell__navigation-toggle').attributes('aria-expanded')).toBe('false');
    expect(wrapper.find('.admin-shell__scrim').exists()).toBe(false);
  });

  it('traps focus in the narrow navigation and closes it with Escape', async () => {
    const wrapper = mount(AdminLayout, { attachTo: document.body });
    const toggle = wrapper.get('.admin-shell__navigation-toggle');
    await toggle.trigger('click');
    await flushPromises();

    const sidebar = wrapper.get('aside');
    expect(sidebar.attributes('role')).toBe('dialog');
    expect(document.activeElement).toBe(sidebar.element.querySelector('a'));
    const logout = sidebar.element.querySelector('[data-testid="admin-logout"]') as HTMLButtonElement;
    logout.focus();
    await sidebar.trigger('keydown', { key: 'Tab' });
    expect(document.activeElement).toBe(sidebar.element.querySelector('a'));
    await sidebar.trigger('keydown', { key: 'Tab', shiftKey: true });
    expect(document.activeElement).toBe(logout);
    await sidebar.trigger('keydown', { key: 'Escape' });

    expect(toggle.attributes('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(toggle.element);
    wrapper.unmount();
  });

  it('logs out and returns to the login page', async () => {
    const wrapper = mount(AdminLayout);
    const logout = vi.spyOn(useSessionStore(), 'logout');

    await wrapper.get('[data-testid="admin-logout"]').trigger('click');

    expect(logout).toHaveBeenCalledOnce();
    expect(router.replace).toHaveBeenCalledWith({ name: 'login' });
  });
});
