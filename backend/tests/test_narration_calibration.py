"""Whole-deck calibration: the rolling window, the report, and who may be rewritten."""
import pytest

from app.narration import calibrate
from app.narration import client as model_client

from test_narration import url
from test_narration_generation import doc_material


@pytest.fixture(autouse=True)
def authoring_key(monkeypatch):
    monkeypatch.setenv("REBORN_AUTHORING_AI_API_KEY", "test-key")
    monkeypatch.setenv("REBORN_AUTHORING_AI_MODEL", "deepseek-flash")


def narrate(client, classroom, material, teacher, pages):
    for page_no in pages:
        result = client.post(url(classroom, material, f"/{page_no}/generate"), json=None, headers=teacher["headers"])
        assert result.status_code == 200, result.text


@pytest.fixture
def generated(client, db, classroom, teacher, monkeypatch):
    """Three narrated pages; the model itself is stubbed so only calibration logic runs."""
    def fake_chat(messages, *, settings, model, max_tokens=None, effort=None, require_text=True):
        return {"raw": {"text": "讲解。"}, "text": "讲解。", "bridge_in": "", "anchors": [],
                "usage": {}, "model": model}

    monkeypatch.setattr("app.narration.service.model_client.chat", fake_chat)
    material = doc_material(db, classroom, "一" * 300, "二" * 300, "三" * 300)
    db.commit()
    narrate(client, classroom, material, teacher, [1, 2, 3])
    return material


def test_calibration_walks_the_deck_with_one_call_per_window(client, db, classroom, teacher, generated, monkeypatch):
    seen = []

    def fake_chat(messages, *, settings, model, max_tokens=None, effort=None, require_text=True):
        payload_text = messages[1]["content"].rsplit("\n\n", 1)[1]
        import json

        payload = json.loads(payload_text)
        seen.append({"pages": [page["page_no"] for page in payload["pages"]], "summary": payload["summary"],
                     "effort": effort, "require_text": require_text})
        return {"raw": {"summary": f"已看过 {payload['pages'][-1]['page_no']} 页", "terms": ["变量"],
                        "issues": [{"page_no": 2, "kind": "flow", "detail": "第二页开头与上一页重复"}]},
                "text": "", "bridge_in": "", "anchors": [], "usage": {}, "model": model}

    monkeypatch.setattr("app.narration.calibrate.model_client.chat", fake_chat)
    monkeypatch.setattr(calibrate, "WINDOW", 2)
    result = client.post(url(classroom, generated, "/calibrate"), headers=teacher["headers"])
    assert result.status_code == 200, result.text
    body = result.json()
    assert body["windows"] == 2
    assert body["pages"] == 3
    # The rolling summary must be carried into the next window, not restarted.
    assert seen[0]["summary"] == "" and seen[1]["summary"] == "已看过 2 页"
    assert seen[0]["effort"] == "low", "校准要开思考，与生成不同"
    # 校准回复里没有 text 字段，不能在客户端被当成“空讲解”拦下来。
    assert all(item["require_text"] is False for item in seen)
    assert [issue["page_no"] for issue in body["issues"]] == [2]
    assert body["issues"][0]["status"] == "draft"


def test_calibration_report_is_persisted_and_reused(client, db, classroom, teacher, generated, monkeypatch):
    calls = []

    def fake_chat(messages, *, settings, model, max_tokens=None, effort=None, require_text=True):
        calls.append(1)
        return {"raw": {"summary": "摘要", "terms": [], "issues": []}, "text": "", "bridge_in": "",
                "anchors": [], "usage": {}, "model": model}

    monkeypatch.setattr("app.narration.calibrate.model_client.chat", fake_chat)
    assert client.post(url(classroom, generated, "/calibrate"), headers=teacher["headers"]).status_code == 200
    stored = client.get(url(classroom, generated, "/calibrate"), headers=teacher["headers"])
    assert stored.status_code == 200
    assert stored.json()["summary"] == "摘要" and stored.json()["applied_at"] is None
    assert len(calls) == 1


def test_apply_regenerates_flagged_drafts_only(client, db, classroom, teacher, generated, monkeypatch):
    monkeypatch.setattr(calibrate, "WINDOW", 10)

    def calibration(messages, *, settings, model, max_tokens=None, effort=None, require_text=True):
        return {"raw": {"summary": "摘要", "terms": [], "issues": [
            {"page_no": 2, "kind": "mismatch", "detail": "讲了页面没有的内容"},
            {"page_no": 3, "kind": "flow", "detail": "衔接生硬"}]}, "text": "", "bridge_in": "",
            "anchors": [], "usage": {}, "model": model}

    monkeypatch.setattr("app.narration.calibrate.model_client.chat", calibration)
    assert client.post(url(classroom, generated, "/calibrate"), headers=teacher["headers"]).status_code == 200
    # Page 3 is approved, so it must be reported but never rewritten.
    assert client.post(url(classroom, generated, "/3/accept"), headers=teacher["headers"]).status_code == 200
    assert client.post(url(classroom, generated, "/calibrate"), headers=teacher["headers"]).status_code == 200

    regenerated = []

    def fake_generate(db, material, page_no, *, instance_id, target_seconds=None):
        regenerated.append(page_no)
        return {"page_no": page_no, "status": "draft", "reused": False, "model": "deepseek-flash"}

    monkeypatch.setattr("app.narration.service.generate_page", fake_generate)
    applied = client.post(url(classroom, generated, "/calibrate/apply"), headers=teacher["headers"])
    assert applied.status_code == 200, applied.text
    assert applied.json()["regenerated"] == [2]
    assert applied.json()["awaiting_teacher"] == [3]
    assert regenerated == [2]


def test_calibration_reports_failure_when_nothing_was_generated(client, db, classroom, teacher):
    material = doc_material(db, classroom, "一" * 300)
    db.commit()
    refused = client.post(url(classroom, material, "/calibrate"), headers=teacher["headers"])
    assert refused.status_code == 404
    assert "没有可校准的讲解" in refused.json()["detail"]


def test_calibration_surfaces_model_errors_as_bad_gateway(client, db, classroom, teacher, generated, monkeypatch):
    def broken(messages, *, settings, model, max_tokens=None, effort=None, require_text=True):
        raise model_client.ModelFailed("模型返回空内容，请重试")

    monkeypatch.setattr("app.narration.calibrate.model_client.chat", broken)
    result = client.post(url(classroom, generated, "/calibrate"), headers=teacher["headers"])
    assert result.status_code == 502
    assert "模型" in result.json()["detail"]
