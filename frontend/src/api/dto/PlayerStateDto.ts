import type { Direction } from './Direction';

export interface PlayerStateDto {
  playerStateId: number;
  avatarKey: string;
  mapKey: string;
  tileX: number;
  tileY: number;
  direction: Direction;
}
