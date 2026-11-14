"""批量接受与批量合成音频：一次处理整册，单页失败不中断。"""
import pytest

from app.narration import tts

from test_narration import url
from test_narration_generation import doc_material


@pytest.fixture(autouse=True)
def keys(monkeypatch, tmp_path):
    monkeypatch.setenv("REBORN_AUTHORING_AI_API_KEY", "test-key")
    monkeypatch.setenv("REBORN_TTS_BASE_URL", "http://tts.local")
    monkeypatch.setenv("REBORN_TTS_FORMAT", "wav")
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "storage"))
    monkeypatch.setattr("app.narration.service.model_client.chat", lambda *a, **k: {
        "raw": {}, "text": "这一页讲变量。", "bridge_in": "", "anchors": [], "usage": {}, "model": "x"})
    spoken = []
    def fake_synthesize(text, **kwargs):
        spoken.append(text)
        if "爆炸" in text:
            raise tts.SpeechFailed("引擎拒绝了这一页")
        return b"RIFF" + b"\x00" * 1200
    monkeypatch.setattr("app.narration.tts.synthesize", fake_synthesize)
    return spoken


def generate_all(client, classroom, teacher, material, pages):
    for page_no in pages:
        assert client.post(url(classroom, material, f"/{page_no}/generate"), json=None,
                           headers=teacher["headers"]).status_code == 200


def test_accept_batch_accepts_every_reviewable_draft(client, db, classroom, teacher):
    material = doc_material(db, classroom, "一" * 300, "二" * 300, "三" * 300)
    db.commit()
    generate_all(client, classroom, teacher, material, [1, 2, 3])

    accepted = client.post(url(classroom, material, "/accept-batch"), json={}, headers=teacher["headers"])
    assert accepted.status_code == 200, accepted.text
    assert accepted.json()["count"] == 3

    listed = client.get(url(classroom, material), headers=teacher["headers"]).json()
    assert all(page["narration"]["status"] == "accepted" for page in listed["pages"])


def test_accept_batch_respects_a_page_range(client, db, classroom, teacher):
    material = doc_material(db, classroom, "一" * 300, "二" * 300, "三" * 300)
    db.commit()
    generate_all(client, classroom, teacher, material, [1, 2, 3])

    accepted = client.post(url(classroom, material, "/accept-batch"), json={"first_page": 2, "last_page": 2},
                           headers=teacher["headers"])
    assert accepted.json()["accepted"] == [2]
    listed = {page["page_no"]: page["narration"]["status"] for page in
              client.get(url(classroom, material), headers=teacher["headers"]).json()["pages"]}
    assert listed == {1: "draft", 2: "accepted", 3: "draft"}


def test_audio_batch_skips_drafts_and_reports_failures(client, db, classroom, teacher):
    material = doc_material(db, classroom, "一" * 300, "二" * 300, "三" * 300)
    db.commit()
    generate_all(client, classroom, teacher, material, [1, 2, 3])
    client.post(url(classroom, material, "/accept-batch"), json={"first_page": 1, "last_page": 4},
                headers=teacher["headers"])

    # 让第 2 页的讲解触发引擎失败，验证不影响其余页
    row = client.put(url(classroom, material, "/2"), json={"text": "这一页要爆炸了。"}, headers=teacher["headers"])
    assert row.status_code == 200
    client.post(url(classroom, material, "/2/accept"), headers=teacher["headers"])

    result = client.post(url(classroom, material, "/audio-batch"), json={}, headers=teacher["headers"])
    assert result.status_code == 200, result.text
    body = result.json()
    assert body["made"] == [1, 3]
    assert [item["page_no"] for item in body["failed"]] == [2]
    assert body["count"] == 2


def test_audio_batch_requires_an_engine(client, db, classroom, teacher, monkeypatch):
    material = doc_material(db, classroom, "一" * 300)
    db.commit()
    monkeypatch.delenv("REBORN_TTS_BASE_URL", raising=False)
    refused = client.post(url(classroom, material, "/audio-batch"), json={}, headers=teacher["headers"])
    assert refused.status_code == 409
    assert "未配置语音合成服务" in refused.json()["detail"]
