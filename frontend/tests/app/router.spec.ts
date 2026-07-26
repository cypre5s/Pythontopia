import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { router, toSafeInternalPath } from '@/app/router';
import { useSessionStore } from '@stores/session';
import { useMainlineStore } from '@stores/mainline';
import { useToastStore } from '@stores/toast';

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

  it('从未登录（无 token）时提示「请先登录」而不是「登录状态已失效」', async () => {
    const toast = useToastStore();
    const showSpy = vi.spyOn(toast, 'show');

    // 换一个受保护路由，避免与上一个用例的当前地址相同被判重复导航（守卫不会执行）。
    await router.push('/library');

    expect(router.currentRoute.value.name).toBe('login');
    expect(showSpy).toHaveBeenCalledWith('请先登录后再进入小镇。', 'info');
  });

  it('登录过但已过期时才提示「登录状态已失效」', async () => {
    const session = useSessionStore();
    session.token = 'stale-token';
    session.expiresAt = new Date(Date.now() - 1_000).toISOString();
    const toast = useToastStore();
    const showSpy = vi.spyOn(toast, 'show');

    await router.push('/review-hall');

    expect(router.currentRoute.value.name).toBe('login');
    expect(showSpy).toHaveBeenCalledWith('登录状态已失效，请重新登录。', 'error');
  });
});

describe('分块自愈跳转的站内路径断言（开放重定向防护）', () => {
  it('普通站内路径原样放行', () => {
    expect(toSafeInternalPath('/mainline-map')).toBe('/mainline-map');
    expect(toSafeInternalPath('/problems?kcCode=PY43-KC-001')).toBe('/problems?kcCode=PY43-KC-001');
    expect(toSafeInternalPath('/boss/7#top')).toBe('/boss/7#top');
  });

  it('协议相对 URL / 外站地址一律退回首页（不整页跳出本站）', () => {
    // `//evil.com` 交给 location.assign 会跳到 https://evil.com。
    expect(toSafeInternalPath('//evil.com')).toBe('/');
    expect(toSafeInternalPath('//evil.com/pwn')).toBe('/');
    expect(toSafeInternalPath('https://evil.com')).toBe('/');
    expect(toSafeInternalPath('http://evil.com')).toBe('/');
    expect(toSafeInternalPath('javascript:alert(1)')).toBe('/');
    expect(toSafeInternalPath('mainline-map')).toBe('/');
    expect(toSafeInternalPath('')).toBe('/');
  });
});

describe('/boss/:chapter 前置守卫（通过本章大测试后才开放）', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    const session = useSessionStore();
    session.token = 'test-token';
    session.expiresAt = new Date(Date.now() + 3_600_000).toISOString();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  function seedOverview(chapters: { chapter: number; unlocked: boolean; stars: number }[]): void {
    const mainline = useMainlineStore();
    vi.spyOn(mainline, 'load').mockResolvedValue(undefined);
    mainline.overview = {
      totalStars: chapters.reduce((sum, c) => sum + c.stars, 0),
      chapters: chapters.map((c) => ({
        chapter: c.chapter,
        title: `第${c.chapter}章`,
        unlocked: c.unlocked,
        stars: c.stars,
        bestTestScore: c.stars >= 1 ? 80 : null,
        stages: [],
        testStatus: 'NONE' as const,
        starBreakdown: { test: 0, stage: 0, mastery: 0, recovered: [] },
        recovery: { errorbookDone: false, endlessDone: false },
      })),
    };
  }

  it('未通过本章大测试（0★）时手输 /boss/7 被弹回冒险地图并提示', async () => {
    seedOverview([{ chapter: 7, unlocked: true, stars: 0 }]);
    const toast = useToastStore();
    const showSpy = vi.spyOn(toast, 'show');

    await router.push('/boss/7');

    expect(router.currentRoute.value.path).toBe('/mainline-map');
    expect(showSpy).toHaveBeenCalledWith(expect.stringContaining('章末大测试'), 'error');
  });

  it('本章未解锁时同样拦截', async () => {
    seedOverview([{ chapter: 5, unlocked: false, stars: 0 }]);
    await router.push('/boss/5');
    expect(router.currentRoute.value.path).toBe('/mainline-map');
  });

  it('overview 拉不到（章节缺失）时按加载失败拦截，不放行', async () => {
    seedOverview([]);
    useMainlineStore().error = 'network down';
    await router.push('/boss/3');
    expect(router.currentRoute.value.path).toBe('/mainline-map');
  });

  it('本章已通过大测试（≥1★）时正常进入', async () => {
    seedOverview([{ chapter: 1, unlocked: true, stars: 2 }]);
    await router.push('/boss/1');
    expect(router.currentRoute.value.name).toBe('boss');
  });
});
