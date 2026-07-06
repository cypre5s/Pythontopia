import type { CurriculumBootstrapResult } from './CurriculumBootstrapResult';
import type { LearnerProfileDto } from './LearnerProfileDto';
import type { LearnerSkillDto } from './LearnerSkillDto';
import type { LearningPathDto } from './Path';
import type { MasterySnapshotDto } from './MasterySnapshotDto';
import type { NpcDialogStateDto } from './NpcDialogStateDto';
import type { PlayerStateDto } from './PlayerStateDto';
import type { QuestDto } from './Quest';
import type { SaveSlotDto } from './SaveSlotDto';
import type { SkillNodeDto } from './SkillNodeDto';
import type { WorldCalendarDto } from './WorldCalendarDto';
import type { WorldEventDto } from './WorldEventDto';
import type { WorldSessionDto } from './WorldSessionDto';

/**
 * 一次性返回首次进入世界场景所需的全量状态，对齐后端 POST /api/game/bootstrap。
 * 命名无法表达的字段语义：
 *   - npcStates：R-14 hub 的 NPC 基础状态；已交互 NPC 会带 currentNodeId / completedNodeIds；
 *   - activeSlot / saveSlots：当前存档槽 + 全部存档；
 *   - curriculum：可选，仅首次播种课程时返回。
 */
export interface GameBootstrapResponseDto {
  world: WorldSessionDto;
  player: PlayerStateDto;
  quests: QuestDto[];
  mastery: MasterySnapshotDto[];
  profile: LearnerProfileDto | null;
  // 后端实际下发节点数组：path 维持对象 union 形态，节点数组由 pathNodes 承接，world store 解析时二选一。
  path: LearningPathDto | null;
  pathNodes?: unknown[];
  skills: LearnerSkillDto[];
  skillsCatalog?: SkillNodeDto[];
  calendar: WorldCalendarDto;
  /** 后端实际字段名为 pendingEvents，events 是旧前端别名 */
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
  /** 后端实际字段名为 newEvents，events 是旧前端别名 */
  events?: WorldEventDto[];
  newEvents?: WorldEventDto[];
}
