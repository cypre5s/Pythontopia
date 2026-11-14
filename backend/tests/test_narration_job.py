"""任务锁：同一份资料同时只允许一个生成/校准/音频任务。"""
import pytest

from app.narration import repository

from test_narration import url
from test_narration_generation import doc_material


@pytest.fixture(autouse=True)
def keys(monkeypatch, tmp_path):
    monkeypatch.setenv("REBORN_AUTHORING_AI_API_KEY", "test-key")
    monkeypatch.setenv("REBORN_TTS_BASE_URL", "http://tts.local")
    monkeypatch.setenv("REBORN_TTS_FORMAT", "wav")
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "storage"))
    monkeypatch.setattr("app.narration.tts.synthesize", lambda text, **k: b"RIFF" + b"\x00" * 1200)


def test_second_task_is_rejected_while_one_holds_the_material(client, db, classroom, teacher, monkeypatch):
    """长任务跑到一半时，第二个请求必须被挡下来，而不是一起花钱。"""
    material = doc_material(db, classroom, "一" * 300)
    db.commit()
    attempts: list[int] = []

    def slow_chat(*args, **kwargs):
        if not attempts:
            attempts.append(1)
            inner = client.post(url(classroom, material, "/2/generate"), json=None, headers=teacher["headers"])
            attempts.append(inner.status_code)
        return {"raw": {}, "text": "讲解。", "bridge_in": "", "anchors": [], "usage": {}, "model": "x"}

    monkeypatch.setattr("app.narration.service.model_client.chat", slow_chat)
    first = client.post(url(classroom, material, "/1/generate"), json=None, headers=teacher["headers"])
    assert first.status_code == 200, first.text
    assert attempts == [1, 409], "任务进行中，第二次生成必须返回 409"


def test_a_running_job_blocks_other_endpoints(client, db, classroom, teacher, monkeypatch):
    material = doc_material(db, classroom, "一" * 300)
    db.commit()
    row = repository.rows(db, material.id)
    assert row == []

    # 手动占住锁
    repository.acquire(db, material, "generate", None)
    blocked = client.post(url(classroom, material, "/1/generate"), json=None, headers=teacher["headers"])
    assert blocked.status_code == 409
    assert "正在处理中" in blocked.json()["detail"]

    audio_blocked = client.post(url(classroom, material, "/audio-batch"), json={}, headers=teacher["headers"])
    assert audio_blocked.status_code == 409

    state = client.get(url(classroom, material, "/job"), headers=teacher["headers"]).json()
    assert state["running"] is True and state["kind"] == "generate"

    repository.release(db, material.id)
    after = client.get(url(classroom, material, "/job"), headers=teacher["headers"]).json()
    assert after["running"] is False


def test_a_stale_job_can_be_taken_over(db, classroom):
    from datetime import datetime, timedelta, timezone

    from app.models import Material
    from app.narration.models import NarrationJob

    material = Material(id=5150, title="锁测试", kind="text", status="ready", content={"pages": []})
    db.add(material)
    db.flush()
    stale = NarrationJob(material_id=material.id, kind="generate", status="running",
                         started_at=datetime.now(timezone.utc) - timedelta(hours=1),
                         heartbeat_at=datetime.now(timezone.utc) - timedelta(hours=1))
    db.add(stale)
    db.commit()

    acquired = repository.acquire(db, material, "calibrate", None)
    assert acquired.kind == "calibrate" and acquired.status == "running"


def test_heartbeat_reports_progress(client, db, classroom, teacher):
    material = doc_material(db, classroom, "一" * 300)
    db.commit()
    repository.acquire(db, material, "generate", None)
    repository.beat(db, material.id, done=7, total=103, message="第 7 页")
    db.commit()
    state = client.get(url(classroom, material, "/job"), headers=teacher["headers"]).json()
    assert state["running"] is True and state["done"] == 7 and state["total"] == 103
    repository.release(db, material.id)


def test_failed_task_releases_the_lock(client, db, classroom, teacher, monkeypatch):
    material = doc_material(db, classroom, "一" * 300)
    db.commit()
    monkeypatch.setattr("app.narration.service.model_client.chat",
                        lambda *a, **k: (_ for _ in ()).throw(
                            __import__("app.narration.client", fromlist=["ModelFailed"]).ModelFailed("模型挂了")))
    failed = client.post(url(classroom, material, "/1/generate"), json=None, headers=teacher["headers"])
    assert failed.status_code == 502
    state = client.get(url(classroom, material, "/job"), headers=teacher["headers"]).json()
    assert state["running"] is False, "失败后必须放开锁，否则资料会被永久锁住"
