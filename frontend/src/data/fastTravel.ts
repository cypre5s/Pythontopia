// R-13 迭代 18：前端静态快速旅行路由表。
//
// 与后端 V119 `fast_travel_route` seed（11 条）对齐，但 FastTravelService 还没暴露
// Controller，前端先用本地静态映射。真后端接入后改为 useFastTravelStore.fetch。

export interface FastTravelRoute {
  /** 路由 key，用作 i18n / 测试稳定 ID */
  key: string;
  /** 目标地图 key（必须在 SUPPORTED_MAP_KEYS 中） */
  toMap: 'town_hub' | 'farm_north' | 'mine_south' | 'campus_east' | 'port_west';
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

export const FAST_TRAVEL_ROUTES: readonly FastTravelRoute[] = [
  { key: 'hub', toMap: 'town_hub', tileX: 25, tileY: 20, label: '小镇中心', description: '回到喷泉广场', costCoins: 0 },
  { key: 'farm', toMap: 'farm_north', tileX: 20, tileY: 37, label: '知识农场', description: '种植 / 浇水 / 收获 KC', costCoins: 5 },
  { key: 'mine', toMap: 'mine_south', tileX: 15, tileY: 2, label: 'Bug 矿洞', description: '调试副本入口', costCoins: 5 },
  { key: 'campus', toMap: 'campus_east', tileX: 2, tileY: 15, label: '校园广场', description: '图书馆 / 教室 / 复盘大厅', costCoins: 5 },
  { key: 'port', toMap: 'port_west', tileX: 33, tileY: 12, label: '码头', description: '商船 / 班级广场', costCoins: 5 },
] as const;
