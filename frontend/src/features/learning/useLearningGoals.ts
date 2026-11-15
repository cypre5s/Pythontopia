import { computed, ref, watch } from "vue";
import type { Ref } from "vue";
import type { Activity, ActivityProgress, Material } from "../../shared/types";
import type { LectureSegment } from "../../shared/lectureTimeline";

export function useLearningGoals(
  activity: Ref<Activity | undefined>,
  material: Ref<Material | undefined>,
  _progress: Ref<ActivityProgress | undefined>,
) {
  const resource = computed(() => activity.value?.resources.find(row => row.material_id === material.value?.id));
  const hasReadingCompletion = computed(() => Boolean(resource.value && material.value?.kind !== "video"));
  const hasCompletion = computed(() => Boolean(activity.value?.resources.length));
  const learningGoals = computed(() => activity.value ? [] : [{ title: material.value?.title ?? "当前资料", detail: "按照自己的节奏阅读，可使用快速跳转定位内容。" }]);
  const videoOutline = computed(() =>
    material.value?.kind === "video"
      ? [
          { timeSeconds: 0, title: "视频开始" },
          ...(resource.value?.learning.highlights?.video ?? []).map(
            (item) => ({ timeSeconds: item.start_seconds, title: item.label }),
          ),
        ]
      : [],
  );
  const lectureSegments = ref<LectureSegment[]>([]);
  watch(
    () => material.value?.id,
    () => {
      lectureSegments.value = [];
    },
  );
  const documentOutline = computed(() =>
    material.value?.kind === "video"
      ? []
      : (material.value?.pages.map((page) => {
          const highlight = resource.value?.learning.highlights?.pages?.find(
            (item) =>
              page.page_no >= item.start_page && page.page_no <= item.end_page,
          );
          return {
            pageNo: page.page_no,
            title: page.title,
            important: Boolean(highlight),
            highlightLabel: highlight?.label,
            timeSeconds: lectureSegments.value.find(
              (item) => item.pageNo === page.page_no,
            )?.start,
          };
        }) ?? []),
  );
  return {
    hasReadingCompletion,
    hasCompletion,
    learningGoals,
    videoOutline,
    documentOutline,
    lectureSegments,
  };
}
