import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { router } from '@/app/router';
import { useSessionStore } from '@stores/session';

describe('app router building routes', () => {
  it('matches unknown routes so RouterView never renders an empty white page', () => {
    const resolved = router.resolve('/missing-building');

    expect(resolved.matched.length).toBeGreaterThan(0);
  });

  it('R-14: removed placeholder building routes resolve to not-found', () => {
    for (const path of [
      '/lighthouse',
      '/house-east',
      '/house-mid',
      '/house-west',
      '/storage',
      '/coop',
    ]) {
      const resolved = router.resolve(path);
      expect(resolved.name).toBe('not-found');
    }
  });

  it('R-14: /bug-mine 为独立错题矿洞页', () => {
    const resolved = router.resolve('/bug-mine');
    expect(resolved.name).toBe('bug-mine');
  });

  it('R-13: catch-all 404 命中未注册路径', () => {
    const resolved = router.resolve('/this-route-definitely-does-not-exist');
    expect(resolved.name).toBe('not-found');
  });
});

describe('/guide 操作指南路由（需登录）', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
  });

  it('/guide 注册为独立路由且 meta.requiresAuth 为 true', () => {
    const resolved = router.resolve('/guide');
    expect(resolved.name).toBe('guide');
    expect(resolved.meta.requiresAuth).toBe(true);
  });

  it('未登录访问 /guide 被守卫重定向到登录页并携带 redirect 参数', async () => {
    await router.push('/guide');
    expect(router.currentRoute.value.name).toBe('login');
    expect(router.currentRoute.value.query.redirect).toBe('/guide');
  });

  it('已登录访问 /guide 正常进入指南页', async () => {
    const session = useSessionStore();
    session.token = 'test-token';
    session.expiresAt = new Date(Date.now() + 3_600_000).toISOString();

    await router.push('/guide');
    expect(router.currentRoute.value.name).toBe('guide');
  });
});
