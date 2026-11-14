"""Narration audio: approval gate, reuse, invalidation and student streaming.

The speech engine is a seam: no test talks to a real TTS service.
"""

import pytest

from app.narration import audio, tts

from test_narration import url
from test_narration_generation import doc_material


@pytest.fixture(autouse=True)
def engine(monkeypatch, tmp_path):
    monkeypatch.setenv("REBORN_TTS_BASE_URL", "http://tts.local")
    monkeypatch.setenv("REBORN_TTS_VOICE", "Meilin")
    monkeypatch.setenv("REBORN_TTS_MODEL", "zonos3-mini")
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "storage"))
    monkeypatch.setenv("REBORN_AUTHORING_AI_API_KEY", "test-key")
    spoken = []

    def fake_synthesize(text, *, settings_override=None):
        spoken.append(text)
        return b"ID3" + b"\x00" * 2048

    monkeypatch.setattr(tts, "synthesize", fake_synthesize)
    return spoken


def approved_page(client, classroom, teacher, material, page_no=1):
    assert client.post(url(classroom, material, f"/{page_no}/generate"), json=None, headers=teacher["headers"]).status_code == 200
    assert client.post(url(classroom, material, f"/{page_no}/accept"), headers=teacher["headers"]).status_code == 200


def test_draft_wording_is_never_spoken(client, db, classroom, teacher, monkeypatch, engine):
    monkeypatch.setattr("app.narration.service.model_client.chat", lambda *a, **k: {
        "raw": {}, "text": "这一页讲变量。", "bridge_in": "", "anchors": [], "usage": {}, "model": "deepseek-flash"})
    material = doc_material(db, classroom, "一" * 300)
    db.commit()
    assert client.post(url(classroom, material, "/1/generate"), json=None, headers=teacher["headers"]).status_code == 200
    refused = client.post(url(classroom, material, "/1/audio"), headers=teacher["headers"])
    assert refused.status_code == 409
    assert "已接受" in refused.json()["detail"]
    assert engine == []


def test_approved_page_is_synthesized_once_and_reused(client, db, classroom, teacher, monkeypatch, engine):
    monkeypatch.setattr("app.narration.service.model_client.chat", lambda *a, **k: {
        "raw": {}, "text": "这一页讲变量。", "bridge_in": "", "anchors": [], "usage": {}, "model": "deepseek-flash"})
    material = doc_material(db, classroom, "一" * 300)
    db.commit()
    approved_page(client, classroom, teacher, material)

    first = client.post(url(classroom, material, "/1/audio"), headers=teacher["headers"])
    assert first.status_code == 200, first.text
    assert first.json()["reused"] is False and first.json()["key"].startswith("narrations/")
    assert first.json()["seconds"] == max(1, round(len("这一页讲变量。") / 4.2))

    second = client.post(url(classroom, material, "/1/audio"), headers=teacher["headers"])
    assert second.json()["reused"] is True
    assert len(engine) == 1, "同一措辞不该重复合成"

    status = client.get(url(classroom, material, "/audio"), headers=teacher["headers"]).json()
    assert status["available"] is True and status["voice"] == "Meilin"
    assert status["pages"][0]["ready"] is True


def test_changing_approved_wording_invalidates_the_recording(client, db, classroom, teacher, monkeypatch, engine):
    monkeypatch.setattr("app.narration.service.model_client.chat", lambda *a, **k: {
        "raw": {}, "text": "这一页讲变量。", "bridge_in": "", "anchors": [], "usage": {}, "model": "deepseek-flash"})
    material = doc_material(db, classroom, "一" * 300)
    db.commit()
    approved_page(client, classroom, teacher, material)
    assert client.post(url(classroom, material, "/1/audio"), headers=teacher["headers"]).status_code == 200

    assert client.put(url(classroom, material, "/1"), json={"text": "换成新的说法。"}, headers=teacher["headers"]).status_code == 200
    status = client.get(url(classroom, material, "/audio"), headers=teacher["headers"]).json()
    assert status["pages"][0]["ready"] is False, "文本改了，旧音频必须失效"

    again = client.post(url(classroom, material, "/1/audio"), headers=teacher["headers"])
    assert again.status_code == 200, again.text
    assert again.json()["key"] != status["pages"][0].get("key")
    assert len(engine) == 2


def test_missing_engine_makes_audio_unavailable(client, db, classroom, teacher, monkeypatch):
    monkeypatch.delenv("REBORN_TTS_BASE_URL", raising=False)
    material = doc_material(db, classroom, "一" * 300)
    db.commit()
    refused = client.post(url(classroom, material, "/1/audio"), headers=teacher["headers"])
    assert refused.status_code == 409
    assert "未配置语音合成服务" in refused.json()["detail"]
    status = client.get(url(classroom, material, "/audio"), headers=teacher["headers"]).json()
    assert status["available"] is False


def test_students_stream_only_approved_audio(client, db, classroom, teacher, student, monkeypatch, engine):
    monkeypatch.setattr("app.narration.service.model_client.chat", lambda *a, **k: {
        "raw": {}, "text": "这一页讲变量。", "bridge_in": "", "anchors": [], "usage": {}, "model": "deepseek-flash"})
    material = doc_material(db, classroom, "一" * 300)
    db.commit()
    student_url = f"/api/materials/{material.id}/narrations/1/audio?classroom_id={classroom['id']}"
    assert client.get(student_url, headers=student["headers"]).status_code == 404

    approved_page(client, classroom, teacher, material)
    assert client.post(url(classroom, material, "/1/audio"), headers=teacher["headers"]).status_code == 200
    streamed = client.get(student_url, headers=student["headers"])
    assert streamed.status_code == 200
    assert streamed.headers["content-type"] == "audio/mpeg"
    assert streamed.content.startswith(b"ID3")


def test_audio_service_reports_stale_as_unavailable(db, classroom):
    class Material:
        id = 4242
        content = {"pages": [{"id": 1, "page_no": 1, "text": "正文"}]}

    assert audio.current_audio(db, Material(), 1) is None
