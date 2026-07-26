import { ref } from 'vue';
import {
  getActiveLearningPath,
  getArchivedLearningPaths,
  planLearningPath,
} from '@/api/path';
import type { LearningPathDto } from '@/api/dto/Path';

export type PathPhase = 'idle' | 'planning' | 'ready' | 'error';

export interface PathState {
  active: LearningPathDto | null;
  archived: LearningPathDto[];
  phase: PathPhase;
  loading: boolean;
  lastError: string | null;
}

export interface PathSliceDeps {
  /** 规划成功后刷新今日任务（PathPlannerAgent 会顺带派发任务）。 */
  loadTodayQuests: () => Promise<void>;
}

/** 个性化学习路径（PathPlannerAgent 产物）切片。 */
export function createPathSlice(deps: PathSliceDeps) {
  const pathState = ref<PathState>({
    active: null,
    archived: [],
    phase: 'idle',
    loading: false,
    lastError: null,
  });

  async function loadActivePath(): Promise<void> {
    pathState.value.loading = true;
    pathState.value.lastError = null;
    try {
      pathState.value.active = await getActiveLearningPath();
      pathState.value.phase = pathState.value.active ? 'ready' : 'idle';
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      pathState.value.lastError = message;
      pathState.value.phase = 'error';
    } finally {
      pathState.value.loading = false;
    }
  }

  async function loadArchivedPaths(): Promise<void> {
    pathState.value.archived = await getArchivedLearningPaths();
  }

  async function planPath(): Promise<void> {
    pathState.value.phase = 'planning';
    pathState.value.lastError = null;
    try {
      const response = await planLearningPath();
      pathState.value.active = response.path;
      pathState.value.phase = 'ready';
      await deps.loadTodayQuests();
      // 重新规划会归档上一版路径（response.archivedPathIds）：不刷新的话
      //「历史路径」页签永远看不到刚归档的那版（首次规划因列表为空恰好蒙对）。
      try {
        await loadArchivedPaths();
      } catch {
        /* 历史路径刷新失败不影响本次规划结果 */
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      pathState.value.lastError = message;
      pathState.value.phase = 'error';
      throw error;
    }
  }

  return {
    pathState,
    loadActivePath,
    loadArchivedPaths,
    planPath,
  };
}
