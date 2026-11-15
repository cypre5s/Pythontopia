import { api } from "../api";
import type { ActivityProgress } from "../types";

interface ReadingScope {
  owner: () => number | undefined;
  classroom: () => number | null;
  generation: () => number;
}

// One ordered queue owns reading evidence. Callers may wait for a stable tail
// before switching class, leaving an activity, or replacing experience state.
export function createReadingProgress(scope: ReadingScope) {
  let pending: Promise<unknown> = Promise.resolve();
  let latest: { activityId: string; pageNo: number; materialId: number } | null = null;
  function save(activityId: string, pageNo: number, materialId: number): Promise<ActivityProgress> {
    const owner = scope.owner(),
      classroom = scope.classroom(),
      generation = scope.generation();
    latest = { activityId, pageNo, materialId };
    const task = pending
      .catch(() => undefined)
      .then(async () => {
        if (
          owner !== scope.owner() ||
          classroom !== scope.classroom() ||
          generation !== scope.generation()
        ) {
          throw new Error("学习会话已切换。");
        }
        return api.post<ActivityProgress>(
          `/activities/${activityId}/progress`,
          { classroom_id: classroom, page_no: pageNo, material_id: materialId },
        );
      });
    pending = task;
    return task;
  }
  function clear() {
    pending = Promise.resolve();
    latest = null;
  }
  return {
    save,
    clear,
    get pending() {
      return pending;
    },
    get latest() {
      return latest;
    },
  };
}
