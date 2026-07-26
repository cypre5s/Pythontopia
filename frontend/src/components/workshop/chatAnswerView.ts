// 百晓工坊答案气泡的纯视图投影：graph SSE result（snake_case 直传）→ 前端渲染模型。
// 注意与 TutorPanel 的 TutorStreamResult（/api/tutor/ask 的 camelCase 契约）是两种不同线格式，
// 不要合并——这里的形状必须与 graph `_build_result` 保持 snake_case 对齐。
import type { EvidenceCitation } from "@/api/tutor";
import type { TutorVisualAid } from "@/components/tutor/TutorVisualAids.vue";

/** 单道审查结果（与 graph `_build_result` 的 review.* 同形：passed + reasons）。 */
export interface TutorReviewItem {
  passed: boolean;
  reasons: string[];
}

/** 三道审查明细：审查官（引用）/ 校对官（防幻觉）/ 安全员（安全）。 */
export interface TutorReview {
  grounding: TutorReviewItem;
  hallucination: TutorReviewItem;
  safety: TutorReviewItem;
}

/**
 * 导师答疑 SSE 的 `result` 事件载荷——后端按原样转发 graph 的 snake_case JSON，
 * 故字段**必须**用 snake_case 对齐（写成 camelCase 会导致接地状态 / 证据不渲染）。
 */
export interface TutorStreamResult {
  answer_md?: string;
  evidence_page_ids?: number[];
  /** 命中的课件证据明细（页码 + 原文片段）。 */
  evidence?: Array<{
    page_id: number;
    page_no: number;
    snippet?: string;
    score?: number;
  }>;
  grounding_result?: { passed: boolean; reasons: string[] };
  based_on_general_knowledge?: boolean;
  safety_result?: { passed: boolean; reasons: string[] };
  visual_aids?: TutorVisualAid[];
  /** 三道审查明细（导师 → 审查官 → 校对官 → 安全员），供答案气泡渲染「三审徽章」。 */
  review?: TutorReview;
  /** 「课件证据」整组一句话总览说明（结合提问/上下文/画像；可空）。 */
  evidence_note?: string;
  /** 一句话说明本答用了哪些画像信号（「✨ 为你定制」标签 title；可空）。 */
  personalization_note?: string;
  /**
   * 考考我（多轮填空，契约 C4）：
   * 出题 phase='ask' → {round, stem(含 (1)(2) 空位), blanks:[{index,hint}], cite_evidence:false}；
   * 评卷 phase='judge' → {verdict:'correct|partial|wrong', cite_evidence}（命中课件才引 PPT）。
   */
  quiz?: {
    phase: string;
    round?: number;
    stem?: string;
    blanks?: Array<{ index: number; hint?: string }>;
    verdict?: string;
    cite_evidence?: boolean;
  };
}

export function hasCoursewareEvidence(r: TutorStreamResult): boolean {
  return (
    (r.evidence?.length ?? 0) > 0 || (r.evidence_page_ids?.length ?? 0) > 0
  );
}

const GENERAL_KNOWLEDGE_PREFIX_RE = /^\s*基于通识知识\s*[：:]\s*/;

/** 有课件证据时，清掉模型或旧后端残留的「基于通识知识」前缀，避免正文与证据面板自相矛盾。 */
export function normalizeAnswerText(
  r: TutorStreamResult,
  fallback: string,
): string {
  const text = r.answer_md || fallback;
  return hasCoursewareEvidence(r)
    ? text.replace(GENERAL_KNOWLEDGE_PREFIX_RE, "")
    : text;
}

/** 由 graph 的 grounding_result / based_on_general_knowledge / 实际证据归一成前端 g-chip 用的状态串。 */
export function deriveGroundingStatus(r: TutorStreamResult): string {
  if (hasCoursewareEvidence(r)) {
    return r.grounding_result && r.grounding_result.passed === false
      ? "FAILED"
      : "PASSED";
  }
  if (r.based_on_general_knowledge) return "NO_EVIDENCE";
  if (r.grounding_result && r.grounding_result.passed === false)
    return "FAILED";
  return "PASSED";
}

/** 把 result.evidence（snake_case）投影成「课件证据」面板用的 EvidenceCitation（camelCase）。 */
export function toCitations(r: TutorStreamResult): EvidenceCitation[] {
  const fromEvidence = (r.evidence ?? []).map((e) => {
    const citation: EvidenceCitation = {
      pageId: e.page_id,
      pageNo: e.page_no,
      snippet: e.snippet ?? "",
    };
    // exactOptionalPropertyTypes：score 仅在确有数值时赋值，避免写入 undefined。
    if (typeof e.score === "number") citation.score = e.score;
    return citation;
  });
  if (fromEvidence.length > 0) return fromEvidence;
  // 仅有 page_ids（无明细，如部分流程）时用页 id 兜底合成，至少能展示课件页图 + 回链。
  return pageIdsToCitations(r.evidence_page_ids);
}

/** 仅有课件页 id（无页码/片段，如历史回放）时合成证据项：页图按 pageId 取，页码缺省 0（面板显示「课件页」）。 */
export function pageIdsToCitations(
  pageIds: number[] | undefined,
): EvidenceCitation[] {
  return (pageIds ?? []).map((pid) => ({
    pageId: pid,
    pageNo: 0,
    snippet: "",
  }));
}

const GROUNDING_LABELS: Record<string, { text: string; cls: string }> = {
  PASSED: { text: "引用充分", cls: "g--passed" },
  NO_EVIDENCE: { text: "通识知识", cls: "g--general" },
  FAILED: { text: "未通过", cls: "g--failed" },
};

export function groundingInfo(status: string): { text: string; cls: string } {
  return GROUNDING_LABELS[status] ?? { text: status, cls: "" };
}

/** 一枚「三审徽章」视图模型：通过/未通过 + hover 显示该审查的具体理由。 */
export interface ReviewBadge {
  key: string;
  label: string;
  passed: boolean;
  title: string;
}

/**
 * 把 review 三道审查映射成 3 枚徽章（审查官·引用 / 校对官·防幻觉 / 安全员·安全）。
 * title 汇总该审查 reasons，鼠标悬停即可看到「为什么过 / 为什么没过」。
 */
export function reviewBadges(review: TutorReview): ReviewBadge[] {
  const make = (
    key: string,
    label: string,
    qualifier: string,
    item: TutorReviewItem,
  ): ReviewBadge => {
    const reasons = (item.reasons ?? []).filter(
      (r) => r && r.trim().length > 0,
    );
    const head = `${label}（${qualifier}）：${item.passed ? "已通过" : "未通过"}`;
    return {
      key,
      label,
      passed: item.passed,
      title: reasons.length > 0 ? `${head}\n${reasons.join("\n")}` : head,
    };
  };
  return [
    make("grounding", "审查官", "引用", review.grounding),
    make("hallucination", "校对官", "防幻觉", review.hallucination),
    make("safety", "安全员", "安全", review.safety),
  ];
}
