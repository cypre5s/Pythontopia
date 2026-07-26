import { describe, expect, it } from "vitest";
import {
  deriveGroundingStatus,
  groundingInfo,
  hasCoursewareEvidence,
  normalizeAnswerText,
  pageIdsToCitations,
  reviewBadges,
  toCitations,
  type TutorReview,
  type TutorStreamResult,
} from "@/components/workshop/chatAnswerView";

const evidenceChunk = {
  page_id: 7,
  page_no: 12,
  snippet: "for 循环",
  score: 0.9,
};

describe("chatAnswerView", () => {
  it("hasCoursewareEvidence 在 evidence 明细或 page_ids 任一存在时为真", () => {
    expect(hasCoursewareEvidence({})).toBe(false);
    expect(hasCoursewareEvidence({ evidence: [evidenceChunk] })).toBe(true);
    expect(hasCoursewareEvidence({ evidence_page_ids: [7] })).toBe(true);
  });

  it("normalizeAnswerText 有证据时剥掉「基于通识知识」前缀，无证据保留", () => {
    const withEvidence: TutorStreamResult = {
      answer_md: "基于通识知识：for 用于迭代",
      evidence: [evidenceChunk],
    };
    expect(normalizeAnswerText(withEvidence, "")).toBe("for 用于迭代");
    const general: TutorStreamResult = {
      answer_md: "基于通识知识：for 用于迭代",
    };
    expect(normalizeAnswerText(general, "")).toBe("基于通识知识：for 用于迭代");
    expect(normalizeAnswerText({}, "fallback 文本")).toBe("fallback 文本");
  });

  it("deriveGroundingStatus 归一四种线上形态", () => {
    expect(deriveGroundingStatus({ evidence: [evidenceChunk] })).toBe("PASSED");
    expect(
      deriveGroundingStatus({
        evidence: [evidenceChunk],
        grounding_result: { passed: false, reasons: ["页码不符"] },
      }),
    ).toBe("FAILED");
    expect(deriveGroundingStatus({ based_on_general_knowledge: true })).toBe(
      "NO_EVIDENCE",
    );
    expect(
      deriveGroundingStatus({
        grounding_result: { passed: false, reasons: [] },
      }),
    ).toBe("FAILED");
    expect(deriveGroundingStatus({})).toBe("PASSED");
  });

  it("toCitations 优先取明细并只在有数值时带 score；仅 page_ids 时合成兜底", () => {
    const detailed = toCitations({
      evidence: [evidenceChunk, { page_id: 8, page_no: 13 }],
    });
    expect(detailed).toEqual([
      { pageId: 7, pageNo: 12, snippet: "for 循环", score: 0.9 },
      { pageId: 8, pageNo: 13, snippet: "" },
    ]);
    expect(toCitations({ evidence_page_ids: [3, 4] })).toEqual([
      { pageId: 3, pageNo: 0, snippet: "" },
      { pageId: 4, pageNo: 0, snippet: "" },
    ]);
    expect(pageIdsToCitations(undefined)).toEqual([]);
  });

  it("groundingInfo 已知状态映射中文标签，未知状态原样透传", () => {
    expect(groundingInfo("PASSED")).toEqual({
      text: "引用充分",
      cls: "g--passed",
    });
    expect(groundingInfo("WEIRD")).toEqual({ text: "WEIRD", cls: "" });
  });

  it("reviewBadges 产出三枚固定顺序徽章并把 reasons 汇入 title", () => {
    const review: TutorReview = {
      grounding: { passed: true, reasons: [] },
      hallucination: { passed: false, reasons: ["数字无据", " "] },
      safety: { passed: true, reasons: [""] },
    };
    const badges = reviewBadges(review);
    expect(badges.map((b) => b.key)).toEqual([
      "grounding",
      "hallucination",
      "safety",
    ]);
    expect(badges[0]?.title).toBe("审查官（引用）：已通过");
    expect(badges[1]?.passed).toBe(false);
    expect(badges[1]?.title).toBe("校对官（防幻觉）：未通过\n数字无据");
  });
});
