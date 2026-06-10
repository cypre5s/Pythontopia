// 章测防作弊「禁止跳转 AI 页面」守卫（R-15 §4.3）。
//
// 分两层（避免触碰共享的 router.ts）：
//   1. 组件内守卫 `useExamRouteGuard`：在 ChapterTestPage setup 内注册 onBeforeRouteLeave，
//      考试进行中拦截前往 AI 页面的导航——本文件内即可生效，无需改 router.ts。
//   2. 全局守卫工厂 `createExamRouteGuard`：供主代理在 **共享的 router.ts** 里一行注册
//      （见最终报告「路由 + 守卫注册」）。它读模块级 `examActive` 单例（由本页 setExamActive 维护），
//      所以即使用户从别处（HUD/快捷键）发起导航也能拦下。
import { onBeforeRouteLeave } from 'vue-router';
import type { RouteLocationNormalized } from 'vue-router';
import { ref } from 'vue';
import type { Ref } from 'vue';

/**
 * 考试期间禁止进入的 AI 页面前缀（§4.3：百晓工坊 / AI 导学（费曼）/ Bug 矿洞）。
 * `/tutor` 在 router 中 redirect → `/workshop`，一并拦截避免绕路。
 */
export const EXAM_BLOCKED_PATH_PREFIXES: readonly string[] = [
  '/workshop',
  '/tutor',
  '/feynman',
  '/bug-mine',
];

/** 目标路径是否命中受限 AI 页面（精确匹配或子路径/带 query）。 */
export function isExamBlockedPath(path: string): boolean {
  return EXAM_BLOCKED_PATH_PREFIXES.some(
    (prefix) =>
      path === prefix || path.startsWith(`${prefix}/`) || path.startsWith(`${prefix}?`),
  );
}

// 模块级单例：标记「当前是否有进行中的章测」。页面进入 in_progress 时置 true、提交/超时/离开置 false。
const examActive = ref<boolean>(false);

/** 设置考试进行态（页面调用）。 */
export function setExamActive(active: boolean): void {
  examActive.value = active;
}

/** 读考试进行态（全局守卫调用）。 */
export function isExamActive(): boolean {
  return examActive.value;
}

/** 暴露响应式引用（便于页面/测试观察）。 */
export function useExamActiveState(): Ref<boolean> {
  return examActive;
}

export interface ExamRouteGuardOptions {
  /** 返回「考试是否进行中」。 */
  isActive: () => boolean;
  /** 命中拦截时回调（用于弹 toast 提示）。 */
  onBlock?: (path: string) => void;
}

/**
 * 组件内守卫：在 setup 中调用。考试进行中跳转 AI 页面 → 阻止并回调 onBlock。
 * 其余导航（返回地图 / 小镇等）放行（退出确认在页面内自行处理）。
 */
export function useExamRouteGuard(options: ExamRouteGuardOptions): void {
  onBeforeRouteLeave((to: RouteLocationNormalized): boolean => {
    if (options.isActive() && isExamBlockedPath(to.path)) {
      options.onBlock?.(to.path);
      return false;
    }
    return true;
  });
}

/**
 * 全局守卫工厂：返回可直接挂到 `router.beforeEach` 的守卫。
 * 考试进行中（模块单例 examActive=true）且目标是 AI 页面 → 返回 false 阻止跳转。
 *
 * 主代理在 router.ts 集成段注册（示例见最终报告）：
 *   router.beforeEach(createExamRouteGuard((p) => useToastStore().show(`考试中禁止前往 ${p}`, 'error')));
 */
export function createExamRouteGuard(
  onBlock?: (path: string) => void,
): (to: RouteLocationNormalized) => boolean {
  return (to: RouteLocationNormalized): boolean => {
    if (isExamActive() && isExamBlockedPath(to.path)) {
      onBlock?.(to.path);
      return false;
    }
    return true;
  };
}
