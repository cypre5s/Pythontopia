"""Narration audio: one mp3 per approved page, invalidated when the wording changes."""

import tempfile
from pathlib import Path

from ..models import Material, now
from ..storage import get_storage, storage_root
from . import repository, tts
from .models import PageNarration


def audio_key(material_id: int, page_no: int, text_hash: str) -> str:
    """The hash in the key makes stale audio unreachable instead of silently reused."""
    return f"narrations/{material_id}/{page_no}-{text_hash[:16]}.mp3"


def current_audio(db, material: Material, page_no: int) -> tuple[str, str] | None:
    """Return (storage key, text hash) only when the audio still matches approved wording."""
    item = repository.row(db, material.id, page_no)
    if item is None or item.status not in {"accepted"}:
        return None
    if not item.audio_key or not item.audio_text_hash:
        return None
    if item.audio_text_hash != repository.text_hash(item.text):
        return None
    return item.audio_key, item.audio_text_hash


def needs_audio(db, material: Material, page_no: int) -> bool:
    return current_audio(db, material, page_no) is None


def synthesize_page(db, material: Material, page_no: int) -> dict:
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
        return {"page_no": page_no, "reused": True, "key": item.audio_key, "seconds": item.audio_seconds}
    audio = tts.synthesize(item.text)
    key = audio_key(material.id, page_no, digest)
    storage = get_storage()
    with tempfile.TemporaryDirectory(prefix="narr-tts-", dir=storage_root()) as tmp_name:
        local = Path(tmp_name) / "page.mp3"
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
    item.audio_seconds = _duration_seconds(item.text)
    item.audio_created_at = now()
    db.flush()
    return {"page_no": page_no, "reused": False, "key": key, "seconds": item.audio_seconds}


def _duration_seconds(text: str) -> int:
    """Chinese narration runs about 4.2 characters per second; used for the UI only."""
    return max(1, round(len(text.strip()) / 4.2))


def read_audio(db, material: Material, page_no: int) -> tuple[PageNarration, Path] | None:
    """Materialise the current audio into a temporary file for streaming."""
    current = current_audio(db, material, page_no)
    if current is None:
        return None
    key, _ = current
    adapter = get_storage()
    path = Path(storage_root()) / ".narration-stream" / f"{material.id}-{page_no}.mp3"
    path.parent.mkdir(parents=True, exist_ok=True)
    adapter.copy_to(key, path)
    return repository.row(db, material.id, page_no), path
