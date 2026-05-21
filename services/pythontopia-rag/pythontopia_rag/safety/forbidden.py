from __future__ import annotations

DEFAULT_FORBIDDEN = (
    "色情",
    "暴力",
    "毒品",
    "政治攻击",
    "辱骂",
)


def is_forbidden(query: str, forbidden: tuple[str, ...] = DEFAULT_FORBIDDEN) -> bool:
    lowered = query.strip().lower()
    if not lowered:
        return True
    return any(keyword in lowered for keyword in forbidden)
