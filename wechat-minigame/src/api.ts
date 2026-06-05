// 可选后端联动（受保护、非阻塞）：GET /api/game/bootstrap 拿玩家落点。
// 任何失败都安静返回 null，游戏照常使用地图出生点，不硬依赖网络。
import { getStorageString, httpGetJson } from './runtime/wx';

const BOOTSTRAP_URL = 'http://localhost:18080/api/game/bootstrap';
const TOKEN_KEY = 'pyt_token';

export interface SpawnTile {
  tileX: number;
  tileY: number;
}

export async function fetchBootstrapSpawn(): Promise<SpawnTile | null> {
  const token = getStorageString(TOKEN_KEY);
  const header = token ? { Authorization: `Bearer ${token}` } : undefined;
  const result = await httpGetJson({ url: BOOTSTRAP_URL, header });
  if (!result || result.statusCode < 200 || result.statusCode >= 300) {
    return null;
  }
  return extractSpawn(result.data);
}

function extractSpawn(data: unknown): SpawnTile | null {
  if (!isRecord(data)) {
    return null;
  }
  const player = data['player'];
  if (!isRecord(player)) {
    return null;
  }
  const tileX = player['tileX'];
  const tileY = player['tileY'];
  if (typeof tileX === 'number' && typeof tileY === 'number') {
    return { tileX: Math.floor(tileX), tileY: Math.floor(tileY) };
  }
  return null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
