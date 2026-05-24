/**
 * NPC 日程解析：根据当前游戏日+时间，返回 NPC 应该在哪张地图的哪个 tile。
 *
 * 数据来源：data/npcs/<key>/meta.json 的 schedule 字段。
 * 调用者：WorldScene.buildNpcDefinitionFromSpawn 在加载地图时查询。
 */

export interface NpcScheduleSlot {
  from: string;
  map: string;
  tileX: number;
  tileY: number;
}

export type NpcSchedule = Record<string, NpcScheduleSlot[]>;

export interface SchedulePosition {
  map: string;
  tileX: number;
  tileY: number;
}

const WEEKDAYS = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'];

function parseTime(t: string): number {
  const [h, m] = t.split(':').map(Number);
  return (h ?? 0) * 60 + (m ?? 0);
}

/**
 * 从 NPC schedule 中找到当前时间段应该在的位置。
 * 规则：按 `from` 时间降序排列，找第一个 <= currentTime 的 slot。
 */
export function resolveSchedulePosition(
  schedule: NpcSchedule | undefined,
  weekday: string,
  timeOfDay: string,
): SchedulePosition | null {
  if (!schedule) return null;

  const day = weekday.toLowerCase();
  const slots = schedule[day];
  if (!slots || slots.length === 0) return null;

  const currentMinutes = parseTime(timeOfDay);

  const sorted = [...slots].sort((a, b) => parseTime(b.from) - parseTime(a.from));
  for (const slot of sorted) {
    if (currentMinutes >= parseTime(slot.from)) {
      return { map: slot.map, tileX: slot.tileX, tileY: slot.tileY };
    }
  }

  return { map: sorted[sorted.length - 1]!.map, tileX: sorted[sorted.length - 1]!.tileX, tileY: sorted[sorted.length - 1]!.tileY };
}

/**
 * 获取 NPC 在指定地图上的所有日程位置（同一天内），
 * 用于生成简单巡逻路点。
 */
export function getScheduleWaypoints(
  schedule: NpcSchedule | undefined,
  weekday: string,
  mapKey: string,
): { tileX: number; tileY: number }[] {
  if (!schedule) return [];
  const day = weekday.toLowerCase();
  const slots = schedule[day];
  if (!slots) return [];

  return slots
    .filter((s) => s.map === mapKey)
    .map((s) => ({ tileX: s.tileX, tileY: s.tileY }));
}

export function getCurrentWeekday(): string {
  const idx = new Date().getDay();
  return WEEKDAYS[(idx + 6) % 7] ?? 'monday';
}
