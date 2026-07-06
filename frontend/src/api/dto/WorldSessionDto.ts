import type { NpcStateDto } from './NpcStateDto';
import type { PlayerStateDto } from './PlayerStateDto';

// 与后端 `WorldAdvanceService.WEATHERS = ["SUNNY","CLOUDY","RAIN","WIND"]` 对齐；
// 旧的 RAINY/WINDY 等字面量仅为兼容老 UI 文案/图标分支，新数据只用 RAIN/WIND。
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
