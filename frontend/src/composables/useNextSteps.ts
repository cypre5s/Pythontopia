// 「下一步」确定性建议：把主线断点、KC 掌握度与学生画像收敛成主界面上
// 最多 3 条可点击行动，替代此前对所有学生一视同仁的静态章节要点。
//
// 只做纯规则推导（零 LLM、零新请求）——三个数据源都已由现有链路注水：
//   主线进度  mainline store（HudShell 挂载即 load）
//   KC 掌握度 learning.field.masterySnapshot（world bootstrap 下发）
//   画像信号  useProfileSignals（易错 KC / 媒介偏好）
// 数据未就绪时返回空数组，面板回退到静态章节要点，行为向后兼容。

import { computed, type ComputedRef } from "vue";
import { useLearningStore } from "@stores/learning";
import { useMainlineStore } from "@stores/mainline";
import { useProfileSignals } from "@/composables/useProfileSignals";
import { CHAPTERS, parseKcNumber } from "@/data/chapters";
import { kcDisplayName } from "@/data/kcNames";
import type { MasterySnapshotDto } from "@/api/dto/MasterySnapshotDto";

/** 掌握度低于该阈值的 KC 才值得占用主界面一条建议位。 */
const WEAK_MASTERY_THRESHOLD = 0.6;

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
  mastery: readonly MasterySnapshotDto[];
  weakKcs: readonly string[];
  mediaPriority?: readonly string[];
}

function chapterKcRange(
  chapter: number,
): { start: number; end: number } | null {
  const entry = CHAPTERS.find((c) => c.order === chapter);
  return entry ? { start: entry.startKc, end: entry.endKc } : null;
}

/** 本章内最值得补的 KC：画像易错点优先，其次快照里掌握度最低且低于阈值的。 */
function pickWeakKc(
  chapter: number,
  mastery: readonly MasterySnapshotDto[],
  weakKcs: readonly string[],
): { code: string; fromProfile: boolean } | null {
  const range = chapterKcRange(chapter);
  if (!range) return null;
  const inChapter = (code: string): boolean => {
    const num = parseKcNumber(code);
    return num !== null && num >= range.start && num <= range.end;
  };
  const profileHit = weakKcs.find(inChapter);
  if (profileHit) return { code: profileHit, fromProfile: true };
  const weakest = mastery
    .filter(
      (m) => inChapter(m.kcCode) && m.masteryScore < WEAK_MASTERY_THRESHOLD,
    )
    .sort((a, b) => a.masteryScore - b.masteryScore)[0];
  return weakest ? { code: weakest.kcCode, fromProfile: false } : null;
}

/**
 * 推导「下一步」建议（最多 3 条，去重按 kind）：
 * 1. 主线断点：本章第一个未完成小节；全部完成但 0★ → 章末大测试。
 * 2. 弱项补强：画像易错 KC ∩ 本章，否则本章掌握度最低（<0.6）→ 工坊定向练习深链。
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

  const weak = pickWeakKc(input.currentChapter, input.mastery, input.weakKcs);
  if (weak) {
    steps.push({
      kind: "weak-kc",
      label: `补强「${kcDisplayName(weak.code)}」`,
      hint: weak.fromProfile
        ? "画像显示这是你的易错点"
        : "本章掌握度最低的知识点",
      route: { path: "/workshop", query: { kc: weak.code, type: "PRACTICE" } },
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
  const learning = useLearningStore();
  const { signals, weakKcs } = useProfileSignals();
  const steps = computed<NextStep[]>(() =>
    deriveNextSteps({
      chapters: mainline.chapters,
      currentChapter: mainline.firstActionableChapter,
      mastery: learning.field.masterySnapshot,
      weakKcs: weakKcs.value,
      ...(signals.value.mediaPriority
        ? { mediaPriority: signals.value.mediaPriority }
        : {}),
    }),
  );
  return { steps };
}
