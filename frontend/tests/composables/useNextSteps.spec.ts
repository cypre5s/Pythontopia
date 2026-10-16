import { defineComponent, h } from "vue";
import { createPinia, setActivePinia } from "pinia";
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  deriveNextSteps,
  useNextSteps,
  type NextStepsChapter,
} from "@/composables/useNextSteps";
import { fetchLearnerTwinState, type LearnerTwinState } from "@/api/twin";
import { useMainlineStore } from "@stores/mainline";

vi.mock("@/api/twin", () => ({ fetchLearnerTwinState: vi.fn() }));

function chapter(overrides: Partial<NextStepsChapter> = {}): NextStepsChapter {
  return {
    chapter: 1,
    title: "计算思维",
    unlocked: true,
    stars: 0,
    stages: [
      { id: 11, index: 1, title: "认识计算", completed: true },
      { id: 12, index: 2, title: "算法与分解", completed: false },
    ],
    ...overrides,
  };
}

function twinState(overrides: Partial<LearnerTwinState> = {}): LearnerTwinState {
  return {
    assessedKcCount: 0,
    totalKcCount: 65,
    masteredKcCount: 0,
    growthStage: "启程练习",
    kcs: [],
    nextSteps: [],
    ...overrides,
  };
}

const base = { currentChapter: 1, twinState: null } as const;

beforeEach(() => {
  setActivePinia(createPinia());
  vi.mocked(fetchLearnerTwinState).mockReset();
});

describe("deriveNextSteps", () => {
  it("章节数据未就绪或未解锁时返回空（面板回退静态要点）", () => {
    expect(deriveNextSteps({ ...base, chapters: [] })).toEqual([]);
    expect(
      deriveNextSteps({ ...base, chapters: [chapter({ unlocked: false })] }),
    ).toEqual([]);
  });

  it("首条建议是主线断点：第一个未完成小节", () => {
    const steps = deriveNextSteps({ ...base, chapters: [chapter()] });
    expect(steps[0]).toMatchObject({
      kind: "mainline",
      label: "继续 1.2 算法与分解",
      route: { path: "/mainline/stage/12" },
    });
  });

  it("小节全完成且 0★ 时推荐章末大测试", () => {
    const ch = chapter({
      stages: [{ id: 11, index: 1, title: "认识计算", completed: true }],
    });
    const steps = deriveNextSteps({ ...base, chapters: [ch] });
    expect(steps[0]).toMatchObject({
      kind: "chapter-test",
      route: { path: "/chapter-test/1" },
    });
  });

  it("使用 Twin 服务端推荐顺序和原因，不从本地掌握度重排", () => {
    const steps = deriveNextSteps({
      ...base,
      chapters: [chapter()],
      twinState: twinState({
        kcs: [{
          kcCode: "PY43-KC-003", kcName: "循环", chapter: 1, chapterName: "计算思维",
          assessed: false, masteryScore: null, confidence: null, reviewDue: false,
          dueAt: null, lastPracticedAt: null, consecutiveFailures: 0,
          evidenceSources: [],
        }],
        nextSteps: [{
          kcCode: "PY43-KC-003", kcName: "循环", chapter: 1, chapterName: "计算思维",
          difficulty: 1, action: "PRACTICE", reason: "尚未评估，先从基础题建立证据",
          remediationRequired: false,
        }],
      }),
    });
    const weak = steps.find((s) => s.kind === "weak-kc");
    expect(weak).toMatchObject({
      label: "练习「循环」",
      hint: "尚未评估，先从基础题建立证据",
      route: {
        path: "/mainline-map",
        query: { chapter: "1", kcCode: "PY43-KC-003" },
      },
    });
  });

  it("未评估知识点从主线入口开始，不直接送入远征", () => {
    const recommendation: LearnerTwinState = twinState({
      kcs: [{
        kcCode: "PY43-KC-003", kcName: "循环", chapter: 1, chapterName: "计算思维",
        assessed: false, masteryScore: null, confidence: null, reviewDue: false,
        dueAt: null, lastPracticedAt: null, consecutiveFailures: 0,
        evidenceSources: [],
      }],
      nextSteps: [{
        kcCode: "PY43-KC-003", kcName: "循环", chapter: 1, chapterName: "计算思维",
        difficulty: 1, action: "PRACTICE", reason: "尚未评估，先从基础题建立证据",
        remediationRequired: false,
      }],
    });
    const step = deriveNextSteps({ ...base, chapters: [chapter()], twinState: recommendation })
      .find((item) => item.kind === "weak-kc");
    expect(step?.route).toEqual({
      path: "/mainline-map",
      query: { chapter: "1", kcCode: "PY43-KC-003" },
    });
  });

  it("已评估的第 1 章知识点仍回到主线，因为远征不开放第 1 章", () => {
    const recommendation = twinState({
      kcs: [{
        kcCode: "PY43-KC-003", kcName: "循环", chapter: 1, chapterName: "计算思维",
        assessed: true, masteryScore: 0.4, confidence: 0.4, reviewDue: false,
        dueAt: null, lastPracticedAt: null, consecutiveFailures: 0,
        evidenceSources: [],
      }],
      nextSteps: [{
        kcCode: "PY43-KC-003", kcName: "循环", chapter: 1, chapterName: "计算思维",
        difficulty: 1, action: "PRACTICE", reason: "继续巩固", remediationRequired: false,
      }],
    });
    const step = deriveNextSteps({ ...base, chapters: [chapter()], twinState: recommendation })
      .find((item) => item.kind === "weak-kc");

    expect(step?.route).toEqual({
      path: "/mainline-map",
      query: { chapter: "1", kcCode: "PY43-KC-003" },
    });
  });

  it("Twin 推荐到期复习时跳转错题矿洞；没有服务端建议时不伪造弱项", () => {
    const reviewState = twinState({
      kcs: [{
        kcCode: "PY43-KC-004", kcName: "循环", chapter: 1, chapterName: "计算思维",
        assessed: true, masteryScore: 0.2, confidence: 0.8, reviewDue: true,
        dueAt: "2026-10-14T00:00:00Z", lastPracticedAt: null, consecutiveFailures: 0,
        evidenceSources: [],
      }],
      nextSteps: [{
        kcCode: "PY43-KC-004", kcName: "循环", chapter: 1, chapterName: "计算思维",
        difficulty: 1, action: "REVIEW", reason: "FSRS-lite 复习已到期",
        remediationRequired: false,
      }],
    });
    const recommended = deriveNextSteps({
      ...base,
      chapters: [chapter()],
      twinState: reviewState,
    });
    expect(recommended.find((s) => s.kind === "weak-kc")).toMatchObject({
      hint: "FSRS-lite 复习已到期",
      route: {
        path: "/bug-mine",
        query: { review: "PY43-KC-004" },
      },
    });

    const noRecommendation = deriveNextSteps({
      ...base,
      chapters: [chapter()],
    });
    expect(noRecommendation.find((s) => s.kind === "weak-kc")).toBeUndefined();
  });

  it("小节全完成且 1–2★ 时推荐无尽远征补星（带 focus 参数）", () => {
    const ch = chapter({
      stars: 2,
      stages: [{ id: 11, index: 1, title: "认识计算", completed: true }],
    });
    const steps = deriveNextSteps({ ...base, chapters: [ch] });
    expect(steps.find((s) => s.kind === "expedition")).toMatchObject({
      route: { path: "/expedition", query: { focus: "1" } },
    });
  });

  it("媒介偏好为视频且不满足补星条件时推荐本章视频课", () => {
    const steps = deriveNextSteps({
      ...base,
      chapters: [chapter()],
      twinState: twinState({ nextSteps: [{
        kcCode: "PY43-KC-004", kcName: "循环", chapter: 1, chapterName: "计算思维",
        difficulty: 1, action: "PRACTICE", reason: "需继续巩固", remediationRequired: false,
      }] }),
      mediaPriority: ["video", "text"],
    });
    expect(steps.find((s) => s.kind === "preference")).toMatchObject({
      route: { path: "/lecture/1" },
    });
  });

  it("最多返回 3 条", () => {
    const steps = deriveNextSteps({
      ...base,
      chapters: [chapter()],
      twinState: twinState({ nextSteps: [{
        kcCode: "PY43-KC-003", kcName: "循环", chapter: 1, chapterName: "计算思维",
        difficulty: 1, action: "REMEDIATION", reason: "连续练习未通过", remediationRequired: true,
      }] }),
      mediaPriority: ["video"],
    });
    expect(steps).toHaveLength(3);
    expect(steps.map((s) => s.kind)).toEqual([
      "mainline",
      "weak-kc",
      "preference",
    ]);
  });

  it("composable loads the existing Twin state endpoint and keeps the mainline step", async () => {
    vi.mocked(fetchLearnerTwinState).mockResolvedValue(twinState({
      nextSteps: [{
        kcCode: "PY43-KC-003", kcName: "循环", chapter: 1, chapterName: "计算思维",
        difficulty: 1, action: "PRACTICE", reason: "服务端建议", remediationRequired: false,
      }],
    }));
    const mainline = useMainlineStore();
    mainline.overview = {
      totalStars: 0,
      chapters: [{
        chapter: 1, title: "计算思维", unlocked: true, stars: 0, bestTestScore: null,
        stages: [{
          id: 12, index: 2, title: "算法与分解", videoWatchedSec: 0, requiredVideoSec: 0,
          pagesRead: 0, requiredPages: 0, examplesDone: 0, requiredExamples: 0, completed: false,
        }],
        testStatus: "NONE", starBreakdown: {}, recovery: { errorbookDone: false, endlessDone: false },
      }],
    };

    let result!: ReturnType<typeof useNextSteps>;
    const wrapper = mount(defineComponent({
      setup() {
        result = useNextSteps();
        return () => h("div");
      },
    }));
    await flushPromises();

    expect(fetchLearnerTwinState).toHaveBeenCalledOnce();
    expect(result.steps.value.map((step) => step.kind)).toEqual(["mainline", "weak-kc"]);
    expect(result.steps.value[1]?.hint).toBe("服务端建议");
    wrapper.unmount();
  });
});
