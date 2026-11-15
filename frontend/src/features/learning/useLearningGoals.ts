import { computed, ref, watch } from "vue";
import type { Ref } from "vue";
import type { Activity, ActivityProgress, Material } from "../../shared/types";
import type { LectureSegment } from "../../shared/lectureTimeline";

export function useLearningGoals(
  activity: Ref<Activity | undefined>,
  material: Ref<Material | undefined>,
  progress: Ref<ActivityProgress | undefined>,
) {
  const hasReadingCompletion = computed(() =>
    Boolean(activity.value?.completion || activity.value?.learning?.reading),
  );
  const hasCompletion = computed(
    () =>
      hasReadingCompletion.value || Boolean(activity.value?.learning?.video),
  );
  const requiredPageIds = computed(
    () =>
      progress.value?.required_page_ids ??
      activity.value?.completion?.required_page_ids ??
      material.value?.pages
        .filter((page) => {
          const rule = activity.value?.learning?.reading;
          return (
            rule &&
            (rule.scope === "all" ||
              (page.page_no >= (rule.start_page ?? 1) &&
                page.page_no <= (rule.end_page ?? Infinity)))
          );
        })
        .map((page) => Number(page.id)) ??
      [],
  );
  const requiredVisited = computed(
    () =>
      progress.value?.read_count ??
      requiredPageIds.value.filter((id) =>
        progress.value?.visited_pages.includes(id),
      ).length,
  );
  const requiredPages = computed(
    () =>
      material.value?.pages.filter((page) =>
        requiredPageIds.value.includes(Number(page.id)),
      ) ?? [],
  );
  const learningGoals = computed(() => {
    const title = material.value?.title ?? "当前资料",
      learning = activity.value?.learning;
    if (learning?.video) {
      const watched = Math.floor(progress.value?.watched_seconds ?? 0),
        required =
          progress.value?.required_seconds ?? learning.video.minimum_seconds;
      return [
        {
          title: `观看《${title}》`,
          detail: `有效播放不少于 ${formatTime(required)}，跳转片段不计观看时长。`,
          completed: progress.value?.completed ?? false,
          progress: {
            value: watched,
            max: required,
            label: `有效观看 ${formatTime(watched)} / ${formatTime(required)}`,
          },
          items: (learning.highlights?.video ?? []).map((item) => ({
            title: `${item.label} · ${formatTime(item.start_seconds)}–${formatTime(item.end_seconds)}`,
            timeSeconds: item.start_seconds,
          })),
        },
      ];
    }
    const total = progress.value?.read_total ?? requiredPageIds.value.length;
    return [
      {
        title: `${material.value?.kind === "video" ? "观看" : "阅读"}《${title}》`,
        detail: hasReadingCompletion.value
          ? learning?.reading?.scope === "all"
            ? "阅读完整份课件。"
            : "阅读本次指定的页面。"
          : material.value?.kind === "video"
            ? "课程视频可按自己的节奏观看，不计正式成绩。"
            : `共 ${material.value?.pages.length ?? 0} 页，可使用底部分页继续阅读。`,
        completed:
          hasReadingCompletion.value && Boolean(progress.value?.completed),
        ...(hasReadingCompletion.value
          ? {
              progress: {
                value: requiredVisited.value,
                max: total,
                label: `已阅读 ${requiredVisited.value} / ${total} 页`,
              },
            }
          : {}),
        items: requiredPages.value.map((page) => ({
          title: page.title || `第 ${page.page_no} 页`,
          pageNo: page.page_no,
          completed:
            progress.value?.visited_pages.includes(Number(page.id)) ?? false,
        })),
      },
    ];
  });
  const videoOutline = computed(() =>
    material.value?.kind === "video"
      ? [
          { timeSeconds: 0, title: "视频开始" },
          ...(activity.value?.learning?.highlights?.video ?? []).map(
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
          const highlight = activity.value?.learning?.highlights?.pages?.find(
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
  function formatTime(value: number) {
    const seconds = Math.max(0, Math.floor(value));
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
  }

  return {
    hasReadingCompletion,
    hasCompletion,
    learningGoals,
    videoOutline,
    documentOutline,
    lectureSegments,
  };
}
