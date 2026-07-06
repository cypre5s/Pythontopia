from __future__ import annotations

DEFAULT_FORBIDDEN = (
    "色情",
    "暴力",
    "毒品",
    "政治攻击",
    "辱骂",
)


def is_forbidden(query: str, forbidden: tuple[str, ...] = DEFAULT_FORBIDDEN) -> bool:
    """安全过滤：空查询或命中禁用词时返回 True，调用方应置 no_evidence_found。"""
    lowered = query.strip().lower()
    if not lowered:
        return True
    return any(keyword in lowered for keyword in forbidden)
