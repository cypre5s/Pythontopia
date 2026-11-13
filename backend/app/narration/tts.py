"""Server-side speech synthesis for approved narration.

Only teacher-approved wording may be spoken, and the audio is invalidated as soon as the
wording changes — the voice must never say something the teacher did not review.

The provider is OpenAI-compatible (`POST /v1/audio/speech`), so switching engines is an
environment change. Weights for a self-hosted engine are fetched outside the image.
"""

import os

import httpx

DEFAULT_VOICE = "Meilin"


class SpeechUnavailable(RuntimeError):
    """No TTS endpoint configured; the feature is unavailable rather than degraded."""


class SpeechFailed(RuntimeError):
    """The engine answered but the audio cannot be used."""


def settings() -> dict:
    return {
        "base_url": (os.getenv("REBORN_TTS_BASE_URL") or "").rstrip("/"),
        "api_key": (os.getenv("REBORN_TTS_API_KEY") or "").strip(),
        "model": (os.getenv("REBORN_TTS_MODEL") or "zonos3-mini").strip(),
        "voice": (os.getenv("REBORN_TTS_VOICE") or DEFAULT_VOICE).strip(),
        "format": (os.getenv("REBORN_TTS_FORMAT") or "mp3").strip(),
        "speed": float(os.getenv("REBORN_TTS_SPEED") or 1.0),
        "timeout": int(os.getenv("REBORN_TTS_TIMEOUT_SECONDS") or 120),
    }


def unavailable_reason() -> str | None:
    if not settings()["base_url"]:
        return "未配置语音合成服务，讲解音频不可用"
    return None


def synthesize(text: str, *, settings_override: dict | None = None) -> bytes:
    """Return audio bytes for one page; the caller decides where to store them."""
    config = settings_override or settings()
    if not config["base_url"]:
        raise SpeechUnavailable("未配置语音合成服务")
    body = {
        "model": config["model"],
        "voice": config["voice"],
        "input": text,
        "response_format": config["format"],
        "speed": config["speed"],
    }
    headers = {"Content-Type": "application/json"}
    if config["api_key"]:
        headers["Authorization"] = f"Bearer {config['api_key']}"
    try:
        with httpx.Client(timeout=config["timeout"]) as client:
            response = client.post(config["base_url"] + "/v1/audio/speech", headers=headers, json=body)
    except httpx.HTTPError as exc:
        raise SpeechFailed("语音合成请求失败，请稍后重试") from exc
    if response.status_code in {401, 403}:
        raise SpeechFailed("语音合成服务鉴权失败")
    if response.status_code != 200:
        raise SpeechFailed(f"语音合成服务返回 {response.status_code}")
    audio = response.content
    if len(audio) < 512:
        raise SpeechFailed("语音合成返回的音频为空或过短")
    return audio
