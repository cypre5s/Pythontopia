import { describe, expect, it } from 'vitest';
import { router } from '@/app/router';

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
