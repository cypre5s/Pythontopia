import json

import pytest

from app.integration_models import AssistantModelSettings
from app.models import User
from app.narration import config, tts
from app.service_settings import configuration
from test_narration import material_fixture, url


@pytest.mark.parametrize("service,body,env", [
    ("authoring", {"model": "writer", "vision_model": "vision", "effort": "none"}, "REBORN_AUTHORING_AI_API_KEY"),
    ("speech", {"protocol": "deepseek", "model": "", "voice": "stella", "format": "wav", "speed": 1}, "REBORN_TTS_API_KEY"),
])
def test_service_permissions_encryption_conflict_and_runtime(client, db, teacher, student, monkeypatch, tmp_path, service, body, env):
    monkeypatch.setenv("REBORN_AI_MASTER_KEY_FILE", str(tmp_path / "master.key"))
    monkeypatch.setenv(env, "environment-test-key")
    path = f"/api/teacher/services/{service}"
    assert client.get(path, headers=student["headers"]).status_code == 403
    assert client.get(path, headers=teacher["headers"]).status_code == 403
    db.get(User, teacher["id"]).role = "admin"
    db.commit()
    assert client.get(path, headers=teacher["headers"]).json()["source"] == "environment"
    body = {**body, "base_url": "https://example.invalid", "expected_revision": 0, "api_key": "private-test-key"}
    invalid = client.put(path, headers=teacher["headers"], json={**body, "timeout": -1})
    assert invalid.status_code == 422 and "private-test-key" not in invalid.text
    response = client.put(path, headers=teacher["headers"], json=body)
    assert response.status_code == 200, response.text
    assert "private-test-key" not in response.text
    assert response.json()["key_configured"] and response.json()["revision"] == 1
    row = db.get(AssistantModelSettings, 2 if service == "authoring" else 3)
    assert "private-test-key" not in row.encrypted_key + json.dumps(row.configuration)
    runtime = config.settings(db) if service == "authoring" else tts.settings(db)
    assert runtime["api_key"] == "private-test-key"
    if service == "speech":
        assert tts._request(runtime, "你好")[0] == "/v1/tools/tts"
    assert client.put(path, headers=teacher["headers"], json=body).status_code == 409
    body.update(expected_revision=1, api_key=None)
    assert client.put(path, headers=teacher["headers"], json=body).status_code == 200
    assert configuration(service, db)["api_key"] == "private-test-key"
    body.update(expected_revision=2, clear_key=True)
    assert client.put(path, headers=teacher["headers"], json=body).status_code == 200
    assert configuration(service, db)["api_key"] == ""
    assert db.get(AssistantModelSettings, 1) is None


@pytest.mark.parametrize("kind", ["video", "text", "document"])
def test_non_slide_material_refuses_narration(client, db, classroom, teacher, kind):
    material = material_fixture(db, classroom, ["正文"], kind=kind)
    db.commit()
    for method, suffix, body in [("get", "", None), ("post", "/1/generate", {}), ("post", "/audio-batch", {})]:
        response = client.request(method, url(classroom, material, suffix), headers=teacher["headers"], json=body)
        assert response.status_code == 422, response.text


def test_speech_protocol_validation(client, db, teacher):
    db.get(User, teacher["id"]).role = "admin"
    db.commit()
    response = client.put("/api/teacher/services/speech", headers=teacher["headers"], json={
        "expected_revision": 0, "base_url": "https://example.invalid", "protocol": "deepseek",
        "model": "", "voice": "stella", "format": "mp3", "api_key": "secret-must-not-leak",
    })
    assert response.status_code == 422 and "secret-must-not-leak" not in response.text
