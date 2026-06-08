// 前端静态快速旅行路由表。
//
// 后端有 FastTravelService 但尚未暴露 Controller，前端先用本地静态映射；
// 真后端接入后可改为 useFastTravelStore.fetch。

export interface FastTravelRoute {
  /** 路由 key，用作 i18n / 测试稳定 ID */
  key: string;
  /** 目标地图 key（必须在 SUPPORTED_MAP_KEYS 中） */
  toMap: 'town_hub' | 'mine_south';
  /** 落点 tile，与 index.json spawn 一致 */
  tileX: number;
  tileY: number;
  /** 中文显示名 */
  label: string;
  /** 描述（hover tooltip 用） */
  description: string;
  /** 单次传送消耗金币（教学目标：让玩家思考是否值得） */
  costCoins: number;
}

// 世界收缩为单一中央小镇 hub，所有学习入口与 NPC 都在 town_hub，
// 不再提供前往外围图的快速旅行（外围图已从主线与导航中弃用）。
export const FAST_TRAVEL_ROUTES: readonly FastTravelRoute[] = [
  { key: 'hub', toMap: 'town_hub', tileX: 25, tileY: 20, label: '小镇中心', description: '回到喷泉广场', costCoins: 0 },
] as const;
