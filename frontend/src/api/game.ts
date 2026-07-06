import { http } from './http';
import type {
  GameBootstrapResponseDto,
  WorldSleepResponseDto,
} from './dto/GameBootstrapResponseDto';

export async function fetchGameBootstrap(): Promise<GameBootstrapResponseDto> {
  const response = await http.post<GameBootstrapResponseDto>('/api/game/bootstrap');
  return response.data;
}

export async function postWorldSleep(): Promise<WorldSleepResponseDto> {
  const response = await http.post<WorldSleepResponseDto>('/api/world/sleep');
  return response.data;
}
