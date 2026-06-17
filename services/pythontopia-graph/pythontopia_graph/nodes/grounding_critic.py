from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field

from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle
from pythontopia_graph.schemas.resource import (
    GroundingResult,
    HandoutContent,
    PageRef,
    ReadingContent,
    ResourceDraft,
)


class PrerequisiteAudit(BaseModel):
    """前置链路可审计解释（KC 子图感知，§3.3）。

    当 EvidenceBundle 携带 ``subgraph``（前置 KC 边）时，把「为什么先学 X」整理为
    可审计、可向学生展示的解释来源；并按 ``RagHit.via_prerequisite`` 把证据页分成
    「直接证据页」与「前置页」两区，供引用 chips 分区呈现（§3.8）。

    注意：前置链路只解释「学习顺序」，**不构成**答案主张的直接出处。
    """

    explanations: list[str] = Field(default_factory=list)
    edges: list[dict[str, str]] = Field(default_factory=list)
    direct_page_ids: list[int] = Field(default_factory=list)
    prerequisite_page_ids: list[int] = Field(default_factory=list)

    @property
    def has_prerequisite_context(self) -> bool:
        return bool(self.edges) or bool(self.prerequisite_page_ids)


class GroundingReport(GroundingResult):
    """``grounding_critic`` 的扩展返回值。

    在基类（``passed`` / ``reasons`` / ``no_evidence_found`` / ``retried``）之外，附带
    前置链路审计 ``prerequisite_audit``。基类消费方（``resource_graph`` 等）只读基类字段，
    完全向后兼容；Phase 2 编排可读 ``prerequisite_audit`` 渲染引用 chips / 记 eval。
    """

    prerequisite_audit: PrerequisiteAudit = Field(default_factory=PrerequisiteAudit)


def _collect_refs(draft: ResourceDraft) -> list[PageRef]:
    refs: list[PageRef] = []
    if isinstance(draft.handout, HandoutContent):
        for section in draft.handout.sections:
            refs.extend(section.page_refs)
    if isinstance(draft.reading, ReadingContent):
        for item in draft.reading.items:
            refs.extend(item.page_refs)
    return refs


def direct_evidence_page_ids(evidence: EvidenceBundle) -> set[int]:
    """直接证据页 page_id 集合（``via_prerequisite`` 为假；旧数据全部计入直接页）。"""
    return {hit.page_id for hit in evidence.chunks if not getattr(hit, "via_prerequisite", False)}


def direct_evidence_page_nos(evidence: EvidenceBundle) -> set[int]:
    return {hit.page_no for hit in evidence.chunks if not getattr(hit, "via_prerequisite", False)}


def prerequisite_page_ids(evidence: EvidenceBundle) -> set[int]:
    """前置页 page_id 集合（``via_prerequisite`` 为真，来自前置 KC 召回路）。"""
    return {hit.page_id for hit in evidence.chunks if getattr(hit, "via_prerequisite", False)}


def prerequisite_page_nos(evidence: EvidenceBundle) -> set[int]:
    return {hit.page_no for hit in evidence.chunks if getattr(hit, "via_prerequisite", False)}


def _edge_value(edge: Any, name: str) -> str:
    """从子图边读取字段，兼容 KcEdge 模型与等价 dict（鲁棒解耦 G2a 实现细节）。"""
    if isinstance(edge, dict):
        return str(edge.get(name, "") or "")
    return str(getattr(edge, name, "") or "")


def build_prerequisite_audit(evidence: EvidenceBundle) -> PrerequisiteAudit:
    """把 EvidenceBundle 的前置子图整理成可审计的「为什么先学 X」解释 + 页面分区。

    - 子图字段缺失（旧数据 / G2a 未合并）时返回空 explanations/edges，仍给出页面分区。
    - 直接页与前置页若 page_id 重叠，按「直接优先」归入直接页（前置页只保留纯前置项）。
    """
    subgraph = getattr(evidence, "subgraph", None) or []
    direct_ids = direct_evidence_page_ids(evidence)
    prereq_ids = prerequisite_page_ids(evidence)

    edges: list[dict[str, str]] = []
    explanations: list[str] = []
    for edge in subgraph:
        frm = _edge_value(edge, "from_kc")
        to = _edge_value(edge, "to_kc")
        if not frm or not to:
            continue
        relation = _edge_value(edge, "relation") or "prerequisite"
        edges.append({"from_kc": frm, "to_kc": to, "relation": relation})
        if relation == "prerequisite":
            explanations.append(
                f"为什么先学 {frm}：{frm} 是 {to} 的前置知识（{frm} → {to}）"
            )
        else:
            explanations.append(f"{frm} 与 {to} 的关系：{relation}（{frm} → {to}）")

    return PrerequisiteAudit(
        explanations=explanations,
        edges=edges,
        direct_page_ids=sorted(direct_ids),
        prerequisite_page_ids=sorted(prereq_ids - direct_ids),
    )


def grounding_critic(draft: ResourceDraft, evidence: EvidenceBundle) -> GroundingReport:
    """引用审查（KC 子图感知）：草稿中每个 PageRef 须落在**直接证据页**集合内。

    防幻觉升级（§4 G2 第 2-3 条）：
      - 有 ``subgraph`` 时，前置 KC 链路作为「为什么先学 X」的可审计解释来源
        （写入 ``prerequisite_audit``，不写进 reasons，避免污染修订回灌）。
      - 新增拒绝规则：把**前置页**（``via_prerequisite``）当作答案直接出处引用 → 拒绝
        （前置链路只解释学习顺序，答案主张仍须命中直接证据页）。
      - 既不命中直接页也不命中前置页的引用 → 仍按「未知引用」拒绝。

    向后兼容：旧数据无 ``subgraph`` 字段、且 chunk 默认 ``via_prerequisite=False`` 时，
    所有 chunk 计入直接页，行为与升级前完全一致。

    no_evidence_found 或无 chunks 时视为通识知识，诚实通过并标注 no_evidence_found=True，
    绝不编造引用。
    """
    audit = build_prerequisite_audit(evidence)

    if evidence.no_evidence_found:
        return GroundingReport(passed=True, no_evidence_found=True, prerequisite_audit=audit)

    all_page_ids = {hit.page_id for hit in evidence.chunks}
    if not all_page_ids:
        return GroundingReport(passed=True, no_evidence_found=True, prerequisite_audit=audit)

    refs = _collect_refs(draft)
    if not refs:
        return GroundingReport(passed=True, no_evidence_found=False, prerequisite_audit=audit)

    direct_ids = direct_evidence_page_ids(evidence)
    direct_nos = direct_evidence_page_nos(evidence)
    # 纯前置页（不与任一直接页重叠）：被当成直接出处引用时触发前置拒绝规则。
    prereq_only_ids = prerequisite_page_ids(evidence) - direct_ids
    prereq_only_nos = prerequisite_page_nos(evidence) - direct_nos

    masquerade: list[str] = []
    unknown: list[str] = []
    for ref in refs:
        if ref.page_id in direct_ids or ref.page_no in direct_nos:
            continue
        location = f"page_id={ref.page_id},page_no={ref.page_no}"
        if ref.page_id in prereq_only_ids or ref.page_no in prereq_only_nos:
            masquerade.append(location)
        else:
            unknown.append(location)

    reasons: list[str] = []
    for location in masquerade:
        reasons.append(
            "PREREQUISITE_PAGE_AS_DIRECT_SOURCE: 前置页冒充直接出处"
            f"（{location}）；前置链路仅可作「为什么先学」的解释，答案主张须命中直接证据页"
        )
    for location in unknown:
        reasons.append(f"Unknown evidence ref: {location}")

    if reasons:
        return GroundingReport(passed=False, reasons=reasons, prerequisite_audit=audit)
    return GroundingReport(passed=True, prerequisite_audit=audit)
