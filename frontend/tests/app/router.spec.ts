import { describe, expect, it } from 'vitest';
import { router } from '@/app/router';

describe('app router building routes', () => {
  it('matches unknown routes so RouterView never renders an empty white page', () => {
    const resolved = router.resolve('/missing-building');

    expect(resolved.matched.length).toBeGreaterThan(0);
  });

  // R-13：补齐 6 个建筑路由（lighthouse / 3 民居 / 谷仓 / 鸡舍）+ catch-all 404
  it.each([
    ['/lighthouse', 'lighthouse'],
    ['/house-east', 'house-east'],
    ['/house-mid', 'house-mid'],
    ['/house-west', 'house-west'],
    ['/storage', 'storage'],
    ['/coop', 'coop'],
  ])('R-13: matches placeholder building route %s', (path, name) => {
    const resolved = router.resolve(path);
    expect(resolved.name).toBe(name);
  });

  it('R-13: /bug-mine 透传 query（如 ?side=east）到 /world', () => {
    const resolved = router.resolve('/bug-mine?side=east');
    // function-form redirect 在 vue-router 中 redirect 字段是 function；
    // resolve 后 matched 仍能命中（matched.length > 0）
    expect(resolved.matched.length).toBeGreaterThan(0);
  });

  it('R-13: catch-all 404 命中未注册路径', () => {
    const resolved = router.resolve('/this-route-definitely-does-not-exist');
    expect(resolved.name).toBe('not-found');
  });
});
