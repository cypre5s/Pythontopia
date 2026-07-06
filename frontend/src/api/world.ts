import { http } from './http';
import type { PlayerStateDto } from './dto/PlayerStateDto';
import type { WorldSessionDto } from './dto/WorldSessionDto';
import type { Direction } from './dto/Direction';

export interface PlayerStatePatch {
  tileX: number;
  tileY: number;
  direction: Direction;
}

export async function fetchWorldSession(): Promise<WorldSessionDto> {
  const response = await http.get<WorldSessionDto>('/api/world/session');
  return response.data;
}

export async function patchPlayerState(req: PlayerStatePatch): Promise<PlayerStateDto> {
  const response = await http.patch<PlayerStateDto>('/api/world/player', req);
  return response.data;
}
