import { describe, expect, it } from "vitest";
import {
  deriveNextSteps,
  type NextStepsChapter,
} from "@/composables/useNextSteps";
import { kcDisplayName } from "@/data/kcNames";
import type { MasterySnapshotDto } from "@/api/dto/MasterySnapshotDto";

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

function mastery(kcCode: string, masteryScore: number): MasterySnapshotDto {
  return {
    kcCode,
    masteryScore,
    confidence: 0.5,
    lastEventAt: null,
    latestSource: null,
  };
}

const base = { currentChapter: 1, mastery: [], weakKcs: [] } as const;

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

  it("画像易错 KC 命中本章时优先于掌握度最低项，并给出工坊练习深链", () => {
    const steps = deriveNextSteps({
      ...base,
      chapters: [chapter()],
      mastery: [mastery("PY43-KC-002", 0.1)],
      weakKcs: ["PY43-KC-030", "PY43-KC-003"],
    });
    const weak = steps.find((s) => s.kind === "weak-kc");
    expect(weak).toMatchObject({
      label: `补强「${kcDisplayName("PY43-KC-003")}」`,
      hint: "画像显示这是你的易错点",
      route: {
        path: "/workshop",
        query: { kc: "PY43-KC-003", type: "PRACTICE" },
      },
    });
  });

  it("无画像易错点时取本章掌握度最低且低于阈值的 KC；达标 KC 不推荐", () => {
    const withWeak = deriveNextSteps({
      ...base,
      chapters: [chapter()],
      mastery: [mastery("PY43-KC-002", 0.55), mastery("PY43-KC-004", 0.2)],
    });
    expect(withWeak.find((s) => s.kind === "weak-kc")).toMatchObject({
      hint: "本章掌握度最低的知识点",
      route: {
        path: "/workshop",
        query: { kc: "PY43-KC-004", type: "PRACTICE" },
      },
    });

    const allGood = deriveNextSteps({
      ...base,
      chapters: [chapter()],
      mastery: [mastery("PY43-KC-002", 0.8)],
    });
    expect(allGood.find((s) => s.kind === "weak-kc")).toBeUndefined();
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
      mastery: [mastery("PY43-KC-004", 0.2)],
      weakKcs: ["PY43-KC-003"],
      mediaPriority: ["video"],
    });
    expect(steps).toHaveLength(3);
    expect(steps.map((s) => s.kind)).toEqual([
      "mainline",
      "weak-kc",
      "preference",
    ]);
  });
});
