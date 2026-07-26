import { ref } from 'vue';
import {
  acceptEvaluationPath as acceptEvaluationPathApi,
  getEvaluation,
  keepOldEvaluationPath,
  listEvaluations,
  runEvaluation,
} from '@/api/evaluation';
import type { EvaluationDto, EvaluationRequest } from '@/api/dto/Evaluation';

export type EvaluationPhase = 'idle' | 'running' | 'ready' | 'error';

export interface EvaluationState {
  list: EvaluationDto[];
  current: EvaluationDto | null;
  phase: EvaluationPhase;
  loading: boolean;
  lastError: string | null;
}

export interface EvaluationSliceDeps {
  /** 采纳新路径后刷新当前激活路径。 */
  loadActivePath: () => Promise<void>;
  /** 采纳新路径后刷新今日任务。 */
  loadTodayQuests: () => Promise<void>;
  /** 采纳新路径会归档旧路径 → 刷新「历史路径」列表。 */
  loadArchivedPaths: () => Promise<void>;
}

/**
 * 复盘列表上限：与 ReviewHallPage 的 loadEvaluations(20) 对齐。
 * 「立即评估」后就地插入结果时按同一上限截断，否则会把已加载的历史周报截没。
 */
export const EVALUATION_LIST_LIMIT = 20;

/** 复盘大厅（阶段评估 / 路径再规划建议）切片。 */
export function createEvaluationSlice(deps: EvaluationSliceDeps) {
  const evaluation = ref<EvaluationState>({
    list: [],
    current: null,
    phase: 'idle',
    loading: false,
    lastError: null,
  });

  async function loadEvaluations(limit = 4): Promise<void> {
    evaluation.value.loading = true;
    evaluation.value.lastError = null;
    try {
      evaluation.value.list = await listEvaluations(limit);
    } catch (error) {
      evaluation.value.lastError = error instanceof Error ? error.message : String(error);
    } finally {
      evaluation.value.loading = false;
    }
  }

  async function openEvaluation(id: number): Promise<EvaluationDto | null> {
    evaluation.value.loading = true;
    evaluation.value.lastError = null;
    try {
      const detail = await getEvaluation(id);
      evaluation.value.current = detail;
      return detail;
    } catch (error) {
      evaluation.value.lastError = error instanceof Error ? error.message : String(error);
      return null;
    } finally {
      evaluation.value.loading = false;
    }
  }

  async function runEvaluationNow(request: EvaluationRequest = {}): Promise<EvaluationDto | null> {
    evaluation.value.phase = 'running';
    evaluation.value.lastError = null;
    try {
      const result = await runEvaluation(request);
      evaluation.value.current = result;
      evaluation.value.list = [
        result,
        ...evaluation.value.list.filter((entry) => entry.id !== result.id),
      ].slice(0, EVALUATION_LIST_LIMIT);
      evaluation.value.phase = 'ready';
      return result;
    } catch (error) {
      evaluation.value.lastError = error instanceof Error ? error.message : String(error);
      evaluation.value.phase = 'error';
      return null;
    }
  }

  async function acceptEvaluationPath(id: number): Promise<EvaluationDto | null> {
    evaluation.value.loading = true;
    try {
      const updated = await acceptEvaluationPathApi(id);
      evaluation.value.current = updated;
      evaluation.value.list = evaluation.value.list.map((entry) =>
        entry.id === updated.id ? updated : entry,
      );
      await deps.loadActivePath();
      await deps.loadTodayQuests();
      // 采纳新路径 = 旧路径被归档：不刷新的话「历史路径」页签仍是旧快照。
      try {
        await deps.loadArchivedPaths();
      } catch {
        /* 历史路径刷新失败不影响采纳结果 */
      }
      return updated;
    } catch (error) {
      evaluation.value.lastError = error instanceof Error ? error.message : String(error);
      return null;
    } finally {
      evaluation.value.loading = false;
    }
  }

  async function keepCurrentEvaluationPath(id: number): Promise<EvaluationDto | null> {
    evaluation.value.loading = true;
    try {
      const updated = await keepOldEvaluationPath(id);
      evaluation.value.current = updated;
      evaluation.value.list = evaluation.value.list.map((entry) =>
        entry.id === updated.id ? updated : entry,
      );
      return updated;
    } catch (error) {
      evaluation.value.lastError = error instanceof Error ? error.message : String(error);
      return null;
    } finally {
      evaluation.value.loading = false;
    }
  }

  return {
    evaluation,
    loadEvaluations,
    openEvaluation,
    runEvaluationNow,
    acceptEvaluationPath,
    keepCurrentEvaluationPath,
  };
}
