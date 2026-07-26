from collections.abc import AsyncIterator

import pytest

from pythontopia_graph.graph.resource_graph import ResourceGraph, ResourceGraphConfig
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import (
    EvidenceBundle,
    RagHit,
    StaticEvidenceFetcher,
)
from pythontopia_graph.nodes.semantic_grounding_verifier import (
    verify_answer_entailment,
    verify_entailment,
    verify_semantic_grounding,
)
from pythontopia_graph.schemas.resource import (
    HandoutContent,
    HandoutSection,
    PageRef,
    ResourceDraft,
    ResourceProduceRequest,
)


def make_rag_hit(page_id: int) -> RagHit:
    return RagHit(chunk_id=f"c{page_id}", page_id=page_id, page_no=page_id, text="t", score=0.9)


def test_valid_refs_pass():
    ev = EvidenceBundle(chunks=[make_rag_hit(1), make_rag_hit(2)])
    draft = ResourceDraft(title="T", summary="S", content_format="MARKDOWN",
        handout=HandoutContent(sections=[
            HandoutSection(heading="H", paragraphs=["p1"], page_refs=[PageRef(page_id=1, page_no=1)]),
        ]))
    ok, reasons = verify_semantic_grounding(draft, ev)
    assert ok, reasons


def test_missing_page_ref_fails():
    ev = EvidenceBundle(chunks=[make_rag_hit(1)])
    draft = ResourceDraft(title="T", summary="S", content_format="MARKDOWN",
        handout=HandoutContent(sections=[
            HandoutSection(heading="H", paragraphs=["p1"], page_refs=[PageRef(page_id=99, page_no=99)]),
        ]))
    ok, reasons = verify_semantic_grounding(draft, ev)
    assert not ok
    assert any("99" in r for r in reasons)


def test_strong_assertions_without_refs_flagged():
    ev = EvidenceBundle(chunks=[make_rag_hit(1)])
    draft = ResourceDraft(title="T", summary="80% 的学生易错", content_format="MARKDOWN",
        handout=HandoutContent(sections=[
            HandoutSection(heading="H", paragraphs=["p1"], page_refs=[]),
        ]))
    ok, reasons = verify_semantic_grounding(draft, ev)
    assert not ok


def test_no_assertions_no_refs_passes():
    ev = EvidenceBundle(chunks=[make_rag_hit(1)])
    draft = ResourceDraft(title="T", summary="基本概念", content_format="MARKDOWN",
        handout=HandoutContent(sections=[
            HandoutSection(heading="H", paragraphs=["p1"], page_refs=[]),
        ]))
    ok, _ = verify_semantic_grounding(draft, ev)
    assert ok


# ── P3.4 LLM entailment 接入主链（plan line 307/313） ──

# entailment prompt 的稳定标记，用于在 stub 里区分 design 调用与 entailment 调用。
_ENTAILMENT_MARKER = "判断论述是否被证据支持"

_ENTAIL_HIT = RagHit(
    chunk_id="c1",
    page_id=101,
    page_no=12,
    chapter_id=3,
    kc_codes=["PY43-KC-001"],
    text="for 循环遍历可迭代对象。",
    score=0.81,
)


class _EntailmentStubProvider(LlmProvider):
    """区分 design 与 entailment 两类 chat：entailment prompt 含 marker。

    design 调用依次返回 drafts；entailment 调用依次返回 verdicts，记录次数与 design 入参。
    """

    def __init__(self, drafts: list[dict | None], verdicts: list[dict]) -> None:
        self._drafts = list(drafts)
        self._verdicts = list(verdicts)
        self.design_messages: list[list[dict[str, str]]] = []
        self.entailment_calls = 0

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        text = " ".join(m["content"] for m in messages)
        if _ENTAILMENT_MARKER in text:
            self.entailment_calls += 1
            verdict = self._verdicts.pop(0) if self._verdicts else {"supported": True}
            return LlmResponse(content_json=verdict)
        self.design_messages.append(messages)
        draft = self._drafts.pop(0) if self._drafts else None
        return LlmResponse(content_json=draft)

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield ""


def _handout_payload(paragraph: str, *, title: str) -> dict:
    return {
        "title": title,
        "summary": "循环讲解",
        "sections": [{
            "heading": "for 循环",
            "paragraphs": [paragraph],
            "code_snippets": [],
            "evidence_refs": ["page-101"],
        }],
    }


def _produce_request() -> ResourceProduceRequest:
    return ResourceProduceRequest(
        user_id=1, kc_codes=["PY43-KC-001"], type="HANDOUT",  # type: ignore[arg-type]
    )


@pytest.mark.asyncio
async def test_entailment_distortion_triggers_revision() -> None:
    """KEY (plan 313)：page_id 合法但论述歪曲证据 → entailment 判 unsupported → 触发修订并最终通过。"""
    distorted = _handout_payload(
        "根据第12页，for 循环只能遍历列表，不能遍历字典。",  # 强断言+合法页码，但歪曲证据
        title="讲义 v1",
    )
    fixed = _handout_payload(
        "根据第12页，for 循环用于遍历可迭代对象。",  # 与证据一致
        title="讲义 v2（已修订）",
    )
    provider = _EntailmentStubProvider(
        drafts=[distorted, fixed],
        verdicts=[
            {"supported": False, "reason": "证据未提及'只能遍历列表'，论述歪曲了证据"},
            {"supported": True},
        ],
    )
    graph = ResourceGraph(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([_ENTAIL_HIT]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )

    response = await graph.run(_produce_request())

    # 最终通过且标记重试（首稿被 entailment 打回 → 第二稿修订通过）。
    assert response.status == "SUCCESS"
    assert response.grounding_result.passed is True
    assert response.grounding_result.retried is True
    # 两稿（design 两次）+ 两次 entailment（两稿均含强断言且页码合法）。
    assert len(provider.design_messages) == 2
    assert provider.entailment_calls == 2
    # entailment 的 unsupported 理由回灌进第二稿的 revision_notes。
    second_design_text = " ".join(m["content"] for m in provider.design_messages[1])
    assert "打回原因" in second_design_text
    assert "歪曲" in second_design_text
    # trace 含两次 attempt。
    node_names = [step.node for step in response.graph_trace]
    assert "ResourceDesignerAgent.handout.attempt0" in node_names
    assert "ResourceDesignerAgent.handout.attempt1" in node_names


@pytest.mark.asyncio
async def test_entailment_skipped_when_no_strong_assertion() -> None:
    """成本守恒：无强断言句不触发 LLM entailment（仅强断言句核验，plan 314）。"""
    plain = _handout_payload("for 循环用于遍历，先理解可迭代对象。", title="讲义")
    provider = _EntailmentStubProvider(drafts=[plain], verdicts=[])
    graph = ResourceGraph(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([_ENTAIL_HIT]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )
    response = await graph.run(_produce_request())
    assert response.status == "SUCCESS"
    assert provider.entailment_calls == 0


# ── entailment 裁决必须显式为布尔（不合约 → 降级规则，不静默判「支持」） ──


class _VerdictProvider(LlmProvider):
    """固定返回一个 entailment 裁决 payload（None 表示 content_json 缺失）。"""

    def __init__(self, verdict: dict | None) -> None:
        self._verdict = verdict

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        return LlmResponse(content_json=self._verdict)

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield ""


def _ungrounded_draft() -> ResourceDraft:
    """规则版必定判失败的草稿：含强断言 + PageRef 指向非证据页。"""
    return ResourceDraft(
        title="T",
        summary="该操作耗时 5 秒",
        content_format="MARKDOWN",
        handout=HandoutContent(
            sections=[
                HandoutSection(
                    heading="H",
                    paragraphs=["p1"],
                    page_refs=[PageRef(page_id=99, page_no=99)],
                )
            ]
        ),
    )


@pytest.mark.parametrize(
    "verdict",
    [
        {"supported": "false"},  # 字符串而非布尔
        {"supported": {"value": False}},  # 嵌套结构
        {"reason": "模型只回了理由"},  # 缺字段
        None,  # content_json 解析失败
    ],
)
@pytest.mark.asyncio
async def test_entailment_non_boolean_verdict_falls_back_to_rules(verdict) -> None:
    """裁决不是布尔 → 降级规则版核验；绝不因为「不是 False」就默认判支持。"""
    evidence = EvidenceBundle(chunks=[make_rag_hit(1)])

    ok, reasons = await verify_entailment(
        _ungrounded_draft(), evidence, _VerdictProvider(verdict)
    )

    assert ok is False
    assert any("99" in reason for reason in reasons)


@pytest.mark.asyncio
async def test_entailment_boolean_true_still_passes() -> None:
    evidence = EvidenceBundle(chunks=[make_rag_hit(1)])

    ok, reasons = await verify_entailment(
        _ungrounded_draft(), evidence, _VerdictProvider({"supported": True})
    )

    assert ok is True
    assert reasons == []


@pytest.mark.asyncio
async def test_entailment_boolean_false_still_fails_with_model_reason() -> None:
    evidence = EvidenceBundle(chunks=[make_rag_hit(1)])

    ok, reasons = await verify_entailment(
        _ungrounded_draft(),
        evidence,
        _VerdictProvider({"supported": False, "reason": "证据未提及该耗时"}),
    )

    assert ok is False
    assert reasons == ["证据未提及该耗时"]


@pytest.mark.asyncio
async def test_answer_entailment_non_boolean_verdict_falls_back_to_rules() -> None:
    """答疑侧同源：裁决不合约时回落 verify_answer_grounding，而不是判「支持」。"""
    answer = "该操作耗时 5 秒。"

    ok, reasons = await verify_answer_entailment(
        answer, [_ENTAIL_HIT], _VerdictProvider({"supported": "true"})
    )

    assert ok is False
    assert any("未标注引用页" in reason for reason in reasons)
