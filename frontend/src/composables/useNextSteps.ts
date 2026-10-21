// 「下一步」确定性建议：把主线断点、KC 掌握度与学生画像收敛成主界面上
// 最多 3 条可点击行动，替代此前对所有学生一视同仁的静态章节要点。
//
// 主线进度继续来自 mainline store；个性化 KC 推荐使用服务端 Twin 状态，
// 画像仅保留媒介偏好展示，不在前端推断掌握度或重排 KC。
// 数据未就绪时保留主线建议，个性化项暂不显示。

import { computed, onMounted, ref, type ComputedRef } from "vue";
import { useMainlineStore } from "@stores/mainline";
import { useProfileSignals } from "@/composables/useProfileSignals";
import { fetchLearnerTwinState, type LearnerTwinState } from "@/api/twin";

export interface NextStepRoute {
  path: string;
  query?: Record<string, string>;
}

export interface NextStep {
  kind: "mainline" | "chapter-test" | "weak-kc" | "expedition" | "preference";
  /** 行动短语（按钮主文案），如「继续 2.3 顺序与分支」。 */
  label: string;
  /** 一句话推荐理由——画像可解释性：学生能看到「为什么推荐我做这个」。 */
  hint: string;
  route: NextStepRoute;
}

/** 与 mainline store 视图解耦的最小结构（只取推导所需字段）。 */
export interface NextStepsStage {
  id: number;
  index: number;
  title: string;
  completed: boolean;
}

export interface NextStepsChapter {
  chapter: number;
  title: string;
  unlocked: boolean;
  stars: number;
  stages: readonly NextStepsStage[];
}

export interface NextStepsInput {
  chapters: readonly NextStepsChapter[];
  currentChapter: number;
  twinState: LearnerTwinState | null;
  mediaPriority?: readonly string[];
}

/**
 * 推导「下一步」建议（最多 3 条，去重按 kind）：
 * 1. 主线断点：本章第一个未完成小节；全部完成但 0★ → 章末大测试。
 * 2. 个性化练习：使用服务端 Twin 状态中的统一推荐与原因，不在前端重排能力信号。
 * 3. 冲星 / 偏好：小节全完成且 1–2★ → 无尽远征补星；否则按媒介偏好荐视频课。
 */
export function deriveNextSteps(input: NextStepsInput): NextStep[] {
  const chapterView = input.chapters.find(
    (c) => c.chapter === input.currentChapter && c.unlocked,
  );
  if (!chapterView) return [];
  const steps: NextStep[] = [];

  const pendingStage = chapterView.stages.find((s) => !s.completed);
  const allStagesDone = chapterView.stages.length > 0 && !pendingStage;
  if (pendingStage) {
    steps.push({
      kind: "mainline",
      label: `继续 ${chapterView.chapter}.${pendingStage.index} ${pendingStage.title}`,
      hint: "主线进行中",
      route: { path: `/mainline/stage/${pendingStage.id}` },
    });
  } else if (allStagesDone && chapterView.stars === 0) {
    steps.push({
      kind: "chapter-test",
      label: `参加第 ${chapterView.chapter} 章大测试`,
      hint: "小节已全部完成，拿下第 1 颗星",
      route: { path: `/chapter-test/${chapterView.chapter}` },
    });
  }

  const recommendation = input.twinState?.nextSteps[0];
  if (recommendation) {
    const progress = input.twinState?.kcs.find((kc) => kc.kcCode === recommendation.kcCode);
    const label = recommendation.action === "REMEDIATION"
      ? `补强「${recommendation.kcName}」`
      : recommendation.action === "REVIEW"
        ? `复习「${recommendation.kcName}」`
        : `练习「${recommendation.kcName}」`;
    steps.push({
      kind: "weak-kc",
      label,
      hint: recommendation.reason,
      route: progress?.reviewDue
        ? { path: "/bug-mine", query: { review: recommendation.kcCode } }
        : {
          path: "/expedition",
          query: { focus: String(recommendation.chapter), kcCode: recommendation.kcCode },
        },
    });
  }

  if (allStagesDone && chapterView.stars >= 1 && chapterView.stars < 3) {
    steps.push({
      kind: "expedition",
      label: "无尽远征 · 补星",
      hint: `第 ${chapterView.chapter} 章冲刺 3★`,
      route: {
        path: "/expedition",
        query: { focus: String(chapterView.chapter) },
      },
    });
  } else if ((input.mediaPriority?.[0] ?? "") === "video") {
    steps.push({
      kind: "preference",
      label: `看第 ${chapterView.chapter} 章视频课`,
      hint: "按你的视频偏好推荐",
      route: { path: `/lecture/${chapterView.chapter}` },
    });
  }

  return steps.slice(0, 3);
}

export interface UseNextStepsReturn {
  steps: ComputedRef<NextStep[]>;
}

export function useNextSteps(): UseNextStepsReturn {
  const mainline = useMainlineStore();
  const { signals } = useProfileSignals();
  const twinState = ref<LearnerTwinState | null>(null);
  onMounted(() => {
    void fetchLearnerTwinState()
      .then((state) => { twinState.value = state; })
      .catch(() => { twinState.value = null; });
  });
  const steps = computed<NextStep[]>(() =>
    deriveNextSteps({
      chapters: mainline.chapters,
      currentChapter: mainline.firstActionableChapter,
      twinState: twinState.value,
      ...(signals.value.mediaPriority
        ? { mediaPriority: signals.value.mediaPriority }
        : {}),
    }),
  );
  return { steps };
}
