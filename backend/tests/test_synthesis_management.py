from datetime import timedelta

import pytest

from app.models import Classroom, now
from app.narration import repository, service
from app.narration.models import NarrationRun
from app.narration.tts import endpoint
from test_narration import material_fixture, url


def overview(classroom):
    return f"/api/teacher/classrooms/{classroom['id']}/synthesis"


def test_overview_scope_types_and_interrupted_tasks(client, db, classroom, teacher, student):
    material = material_fixture(db, classroom, ["正文"])
    material.title = "正在处理"
    material_fixture(db, classroom, ["正文"], kind="video")
    db.add(NarrationRun(material_id=material.id, kind="audio", status="running", started_at=now() - timedelta(hours=1)))
    db.commit()
    response = client.get(overview(classroom) + "?query=正在", headers=teacher["headers"])
    assert response.status_code == 200, response.text
    item = response.json()["items"][0]
    assert item["status"] == "interrupted" and not item["retryable"]
    assert client.get(overview(classroom), headers=student["headers"]).status_code == 403
    db.get(Classroom, classroom["id"]).teacher_id = student["id"]
    db.commit()
    assert client.get(overview(classroom), headers=teacher["headers"]).status_code == 403


def fail_page(client, db, classroom, teacher, monkeypatch):
    material = material_fixture(db, classroom, ["正文" * 150], kind="pdf")
    db.commit()

    def fail(*args, **kwargs):
        raise service.NarrationUnavailable("讲稿服务尚未配置")

    monkeypatch.setattr(service, "generate_page", fail)
    assert client.post(url(classroom, material, "/1/generate"), json={}, headers=teacher["headers"]).status_code == 409
    return material, repository.job_of(db, material.id).started_at.isoformat()


def test_explicit_retry_uses_saved_request_and_cannot_repeat(client, db, classroom, teacher, monkeypatch):
    material, started = fail_page(client, db, classroom, teacher, monkeypatch)
    calls = []

    def success(*args, **kwargs):
        calls.append(args[2])
        return {"page_no": 1, "status": "draft", "reused": False, "model": "test", "chars": 1, "image_used": False}

    monkeypatch.setattr(service, "generate_page", success)
    item = next(i for i in client.get(overview(classroom), headers=teacher["headers"]).json()["items"] if i["material_id"] == material.id)
    assert item["retryable"] and item["message"] == "讲稿服务尚未配置"
    assert not calls, "Opening management must never invoke a model"
    path = overview(classroom) + f"/{material.id}/retry"
    result = client.post(path, json={"expected_started_at": started}, headers=teacher["headers"])
    assert result.status_code == 200, result.text
    assert result.json()["status"] == "done" and calls == [1]
    assert client.post(path, json={"expected_started_at": started}, headers=teacher["headers"]).status_code == 409
    assert calls == [1]
    history = client.get(overview(classroom) + "?view=history", headers=teacher["headers"]).json()
    assert history["total"] == 2
    assert [row["status"] for row in history["items"]] == ["done", "failed"]
    assert history["items"][0]["id"] != history["items"][1]["id"]
    assert not history["items"][1]["retryable"]
    assert client.get(overview(classroom) + "?view=running", headers=teacher["headers"]).json()["total"] == 0


def test_live_progress_and_takeover_retain_previous_attempt(db, client, classroom, teacher):
    from app.models import User
    material = material_fixture(db, classroom, ["正文"])
    db.commit()
    user = db.get(User, teacher["id"])
    repository.acquire(db, material, "generate", user)
    repository.beat(db, material.id, done=2, total=4, message="正在生成第 3 页")
    db.commit()
    current = client.get(overview(classroom) + "?view=running", headers=teacher["headers"]).json()
    assert current["total"] == 1 and current["items"][0]["done"] == 2
    job = repository.job_of(db, material.id)
    job.heartbeat_at = now() - timedelta(hours=1)
    repository.sync_run(db, job)
    db.commit()
    stale = client.get(overview(classroom) + "?view=history", headers=teacher["headers"]).json()
    assert stale["items"][0]["status"] == "interrupted"
    repository.acquire(db, material, "audio", user)
    repository.release(db, material.id, message="完成配音")
    records = client.get(overview(classroom) + "?view=history", headers=teacher["headers"]).json()
    assert records["total"] == 2
    assert [row["status"] for row in records["items"]] == ["done", "interrupted"]


def test_changed_source_blocks_retry_before_external_request(client, db, classroom, teacher, monkeypatch):
    material, started = fail_page(client, db, classroom, teacher, monkeypatch)
    material.content = {**material.content, "sha256": "new-upload"}
    db.commit()

    def unexpected(*args, **kwargs):
        pytest.fail("Changed material must not reach generation")

    monkeypatch.setattr(service, "generate_page", unexpected)
    response = client.post(overview(classroom) + f"/{material.id}/retry", json={"expected_started_at": started}, headers=teacher["headers"])
    assert response.status_code == 409, response.text
    assert repository.job_of(db, material.id).status == "failed"


def test_batch_retry_runs_failed_pages_only(client, db, classroom, teacher, monkeypatch):
    material = material_fixture(db, classroom, ["正文" * 150, "正文" * 150], kind="pdf")
    db.commit()
    calls = []

    def generate(db, material, page_no, **kwargs):
        calls.append(page_no)
        if page_no == 2 and len(calls) == 2:
            raise ValueError("第二页失败")
        return {"page_no": page_no, "status": "draft", "reused": False, "model": "test", "chars": 1, "image_used": False}

    monkeypatch.setattr(service, "generate_page", generate)
    response = client.post(url(classroom, material, "/generate"), json={"first_page": 1, "last_page": 2}, headers=teacher["headers"])
    assert response.status_code == 200
    job = repository.job_of(db, material.id)
    assert job.status == "failed" and job.progress["retry_pages"] == [2]
    response = client.post(overview(classroom) + f"/{material.id}/retry", json={"expected_started_at": job.started_at.isoformat()}, headers=teacher["headers"])
    assert response.status_code == 200, response.text
    assert calls == [1, 2, 2]


def test_legacy_failure_has_no_blind_retry(client, db, classroom, teacher):
    material = material_fixture(db, classroom, ["正文"])
    db.add(NarrationRun(material_id=material.id, kind="generate", status="failed", started_at=now()))
    db.commit()
    items = client.get(overview(classroom), headers=teacher["headers"]).json()["items"]
    assert not next(i for i in items if i["material_id"] == material.id)["retryable"]


@pytest.mark.parametrize("root", ["https://api.deepseek.com", "https://api.deepseek.com/v1", "https://api.deepseek.com/v1/tools/tts/"])
def test_speech_endpoint_accepts_root_or_complete_url(root):
    assert endpoint(root, "/v1/tools/tts") == "https://api.deepseek.com/v1/tools/tts"
