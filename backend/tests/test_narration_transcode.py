"""转码到 mp3：有 ffmpeg 就压，没有就保留引擎原始容器，时长仍取真实值。"""
import io
import wave

import pytest

from app.narration import audio

from test_narration import url
from test_narration_generation import doc_material


def wav_bytes(seconds: float = 2.0, rate: int = 24000) -> bytes:
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(rate)
        handle.writeframes(b"\x00\x00" * int(rate * seconds))
    return buffer.getvalue()


@pytest.fixture
def wav_engine(monkeypatch, tmp_path):
    monkeypatch.setenv("REBORN_TTS_BASE_URL", "http://tts.local")
    monkeypatch.setenv("REBORN_TTS_FORMAT", "wav")
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "storage"))
    monkeypatch.setenv("REBORN_AUTHORING_AI_API_KEY", "test-key")
    monkeypatch.setattr("app.narration.tts.synthesize", lambda text, **kwargs: wav_bytes(2.0))
    monkeypatch.setattr("app.narration.service.model_client.chat", lambda *a, **k: {
        "raw": {}, "text": "这一页讲变量。", "bridge_in": "", "anchors": [], "usage": {}, "model": "x"})


def approve(client, classroom, teacher, material):
    assert client.post(url(classroom, material, "/1/generate"), json=None, headers=teacher["headers"]).status_code == 200
    assert client.post(url(classroom, material, "/1/accept"), headers=teacher["headers"]).status_code == 200


def test_wav_is_transcoded_to_mp3_when_ffmpeg_exists(client, db, classroom, teacher, student, wav_engine, monkeypatch):
    captured = {}

    def fake_mp3(data: bytes) -> bytes:
        captured["source_bytes"] = len(data)
        return b"ID3\x04" + b"\x00" * 3000

    monkeypatch.setattr(audio, "_to_mp3", fake_mp3)
    material = doc_material(db, classroom, "一" * 300)
    db.commit()
    approve(client, classroom, teacher, material)

    made = client.post(url(classroom, material, "/1/audio"), headers=teacher["headers"])
    assert made.status_code == 200, made.text
    assert made.json()["key"].endswith(".mp3")
    assert made.json()["seconds"] == 2, "时长要在转码前从 wav 头取，转成 mp3 后就没有头了"
    assert captured["source_bytes"] > 1000

    streamed = client.get(f"/api/materials/{material.id}/narrations/1/audio?classroom_id={classroom['id']}",
                          headers=student["headers"])
    assert streamed.status_code == 200
    assert streamed.headers["content-type"] == "audio/mpeg"
    assert streamed.content.startswith(b"ID3")


def test_without_ffmpeg_the_original_container_is_kept(client, db, classroom, teacher, wav_engine, monkeypatch):
    monkeypatch.setattr(audio.shutil, "which", lambda name: None)
    material = doc_material(db, classroom, "一" * 300)
    db.commit()
    approve(client, classroom, teacher, material)

    made = client.post(url(classroom, material, "/1/audio"), headers=teacher["headers"])
    assert made.status_code == 200, made.text
    assert made.json()["key"].endswith(".wav"), "没有 ffmpeg 时不能假装发了 mp3"
    assert made.json()["seconds"] == 2


def test_transcode_can_be_switched_off(client, db, classroom, teacher, wav_engine, monkeypatch):
    monkeypatch.setenv("REBORN_TTS_TRANSCODE", "off")
    called = []
    monkeypatch.setattr(audio, "_to_mp3", lambda data: called.append(1) or b"ID3")
    material = doc_material(db, classroom, "一" * 300)
    db.commit()
    approve(client, classroom, teacher, material)

    made = client.post(url(classroom, material, "/1/audio"), headers=teacher["headers"])
    assert made.json()["key"].endswith(".wav")
    assert called == []


def test_failed_transcode_falls_back_instead_of_losing_the_recording(client, db, classroom, teacher, wav_engine, monkeypatch):
    def boom(_data: bytes) -> bytes | None:
        return None

    monkeypatch.setattr(audio, "_to_mp3", boom)
    material = doc_material(db, classroom, "一" * 300)
    db.commit()
    approve(client, classroom, teacher, material)

    made = client.post(url(classroom, material, "/1/audio"), headers=teacher["headers"])
    assert made.status_code == 200
    assert made.json()["key"].endswith(".wav"), "转码失败要保留原音频，而不是让整页失败"
