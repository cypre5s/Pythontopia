// 可选后端联动（受保护、非阻塞）：POST /api/game/bootstrap。
// 一次往返同时拿到「玩家落点」与「HUD 信息（天数 / 天气 / 今日任务数）」。
// 任何失败都安静返回 null，游戏照常使用地图出生点、隐藏 HUD 状态，不硬依赖网络。
//
// 端点为 POST（见 docs/clients/api-contract.md §4.1 与后端 GameBootstrapController）；
// 鉴权用 wx 本地存储里的 token（与配套学习小程序共享）。
import { BACKEND } from './config';
import { getStorageString, httpPostJson } from './runtime/wx';

export interface SpawnTile {
  tileX: number;
  tileY: number;
}

/** 进入小镇后用于 HUD 的世界/任务摘要（字段缺失即为 null，渲染端按需省略）。 */
export interface HudInfo {
  dayIndex: number | null;
  /** 原始天气码（SUNNY/CLOUDY/RAIN/WIND…），由渲染端本地化。 */
  weather: string | null;
  /** 原始星期码（monday…），由渲染端本地化。 */
  weekday: string | null;
  /** 今日任务数。 */
  questCount: number | null;
}

export interface BootstrapResult {
  spawn: SpawnTile | null;
  hud: HudInfo | null;
}

export async function fetchBootstrap(): Promise<BootstrapResult | null> {
  const token = getStorageString(BACKEND.tokenKey);
  const header = token ? { Authorization: `Bearer ${token}` } : undefined;
  const result = await httpPostJson({
    url: BACKEND.baseUrl + BACKEND.bootstrapPath,
    header,
    body: {},
    timeoutMs: BACKEND.timeoutMs,
  });
  if (!result || result.statusCode < 200 || result.statusCode >= 300) {
    return null;
  }
  return { spawn: extractSpawn(result.data), hud: extractHud(result.data) };
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

function extractHud(data: unknown): HudInfo | null {
  if (!isRecord(data)) {
    return null;
  }
  const world = isRecord(data['world']) ? data['world'] : null;
  const quests = data['quests'];

  const dayIndex = world && typeof world['dayIndex'] === 'number' ? world['dayIndex'] : null;
  const weather = world && typeof world['weather'] === 'string' ? world['weather'] : null;
  const weekday = world && typeof world['weekday'] === 'string' ? world['weekday'] : null;
  const questCount = Array.isArray(quests) ? quests.length : null;

  if (dayIndex === null && weather === null && weekday === null && questCount === null) {
    return null;
  }
  return { dayIndex, weather, weekday, questCount };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
