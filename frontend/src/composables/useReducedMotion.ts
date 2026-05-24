// 隔离原因：
// 全站动效（路由切换 fade、HUD slot bounce、Phaser tween、粒子、mermaid 入场）
// 都要在 `prefers-reduced-motion: reduce` 时降级或禁用。本 composable 是业务层
// 与 window.matchMedia 之间的唯一桥，避免每个组件各自写一遍 matchMedia + 事件清理。
//
// 落地约束（与 docs/a11y-checklist.md 第 10 章对齐）：
// 1. SSR / 测试环境下 window 可能不存在，必须 fail-safe 返回 `false`。
// 2. 用户在系统设置里切换"减少动作"必须实时生效，禁止刷新页面。
// 3. 组件卸载时必须移除监听器，避免长会话泄漏。
// 4. 多个组件同时调用 useReducedMotion 各拿一个独立 ref，互不干扰；
//    监听器各自挂载/卸载，简单可预测，不引入模块级单例。

import { onUnmounted, ref, type Ref } from 'vue';

const MEDIA_QUERY = '(prefers-reduced-motion: reduce)';

function readInitialPreference(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false;
  }
  return window.matchMedia(MEDIA_QUERY).matches;
}

export function useReducedMotion(): Ref<boolean> {
  const prefersReducedMotion = ref<boolean>(readInitialPreference());

  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return prefersReducedMotion;
  }

  const mediaQueryList = window.matchMedia(MEDIA_QUERY);

  const handleChange = (event: MediaQueryListEvent): void => {
    prefersReducedMotion.value = event.matches;
  };

  // Safari < 14 仅支持 addListener / removeListener；现代浏览器使用 addEventListener。
  // 我们要求 Beta 演示环境为 Chrome/Edge/Firefox/Safari 14+，因此走 addEventListener 主路径，
  // 同时通过 typeof 守卫向下兼容，避免运行时抛 TypeError。
  if (typeof mediaQueryList.addEventListener === 'function') {
    mediaQueryList.addEventListener('change', handleChange);
    onUnmounted(() => {
      mediaQueryList.removeEventListener('change', handleChange);
    });
  } else {
    const legacyList = mediaQueryList as MediaQueryList & {
      addListener: (listener: (event: MediaQueryListEvent) => void) => void;
      removeListener: (listener: (event: MediaQueryListEvent) => void) => void;
    };
    legacyList.addListener(handleChange);
    onUnmounted(() => {
      legacyList.removeListener(handleChange);
    });
  }

  return prefersReducedMotion;
}
