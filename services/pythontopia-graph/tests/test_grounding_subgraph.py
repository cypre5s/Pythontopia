"""G2b-grounding 子图感知防幻觉测试矩阵（plan 2026-06-17 §4 G2 第 2-3 条）。

覆盖：
  ① 有 subgraph 时引用审计含「前置链路」可解释（为什么先学 X）
  ② 「前置页冒充直接出处」被 grounding_critic 拒绝
  ③ 答案主张仍须命中直接证据页（直接页引用通过；前置页引用不通过）
  ④ no_evidence 时不编造（诚实标注，不发明引用）
  ⑤ 旧数据（无 subgraph / 空子图）向后兼容

集成说明：EvidenceBundle 的 ``route``/``subgraph`` 与 ``KcEdge`` 由兄弟单元 G2a-evidence
落地（nodes/evidence_fetcher.py，已合并），本测试直接用真实契约类型校验集成；另用 duck stub
覆盖「字段缺失 / dict 边」两条防御路径（grounding_critic 用 getattr 容错，向后兼容旧 mock）。
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from types import SimpleNamespace

import pytest

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle, KcEdge, RagHit
from pythontopia_graph.nodes.grounding_critic import (
    GroundingReport,
    PrerequisiteAudit,
    build_prerequisite_audit,
    grounding_critic,
)
from pythontopia_graph.nodes.semantic_grounding_verifier import (
    verify_entailment,
    verify_semantic_grounding,
)
from pythontopia_graph.schemas.resource import (
    HandoutContent,
    HandoutSection,
    PageRef,
    ResourceDraft,
)


def _hit(
    page_id: int,
    page_no: int,
    *,
    via_prerequisite: bool = False,
    kc_codes: list[str] | None = None,
    text: str = "证据正文。",
) -> RagHit:
    return RagHit(
        chunk_id=f"c{page_id}",
        page_id=page_id,
        page_no=page_no,
        kc_codes=kc_codes or [],
        text=text,
        score=0.9,
        via_prerequisite=via_prerequisite,
    )


def _draft(refs: list[PageRef], *, paragraph: str = "讲解段落。") -> ResourceDraft:
    return ResourceDraft(
        title="讲义",
        summary="概要",
        content_format="MARKDOWN",
        handout=HandoutContent(
            sections=[
                HandoutSection(heading="主题", paragraphs=[paragraph], page_refs=refs)
            ]
        ),
    )


def _bundle_with_subgraph() -> EvidenceBundle:
    """直接证据页 12(id=101) + 前置页 30(id=207, via_prerequisite) + 前置子图边。"""
    return EvidenceBundle(
        chunks=[
            _hit(101, 12, kc_codes=["KC12"], text="for 循环遍历可迭代对象。"),
            _hit(207, 30, via_prerequisite=True, kc_codes=["KC07"], text="变量与赋值是基础。"),
        ],
        route="graph",
        subgraph=[KcEdge(from_kc="KC07", to_kc="KC12", relation="prerequisite")],
    )


# ── ① 有 subgraph 时引用审计含「前置链路」可解释 ──────────────────────────────
def test_subgraph_audit_explains_prerequisite_chain() -> None:
    evidence = _bundle_with_subgraph()
    # 草稿引用直接证据页（合法），审计仍应携带前置链路解释。
    report = grounding_critic(_draft([PageRef(page_id=101, page_no=12)]), evidence)

    assert isinstance(report, GroundingReport)
    assert report.passed is True
    audit = report.prerequisite_audit
    assert isinstance(audit, PrerequisiteAudit)
    assert audit.explanations, "应给出「为什么先学 X」的可审计解释"
    joined = " ".join(audit.explanations)
    assert "为什么先学" in joined
    assert "KC07" in joined and "KC12" in joined
    assert {"from_kc": "KC07", "to_kc": "KC12", "relation": "prerequisite"} in audit.edges
    # 直接页/前置页分区正确（直接=101，前置=207）。
    assert audit.direct_page_ids == [101]
    assert audit.prerequisite_page_ids == [207]
    assert audit.has_prerequisite_context is True


def test_build_prerequisite_audit_handles_missing_attr_and_dict_edges() -> None:
    """audit 构造器可独立复用，并对「subgraph 字段缺失 / dict 边」两条路径容错（防御解耦）。"""
    # (a) 字段缺失（旧 mock / 等价对象不带 subgraph）→ getattr 回退空，仅给页面分区。
    legacy_like = SimpleNamespace(
        chunks=[_hit(101, 12), _hit(207, 30, via_prerequisite=True)]
    )
    legacy_audit = build_prerequisite_audit(legacy_like)  # type: ignore[arg-type]
    assert legacy_audit.edges == []
    assert legacy_audit.explanations == []
    assert legacy_audit.direct_page_ids == [101]
    assert legacy_audit.prerequisite_page_ids == [207]

    # (b) dict 边（兼容非 KcEdge 实现）→ _edge_value 走 dict 分支。
    dict_like = SimpleNamespace(
        chunks=[_hit(101, 12), _hit(207, 30, via_prerequisite=True)],
        subgraph=[{"from_kc": "KC07", "to_kc": "KC12", "relation": "prerequisite"}],
    )
    dict_audit = build_prerequisite_audit(dict_like)  # type: ignore[arg-type]
    assert dict_audit.edges == [
        {"from_kc": "KC07", "to_kc": "KC12", "relation": "prerequisite"}
    ]
    assert "为什么先学" in " ".join(dict_audit.explanations)


# ── ② 「前置页冒充直接出处」被拒绝 ───────────────────────────────────────────
def test_prerequisite_page_masquerading_as_direct_source_rejected() -> None:
    evidence = _bundle_with_subgraph()
    # 草稿把前置页（id=207/page 30, via_prerequisite）当作答案直接出处引用 → 必须拒绝。
    report = grounding_critic(_draft([PageRef(page_id=207, page_no=30)]), evidence)

    assert report.passed is False
    blob = " ".join(report.reasons)
    assert "PREREQUISITE_PAGE_AS_DIRECT_SOURCE" in blob
    assert "前置页冒充直接出处" in blob
    assert "207" in blob
    # 即便被拒，前置链路审计仍应给出（用于诚实解释「这是前置页」）。
    assert report.prerequisite_audit.explanations


def test_mixed_refs_rejected_when_any_is_prerequisite_page() -> None:
    evidence = _bundle_with_subgraph()
    # 直接页(101)合法 + 前置页(207)冒充 → 整体不通过，且仅前置页那条触发前置拒绝规则。
    report = grounding_critic(
        _draft([PageRef(page_id=101, page_no=12), PageRef(page_id=207, page_no=30)]),
        evidence,
    )
    assert report.passed is False
    blob = " ".join(report.reasons)
    assert "PREREQUISITE_PAGE_AS_DIRECT_SOURCE" in blob
    assert "207" in blob
    assert "101" not in blob  # 直接页不应被误判


# ── ③ 答案主张仍须命中直接证据页 ────────────────────────────────────────────
def test_answer_claim_must_hit_direct_evidence_page() -> None:
    evidence = _bundle_with_subgraph()
    # 引用直接证据页 → 通过（前置上下文存在不放宽对直接页的要求）。
    ok_report = grounding_critic(_draft([PageRef(page_id=101, page_no=12)]), evidence)
    assert ok_report.passed is True
    assert ok_report.reasons == []

    # 完全不在任何证据页（既非直接也非前置）→ 普通未知引用拒绝。
    bad_report = grounding_critic(_draft([PageRef(page_id=999, page_no=88)]), evidence)
    assert bad_report.passed is False
    assert any("Unknown evidence ref" in r for r in bad_report.reasons)


# ── ④ no_evidence 时不编造 ─────────────────────────────────────────────────
def test_no_evidence_does_not_fabricate() -> None:
    evidence = EvidenceBundle(chunks=[], no_evidence_found=True)
    report = grounding_critic(_draft([PageRef(page_id=5, page_no=5)]), evidence)
    # 通识知识：诚实标注 no_evidence_found，不发明引用、不报「未知引用」。
    assert report.passed is True
    assert report.no_evidence_found is True
    assert report.reasons == []
    assert report.prerequisite_audit.explanations == []


def test_empty_chunks_treated_as_general_knowledge() -> None:
    evidence = EvidenceBundle(chunks=[], no_evidence_found=False)
    report = grounding_critic(_draft([PageRef(page_id=5, page_no=5)]), evidence)
    assert report.passed is True
    assert report.no_evidence_found is True


# ── ⑤ 旧数据（无 subgraph / 空子图）向后兼容 ────────────────────────────────
def test_backward_compatible_without_subgraph() -> None:
    # 旧式证据：空子图 + 全部直接页（via_prerequisite 默认 False）。
    evidence = EvidenceBundle(chunks=[_hit(101, 12), _hit(102, 13)])
    assert evidence.subgraph == []

    ok = grounding_critic(_draft([PageRef(page_id=101, page_no=12)]), evidence)
    assert ok.passed is True
    assert ok.prerequisite_audit.edges == []
    assert ok.prerequisite_audit.prerequisite_page_ids == []

    bad = grounding_critic(_draft([PageRef(page_id=77, page_no=77)]), evidence)
    assert bad.passed is False
    assert any("Unknown evidence ref" in r for r in bad.reasons)


def test_old_data_all_chunks_count_as_direct() -> None:
    # 旧数据每个 chunk via_prerequisite 默认 False → 全部视作直接证据页（不退化为前置拒绝）。
    evidence = EvidenceBundle(chunks=[_hit(101, 12), _hit(207, 30)])
    report = grounding_critic(_draft([PageRef(page_id=207, page_no=30)]), evidence)
    assert report.passed is True


# ── semantic_grounding_verifier：区分直接证据页与前置页 ──────────────────────
def test_semantic_verifier_distinguishes_direct_and_prerequisite_pages() -> None:
    evidence = _bundle_with_subgraph()

    # 直接页引用 → 通过。
    ok, reasons = verify_semantic_grounding(_draft([PageRef(page_id=101, page_no=12)]), evidence)
    assert ok, reasons

    # 前置页引用 → 被标记为「前置页，非直接证据页」。
    not_ok, prereq_reasons = verify_semantic_grounding(
        _draft([PageRef(page_id=207, page_no=30)]), evidence
    )
    assert not not_ok
    assert any("前置页" in r and "207" in r for r in prereq_reasons)


def test_semantic_verifier_backward_compatible() -> None:
    evidence = EvidenceBundle(chunks=[_hit(101, 12)])
    ok, _ = verify_semantic_grounding(_draft([PageRef(page_id=101, page_no=12)]), evidence)
    assert ok

    not_ok, reasons = verify_semantic_grounding(_draft([PageRef(page_id=99, page_no=99)]), evidence)
    assert not not_ok
    assert any("99" in r and "不在证据页中" in r for r in reasons)


# ── semantic entailment：强断言只对直接证据页做支撑核验 ─────────────────────
class _CapturingProvider(LlmProvider):
    """捕获 entailment prompt 的 evidence 文段，断言只喂直接证据页。"""

    def __init__(self) -> None:
        self.evidence_seen = ""

    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        self.evidence_seen = " ".join(m["content"] for m in messages)
        return LlmResponse(content_json={"supported": True})

    async def chat_stream(
        self, messages: list[dict[str, str]], **kwargs: object
    ) -> AsyncIterator[str]:
        yield ""


@pytest.mark.asyncio
async def test_entailment_uses_direct_evidence_text_only() -> None:
    evidence = EvidenceBundle(
        chunks=[
            _hit(101, 12, text="DIRECT_EVIDENCE_TOKEN 直接证据正文。"),
            _hit(207, 30, via_prerequisite=True, text="PREREQ_EVIDENCE_TOKEN 前置证据正文。"),
        ],
        route="graph",
        subgraph=[KcEdge(from_kc="KC07", to_kc="KC12")],
    )
    # 含强断言（"5 秒"）以触发 LLM entailment。
    draft = _draft([PageRef(page_id=101, page_no=12)], paragraph="该操作耗时 5 秒。")
    provider = _CapturingProvider()

    ok, _ = await verify_entailment(draft, evidence, provider)
    assert ok
    # entailment 证据文段只取直接证据页，不混入前置页文本。
    assert "DIRECT_EVIDENCE_TOKEN" in provider.evidence_seen
    assert "PREREQ_EVIDENCE_TOKEN" not in provider.evidence_seen
