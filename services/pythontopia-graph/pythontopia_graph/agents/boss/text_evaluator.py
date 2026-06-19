"""Boss 文本题（short_answer / teach_back / pdf_question）接地评分。

非代码题不能跑 stdout 沙箱：把自然语言当 Python 跑必然报错、永远过不了关。本模块给出
**确定性、结构化**的「通过 / 部分 / 未过」评分，沿用 ``grounding.py`` 的「证据优先、绝不臆造」
理念（NFR-AIH）：

* 评分证据来自题目自带的参考要点 ``test_cases[*].expected_output`` 与可选 ``reference_answer``；
* 完全没有证据时返回 ``no_evidence_found``，而不是臆造通过或伪造引用；
* 全程不调用 LLM，确定性可复现，fail fast（不会静默降级为闲聊）。

返回 ``evaluator.EvaluationResult``，与代码题同一 schema，路由层无需区分两条路径的返回形状。
"""

from __future__ import annotations

import re
from math import ceil
from typing import Sequence

from .evaluator import EvaluationResult
from .grounding import _default_keywords_for_chapter
from .kc_map import chapter_for_kc
from .schemas import TestCase

_WORD_RE = re.compile(r"[a-zA-Z_][a-zA-Z0-9_]+")
_CJK_RUN_RE = re.compile(r"[\u4e00-\u9fff]+")

# 单个要点：命中其显著 token 的比例 ≥ 该值视为「覆盖」。
DEFAULT_POINT_COVERAGE = 0.5
# 覆盖要点数 / 可评要点数 ≥ 该值视为「通过」。
DEFAULT_PASS_RATIO = 0.6
# 质量门：答案显著 token 少于该值视为「过短」。
MIN_ANSWER_TOKENS = 3


def _significant_tokens(text: str) -> set[str]:
    """抽取用于接地匹配的显著 token：英文单词（≥2 字符）+ 中文 bigram。

    中文按相邻二字成词（如「递归」「函数」「字典」）提升匹配鲁棒性；英文取标识符样式词。
    """

    if not text:
        return set()
    tokens: set[str] = set(_WORD_RE.findall(text.lower()))
    # 仅在每段连续中文（maximal CJK run）内部取 bigram，避免跨空格/标点拼出「归函」之类噪声词。
    for run in _CJK_RUN_RE.findall(text):
        tokens.update(run[i : i + 2] for i in range(len(run) - 1))
    return tokens


def _hint_token(point: str) -> str:
    """从一个未覆盖要点里挑一个最长显著 token 作为提示词（不泄露完整参考答案）。"""

    toks = _significant_tokens(point)
    return max(toks, key=len) if toks else ""


def _collect_reference_points(
    test_cases: Sequence[TestCase],
    reference_answer: str | None,
) -> list[str]:
    """汇总接地证据：非空的 expected_output 作为参考要点；都为空时退而用 reference_answer。"""

    points = [
        tc.expected_output.strip()
        for tc in test_cases
        if (tc.expected_output or "").strip()
    ]
    if not points and reference_answer and reference_answer.strip():
        points = [reference_answer.strip()]
    return points


def evaluate_text_answer(
    answer: str,
    test_cases: Sequence[TestCase],
    *,
    reference_answer: str | None = None,
    kc_code: str | None = None,
    point_coverage: float = DEFAULT_POINT_COVERAGE,
    pass_ratio: float = DEFAULT_PASS_RATIO,
    min_answer_tokens: int = MIN_ANSWER_TOKENS,
) -> EvaluationResult:
    """对文本作答做接地评分，返回与代码题同构的 ``EvaluationResult``。"""

    total_declared = len(test_cases)
    points = _collect_reference_points(test_cases, reference_answer)

    # 可选：用 KC 对应章节关键词补充证据（仅当题目自身要点不足时），复用 grounding 的关卡词表。
    if kc_code:
        chapter = chapter_for_kc(kc_code)
        if chapter is not None:
            extra = _default_keywords_for_chapter(chapter)
            if extra:
                points = points + [" ".join(extra)]

    gradable = [(i, p, _significant_tokens(p)) for i, p in enumerate(points)]
    gradable = [(i, p, toks) for (i, p, toks) in gradable if toks]

    # NFR-AIH：没有任何可接地的证据时，明确返回 no_evidence_found，不臆造通过。
    if not gradable:
        return EvaluationResult(
            passed=False,
            passed_count=0,
            total_count=total_declared,
            runtime_error="no_evidence_found",
            failed_indices=list(range(total_declared)),
            feedback="无参考要点可供接地评分（拒绝臆造，NFR-AIH）：题目缺少参考答案/要点。",
        )

    answer_tokens = _significant_tokens(answer)
    total = len(gradable)

    # 质量门：答案过短直接判未过，给出明确反馈。
    if len(answer_tokens) < min_answer_tokens:
        return EvaluationResult(
            passed=False,
            passed_count=0,
            total_count=total,
            failed_indices=list(range(total)),
            feedback=(
                f"回答过短（仅 {len(answer_tokens)} 个有效词，需 ≥ {min_answer_tokens}），"
                "请展开说明关键概念。"
            ),
        )

    covered = 0
    failed_indices: list[int] = []
    missed: list[str] = []
    for idx, point, point_tokens in gradable:
        overlap = len(point_tokens & answer_tokens) / len(point_tokens)
        if overlap >= point_coverage:
            covered += 1
        else:
            failed_indices.append(idx)
            missed.append(point)

    need = max(1, ceil(pass_ratio * total))
    passed = covered >= need

    if passed:
        return EvaluationResult(
            passed=True,
            passed_count=covered,
            total_count=total,
            failed_indices=failed_indices,
        )

    hints = [t for t in (_hint_token(p) for p in missed[:2]) if t]
    feedback = f"命中 {covered}/{total} 个关键点（需 ≥ {need}）。"
    if hints:
        feedback += "可补充与「" + "、".join(hints) + "」相关的说明。"
    return EvaluationResult(
        passed=False,
        passed_count=covered,
        total_count=total,
        failed_indices=failed_indices,
        feedback=feedback,
    )
