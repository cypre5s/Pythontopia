import type { CropTileDto } from './CropTileDto';
import type { CurriculumBootstrapResult } from './CurriculumBootstrapResult';
import type { InventoryItemDto } from './InventoryItemDto';
import type { LearnerProfileDto } from './LearnerProfileDto';
import type { LearnerSkillDto } from './LearnerSkillDto';
import type { LearningPathDto } from './Path';
import type { MasterySnapshotDto } from './MasterySnapshotDto';
import type { NpcDialogStateDto } from './NpcDialogStateDto';
import type { PlayerCurrencyDto } from './PlayerCurrencyDto';
import type { PlayerStateDto } from './PlayerStateDto';
import type { QuestDto } from './Quest';
import type { SaveSlotDto } from './SaveSlotDto';
import type { SkillNodeDto } from './SkillNodeDto';
import type { WorldCalendarDto } from './WorldCalendarDto';
import type { WorldEventDto } from './WorldEventDto';
import type { WorldSessionDto } from './WorldSessionDto';

/**
 * 一次性返回首次进入世界场景需要的全量状态。
 *
 * 与 W3-CTRL 后端 POST /api/game/bootstrap 对齐：
 *   - world / player：世界与玩家状态（替代旧的 /api/world/session）
 *   - crops / mastery / profile / path / quests：复用 learning 域已有 DTO
 *   - inventory / skills / skillsCatalog / currency：M4 新增 RPG 存档
 *   - calendar / events：天 / 季节 / 待处理事件
 *   - npcStates：已交互过的 NPC 状态（未交互过的不会出现）
 *   - activeSlot / saveSlots：当前存档槽 + 全部存档
 *   - curriculum：可选，仅首次播种时返回
 */
export interface GameBootstrapResponseDto {
  world: WorldSessionDto;
  player: PlayerStateDto;
  crops: CropTileDto[];
  quests: QuestDto[];
  mastery: MasterySnapshotDto[];
  profile: LearnerProfileDto | null;
  // 后端实际返回 LearningPathNodeDto[]，老前端期望 LearningPathDto 整体对象。
  // 这里保留旧 union，新增 pathNodes 字段承接节点数组，由 world store 解析时择一使用。
  path: LearningPathDto | null;
  pathNodes?: unknown[];
  inventory: InventoryItemDto[];
  skills: LearnerSkillDto[];
  skillsCatalog?: SkillNodeDto[];
  currency: PlayerCurrencyDto;
  calendar: WorldCalendarDto;
  /** R-13：后端实际字段名是 pendingEvents，events 是旧前端别名 */
  events?: WorldEventDto[];
  pendingEvents?: WorldEventDto[];
  npcStates?: NpcDialogStateDto[];
  activeSlot: SaveSlotDto | null;
  saveSlots?: SaveSlotDto[];
  curriculum?: CurriculumBootstrapResult;
}

/**
 * POST /api/world/sleep 推进一天后的轻量响应。
 *
 * world 字段只携带「需要 patch 进 session 的子集」（dayIndex/weather/inGameMinutes/...），
 * 由 world store 用 spread 合并到现有 session 上，不需要全量替换 player/npcs。
 */
export interface WorldSleepResponseDto {
  world: Pick<WorldSessionDto, 'worldSessionId' | 'dayIndex' | 'weather' | 'inGameMinutes'>;
  calendar?: WorldCalendarDto;
  /** R-13：后端实际字段名是 newEvents，events 是旧前端别名 */
  events?: WorldEventDto[];
  newEvents?: WorldEventDto[];
}
