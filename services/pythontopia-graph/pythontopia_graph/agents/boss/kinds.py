"""Boss / Arena 9 种题型的判题路由分类。

显式区分两条判题路径，杜绝「把自然语言当 Python 跑」：

* **文本题**（``TEXT_KINDS``）：学生用自然语言作答，必须走接地评分
  （``text_evaluator.evaluate_text_answer``），绝不能进 stdout 沙箱。
* **代码题**（``CODE_KINDS`` 及缺省/未知题型）：走 ``evaluator.evaluate_student_code``
  的 stdout 沙箱判题；未知或缺省 ``kind`` 默认按代码题处理，保持旧 evaluate 调用向后兼容。
"""

from __future__ import annotations

# 文本题：自然语言作答 → 接地评分。
TEXT_KINDS: frozenset[str] = frozenset(
    {
        "short_answer",
        "teach_back",
        "pdf_question",
    }
)

# 代码题：stdout 沙箱判题。
CODE_KINDS: frozenset[str] = frozenset(
    {
        "fill_blank",
        "debug",
        "closed_loop",
        "dual_track",
        "bug_mine",
        "multi_round",
    }
)


def normalize_kind(kind: str | None) -> str:
    """归一化题型字符串（去空白 + 小写）；``None`` → ``""``。"""

    return (kind or "").strip().lower()


def is_text_kind(kind: str | None) -> bool:
    """是否文本题（走接地评分）。"""

    return normalize_kind(kind) in TEXT_KINDS


def is_code_kind(kind: str | None) -> bool:
    """是否代码题（走 stdout 沙箱）。

    缺省 / 未知题型一律按代码题处理，保持旧 evaluate 调用（无 kind）向后兼容。
    """

    return not is_text_kind(kind)
