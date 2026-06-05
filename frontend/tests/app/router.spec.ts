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

  it('R-14: /bug-mine 为独立错题矿洞页并保留 query', () => {
    const resolved = router.resolve('/bug-mine?submissionId=99');
    expect(resolved.name).toBe('bug-mine');
    expect(resolved.query.submissionId).toBe('99');
  });

  it('R-13: catch-all 404 命中未注册路径', () => {
    const resolved = router.resolve('/this-route-definitely-does-not-exist');
    expect(resolved.name).toBe('not-found');
  });
});
