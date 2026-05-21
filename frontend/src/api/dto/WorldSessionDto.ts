import type { NpcStateDto } from './NpcStateDto';
import type { PlayerStateDto } from './PlayerStateDto';

export interface WorldSessionDto {
  worldSessionId: number;
  dayIndex: number;
  weather: 'SUNNY' | 'RAINY' | 'CLOUDY' | 'WINDY';
  inGameMinutes: number;
  player: PlayerStateDto;
  npcs: NpcStateDto[];
}
