"""语音容器格式跟随配置：键后缀、Content-Type 与真实时长都要一致。"""
import io
import wave

import pytest

from app.narration import audio

from test_narration import url
from test_narration_generation import doc_material


def wav_bytes(seconds: float = 2.0, rate: int = 16000) -> bytes:
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
    # This suite verifies the untranscoded container contract on hosts with or without lame.
    monkeypatch.setenv("REBORN_TTS_TRANSCODE", "off")
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "storage"))
    monkeypatch.setenv("REBORN_AUTHORING_AI_API_KEY", "test-key")
    monkeypatch.setattr("app.narration.tts.synthesize", lambda text, **kwargs: wav_bytes(2.0))
    monkeypatch.setattr("app.narration.service.model_client.chat", lambda *a, **k: {
        "raw": {}, "text": "这一页讲变量。", "bridge_in": "", "anchors": [], "usage": {}, "model": "x"})


def test_wav_format_drives_key_suffix_content_type_and_duration(client, db, classroom, teacher, student, wav_engine):
    material = doc_material(db, classroom, "一" * 300)
    db.commit()
    assert client.post(url(classroom, material, "/1/generate"), json=None, headers=teacher["headers"]).status_code == 200
    assert client.post(url(classroom, material, "/1/accept"), headers=teacher["headers"]).status_code == 200

    made = client.post(url(classroom, material, "/1/audio"), headers=teacher["headers"])
    assert made.status_code == 200, made.text
    assert made.json()["key"].endswith(".wav")
    # 2 秒的 wav 不该再按字符数估成 1 秒。
    assert made.json()["seconds"] == 2

    teacher_stream = client.get(url(classroom, material, "/1/audio/file"), headers=teacher["headers"])
    assert teacher_stream.status_code == 200
    assert teacher_stream.headers["content-type"] == "audio/wav"
    assert teacher_stream.content[:4] == b"RIFF"

    student_stream = client.get(f"/api/materials/{material.id}/narrations/1/audio?classroom_id={classroom['id']}",
                               headers=student["headers"])
    assert student_stream.status_code == 200
    assert student_stream.headers["content-type"] == "audio/wav"
    assert "narration-" in student_stream.headers["content-disposition"]
    assert student_stream.headers["content-disposition"].endswith('.wav"') or ".wav" in student_stream.headers["content-disposition"]


def test_unknown_format_falls_back_to_mp3(monkeypatch):
    monkeypatch.setenv("REBORN_TTS_FORMAT", "speex")
    assert audio.audio_key(1, 2, "a" * 40).endswith(".mp3")
    assert audio.media_type("narrations/1/2-abc.mp3") == "audio/mpeg"


def test_wav_header_without_byte_rate_falls_back_to_estimate():
    assert audio._duration_seconds("一" * 42, b"RIFFxxxxWAVE") == 10
    assert audio._duration_seconds("一" * 42, None) == 10
