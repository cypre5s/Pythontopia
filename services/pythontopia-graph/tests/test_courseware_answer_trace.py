from __future__ import annotations

from collections.abc import AsyncIterator

import pytest

from pythontopia_graph.llm.provider import LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import KcEdge, RagHit, StaticEvidenceFetcher
from pythontopia_graph.routes.courseware import CoursewareAnswerInput, courseware_answer


class _AnswerProvider:
    async def chat(
        self, messages: list[dict[str, str]], *, json_mode: bool = True, **kwargs: object
    ) -> LlmResponse:
        return LlmResponse(
            content_json={
                "answer_md": "字典词频统计需要先掌握 get/items/keys/values。[页 62]",
                "cited_page_nos": [62],
            }
        )

    async def chat_stream(
        self, messages: list[dict[str, str]], **kwargs: object
    ) -> AsyncIterator[str]:
        if False:
            yield ""


class _FailingAnswerProvider(_AnswerProvider):
    async def chat(self, messages, *, json_mode: bool = True, **kwargs: object) -> LlmResponse:
        raise RuntimeError("courseware-query-secret-sentinel")


@pytest.mark.asyncio
async def test_courseware_answer_carries_graph_trace_to_student_endpoint() -> None:
    fetcher = StaticEvidenceFetcher(
        [
            RagHit(
                chunk_id="p62:0",
                page_id=4201,
                page_no=62,
                chapter_id=5,
                kc_codes=["PY43-KC-042"],
                text="字典 get、items、keys、values 是完成词频统计前需要掌握的基本操作。",
                score=0.72,
                via_relation="co_occur",
            )
        ],
        route="graph",
        retrieval_mode="graph",
        subgraph=[
            KcEdge(
                from_kc="PY43-KC-041",
                to_kc="PY43-KC-042",
                relation="co_occur",
            )
        ],
        graph_trace={
            "route": "graph",
            "retrieval_mode": "graph",
            "top_k": 6,
            "hit_count": 3,
            "direct_hit_count": 2,
            "supplemental_hit_count": 1,
            "co_occur_hit_count": 1,
            "supplemental_hit_limit": 2,
            "supplemental_hit_ratio": 1 / 3,
            "relation_counts": {"co_occur": 1},
            "noise_guard": "supplemental_hits_capped_at_top_k_div_3",
        },
    )

    output = await courseware_answer(
        CoursewareAnswerInput(
            query="为什么字典词频统计要先理解 get items keys values",
            kc_codes=["PY43-KC-041"],
            top_k=6,
        ),
        provider=_AnswerProvider(),
        fetcher=fetcher,
    )

    assert output.no_evidence is False
    assert output.route == "graph"
    assert output.retrieval_mode == "graph"
    assert output.subgraph == [
        KcEdge(
            from_kc="PY43-KC-041",
            to_kc="PY43-KC-042",
            relation="co_occur",
        )
    ]
    assert output.graph_trace["co_occur_hit_count"] == 1
    assert output.graph_trace["supplemental_hit_limit"] == 2


@pytest.mark.asyncio
async def test_courseware_provider_error_logs_only_exception_type(caplog) -> None:
    output = await courseware_answer(
        CoursewareAnswerInput(query="解释列表"),
        provider=_FailingAnswerProvider(),
        fetcher=StaticEvidenceFetcher(
            [
                RagHit(
                    chunk_id="p1:0",
                    page_id=1,
                    page_no=1,
                    chapter_id=1,
                    kc_codes=["PY43-KC-001"],
                    text="列表是一种序列。",
                    score=0.8,
                )
            ]
        ),
    )

    assert "courseware-query-secret-sentinel" not in caplog.text
    assert "RuntimeError" in caplog.text
    assert "列表是一种序列" in output.answer_md
