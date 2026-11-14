"""Typed shapes shared across the narration package.

These are the values that cross module boundaries (settings, model replies, per-page
results, student answers). They are intentionally plain TypedDicts rather than pydantic
models: the wire contract lives in `router.py`, and these only describe in-process data.
"""

from typing import Any, TypedDict


class AuthoringSettings(TypedDict):
    api_key: str
    base_url: str
    model: str
    vision_model: str
    effort: str
    max_tokens: int
    timeout: int
    daily_pages: int


class SpeechSettings(TypedDict):
    base_url: str
    api_key: str
    model: str
    voice: str
    format: str
    speed: float
    timeout: int


class ChatReply(TypedDict):
    raw: dict[str, Any]
    text: str
    bridge_in: str
    anchors: list[str]
    usage: dict[str, Any]
    model: str


class GeneratedPage(TypedDict, total=False):
    page_no: int
    status: str
    reused: bool
    model: str | None
    chars: int
    image_used: bool
    error: str


class Quota(TypedDict):
    used_today: int
    daily_limit: int
    available: bool


class AudioRecord(TypedDict):
    page_no: int
    reused: bool
    key: str
    seconds: int


class CalibrationIssue(TypedDict):
    page_no: int
    kind: str
    detail: str
    status: str


class CalibrationReport(TypedDict):
    windows: int
    pages: int
    summary: str
    terms: list[str]
    issues: list[CalibrationIssue]
    revisions: dict[str, str]
    generated_at: str


class AppliedCalibration(TypedDict):
    regenerated: list[int]
    failed: list[dict[str, Any]]
    awaiting_teacher: list[int]
