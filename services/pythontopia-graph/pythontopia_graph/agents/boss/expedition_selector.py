"""无尽远征「知识前沿选择器」：把出题 KC 的挑选逻辑下沉到 graph（权威 + 可扩展间隔重复）。

输入学生掌握度 + 画像信号 + 近期练过的 KC，输出按优先级排序的候选 KC（含章节、建议难度、原因）。
纯逻辑无 I/O，便于单测：

* ZPD 优先：掌握度在 [0.2, 0.8] 的「够得着」知识点优先；
* 画像弱项加权；尚未掌握加权；越不熟越靠前；
* 间隔重复：久未练（不在近期列表）适合复习上浮，刚练过的强力压制避免立刻重复；
* 难度由掌握度推断，低成功率下调、高成功率上调。

KC↔章节用单一事实源 ``kc_map``，绝不臆造课程包外的 KC。
"""

from __future__ import annotations

from dataclasses import dataclass

from pythontopia_graph.agents.boss.kc_map import (
    all_kc_codes,
    chapter_for_kc,
    kc_codes_for_chapter,
)

ZPD_LO = 0.2
ZPD_HI = 0.8
MASTERED = 0.8


@dataclass(frozen=True)
class FrontierPick:
    kc_code: str
    chapter: int
    difficulty: int
    reason: str


def _difficulty_for(mastery: float, success_rate: float | None) -> int:
    d = round(1 + mastery * 4)
    if success_rate is not None:
        if success_rate < 0.3:
            d -= 1
        elif success_rate >= 0.75:
            d += 1
    return max(1, min(5, d))


def _priority(
    kc: str,
    mastery: dict[str, float],
    weak: set[str],
    recent_rank: int | None,
) -> tuple[float, str]:
    m = mastery.get(kc, 0.0)
    score = 0.0
    reasons: list[str] = []
    if kc in weak and m < MASTERED:
        score += 100
        reasons.append("画像弱项")
    if ZPD_LO <= m <= ZPD_HI:
        score += 50
        reasons.append("最近发展区")
    if m < ZPD_LO:
        score += 20
        reasons.append("尚未掌握")
    score += (1 - m) * 10
    if recent_rank is None:
        score += 15  # 久未练 → 适合间隔复习
        reasons.append("间隔复习")
    else:
        # 越近越压制（rank 0 最近 → -25，rank≥5 → -5），避免立刻重复同一 KC。
        score -= 25 - min(recent_rank, 5) * 4
    return score, "、".join(reasons) or "巩固"


def select_frontier(
    *,
    mastery: dict[str, float] | None = None,
    profile_signals: dict | None = None,
    recent_kcs: list[str] | None = None,
    focus_chapter: int | None = None,
    exclude: list[str] | None = None,
    count: int = 1,
) -> list[FrontierPick]:
    """返回按优先级排序的前沿 KC（最多 ``count`` 个）。"""

    mastery = mastery or {}
    signals = profile_signals or {}
    weak = set(signals.get("weakKcCodes") or signals.get("weak_kc_codes") or [])
    success_rate = signals.get("successRate", signals.get("success_rate"))
    recent = list(recent_kcs or [])
    recent_rank = {kc: i for i, kc in enumerate(recent)}
    exclude_set = set(exclude or [])

    base_pool = (
        list(kc_codes_for_chapter(focus_chapter)) if focus_chapter else list(all_kc_codes())
    )
    pool = [kc for kc in base_pool if kc not in exclude_set] or base_pool

    scored = sorted(
        (
            (*_priority(kc, mastery, weak, recent_rank.get(kc)), kc)
            for kc in pool
        ),
        key=lambda t: t[0],
        reverse=True,
    )

    picks: list[FrontierPick] = []
    for score, reason, kc in scored[: max(1, count)]:
        del score
        picks.append(
            FrontierPick(
                kc_code=kc,
                chapter=chapter_for_kc(kc) or 1,
                difficulty=_difficulty_for(mastery.get(kc, 0.0), success_rate),
                reason=reason,
            )
        )
    return picks
