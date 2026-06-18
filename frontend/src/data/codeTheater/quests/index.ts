/**
 * 代码剧场具身任务公共入口（C-quest-sample 对外门面）。
 *
 * 集成点：stage/quest UI 用 {@link getQuestById} / {@link CODE_THEATER_QUESTS}
 * 加载任务；学习预览阶段用 {@link checkQuestWorldGoal} 判定世界条件，再用
 * {@link toWorldEffectCommands} 把结果驱动成 `world_effect` 渲染指令。
 */

import type { CodeTheaterQuest } from './types';
import { LIGHT_STREET_LAMPS_QUEST } from './lightStreetLamps';
import { OPEN_EVEN_GATES_QUEST } from './openEvenGates';
import { FERRY_TRAVELERS_QUEST } from './ferryTravelers';
import { DELIVER_MAIL_ROUTE_QUEST } from './deliverMailRoute';
import { CLEAN_CINEMA_SEATS_QUEST } from './cleanCinemaSeats';
import { REKEY_ODD_LOCKERS_QUEST } from './rekeyOddLockers';
import { SHELVE_RETURNED_BOOKS_QUEST } from './shelveReturnedBooks';
import { LIGHT_BEACON_CHAIN_QUEST } from './lightBeaconChain';

/**
 * 当前可用的具身任务清单（按 KC 难度递进）：
 * coverage：for+range → for+if → while → 列表遍历 → 嵌套循环 → range 步长 → 列表 append；
 * sequence：从最远烽火台逆序点回（顺序覆盖 + range 负步长）。
 * 每个任务的世界动作都在自身 starterCode 里 `def` 出来，故可真实 Pyodide 执行。
 */
export const CODE_THEATER_QUESTS: readonly CodeTheaterQuest[] = [
  LIGHT_STREET_LAMPS_QUEST,
  OPEN_EVEN_GATES_QUEST,
  FERRY_TRAVELERS_QUEST,
  DELIVER_MAIL_ROUTE_QUEST,
  CLEAN_CINEMA_SEATS_QUEST,
  REKEY_ODD_LOCKERS_QUEST,
  SHELVE_RETURNED_BOOKS_QUEST,
  LIGHT_BEACON_CHAIN_QUEST,
];

/** 按 id 取任务；不存在返回 undefined。 */
export function getQuestById(id: string): CodeTheaterQuest | undefined {
  return CODE_THEATER_QUESTS.find((quest) => quest.id === id);
}

export type {
  ActedTargetExtractor,
  CodeTheaterQuest,
  CoverageWorldGoal,
  SequenceWorldGoal,
  WorldCheckResult,
  WorldGoal,
  WorldGoalKind,
} from './types';
export {
  checkCoverageGoal,
  checkQuestWorldGoal,
  checkSequenceGoal,
  extractActedTargets,
} from './worldConditionChecker';
export { toWorldEffectCommands } from './worldEffects';
export type { TargetEffectState, WorldEffectOptions } from './worldEffects';
export { LIGHT_STREET_LAMPS_QUEST } from './lightStreetLamps';
export { OPEN_EVEN_GATES_QUEST } from './openEvenGates';
export { FERRY_TRAVELERS_QUEST } from './ferryTravelers';
export { DELIVER_MAIL_ROUTE_QUEST } from './deliverMailRoute';
export { CLEAN_CINEMA_SEATS_QUEST } from './cleanCinemaSeats';
export { REKEY_ODD_LOCKERS_QUEST } from './rekeyOddLockers';
export { SHELVE_RETURNED_BOOKS_QUEST } from './shelveReturnedBooks';
export { LIGHT_BEACON_CHAIN_QUEST } from './lightBeaconChain';
