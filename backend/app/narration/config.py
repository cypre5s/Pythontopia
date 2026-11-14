"""Authoring-model settings, kept separate from the student assistant on purpose.

Course authoring must not consume a class's teaching budget, and swapping providers
must not touch student-facing behaviour.
"""

import os

from .contracts import AuthoringSettings

DEFAULT_MODEL = "deepseek-flash"
DEFAULT_BASE_URL = "https://api.deepseek.com"


def _int(name: str, default: int) -> int:
    raw = (os.getenv(name) or "").strip()
    if not raw:
        return default
    try:
        return int(raw)
    except ValueError:
        return default


def settings() -> AuthoringSettings:
    model = (os.getenv("REBORN_AUTHORING_AI_MODEL") or DEFAULT_MODEL).strip()
    return AuthoringSettings(
        base_url=(os.getenv("REBORN_AUTHORING_AI_BASE_URL") or DEFAULT_BASE_URL).rstrip("/"),
        api_key=(os.getenv("REBORN_AUTHORING_AI_API_KEY") or "").strip(),
        model=model,
        vision_model=(os.getenv("REBORN_AUTHORING_AI_VISION_MODEL") or model).strip(),
        # "none" disables the thinking pass; measured at 2-3s per page against 15-55s.
        effort=(os.getenv("REBORN_AUTHORING_AI_EFFORT") or "none").strip(),
        max_tokens=_int("REBORN_AUTHORING_AI_MAX_TOKENS", 8000),
        timeout=_int("REBORN_AUTHORING_AI_TIMEOUT_SECONDS", 120),
        daily_pages=_int("REBORN_AUTHORING_AI_DAILY_PAGES", 300),
    )


def unavailable_reason() -> str | None:
    """Missing credentials mean unavailable, never a silent fallback."""
    if not settings()["api_key"]:
        return "未配置作者期模型凭据，讲解生成不可用"
    return None
