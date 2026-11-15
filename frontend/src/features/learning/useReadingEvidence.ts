import type { Ref } from "vue";
import type { Activity, ActivityProgress, Material } from "../../shared/types";

interface ReadingEvidenceContext {
  activity: Ref<Activity | undefined>;
  material: Ref<Material | undefined>;
  progress: Ref<ActivityProgress | undefined>;
  progressSaving: Ref<boolean>;
  progressError: Ref<string>;
  loading: Ref<boolean>;
  error: Ref<string>;
  hasReadingCompletion: Ref<boolean>;
  currentOwner: () => boolean;
  pageNo: () => number | undefined;
  save: (activityId: string, page: number, materialId: number) => Promise<ActivityProgress>;
}

export function useReadingEvidence({
  activity,
  material,
  progress,
  progressSaving,
  progressError,
  loading,
  error,
  hasReadingCompletion,
  currentOwner,
  pageNo,
  save,
}: ReadingEvidenceContext) {
  let progressGeneration = 0,
    queuedPage = "";
  let pending: Promise<void> = Promise.resolve();
  function persistProgress() {
    pending = saveProgress();
    return pending;
  }
  async function saveProgress() {
    if (
      !currentOwner() ||
      loading.value ||
      error.value ||
      !hasReadingCompletion.value ||
      material.value?.status !== "ready" ||
      !activity.value
    )
      return;
    if (!activity.value.resources.some(row => row.material_id === material.value?.id)) return;
    const page = pageNo();
    if (!page) return;
    const pageKey = `${activity.value.id}:${material.value.id}:${page}`;
    if (queuedPage === pageKey) return pending;
    queuedPage = pageKey;
    const generation = ++progressGeneration;
    progressSaving.value = true;
    progressError.value = "";
    try {
      const saved = await save(activity.value.id, page, material.value.id);
      if (currentOwner() && generation === progressGeneration)
        progress.value = saved;
    } catch (error) {
      if (queuedPage === pageKey) queuedPage = "";
      if (currentOwner() && generation === progressGeneration)
        progressError.value =
          error instanceof Error ? error.message : "阅读位置保存失败，请重试。";
    } finally {
      if (currentOwner() && generation === progressGeneration)
        progressSaving.value = false;
    }
  }

  function resetReading() {
    progressGeneration++;
    queuedPage = "";
    progressSaving.value = false;
    progressError.value = "";
  }
  return { persistProgress, resetReading };
}
