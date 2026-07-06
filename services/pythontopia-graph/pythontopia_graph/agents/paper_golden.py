"""确定式 golden 题组构造器（章末大测试兜底）。

用途：当 LLM 不可用或后端重试耗尽时，由调用方（后端预生成队列）用**真实证据页 id**
（来自 seed/RAG，绝不编造）构造一套可稳定通过三审的题面。本模块只生成内容、不接网络，
因此也用于 dry-run 与单测的确定性基线。

注意：客观题需要真实 ``evidence_page_ids`` 才能过引用审查；若请求含客观题却未提供证据页，
直接报错（拒绝产出无法接地的题，符合防幻觉约束）。
"""

from __future__ import annotations

from pythontopia_graph.schemas.chapter_test import (
    ChoiceItem,
    CodingItem,
    CodingSample,
    FillItem,
    JudgeItem,
    PaperCounts,
    PaperItem,
    QuestionPaper,
)

_GOLDEN_REFERENCE_SOLUTION = "a, b = map(int, input().split())\nprint(a + b)"


def build_golden_paper(
    *,
    chapter: int,
    kc_codes: list[str],
    counts: PaperCounts,
    evidence_page_ids: list[int],
) -> QuestionPaper:
    if not kc_codes:
        raise ValueError("build_golden_paper requires at least one kc_code")
    pages = [int(p) for p in evidence_page_ids]
    objective_total = counts.choice + counts.judge + counts.fill
    if objective_total > 0 and not pages:
        raise ValueError(
            "golden paper with objective items requires real evidence_page_ids"
        )

    items: list[PaperItem] = []

    def kc_at(index: int) -> str:
        return kc_codes[index % len(kc_codes)]

    for i in range(counts.choice):
        kc = kc_at(i)
        items.append(
            ChoiceItem(
                qid=f"c{i + 1}",
                stem=f"关于知识点 {kc}（第{chapter}章）的说法，下列哪一项最准确？",
                options=[
                    f"{kc} 是第{chapter}章需要掌握的核心知识点。",
                    f"{kc} 与本课程无关，可以跳过。",
                    f"{kc} 只能在其它编程语言中使用。",
                    f"{kc} 无需理解即可直接掌握。",
                ],
                answer="A",
                explain=f"{kc} 属于第{chapter}章重点内容，需结合课件理解。",
                kc_code=kc,
                evidence_page_ids=list(pages),
                score=counts.choice_score,
            )
        )

    for i in range(counts.judge):
        kc = kc_at(i)
        items.append(
            JudgeItem(
                qid=f"j{i + 1}",
                stem=f"知识点 {kc} 属于第{chapter}章的学习内容。",
                answer=True,
                explain=f"{kc} 确为第{chapter}章知识点。",
                kc_code=kc,
                evidence_page_ids=list(pages),
                score=counts.judge_score,
            )
        )

    for i in range(counts.fill):
        kc = kc_at(i)
        items.append(
            FillItem(
                qid=f"f{i + 1}",
                stem=f"（{kc}）在 Python 中，用于把内容输出到屏幕的内置函数是 ____。",
                answers=["print", "print()"],
                match="normalized",
                explain="Python 使用内置函数 print 输出内容。",
                kc_code=kc,
                evidence_page_ids=list(pages),
                score=counts.fill_score,
            )
        )

    for i in range(counts.coding):
        kc = kc_at(i)
        base = (i + 1) * 10
        items.append(
            CodingItem(
                qid=f"p{i + 1}",
                title=f"两数之和（{kc}）",
                statement_markdown=(
                    f"考查知识点 {kc}。读入一行两个整数 a 和 b（空格分隔），"
                    "用 input 读取、用 print 输出它们的和。"
                ),
                samples=[CodingSample(input="1 2", output="3")],
                hidden_test_cases=[
                    CodingSample(input=f"{base} {base + 5}", output=str(base + base + 5)),
                    CodingSample(input="0 0", output="0"),
                ],
                reference_solution=_GOLDEN_REFERENCE_SOLUTION,
                kc_code=kc,
                score=counts.coding_score,
            )
        )

    return QuestionPaper(items=items)
