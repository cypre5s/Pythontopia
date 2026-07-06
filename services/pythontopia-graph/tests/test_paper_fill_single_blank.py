"""R5：FILL 题面单空硬校验——多空与后端 judgeFill 单空归一化判分语义冲突，须 fail-fast。"""

from __future__ import annotations

import pytest

from pythontopia_graph.agents.paper_designer import PaperDesignError, PaperDesignerAgent
from pythontopia_graph.schemas.chapter_test import FillItem, QuestionPaper


def _fill(qid: str, stem: str) -> FillItem:
    return FillItem(
        qid=qid,
        stem=stem,
        answers=["print"],
        kc_code="PY43-KC-001",
        evidence_page_ids=[101],
    )


def test_single_blank_fill_passes() -> None:
    paper = QuestionPaper(items=[_fill("f1", "输出函数是 ____ 。")])
    PaperDesignerAgent._assert_fill_single_blank(paper)  # 不抛即通过


def test_multi_blank_fill_fails_fast() -> None:
    paper = QuestionPaper(items=[_fill("f1", "____ 和 ____ 都是循环。")])
    with pytest.raises(PaperDesignError, match="exactly one blank"):
        PaperDesignerAgent._assert_fill_single_blank(paper)


def test_zero_blank_fill_fails_fast() -> None:
    paper = QuestionPaper(items=[_fill("f1", "没有空位的题面。")])
    with pytest.raises(PaperDesignError, match="exactly one blank"):
        PaperDesignerAgent._assert_fill_single_blank(paper)
