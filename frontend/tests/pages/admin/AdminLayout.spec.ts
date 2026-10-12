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
  RouterLink: { props: ['to'], template: '<a><slot /></a>' },
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

    expect(wrapper.findAll('.admin-nav__group')).toHaveLength(2);
    expect(wrapper.text()).toContain('平台运行');
    expect(wrapper.text()).toContain('内容审核');
    expect(wrapper.text()).toContain('AI 运维概览');
    expect(wrapper.text()).toContain('AI 出题审核');
    expect(wrapper.find('aside').attributes('aria-hidden')).toBe('true');

    await wrapper.get('.admin-topbar__menu').trigger('click');
    expect(wrapper.get('.admin-topbar__menu').attributes('aria-expanded')).toBe('true');
    expect(wrapper.find('aside').attributes('aria-hidden')).toBe('false');
    expect(wrapper.find('.admin-shell__scrim').exists()).toBe(true);

    await wrapper.get('.admin-shell__scrim').trigger('click');
    expect(wrapper.get('.admin-topbar__menu').attributes('aria-expanded')).toBe('false');
    expect(wrapper.find('.admin-shell__scrim').exists()).toBe(false);
  });

  it('logs out and returns to the login page', async () => {
    const wrapper = mount(AdminLayout);
    const logout = vi.spyOn(useSessionStore(), 'logout');

    await wrapper.get('[data-testid="admin-logout"]').trigger('click');

    expect(logout).toHaveBeenCalledOnce();
    expect(router.replace).toHaveBeenCalledWith({ name: 'login' });
  });
});
