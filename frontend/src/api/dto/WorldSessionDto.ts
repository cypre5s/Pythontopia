import type { NpcStateDto } from './NpcStateDto';
import type { PlayerStateDto } from './PlayerStateDto';

// R-13：与后端 `WorldAdvanceService.WEATHERS = ["SUNNY","CLOUDY","RAIN","WIND"]` 严格对齐。
// 保留旧的 RAINY/WINDY 字面量，方便老 UI 文案/图标分支无痛迁移；新接收数据走 RAIN/WIND。
export type WorldWeather =
  | 'SUNNY'
  | 'CLOUDY'
  | 'RAIN'
  | 'WIND'
  | 'RAINY'
  | 'WINDY'
  | 'SNOWY'
  | 'STORMY';

export interface WorldSessionDto {
  worldSessionId: number;
  dayIndex: number;
  weather: WorldWeather;
  inGameMinutes: number;
  player: PlayerStateDto;
  npcs: NpcStateDto[];
}
