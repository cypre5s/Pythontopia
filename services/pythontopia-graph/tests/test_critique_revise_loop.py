"""P1.2 critique→revise 自我纠错闭环验收测试。

验证：首稿故意引用越界页码 → 第二稿注入 reasons 后页码合法并通过；
reasons 为空/通过时不触发额外 LLM 调用。
"""

from __future__ import annotations

from collections.abc import AsyncIterator

import pytest

from pythontopia_graph.graph.resource_graph import ResourceGraph, ResourceGraphConfig
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import (
    EvidenceBundle,
    RagHit,
    StaticEvidenceFetcher,
)
from pythontopia_graph.schemas.resource import (
    HandoutContent,
    PageRef,
    ResourceProduceRequest,
)


class CallCountingProvider(LlmProvider):
    """记录每次 chat 调用次数与入参，用于断言 LLM 调用守恒。"""

    def __init__(self, payloads: list[dict | None]) -> None:
        self._payloads = list(payloads)
        self.call_count = 0
        self.all_messages: list[list[dict[str, str]]] = []

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        self.call_count += 1
        self.all_messages.append(messages)
        payload = self._payloads.pop(0) if self._payloads else None
        return LlmResponse(content_json=payload)


HIT = RagHit(
    chunk_id="c1",
    page_id=101,
    page_no=12,
    chapter_id=3,
    kc_codes=["PY43-KC-001"],
    text="for 循环遍历可迭代对象。",
    score=0.81,
)


def request_of(type_: str = "HANDOUT") -> ResourceProduceRequest:
    return ResourceProduceRequest(user_id=1, kc_codes=["PY43-KC-001"], type=type_)  # type: ignore[arg-type]


@pytest.mark.asyncio
async def test_critique_revise_loop_bad_page_then_fixed() -> None:
    """首稿故意引用越界页码 → 第二稿注入 reasons 后页码合法并通过。

    断言两稿不同（修订生效）+ 最终 PASS。
    """
    # 首稿：引用越界 page_id=999
    bad_draft = {
        "title": "讲义 v1",
        "handout": HandoutContent(
            sections=[
                {
                    "heading": "section",
                    "paragraphs": ["引用了越界页码 999"],
                    "page_refs": [PageRef(page_id=999, page_no=999, chapter_id=3).model_dump()],
                }
            ]
        ).model_dump(),
    }
    # 第二稿：修正后引用合法 page_id=101
    fixed_draft = {
        "title": "讲义 v2（已修订）",
        "handout": HandoutContent(
            sections=[
                {
                    "heading": "section",
                    "paragraphs": ["修正后引用了正确页码"],
                    "page_refs": [PageRef(page_id=101, page_no=12, chapter_id=3).model_dump()],
                }
            ]
        ).model_dump(),
    }

    provider = CallCountingProvider([bad_draft, fixed_draft])
    graph = ResourceGraph(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )

    response = await graph.run(request_of("HANDOUT"))

    # 最终通过
    assert response.status == "SUCCESS"
    assert response.grounding_result.passed is True
    assert response.grounding_result.retried is True

    # 两稿不同（修订生效）
    assert provider.call_count == 2
    # 第二稿 prompt 应包含 revision_notes（被打回原因）
    second_call_messages = provider.all_messages[1]
    all_second_text = " ".join(m["content"] for m in second_call_messages)
    assert "打回原因" in all_second_text or "修订" in all_second_text

    # trace 显示 attempt0 和 attempt1
    node_names = [step.node for step in response.graph_trace]
    assert "ResourceDesignerAgent.handout.attempt0" in node_names
    assert "ResourceDesignerAgent.handout.attempt1" in node_names


@pytest.mark.asyncio
async def test_critique_revise_no_extra_call_when_pass_on_first() -> None:
    """首稿即通过时仅 1 次 LLM 调用（成本守恒）。"""
    good_draft = {
        "title": "讲义",
        "handout": HandoutContent(
            sections=[
                {
                    "heading": "section",
                    "paragraphs": ["合法引用"],
                    "page_refs": [PageRef(page_id=101, page_no=12, chapter_id=3).model_dump()],
                }
            ]
        ).model_dump(),
    }

    provider = CallCountingProvider([good_draft])
    graph = ResourceGraph(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )

    response = await graph.run(request_of("HANDOUT"))
    assert response.status == "SUCCESS"
    # 仅 1 次 LLM 调用（首稿即通过，不触发重试）
    assert provider.call_count == 1


@pytest.mark.asyncio
async def test_critique_revise_reasons_injected_into_prompt() -> None:
    """验证 reasons 确实注入到了第二次 design() 的 prompt 中。"""
    bad_draft = {
        "title": "讲义 v1",
        "handout": HandoutContent(
            sections=[
                {
                    "heading": "section",
                    "paragraphs": ["伪造页码"],
                    "page_refs": [PageRef(page_id=999, page_no=999, chapter_id=3).model_dump()],
                }
            ]
        ).model_dump(),
    }
    fixed_draft = {
        "title": "讲义 v2 修订版",
        "handout": HandoutContent(
            sections=[
                {
                    "heading": "section",
                    "paragraphs": ["已修正"],
                    "page_refs": [PageRef(page_id=101, page_no=12, chapter_id=3).model_dump()],
                }
            ]
        ).model_dump(),
    }

    provider = CallCountingProvider([bad_draft, fixed_draft])
    graph = ResourceGraph(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )

    await graph.run(request_of("HANDOUT"))

    # 首稿 prompt 不应含 revision_notes
    first_call_text = " ".join(
        m["content"] for m in provider.all_messages[0]
    )
    assert "打回原因" not in first_call_text

    # 第二稿 prompt 应含 revision_notes
    second_call_text = " ".join(
        m["content"] for m in provider.all_messages[1]
    )
    assert "打回原因" in second_call_text


@pytest.mark.asyncio
async def test_critique_revise_all_fail_still_errors() -> None:
    """全部 attempt 失败仍抛 ResourceGraphError（不静默降级）。"""
    bad_draft = {
        "title": "讲义",
        "handout": HandoutContent(
            sections=[
                {
                    "heading": "section",
                    "paragraphs": ["始终引用越界页码"],
                    "page_refs": [PageRef(page_id=999, page_no=999, chapter_id=3).model_dump()],
                }
            ]
        ).model_dump(),
    }

    # MAX_RETRIES=2 → 3 次全部失败
    provider = CallCountingProvider([bad_draft, bad_draft, bad_draft])
    graph = ResourceGraph(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )

    from pythontopia_graph.graph.resource_graph import ResourceGraphError

    with pytest.raises(ResourceGraphError) as exc:
        await graph.run(request_of("HANDOUT"))
    assert exc.value.response.status == "FAILED"
    assert provider.call_count == 3
