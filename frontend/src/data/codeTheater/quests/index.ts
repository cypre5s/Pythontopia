/**
 * 代码剧场具身任务公共入口（C-quest-sample 对外门面）。
 *
 * 集成点：stage/quest UI 用 {@link getQuestById} / {@link CODE_THEATER_QUESTS}
 * 加载任务；学习预览阶段用 {@link checkQuestWorldGoal} 判定世界条件，再用
 * {@link toWorldEffectCommands} 把结果驱动成 `world_effect` 渲染指令。
 */

import type { CodeTheaterQuest } from './types';
import { WATER_DRY_FIELDS_QUEST } from './waterDryFields';

/** 当前可用的具身任务清单（MVP 仅样板任务）。 */
export const CODE_THEATER_QUESTS: readonly CodeTheaterQuest[] = [WATER_DRY_FIELDS_QUEST];

/** 按 id 取任务；不存在返回 undefined。 */
export function getQuestById(id: string): CodeTheaterQuest | undefined {
  return CODE_THEATER_QUESTS.find((quest) => quest.id === id);
}

export type {
  ActedTargetExtractor,
  CodeTheaterQuest,
  CoverageWorldGoal,
  WorldCheckResult,
  WorldGoal,
  WorldGoalKind,
} from './types';
export {
  checkCoverageGoal,
  checkQuestWorldGoal,
  extractActedTargets,
} from './worldConditionChecker';
export { toWorldEffectCommands } from './worldEffects';
export type { FieldEffectState, WorldEffectOptions } from './worldEffects';
export { WATER_DRY_FIELDS_QUEST } from './waterDryFields';
