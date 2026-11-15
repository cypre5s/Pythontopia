"""Authoring-model settings, kept separate from the student assistant on purpose.

Course authoring must not consume a class's teaching budget, and swapping providers
must not touch student-facing behaviour.
"""

from typing import cast
from sqlalchemy.orm import Session

from .contracts import AuthoringSettings

def settings(db: Session | None = None) -> AuthoringSettings:
    from ..service_settings import configuration
    return cast(AuthoringSettings, configuration("authoring", db))


def unavailable_reason(db: Session | None = None) -> str | None:
    """Missing credentials mean unavailable, never a silent fallback."""
    if not settings(db)["api_key"]:
        return "未配置作者期模型凭据，讲解生成不可用"
    return None
