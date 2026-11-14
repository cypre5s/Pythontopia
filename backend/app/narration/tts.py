"""Server-side speech synthesis for approved narration.

Only teacher-approved wording may be spoken, and the audio is invalidated as soon as the
wording changes — the voice must never say something the teacher did not review.

Two request shapes are supported behind one seam, selected by `REBORN_TTS_PROTOCOL`:

- `openai` (default): `POST /v1/audio/speech` with `{model, input, voice, response_format, speed}`.
- `deepseek`: `POST /v1/tools/tts` with `{text, voice_id, format, filter_markdown}`. DeepSeek
  has no speed control, only two voices (stella/tide) and returns wav (or raw pcm).

Weights for a self-hosted engine are fetched outside the image; nothing is baked in.
"""

import os

import httpx

from .contracts import SpeechSettings

DEFAULT_VOICE = "Meilin"
DEEPSEEK_VOICE = "stella"
DEEPSEEK_FORMATS = {"wav", "pcm"}


class SpeechUnavailable(RuntimeError):
    """No TTS endpoint configured; the feature is unavailable rather than degraded."""


class SpeechFailed(RuntimeError):
    """The engine answered but the audio cannot be used."""


def protocol() -> str:
    value = (os.getenv("REBORN_TTS_PROTOCOL") or "openai").strip().lower()
    return "deepseek" if value == "deepseek" else "openai"


def settings() -> SpeechSettings:
    vendor = protocol()
    return SpeechSettings(
        base_url=(os.getenv("REBORN_TTS_BASE_URL") or "").rstrip("/"),
        api_key=(os.getenv("REBORN_TTS_API_KEY") or "").strip(),
        model=(os.getenv("REBORN_TTS_MODEL") or "zonos3-mini").strip(),
        voice=(os.getenv("REBORN_TTS_VOICE") or (DEEPSEEK_VOICE if vendor == "deepseek" else DEFAULT_VOICE)).strip(),
        format=(os.getenv("REBORN_TTS_FORMAT") or ("wav" if vendor == "deepseek" else "mp3")).strip().lower(),
        speed=float(os.getenv("REBORN_TTS_SPEED") or 1.0),
        timeout=int(os.getenv("REBORN_TTS_TIMEOUT_SECONDS") or 120),
    )


def unavailable_reason() -> str | None:
    if not settings()["base_url"]:
        return "未配置语音合成服务，讲解音频不可用"
    return None


def _request(config: SpeechSettings, text: str) -> tuple[str, dict[str, object]]:
    """Path + body for the configured protocol; unsupported knobs are dropped, not ignored silently."""
    if config["format"] and config["format"] not in {"wav", "pcm"} and protocol() == "deepseek":
        raise SpeechFailed(f"DeepSeek 语音只支持 wav/pcm，当前配置为 {config['format']}")
    if protocol() == "deepseek":
        return "/v1/tools/tts", {
            "text": text,
            # Our prompts forbid Markdown; leaving the filter on would also mangle code
            # identifiers such as `PCA(n_components=0.95)` or `**kwargs`.
            "voice_id": config["voice"],
            "format": config["format"] if config["format"] in DEEPSEEK_FORMATS else "wav",
            "filter_markdown": False,
        }
    return "/v1/audio/speech", {
        "model": config["model"],
        "voice": config["voice"],
        "input": text,
        "response_format": config["format"],
        "speed": config["speed"],
    }


def synthesize(text: str, *, settings_override: SpeechSettings | None = None) -> bytes:
    """Return audio bytes for one page; the caller decides where to store them."""
    config = settings_override or settings()
    if not config["base_url"]:
        raise SpeechUnavailable("未配置语音合成服务")
    path, body = _request(config, text)
    headers = {"Content-Type": "application/json"}
    if config["api_key"]:
        headers["Authorization"] = f"Bearer {config['api_key']}"
    try:
        with httpx.Client(timeout=config["timeout"]) as client:
            response = client.post(config["base_url"] + path, headers=headers, json=body)
    except httpx.HTTPError as exc:
        raise SpeechFailed("语音合成请求失败，请稍后重试") from exc
    if response.status_code in {401, 403}:
        raise SpeechFailed("语音合成服务鉴权失败")
    if response.status_code == 400:
        raise SpeechFailed("语音合成被拒绝：文本为空或未通过内容审核")
    if response.status_code == 429:
        raise SpeechFailed("语音合成并发超限，请稍后重试")
    if response.status_code in {503, 504}:
        raise SpeechFailed("语音合成服务繁忙或超时，请稍后重试")
    if response.status_code != 200:
        raise SpeechFailed(f"语音合成服务返回 {response.status_code}")
    audio = response.content
    if len(audio) < 512:
        raise SpeechFailed("语音合成返回的音频为空或过短")
    if config["format"] == "pcm":
        audio = _wrap_pcm(audio)
    if config["format"] == "wav":
        audio = _normalise_wav(audio)
    return audio


def _wrap_pcm(pcm: bytes) -> bytes:
    """DeepSeek's raw pcm is 24 kHz / 16-bit / mono; wrap it so browsers can play it."""
    channels, bits, rate = 1, 16, 24000
    block_align = channels * bits // 8
    header = b"RIFF" + (36 + len(pcm)).to_bytes(4, "little") + b"WAVEfmt "
    header += (16).to_bytes(4, "little") + (1).to_bytes(2, "little")
    header += channels.to_bytes(2, "little") + rate.to_bytes(4, "little")
    header += (rate * block_align).to_bytes(4, "little") + block_align.to_bytes(2, "little")
    header += bits.to_bytes(2, "little") + b"data" + len(pcm).to_bytes(4, "little")
    return header + pcm


def _normalise_wav(audio: bytes) -> bytes:
    """Fix up streamed wav headers and treat an early end as a failure.

    DeepSeek streams wav with `0xFFFFFFFF` size sentinels (the real length is unknown until
    it finishes), which also breaks later duration parsing. Rewriting the two sizes to the
    bytes actually received makes the stored file correct and the duration computable.
    """
    if audio[:4] != b"RIFF" or audio[8:12] != b"WAVE":
        raise SpeechFailed("语音合成返回的不是完整 wav")
    position = 12
    while position + 8 <= len(audio):
        chunk_id = audio[position:position + 4]
        size = int.from_bytes(audio[position + 4:position + 8], "little")
        if chunk_id == b"data":
            payload = len(audio) - (position + 8)
            if payload <= 0:
                raise SpeechFailed("语音合成返回的 wav 缺少音频数据")
            payload -= payload % 2  # 16-bit mono: a dangling byte means the stream was cut
            if size not in {0, 0xFFFFFFFF} and payload + 1 < size:
                raise SpeechFailed("语音合成中途断开，音频不完整，请重试")
            patched = bytearray(audio[:position + 8 + payload])
            patched[4:8] = (len(patched) - 8).to_bytes(4, "little")
            patched[position + 4:position + 8] = payload.to_bytes(4, "little")
            return bytes(patched)
        if size in {0, 0xFFFFFFFF}:
            break
        position += 8 + size + (size & 1)
    raise SpeechFailed("语音合成返回的 wav 缺少音频数据")
