"""Narration audio: one recording per approved page, invalidated when the wording changes.

The container format follows `REBORN_TTS_FORMAT` (default mp3). It is carried in the storage
key, so the streaming endpoints can answer with an honest Content-Type instead of always
claiming `audio/mpeg`.
"""

import os
import shutil
import subprocess
import tempfile
from pathlib import Path

from sqlalchemy.orm import Session

from ..models import Material, now
from ..storage import get_storage, storage_root
from . import repository, tts
from .contracts import AudioRecord
from .models import PageNarration

MEDIA_TYPES = {
    "mp3": "audio/mpeg",
    "wav": "audio/wav",
    "ogg": "audio/ogg",
    "opus": "audio/ogg",
    "flac": "audio/flac",
    "aac": "audio/aac",
    "m4a": "audio/mp4",
}


def _extension(fmt: str | None = None) -> str:
    """Normalise the configured container format; unknown values fall back to mp3.

    DeepSeek can return raw pcm, which the client wraps into a wav container, so the
    stored file is always a playable container.
    """
    value = (fmt or tts.settings()["format"] or "mp3").strip().lower().lstrip(".")
    if value == "pcm":
        return "wav"
    return value if value in MEDIA_TYPES else "mp3"


def media_type(key: str) -> str:
    return MEDIA_TYPES.get(Path(key).suffix.lower().lstrip("."), "audio/mpeg")


def download_name(material_id: int, page_no: int, key: str) -> str:
    return f"narration-{material_id}-{page_no}{Path(key).suffix or '.mp3'}"


def audio_key(material_id: int, page_no: int, text_hash: str, fmt: str | None = None) -> str:
    """The hash in the key makes stale audio unreachable instead of silently reused."""
    return f"narrations/{material_id}/{page_no}-{text_hash[:16]}.{_extension(fmt)}"


def current_audio(db: Session, material: Material, page_no: int) -> tuple[str, str] | None:
    """Return (storage key, text hash) only when the audio still matches approved wording."""
    item = repository.row(db, material.id, page_no)
    if item is None or item.status not in {"accepted"}:
        return None
    if item.page_hash != repository.source_hash(material, page_no):
        return None
    if not item.audio_key or not item.audio_text_hash:
        return None
    if item.audio_text_hash != repository.text_hash(item.text):
        return None
    return item.audio_key, item.audio_text_hash


def needs_audio(db: Session, material: Material, page_no: int) -> bool:
    return current_audio(db, material, page_no) is None


def synthesize_page(db: Session, material: Material, page_no: int) -> AudioRecord:
    """Speak one approved page. Refuses unapproved wording instead of speaking a draft."""
    reason = tts.unavailable_reason()
    if reason:
        raise tts.SpeechUnavailable(reason)
    item = repository.row(db, material.id, page_no)
    if item is None:
        raise LookupError("本页还没有讲解")
    if item.status != "accepted":
        raise ValueError("只有老师已接受的讲解才能生成音频")
    if item.page_hash != repository.source_hash(material, page_no):
        raise ValueError("页面已更新，请重新审校讲解")
    if not item.text.strip():
        raise ValueError("讲解为空，无法生成音频")
    digest = repository.text_hash(item.text)
    if item.audio_key and item.audio_text_hash == digest:
        return AudioRecord(page_no=page_no, reused=True, key=item.audio_key,
                           seconds=item.audio_seconds or _duration_seconds(item.text))
    spoken = tts.synthesize(item.text)
    seconds = _duration_seconds(item.text, spoken)
    payload, stored_format = _stored_audio(spoken)
    repository.ensure_lease(db, material.id)
    db.refresh(material, with_for_update=True)
    db.refresh(item, with_for_update=True)
    if (item.status != "accepted" or item.audio_text_hash == digest or
            repository.text_hash(item.text) != digest or item.page_hash != repository.source_hash(material, page_no)):
        raise ValueError("讲解或音频已更新，本次合成未覆盖新内容，请刷新")
    key = audio_key(material.id, page_no, digest, stored_format)
    storage = get_storage()
    with tempfile.TemporaryDirectory(prefix="narr-tts-", dir=storage_root()) as tmp_name:
        local = Path(tmp_name) / f"page.{Path(key).suffix.lstrip('.')}"
        local.write_bytes(payload)
        storage.put(key, local)
    previous = item.audio_key
    if previous and previous != key:
        # Replace rather than accumulate: one page keeps exactly one current recording.
        try:
            storage.delete(previous)
        except Exception:  # noqa: BLE001 -- a leftover object must not fail the new recording
            pass
    item.audio_key = key
    item.audio_text_hash = digest
    item.audio_seconds = seconds
    item.audio_created_at = now()
    db.flush()
    return AudioRecord(page_no=page_no, reused=False, key=key, seconds=seconds)


def _stored_audio(audio: bytes) -> tuple[bytes, str]:
    """Store mp3 when we can encode it, otherwise keep the engine's own container.

    DeepSeek only produces wav/pcm, and a 315-character page is ~2.8MB of wav against
    ~680KB of 96k mono mp3. Transcoding costs about 0.4s per page, so it happens once at
    generation time rather than on every playback.
    """
    engine_format = _extension()
    if engine_format == "mp3" or not _transcode_enabled():
        return audio, engine_format
    encoded = _to_mp3(audio)
    if encoded is None:
        return audio, engine_format
    return encoded, "mp3"


def _transcode_enabled() -> bool:
    value = (os.getenv("REBORN_TTS_TRANSCODE") or "mp3").strip().lower()
    return value not in {"off", "none", "0", "false"}


def _to_mp3(audio: bytes) -> bytes | None:
    """Return mp3 bytes, or None when no encoder is available (keep the original).

    `lame` is the intended encoder: it only needs wav→mp3 and costs a few MB, while
    ffmpeg drags in hundreds of MB of codec libraries. ffmpeg is still accepted when a
    development machine happens to have it.
    """
    bitrate = (os.getenv("REBORN_TTS_MP3_BITRATE") or "96k").strip()
    ffmpeg = shutil.which("ffmpeg")
    lame = shutil.which("lame")
    if ffmpeg is None and lame is None:
        return None
    try:
        with tempfile.TemporaryDirectory(prefix="narr-mp3-", dir=storage_root()) as tmp_name:
            source = Path(tmp_name) / "source.wav"
            target = Path(tmp_name) / "encoded.mp3"
            source.write_bytes(audio)
            if ffmpeg:
                command = [ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-i", str(source),
                           "-ac", "1", "-c:a", "libmp3lame", "-b:a", bitrate, str(target)]
            else:
                command = [str(lame), "--silent", "-m", "m", "-b", bitrate.rstrip("kK"),
                           str(source), str(target)]
            subprocess.run(command, check=True, capture_output=True, timeout=180)
            return target.read_bytes()
    except (OSError, subprocess.SubprocessError):
        return None


def _duration_seconds(text: str, data: bytes | None = None) -> int:
    """Prefer the real duration when the container carries it; otherwise estimate.

    A wav header gives the exact length for free, which matters because the estimate is
    only ~4.2 Chinese characters per second and drifts with pacing and punctuation.
    """
    exact = _wav_seconds(data)
    if exact:
        return exact
    return max(1, round(len(text.strip()) / 4.2))


def _wav_seconds(data: bytes | None) -> int | None:
    if not data or len(data) < 44 or data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        return None
    position, byte_rate, data_size = 12, 0, 0
    while position + 8 <= len(data):
        chunk_id = data[position:position + 4]
        chunk_size = int.from_bytes(data[position + 4:position + 8], "little")
        body = position + 8
        if chunk_id == b"fmt " and chunk_size >= 12 and body + 12 <= len(data):
            byte_rate = int.from_bytes(data[body + 8:body + 12], "little")
        elif chunk_id == b"data":
            data_size = min(chunk_size, max(0, len(data) - body))
        position = body + chunk_size + (chunk_size & 1)
    if not byte_rate or not data_size:
        return None
    return max(1, round(data_size / byte_rate))


def read_audio(db: Session, material: Material, page_no: int) -> tuple[PageNarration, Path] | None:
    """Materialise the current audio into a temporary file for streaming."""
    current = current_audio(db, material, page_no)
    item = repository.row(db, material.id, page_no)
    if current is None or item is None:
        return None
    key, _ = current
    adapter = get_storage()
    path = Path(storage_root()) / ".narration-stream" / f"{material.id}-{page_no}{Path(key).suffix or '.mp3'}"
    path.parent.mkdir(parents=True, exist_ok=True)
    adapter.copy_to(key, path)
    return item, path
