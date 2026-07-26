// 前端静态快速旅行路由表。
//
// 后端有 FastTravelService 但尚未暴露 Controller，前端先用本地静态映射；
// 真后端接入后可改为 useFastTravelStore.fetch。

export interface FastTravelRoute {
  /** 路由 key，用作 i18n / 测试稳定 ID */
  key: string;
  /** 目标地图 key（R-14 单 hub：仅 town_hub） */
  toMap: 'town_hub';
  /** 落点 tile，与 index.json spawn 一致 */
  tileX: number;
  tileY: number;
  /** 中文显示名 */
  label: string;
  /** 描述（hover tooltip 用） */
  description: string;
}

// 世界收缩为单一中央小镇 hub，所有学习入口与 NPC 都在 town_hub，
// 不再提供前往外围图的快速旅行（外围图已从主线与导航中弃用）。
export const FAST_TRAVEL_ROUTES: readonly FastTravelRoute[] = [
  { key: 'hub', toMap: 'town_hub', tileX: 25, tileY: 20, label: '小镇中心', description: '回到喷泉广场' },
] as const;

/**
 * 是否值得对玩家暴露「快速旅行」入口。
 *
 * 只剩一条路线时，面板里唯一一行必然是玩家当前所在地图 → 恒被 disabled，
 * 打开面板 = 一个什么都点不了的死路。所以入口（面板 / Tab 轮盘 / HUD 按钮 / T 键 / 指南条目）
 * 统一按本开关渲染；将来恢复多张地图，路线表一加就自动回来。
 */
export const FAST_TRAVEL_ENABLED: boolean = FAST_TRAVEL_ROUTES.length > 1;
