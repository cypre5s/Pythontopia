"""TutorAgent 课件证据投影：把 RAG 命中按页去重投影成可展示的 EvidenceCitation。

证据明细让学生「看见系统命中的证据」（页码 + 原文片段），并保证 evidence_page_ids 与其同源同序。
"""

from __future__ import annotations

from pythontopia_graph.agents.tutor_agent import TutorAgent
from pythontopia_graph.nodes.evidence_fetcher import RagHit


def _hit(page_id: int, page_no: int, text: str, score: float = 0.9) -> RagHit:
    return RagHit(
        chunk_id=f"c{page_id}",
        page_id=page_id,
        page_no=page_no,
        kc_codes=["PY43-KC-030"],
        text=text,
        score=score,
    )


def test_build_response_projects_citations_and_pageids() -> None:
    chunks = [
        _hit(101, 47, "for 循环用于已知次数的遍历。", 0.91),
        _hit(101, 47, "同一页的第二个 chunk，应被按页去重。", 0.80),
        _hit(102, 55, "while 循环依赖条件表达式。", 0.77),
    ]

    response = TutorAgent(provider=None)._build_response("答案 [页 47]", chunks)

    # 按 page_id 去重，保留首条（最高分）；与 evidence_page_ids 同序同集合。
    assert [c.page_id for c in response.evidence] == [101, 102]
    assert response.evidence_page_ids == [101, 102]
    assert [c.page_no for c in response.evidence] == [47, 55]
    assert response.evidence[0].snippet == "for 循环用于已知次数的遍历。"
    assert response.evidence[0].score == 0.91
    assert response.grounding_result.passed is True
    assert response.based_on_general_knowledge is False


def test_build_response_truncates_and_collapses_snippet() -> None:
    long_text = "第一行\n第二行  含多空格" + "啊" * 200
    response = TutorAgent(provider=None)._build_response("答案", [_hit(1, 1, long_text)])

    snippet = response.evidence[0].snippet
    assert "\n" not in snippet  # 换行折叠
    assert "  " not in snippet  # 多空格折叠
    assert snippet.endswith("…")  # 超长截断
    assert len(snippet) <= 161  # 160 + 省略号


def test_build_response_no_evidence_marks_general_knowledge() -> None:
    response = TutorAgent(provider=None)._build_response("基于通识知识 ...", [])

    assert response.evidence == []
    assert response.evidence_page_ids == []
    assert response.based_on_general_knowledge is True
    assert response.grounding_result.passed is False
