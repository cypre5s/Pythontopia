"""Environment initialization for unsaved authoring and speech service settings."""

import os

from .contracts import AuthoringSettings, SpeechSettings

DEFAULT_VOICE = "Meilin"
DEEPSEEK_VOICE = "stella"

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


def authoring_environment() -> AuthoringSettings:
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


def environment_protocol() -> str:
    value = (os.getenv("REBORN_TTS_PROTOCOL") or "openai").strip().lower()
    return "deepseek" if value == "deepseek" else "openai"


def speech_environment() -> SpeechSettings:
    vendor = environment_protocol()
    return SpeechSettings(
        protocol=vendor,
        base_url=(os.getenv("REBORN_TTS_BASE_URL") or "").rstrip("/"),
        api_key=(os.getenv("REBORN_TTS_API_KEY") or "").strip(),
        model=(os.getenv("REBORN_TTS_MODEL") or "zonos3-mini").strip(),
        voice=(os.getenv("REBORN_TTS_VOICE") or (DEEPSEEK_VOICE if vendor == "deepseek" else DEFAULT_VOICE)).strip(),
        format=(os.getenv("REBORN_TTS_FORMAT") or ("wav" if vendor == "deepseek" else "mp3")).strip().lower(),
        speed=float(os.getenv("REBORN_TTS_SPEED") or 1.0),
        timeout=int(os.getenv("REBORN_TTS_TIMEOUT_SECONDS") or 120),
    )
