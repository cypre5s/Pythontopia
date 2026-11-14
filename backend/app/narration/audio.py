"""Narration audio: one recording per approved page, invalidated when the wording changes.

The container format follows `REBORN_TTS_FORMAT` (default mp3). It is carried in the storage
key, so the streaming endpoints can answer with an honest Content-Type instead of always
claiming `audio/mpeg`.
"""

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
    """Normalise the configured container format; unknown values fall back to mp3."""
    value = (fmt or tts.settings()["format"] or "mp3").strip().lower().lstrip(".")
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
    if not item.text.strip():
        raise ValueError("讲解为空，无法生成音频")
    digest = repository.text_hash(item.text)
    if item.audio_key and item.audio_text_hash == digest:
        return AudioRecord(page_no=page_no, reused=True, key=item.audio_key,
                           seconds=item.audio_seconds or _duration_seconds(item.text))
    audio = tts.synthesize(item.text)
    key = audio_key(material.id, page_no, digest)
    storage = get_storage()
    with tempfile.TemporaryDirectory(prefix="narr-tts-", dir=storage_root()) as tmp_name:
        local = Path(tmp_name) / f"page.{_extension()}"
        local.write_bytes(audio)
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
    item.audio_seconds = _duration_seconds(item.text, audio)
    item.audio_created_at = now()
    db.flush()
    return AudioRecord(page_no=page_no, reused=False, key=key, seconds=item.audio_seconds)


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
