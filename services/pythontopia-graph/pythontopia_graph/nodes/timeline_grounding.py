"""视频关键时间点的防幻觉硬约束节点（纯函数）。

与「不伪造课件页码」同级红线：``VideoInsightAgent`` 让 LLM 产出候选 ``key_points`` 后，
本节点把 ``t_sec`` 不在**真实时间集合**（官方 AI 总结 outline 各段秒 ∪ 分 P 各 P 起始秒）
里的点**全部剔除**——无论 LLM 多自信，编造的秒数一律不入库、不返回。

``based_on`` 据真实数据可用性裁定（与 schema ``VideoInsightResponse.based_on`` 对齐）：
有 outline 时间轴→``ai_conclusion``；否则有分 P 时间轴→``pages``；都没有→``metadata_only``。

本模块只依赖 schema 与标准库，不触网、不调 LLM，便于单测与 ``routes`` 复用其时间集合提取。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Literal

from pythontopia_graph.schemas.video_insight import VideoKeyPoint

BasedOn = Literal["ai_conclusion", "pages", "metadata_only"]


def coerce_t_sec(value: object) -> int | None:
    """把 B 站时间字段（int/float/纯数字字符串）规整成非负秒；无法解析返回 ``None``。

    与 ``routes/video.py`` 的 ``_to_int`` 同源思路：``bool`` 不当数字、负数夹到 0、
    带小数的秒向下取整。``get_pages`` 的 ``duration`` 与 outline 的 ``timestamp`` 均为秒。
    """
    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return max(0, value)
    if isinstance(value, float):
        return max(0, int(value))
    if isinstance(value, str):
        text = value.strip()
        if text.isdigit():
            return int(text)
    return None


@dataclass(frozen=True)
class TimelineSegment:
    """一段真实时间轴：来自官方 AI 总结 outline 的一段，或分 P 的一 P 起始。

    ``text`` 仅用于喂给 LLM 概括 ``label``（不直接外泄为最终文案），``t_sec`` 是唯一可信跳转秒。
    """

    t_sec: int
    text: str = ""


@dataclass(frozen=True)
class TimelineGroundingResult:
    """grounding 产物：已校验的关键时间点 + 概述依据标签。"""

    key_points: list[VideoKeyPoint] = field(default_factory=list)
    based_on: BasedOn = "metadata_only"


def _resolve_based_on(
    outline_timestamps: set[int], pages_timestamps: set[int]
) -> BasedOn:
    """据真实时间集合的可用性裁定概述依据（官方 AI 总结优先于分 P）。"""
    if outline_timestamps:
        return "ai_conclusion"
    if pages_timestamps:
        return "pages"
    return "metadata_only"


def timeline_grounding(
    candidates: list[VideoKeyPoint],
    outline_timestamps: set[int],
    pages_timestamps: set[int],
) -> TimelineGroundingResult:
    """剔除 ``t_sec`` 不在真实集合里的候选点，并据可用数据返回 ``based_on``。

    - 保留 LLM 给定顺序（即「按学生问题相关度排序」），仅过滤、不重排；
    - 按 ``t_sec`` 去重（保留首次出现，维持相关度次序）；
    - ``source`` 以**实际命中**的真实集合为准（outline 命中记 ``ai_conclusion``，否则
      分 P 命中记 ``pages``），不轻信 LLM 自报来源——这是防幻觉的纵深防御。
    """
    based_on = _resolve_based_on(outline_timestamps, pages_timestamps)
    grounded: list[VideoKeyPoint] = []
    seen: set[int] = set()
    for candidate in candidates:
        t_sec = candidate.t_sec
        if t_sec in seen:
            continue
        if t_sec in outline_timestamps:
            source: Literal["ai_conclusion", "pages"] = "ai_conclusion"
        elif t_sec in pages_timestamps:
            source = "pages"
        else:
            # 不在任何真实时间轴里 → 判为编造，硬剔除（红线）。
            continue
        grounded.append(
            VideoKeyPoint(t_sec=t_sec, label=candidate.label, source=source)
        )
        seen.add(t_sec)
    return TimelineGroundingResult(key_points=grounded, based_on=based_on)
