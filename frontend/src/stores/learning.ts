import { defineStore } from 'pinia';
import { ref } from 'vue';
import { createProfileSlice } from './learning/profileSlice';
import { createFieldSlice } from './learning/fieldSlice';
import { createWorkshopSlice } from './learning/workshopSlice';
import { createPathSlice } from './learning/pathSlice';
import { createQuestsSlice } from './learning/questsSlice';
import { createTrainingSlice } from './learning/trainingSlice';
import { createBugMineSlice } from './learning/bugMineSlice';
import { createEvaluationSlice } from './learning/evaluationSlice';
import { notifyMainQuestMastery } from './learning/notify';
import type { LearnerProfileDto } from '@/api/dto/LearnerProfileDto';
import type { MasterySnapshotDto } from '@/api/dto/MasterySnapshotDto';
import type { LearningPathDto } from '@/api/dto/Path';
import type { QuestDto } from '@/api/dto/Quest';

// 各域类型从切片处统一 re-export，保持原 learning.ts 的公开类型面不变。
export type { FieldState } from './learning/fieldSlice';
export type {
  ResourcesState,
  WorkshopFilters,
  WorkshopRunPhase,
} from './learning/workshopSlice';
export type { PathPhase, PathState } from './learning/pathSlice';
export type { QuestsState } from './learning/questsSlice';
export type { TrainingPhase, TrainingState } from './learning/trainingSlice';
export type { BugMineState } from './learning/bugMineSlice';
export type { EvaluationPhase, EvaluationState } from './learning/evaluationSlice';

/**
 * 学习域聚合 store（setup 形式）。
 *
 * 历史上这里是一个 925 行、横跨 11 个 API 域的 god-store；现已按域拆成
 * `./learning/*Slice.ts` 切片，本文件只负责：
 *   1. 组装切片并注入跨域依赖（如 training AC 后刷新 field 掌握度）；
 *   2. 提供 `setBootstrapData` 这类跨域编排入口；
 *   3. 保持对外 state / action 名称与拆分前完全一致（49 处调用方零改动）。
 *
 * 新增领域逻辑请落到对应切片，不要再往本文件堆业务。
 */
export const useLearningStore = defineStore('learning', () => {
  const activePackId = ref(43);
  const kcs = ref<string[]>([]);
  const path = ref<string[]>([]);

  const questsSlice = createQuestsSlice();
  const fieldSlice = createFieldSlice();
  const profileSlice = createProfileSlice();
  const workshopSlice = createWorkshopSlice();
  const pathSlice = createPathSlice({
    loadTodayQuests: questsSlice.loadTodayQuests,
  });
  const trainingSlice = createTrainingSlice({
    loadTodayQuests: questsSlice.loadTodayQuests,
    refreshAfterEvent: fieldSlice.refreshAfterEvent,
  });
  const bugMineSlice = createBugMineSlice({
    loadTodayQuests: questsSlice.loadTodayQuests,
  });
  const evaluationSlice = createEvaluationSlice({
    loadActivePath: pathSlice.loadActivePath,
    loadTodayQuests: questsSlice.loadTodayQuests,
  });

  /**
   * 由 world store 的 `bootstrap()` 一次性下发后调用，避免页面挂载时 N 次串行 GET。
   * 各字段都是可选：后端在某些场景下（例如老用户没有 path）会留空，
   * 此时保持现有 state 不变，等业务侧再触发对应 lazy load。
   */
  function setBootstrapData(payload: {
    quests?: QuestDto[];
    mastery?: MasterySnapshotDto[];
    profile?: LearnerProfileDto | null;
    path?: LearningPathDto | null;
  }): void {
    if (payload.mastery) {
      fieldSlice.field.value.masterySnapshot = payload.mastery;
      notifyMainQuestMastery(payload.mastery);
    }
    if (payload.profile !== undefined) {
      profileSlice.profile.value = payload.profile;
    }
    if (payload.path !== undefined) {
      pathSlice.pathState.value.active = payload.path;
      pathSlice.pathState.value.phase = payload.path ? 'ready' : 'idle';
    }
    if (payload.quests) {
      questsSlice.quests.value.today = payload.quests;
    }
  }

  return {
    activePackId,
    kcs,
    path,
    setBootstrapData,
    ...profileSlice,
    ...fieldSlice,
    ...workshopSlice,
    ...pathSlice,
    ...questsSlice,
    ...trainingSlice,
    ...bugMineSlice,
    ...evaluationSlice,
  };
});
