"""课程包 ``packId=43``「Python语言基础」章节 ↔ KC 的**单一事实源**。

唯一权威来源：``data/course-pack-43/json/course_kc.json``（7 章 / 65 KC，
每条 KC 的 ``chapter_order`` 字段决定其章节归属）。本模块把课程包派生出的
chapter↔KC 映射固化为常量，供：

* ``routes/boss.py`` 的 ``_resolve_kc``（章号 → 默认 KC）；
* ``agents/boss/grounding.py``（判断 KC 是否真实存在，防幻觉）；
* 其它需要 KC↔章节换算的图层。

历史问题：``routes/boss`` 与 ``grounding`` 各自硬编码了「每章 10 个 KC」的边界
（``{2: KC-006, 3: KC-016, ...}`` / ``n <= 5 → 1, n <= 15 → 2 ...``），与课程包真实
分章 1-7 / 8-16 / 17-30 / 31-38 / 39-47 / 48-53 / 54-65 不一致，导致章号解析出来的
KC 落到了相邻的错误章节。本模块为权威映射，前端 ``useEndlessRun.ts`` / mainQuests 的
``kcRange`` 区间与此完全一致。

``tests/agents/boss/test_kc_map.py`` 会读取 ``course_kc.json`` 校验本表与课程包逐条一致，
确保「单一事实源」不漂移；编码均来自课程包，绝不臆造新 KC。
"""

from __future__ import annotations

PACK_ID = 43
PACK_NAME = "Python语言基础"

MIN_CHAPTER = 1
MAX_CHAPTER = 7

# 课程包 ``chapter_order`` (1..7) → 该章 KC 编号闭区间 ``[lo, hi]``。
# 区间均取自 ``course_kc.json``：编号在课程包内连续无空洞（共 65 个：001..065）。
_CHAPTER_KC_RANGES: dict[int, tuple[int, int]] = {
    1: (1, 7),    # 第一章 计算工具与计算思维
    2: (8, 16),   # 第二章 Python 语言基础
    3: (17, 30),  # 第三章 计算机信息表示及计算
    4: (31, 38),  # 第四章 流程自动化
    5: (39, 47),  # 第五章 数据结构
    6: (48, 53),  # 第六章 数据存取与分析
    7: (54, 65),  # 第七章 归纳与抽象
}


def format_kc_code(number: int) -> str:
    """KC 序号 → 规范编码，如 ``1 -> "PY43-KC-001"``。"""

    return f"PY43-KC-{number:03d}"


def _build_chapter_to_kcs() -> dict[int, tuple[str, ...]]:
    out: dict[int, tuple[str, ...]] = {}
    for chapter, (lo, hi) in _CHAPTER_KC_RANGES.items():
        out[chapter] = tuple(format_kc_code(n) for n in range(lo, hi + 1))
    return out


# 章号 → 该章全部 KC 编码（按编号升序）。本表是其它换算函数的唯一来源。
CHAPTER_TO_KCS: dict[int, tuple[str, ...]] = _build_chapter_to_kcs()

# KC 编码 → 章号 的反查表。
_KC_TO_CHAPTER: dict[str, int] = {
    kc: chapter for chapter, kcs in CHAPTER_TO_KCS.items() for kc in kcs
}


def kc_codes_for_chapter(chapter: int) -> tuple[str, ...]:
    """返回某章（课程包 ``chapter_order`` 1..7）的全部 KC 编码；越界返回空元组。"""

    return CHAPTER_TO_KCS.get(chapter, ())


def chapter_default_kc(chapter: int) -> str:
    """章号 → 该章首个（编号最小）KC，作为缺省 KC。

    章号越界时 clamp 到 ``[1, 7]``，保证总能返回一个真实存在的课程包 KC，
    与 ``golden_problems.get_golden_problem`` 的越界 clamp 行为一致。
    """

    c = max(MIN_CHAPTER, min(MAX_CHAPTER, chapter))
    return CHAPTER_TO_KCS[c][0]


def chapter_for_kc(kc_code: str) -> int | None:
    """KC 编码 → 课程包章号；未知编码返回 ``None``（不臆造归属）。"""

    if not kc_code:
        return None
    return _KC_TO_CHAPTER.get(kc_code.strip())


def is_known_kc(kc_code: str) -> bool:
    """该 KC 是否属于 ``packId=43`` 课程包（防幻觉用）。"""

    return chapter_for_kc(kc_code) is not None


def all_kc_codes() -> tuple[str, ...]:
    """返回课程包全部 65 个 KC 编码（按编号升序）。"""

    return tuple(_KC_TO_CHAPTER.keys())
