// 空闲资源预取：应用挂载后，利用浏览器空闲时段预热「玩家进入 town_hub 后下一步最可能去」
// 的学习页路由分块与课程清单，降低首次进入冒险地图 / 图书馆 / 工坊 / 训练场的等待。
//
// 设计红线：纯加法、幂等、尽力而为（失败静默）；尊重 Save-Data / 2g 省流偏好；
// 预取只 import 模块以预热 chunk（与 router 的同名动态 import 去重到同一 chunk），绝不挂载组件。
import { listCourseDocuments, loadManifest } from '@/api/courseLibrary';

interface IdleWindow {
  requestIdleCallback?: (cb: () => void, opts?: { timeout?: number }) => number;
}

function onIdle(cb: () => void, timeout = 2500): void {
  const w = window as Window & IdleWindow;
  if (typeof w.requestIdleCallback === 'function') {
    w.requestIdleCallback(cb, { timeout });
  } else {
    window.setTimeout(cb, Math.min(timeout, 1200));
  }
}

/** 省流量模式（Save-Data 或 2g）下不预取，尊重用户带宽偏好。 */
function prefetchAllowed(): boolean {
  const conn = (
    navigator as Navigator & {
      connection?: { saveData?: boolean; effectiveType?: string };
    }
  ).connection;
  if (!conn) {
    return true;
  }
  if (conn.saveData) {
    return false;
  }
  return !(conn.effectiveType ?? '').includes('2g');
}

// 玩家进 town_hub 后下一步最可能去的学习页（与主线「看 → 学 → 练 → 测」入口对齐）。
// 这些 import 的字面量必须与 router.ts 中一致，rollup 才会去重到同一 chunk。
const ROUTE_CHUNKS: ReadonlyArray<() => Promise<unknown>> = [
  () => import('@/pages/MainlineMapPage.vue'),
  () => import('@/pages/LibraryPage.vue'),
  () => import('@/pages/WorkshopPage.vue'),
  () => import('@/pages/TrainingPage.vue'),
];

let started = false;

/**
 * 安排一次空闲预取。幂等：仅首次生效。
 * 未登录时按 attempt 重试若干次（覆盖「先到登录页、登录后再预取」），仍未登录则放弃。
 */
export function schedulePrefetch(isAuthenticated: () => boolean): void {
  if (started) {
    return;
  }
  started = true;
  let attempts = 0;
  const tick = (): void => {
    if (!isAuthenticated()) {
      if (attempts++ < 6) {
        onIdle(tick, 4000);
      }
      return;
    }
    if (!prefetchAllowed()) {
      return;
    }
    // 课程数据（带模块级缓存，预热后图书馆 / 阶段页更快可读）。
    void loadManifest().catch(() => undefined);
    void listCourseDocuments().catch(() => undefined);
    // 路由分块逐个预取，每个之间再让出一次空闲，避免与交互抢主线程。
    let i = 0;
    const pump = (): void => {
      const next = ROUTE_CHUNKS[i];
      i += 1;
      if (!next) {
        return;
      }
      void next()
        .catch(() => undefined)
        .finally(() => onIdle(pump));
    };
    pump();
  };
  onIdle(tick);
}

/** 测试用：重置幂等状态。 */
export function resetPrefetchForTest(): void {
  started = false;
}
