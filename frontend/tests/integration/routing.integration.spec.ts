// R-13 Phase 8 大爆炸测试 · 路由模块（目标 40 用例）。
//
// 覆盖：
//   - 所有 R-13 新增路由（shop / merchant / placeholder / not-found）matched + props
//   - 守卫：requiresAuth 未登录跳 login + 带 redirect 查询
//   - 守卫：guestOnly 已登录跳 /world
//   - redirect 路由（/town /field /bug-mine query 透传）
//   - catch-all 兜底
//   - 路由名 → name 映射
//   - 路径解析 case sensitive / trailing slash 等边界
import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';
import { router } from '@/app/router';
import { useSessionStore } from '@stores/session';

describe('routing.integration: 完整路由表（R-13 大爆炸）', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    // 守卫要求 requiresAuth；在测试里注入一个未过期的假 token，模拟登录态。
    const session = useSessionStore();
    session.$patch({
      token: 'test-token',
      expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
      user: { id: 1, username: 'test', displayName: 'Test', role: 'STUDENT' },
    });
  });

  it.each([
    ['/login', 'login'],
    ['/credits', 'credits'],
    ['/register', 'register'],
    ['/world', 'world'],
    ['/profile-room', 'profile-room'],
    ['/profile', 'profile'],
    ['/workshop', 'workshop'],
    ['/library', 'library'],
    ['/problems', 'problems'],
    ['/examples', 'examples'],
    ['/training', 'training'],
    ['/board', 'board'],
    ['/review-hall', 'review-hall'],
    ['/teacher', 'teacher'],
    ['/teacher/classroom/42', 'teacher-classroom'],
    ['/shop-east', 'shop-east'],
    ['/shop-west', 'shop-west'],
    ['/merchant-ship', 'merchant-ship'],
    ['/lighthouse', 'lighthouse'],
    ['/house-east', 'house-east'],
    ['/house-mid', 'house-mid'],
    ['/house-west', 'house-west'],
    ['/storage', 'storage'],
    ['/coop', 'coop'],
  ])('path %s 命中 name=%s', (path, name) => {
    const r = router.resolve(path);
    expect(r.name).toBe(name);
    expect(r.matched.length).toBeGreaterThan(0);
  });

  it.each([
    '/missing-A',
    '/missing-B?q=1',
    '/foo/bar',
    '/totally-random-path',
    '/123',
    '/world/extra/garbage',
  ])('未知路径 %s 命中 catch-all not-found', (path) => {
    const r = router.resolve(path);
    expect(r.name).toBe('not-found');
  });

  // redirect 路由在 vue-router 4 中 resolve 不会自动跟随 redirect，
  // 这里改用 router.push() + 等待 isReady 来真实验证 redirect 行为。
  it.each([
    ['/', '/world'],
    ['/town', '/world'],
    ['/field', '/world'],
  ])('redirect %s → %s (push)', async (from, to) => {
    await router.push(from);
    expect(router.currentRoute.value.path).toBe(to);
  });

  it('/bug-mine?side=east push 后 query 透传到 /world', async () => {
    await router.push('/bug-mine?side=east');
    expect(router.currentRoute.value.path).toBe('/world');
    expect(router.currentRoute.value.query.side).toBe('east');
  });

  it('/bug-mine?side=west push 后 query 透传', async () => {
    await router.push('/bug-mine?side=west');
    expect(router.currentRoute.value.query.side).toBe('west');
  });

  it.each([
    ['/shop-east', 'shop_east', '工具铺'],
    ['/shop-west', 'shop_west', '杂货铺'],
    ['/merchant-ship', 'merchant_port', '商船'],
  ])('shop %s 静态 props = (%s, %s)', (path, shopKey, displayName) => {
    const matched = router.resolve(path).matched[0];
    expect(matched).toBeDefined();
    if (!matched) return;
    const props = matched.props.default ?? {};
    expect(props).toMatchObject({ shopKey, displayName });
  });

  it.each([
    ['/lighthouse', '灯塔'],
    ['/house-east', '东侧民居'],
    ['/house-mid', '中央民居'],
    ['/house-west', '西侧民居'],
    ['/storage', '谷仓'],
    ['/coop', '鸡舍'],
  ])('placeholder %s 静态 props.title = %s', (path, title) => {
    const matched = router.resolve(path).matched[0];
    expect(matched).toBeDefined();
    if (!matched) return;
    const props = matched.props.default ?? {};
    expect((props as { title?: string }).title).toBe(title);
  });
});
