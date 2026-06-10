// 防作弊路由守卫单测：受限 AI 路径判定 + 全局守卫工厂（考试中拦截 / 非考试放行）。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { RouteLocationNormalized } from 'vue-router';
import {
  EXAM_BLOCKED_PATH_PREFIXES,
  isExamBlockedPath,
  isExamActive,
  setExamActive,
  createExamRouteGuard,
} from '@/components/chaptertest/useExamRouteGuard';

function toRoute(path: string): RouteLocationNormalized {
  return { path } as unknown as RouteLocationNormalized;
}

beforeEach(() => {
  setExamActive(false);
});

afterEach(() => {
  setExamActive(false);
});

describe('isExamBlockedPath', () => {
  it('blocks AI pages (exact + sub-paths + with query)', () => {
    expect(isExamBlockedPath('/workshop')).toBe(true);
    expect(isExamBlockedPath('/workshop/codelab')).toBe(true);
    expect(isExamBlockedPath('/workshop?kc=PY43')).toBe(true);
    expect(isExamBlockedPath('/tutor')).toBe(true);
    expect(isExamBlockedPath('/feynman')).toBe(true);
    expect(isExamBlockedPath('/bug-mine')).toBe(true);
  });

  it('allows non-AI pages (map / town / endless)', () => {
    expect(isExamBlockedPath('/mainline-map')).toBe(false);
    expect(isExamBlockedPath('/world')).toBe(false);
    expect(isExamBlockedPath('/endless')).toBe(false);
    expect(isExamBlockedPath('/chapter-test/1')).toBe(false);
  });

  it('exposes the blocked prefix list', () => {
    expect(EXAM_BLOCKED_PATH_PREFIXES).toContain('/workshop');
    expect(EXAM_BLOCKED_PATH_PREFIXES).toContain('/bug-mine');
  });
});

describe('createExamRouteGuard', () => {
  it('allows everything when no exam is active', () => {
    const guard = createExamRouteGuard();
    expect(isExamActive()).toBe(false);
    expect(guard(toRoute('/workshop'))).toBe(true);
  });

  it('blocks AI pages while an exam is active and reports via onBlock', () => {
    const onBlock = vi.fn();
    const guard = createExamRouteGuard(onBlock);
    setExamActive(true);
    expect(guard(toRoute('/workshop'))).toBe(false);
    expect(onBlock).toHaveBeenCalledWith('/workshop');
    // 非 AI 页面仍放行（退出 / 地图）。
    expect(guard(toRoute('/mainline-map'))).toBe(true);
  });
});
