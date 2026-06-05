// 业务层与 window.matchMedia 之间的唯一桥：全站动效（路由 fade、HUD bounce、Phaser tween、
// 粒子、mermaid 入场）需在 `prefers-reduced-motion: reduce` 时降级，统一在此读取，
// 避免每个组件重复写 matchMedia + 事件清理。
//
// 约束：
// 1. SSR / 测试环境下 window 可能不存在，必须 fail-safe 返回 `false`；
// 2. 用户在系统设置切换"减少动作"须实时生效，无需刷新；
// 3. 组件卸载时必须移除监听器，避免长会话泄漏；
// 4. 每次调用各拿独立 ref，监听器独立挂载/卸载，不引入模块级单例。

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

  // Safari < 14 仅支持 addListener / removeListener，现代浏览器用 addEventListener；
  // 通过 typeof 守卫优先走 addEventListener 主路径并向下兼容，避免运行时抛 TypeError。
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
