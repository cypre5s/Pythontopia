// R-14「快速旅行入口休眠」gate 的共享测试夹具。
//
// 背景：世界收缩为单一 town_hub 后 FAST_TRAVEL_ROUTES 只剩一条，且它的目的地就是
// 玩家当前所在地图 —— 面板里唯一一行永久 disabled，打开它等于给玩家一条死路。
// 实现因此用 `FAST_TRAVEL_ENABLED = FAST_TRAVEL_ROUTES.length > 1`
// （src/data/fastTravel.ts）统一 gate 掉全部入口：面板打开、T 键、QuickWheel
// 「传送」扇区、指南快捷键条目。
//
// 为了让这条 gate 在「将来恢复多张地图」时仍有测试保护，spec 需要能在同一个文件里
// 同时覆盖「真实单 hub」与「模拟多路线」两种世界，故用 getter 形式的模块 mock：
// state.routes 为 null 时透传真实模块，被赋值时整体切到模拟路线表。
//
// 用法（vi.mock 必须写在 spec 文件里才会被 hoist）：
//
//   const ftRoutes: FastTravelRouteOverride = vi.hoisted(() => ({ routes: null }));
//   vi.mock('@/data/fastTravel', async (importOriginal) => {
//     const actual = await importOriginal<typeof import('@/data/fastTravel')>();
//     const { createFastTravelModuleMock } = await import('../_helpers/fastTravelRoutes');
//     return createFastTravelModuleMock(actual, ftRoutes);
//   });

import type { FastTravelRoute } from '@/data/fastTravel';

export interface FastTravelRouteOverride {
  /** null = 透传真实路线表（R-14 单 hub）；非 null = 模拟恢复多图后的路线表。 */
  routes: readonly FastTravelRoute[] | null;
}

/**
 * 模拟「外围图恢复」后的路线表：hub + 一条外围图，长度 > 1 → 入口应整体回归。
 *
 * `toMap` 在 R-14 被收窄成 'town_hub' 字面量，这里断言回去只是为了构造未来场景，
 * 不代表生产代码里存在该值。
 */
export const RESTORED_FAST_TRAVEL_ROUTES: readonly FastTravelRoute[] = [
  { key: 'hub', toMap: 'town_hub', tileX: 25, tileY: 20, label: '小镇中心', description: '回到喷泉广场' },
  {
    key: 'farm',
    toMap: 'farm_north' as FastTravelRoute['toMap'],
    tileX: 10,
    tileY: 8,
    label: '北郊农场',
    description: '回到农场木屋',
  },
];

/**
 * 造一个 `@/data/fastTravel` 的模块 mock：默认透传 actual，
 * `state.routes` 被赋值后改为返回模拟路线表（FAST_TRAVEL_ENABLED 随之翻转）。
 *
 * 刻意用 getter 而非固定值：组件在 setup / 事件回调里读这两个导出，
 * getter 让同一个 spec 文件的不同 describe 能切换世界形态。
 */
export function createFastTravelModuleMock(
  actual: typeof import('@/data/fastTravel'),
  state: FastTravelRouteOverride,
): typeof import('@/data/fastTravel') {
  return {
    ...actual,
    get FAST_TRAVEL_ROUTES(): readonly FastTravelRoute[] {
      return state.routes ?? actual.FAST_TRAVEL_ROUTES;
    },
    get FAST_TRAVEL_ENABLED(): boolean {
      return (state.routes ?? actual.FAST_TRAVEL_ROUTES).length > 1;
    },
  };
}
