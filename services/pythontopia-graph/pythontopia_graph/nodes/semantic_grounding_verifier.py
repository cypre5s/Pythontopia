"""语义接地核验：判断资源内容中的强断言是否被证据页支持。

规则驱动（始终可用）：检查 PageRef 页码 + 强断言引用。
LLM entailment（有 provider 时启用）：用轻量 prompt 判断论述是否被证据支持。
无 key/DryRun 降级为纯规则，不抛异常。

KC 子图感知（§4 G2 第 3 条）：区分「直接证据页」与「前置页」（``RagHit.via_prerequisite``）。
PageRef 须命中直接证据页；命中纯前置页 → 标记「前置页冒充直接证据」；entailment 的证据文段
只取直接证据页（前置链路只解释学习顺序，不支撑答案主张）。旧数据全部计入直接页，行为不变。
"""

from __future__ import annotations

import re
from typing import TYPE_CHECKING

from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle, RagHit
from pythontopia_graph.nodes.grounding_critic import (
    direct_evidence_page_ids,
    prerequisite_page_ids,
)
from pythontopia_graph.schemas.resource import ResourceDraft

if TYPE_CHECKING:
    from pythontopia_graph.llm.provider import LlmProvider

STRONG_ASSERTION_RE = re.compile(
    r"\d+(?:\.\d+)?[\]\)\s]*(?:%|％|倍|秒|分|小时|行|个|[A-Z])"
    r"|根据.{2,20}(?:页|章|节|段)",
)


def _direct_chunks(chunks: list[RagHit]) -> list[RagHit]:
    """entailment 证据文段只取直接证据页（``via_prerequisite`` 为假）。

    全为前置页时回退原列表（成本守恒、保证非空）；旧数据全是直接页，行为不变。
    """
    direct = [c for c in chunks if not getattr(c, "via_prerequisite", False)]
    return direct or chunks


def verify_semantic_grounding(
    draft: ResourceDraft, evidence: EvidenceBundle
) -> tuple[bool, list[str]]:
    reasons: list[str] = []
    direct_ids = direct_evidence_page_ids(evidence)
    prereq_only_ids = prerequisite_page_ids(evidence) - direct_ids
    handout = getattr(draft, "handout", None)
    sections = handout.sections if handout else []

    for section in sections:
        for ref in section.page_refs:
            if ref.page_id in direct_ids:
                continue
            if ref.page_id in prereq_only_ids:
                reasons.append(
                    f"PageRef page_id={ref.page_id} 是前置页，非直接证据页"
                    "（前置链路仅作解释，答案主张须命中直接证据页）"
                )
            else:
                reasons.append(f"PageRef page_id={ref.page_id} 不在证据页中")

    text = _extract_text(draft)
    if text:
        strong = STRONG_ASSERTION_RE.findall(text)
        has_ref = any(s.page_refs for s in sections)
        if strong and not has_ref:
            reasons.append(f"检测到 {len(strong)} 处强断言但无 page_ref")

    return len(reasons) == 0, reasons


# entailment prompt 单一来源：两个 entailment 入口（资源草稿 / 答疑答案）共用，
# 便于审计与测试稳定识别（含 "判断论述是否被证据支持" 标记）。
_ENTAILMENT_PROMPT = (
    "证据：{evidence}\n"
    "论述：{claims}\n"
    "判断论述是否被证据支持。回复 JSON: {{\"supported\":true/false,\"reason\":\"...\"}}"
)


async def _llm_entailment_judgment(
    strong: list[str], evidence_text: str, provider: LlmProvider
) -> tuple[bool, list[str]] | None:
    """调轻量 LLM 判断「强断言是否被证据支持」。

    返回 (False, [reason]) = 不支持；(True, []) = 支持；None = 调用/解析异常（由调用方降级规则）。
    """
    try:
        response = await provider.chat([{
            "role": "user",
            "content": _ENTAILMENT_PROMPT.format(
                evidence=evidence_text[:500], claims="；".join(strong[:3])
            ),
        }])
    except Exception:  # noqa: BLE001 - 任何上游失败都交给调用方降级规则，不抛
        return None
    result = response.content_json or {}
    if result.get("supported") is False:
        return False, [str(result.get("reason") or "LLM判定论述不被证据支持")]
    return True, []


async def verify_entailment(
    draft: ResourceDraft,
    evidence: EvidenceBundle,
    provider: LlmProvider | None = None,
) -> tuple[bool, list[str]]:
    """LLM entailment 路径：有 provider 时调轻量 LLM 判断论述是否被证据支持；
    无 provider/DryRun/异常 降级为规则。仅对带强断言的句子核验（成本守恒）。"""
    if provider is None:
        return verify_semantic_grounding(draft, evidence)

    text = _extract_text(draft)
    if not text:
        return True, []

    strong = STRONG_ASSERTION_RE.findall(text)
    if not strong:
        return True, []

    evidence_text = " ".join(c.text for c in _direct_chunks(evidence.chunks)[:3])
    judgment = await _llm_entailment_judgment(strong, evidence_text, provider)
    if judgment is None:
        return verify_semantic_grounding(draft, evidence)
    return judgment


async def verify_answer_entailment(
    answer_md: str,
    evidence_chunks: list[RagHit],
    provider: LlmProvider | None = None,
) -> tuple[bool, list[str]]:
    """答疑答案的 LLM entailment：有 provider 时判断答案强断言是否被证据支持；
    无 provider/无证据/异常 降级为规则 verify_answer_grounding。仅对强断言句核验。"""
    if provider is None or not evidence_chunks:
        return verify_answer_grounding(answer_md, evidence_chunks)

    strong = STRONG_ASSERTION_RE.findall(answer_md or "")
    if not strong:
        return True, []

    evidence_text = " ".join(c.text for c in _direct_chunks(evidence_chunks)[:3])
    judgment = await _llm_entailment_judgment(strong, evidence_text, provider)
    if judgment is None:
        return verify_answer_grounding(answer_md, evidence_chunks)
    return judgment


def verify_answer_grounding(
    answer_md: str, evidence_chunks: list[RagHit]
) -> tuple[bool, list[str]]:
    """tutor_review 用：检查答疑答案的引用与证据一致性。"""
    reasons: list[str] = []
    if not evidence_chunks:
        return True, []

    cited = {int(m.group(1)) for m in re.finditer(r"\[页\s*(\d+)\]", answer_md)}
    for pg in cited:
        if pg not in {c.page_no for c in evidence_chunks}:
            reasons.append(f"答案引用第{pg}页不在证据中")

    strong_claims = STRONG_ASSERTION_RE.findall(answer_md)
    if strong_claims and not cited:
        reasons.append(f"答案有{len(strong_claims)}处数字/断言但未标注引用页")

    return len(reasons) == 0, reasons


def _extract_text(draft: ResourceDraft) -> str:
    parts: list[str] = []
    if draft.summary:
        parts.append(draft.summary)
    handout = getattr(draft, "handout", None)
    if handout:
        for s in handout.sections:
            parts.append(s.heading)
            parts.extend(s.paragraphs)
    practice = getattr(draft, "practice", None)
    if practice:
        for p in practice.problems:
            parts.append(p.title)
            parts.append(p.statement_md)
    return " ".join(parts)

