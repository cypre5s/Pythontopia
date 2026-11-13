"""Narration generation: idempotency, image routing, quota and per-page failure isolation.

The model and the renderer are seams: these tests never call a provider or poppler.
"""
import pytest

from app.narration import client as model_client
from app.narration import render, service

from test_narration import material_fixture, pages, url


@pytest.fixture(autouse=True)
def authoring_key(monkeypatch):
    monkeypatch.setenv("REBORN_AUTHORING_AI_API_KEY", "test-key")
    monkeypatch.setenv("REBORN_AUTHORING_AI_MODEL", "deepseek-flash")
    monkeypatch.setenv("REBORN_AUTHORING_AI_EFFORT", "none")
    monkeypatch.delenv("REBORN_AUTHORING_AI_VISION_MODEL", raising=False)
    monkeypatch.setenv("REBORN_AUTHORING_AI_DAILY_PAGES", "10")


@pytest.fixture
def calls(monkeypatch):
    recorded = []

    def fake_chat(messages, *, settings, model, max_tokens=None, effort=None):
        recorded.append({"messages": messages, "model": model, "settings": settings})
        return {"text": "这一页讲的是变量。", "bridge_in": "接着上一页。", "anchors": ["变量", "赋值"],
                "usage": {"prompt_tokens": 100, "completion_tokens": 50}, "model": model}

    monkeypatch.setattr(service.model_client, "chat", fake_chat)
    return recorded


def doc_material(db, classroom, *texts):
    """Plain text material whose pages are long enough to take the text-only route."""
    padded = tuple(text * (1 + 240 // max(1, len(text))) for text in texts)
    return material_fixture(db, classroom, padded, kind="text")


def slide_material(db, classroom, *texts):
    material = material_fixture(db, classroom, texts, kind="presentation")
    material.content = {**pages(*texts), "preview_key": "previews/slides.pdf"}
    db.flush()
    return material


def generate(client, classroom, material, page_no, headers, **body):
    return client.post(url(classroom, material, f"/{page_no}/generate"), json=body or None, headers=headers)


def test_generate_writes_a_draft_and_reuses_identical_input(client, db, classroom, teacher, calls):
    material = doc_material(db, classroom, "第一页正文" * 30)
    db.commit()
    first = generate(client, classroom, material, 1, teacher["headers"])
    assert first.status_code == 200, first.text
    assert first.json() == {"page_no": 1, "status": "draft", "reused": False, "model": "deepseek-flash",
                            "chars": 9, "image_used": False}
    assert len(calls) == 1

    again = generate(client, classroom, material, 1, teacher["headers"])
    assert again.json()["reused"] is True
    assert len(calls) == 1, "同输入重跑不该再花一次调用"

    body = client.get(url(classroom, material), headers=teacher["headers"]).json()
    narration = body["pages"][0]["narration"]
    assert narration["status"] == "draft" and narration["source"] == "ai_generated"
    assert narration["anchors"] == ["变量", "赋值"]


def test_presentation_pages_always_send_the_slide_image(client, db, classroom, teacher, calls, monkeypatch):
    material = slide_material(db, classroom, "这一页文本很长" * 40)
    db.commit()
    seen = {}

    def fake_render(item, page_no):
        seen["page_no"] = page_no
        return b"\xff\xd8fakejpeg"

    monkeypatch.setattr(service.render, "render_page", fake_render)
    result = generate(client, classroom, material, 1, teacher["headers"])
    assert result.status_code == 200, result.text
    assert result.json()["image_used"] is True
    assert seen["page_no"] == 1
    # The image must ride along as a real content part, not a text note.
    parts = calls[0]["messages"][1]["content"]
    assert parts[-1]["type"] == "image_url" and parts[-1]["image_url"]["url"].startswith("data:image/jpeg;base64,")


def test_empty_page_text_without_renderable_source_is_unavailable(client, db, classroom, teacher, calls, monkeypatch):
    material = slide_material(db, classroom, "")
    db.commit()
    monkeypatch.setattr(service.render, "render_page", lambda item, page_no: None)
    result = generate(client, classroom, material, 1, teacher["headers"])
    assert result.status_code == 409
    assert "没有可读的页面来源" in result.json()["detail"]
    assert calls == []


def test_regenerating_after_a_text_change_drops_the_old_approval(client, db, classroom, teacher, calls):
    material = doc_material(db, classroom, "第一页正文" * 30)
    db.commit()
    generate(client, classroom, material, 1, teacher["headers"])
    assert client.post(url(classroom, material, "/1/accept"), headers=teacher["headers"]).status_code == 200

    material.content = pages("第一页正文改过了" * 30)
    db.commit()
    again = generate(client, classroom, material, 1, teacher["headers"])
    assert again.status_code == 200, again.text
    assert again.json()["status"] == "draft"
    assert len(calls) == 2
    body = client.get(url(classroom, material), headers=teacher["headers"]).json()
    assert body["pages"][0]["narration"]["status"] == "draft"


def test_missing_credentials_make_generation_unavailable(client, db, classroom, teacher, monkeypatch):
    monkeypatch.delenv("REBORN_AUTHORING_AI_API_KEY", raising=False)
    material = doc_material(db, classroom, "第一页正文" * 30)
    db.commit()
    result = generate(client, classroom, material, 1, teacher["headers"])
    assert result.status_code == 409
    assert "未配置作者期模型凭据" in result.json()["detail"]
    quota = client.get(url(classroom, material, "/quota"), headers=teacher["headers"]).json()
    assert quota["available"] is False


def test_daily_quota_stops_further_calls(client, db, classroom, teacher, calls, monkeypatch):
    monkeypatch.setenv("REBORN_AUTHORING_AI_DAILY_PAGES", "1")
    material = doc_material(db, classroom, "第一页正文" * 30, "第二页正文" * 30)
    db.commit()
    assert generate(client, classroom, material, 1, teacher["headers"]).status_code == 200
    blocked = generate(client, classroom, material, 2, teacher["headers"])
    assert blocked.status_code == 409
    assert "额度已用完" in blocked.json()["detail"]
    assert len(calls) == 1


def test_range_generation_keeps_pages_that_succeeded(client, db, classroom, teacher, monkeypatch):
    material = doc_material(db, classroom, "一" * 300, "二" * 300, "三" * 300)
    db.commit()

    def flaky(messages, *, settings, model, max_tokens=None, effort=None):
        if "二" in messages[1]["content"][1]["text"]:
            raise model_client.ModelFailed("模型返回空内容，请重试")
        return {"text": "讲解正文。", "bridge_in": "", "anchors": [], "usage": {}, "model": model}

    monkeypatch.setattr(service.model_client, "chat", flaky)
    result = client.post(url(classroom, material, "/generate"),
                         json={"first_page": 1, "last_page": 3}, headers=teacher["headers"])
    assert result.status_code == 200, result.text
    statuses = {item["page_no"]: item["status"] for item in result.json()["results"]}
    assert statuses == {1: "draft", 2: "failed", 3: "draft"}
    body = client.get(url(classroom, material), headers=teacher["headers"]).json()
    assert body["coverage"]["covered"] == 2
    assert body["pages"][1]["narration"] is None


def test_students_cannot_generate(client, db, classroom, teacher, student, calls):
    material = doc_material(db, classroom, "第一页正文" * 30)
    db.commit()
    refused = client.post(url(classroom, material, "/1/generate"), headers=student["headers"])
    assert refused.status_code == 403
    assert calls == []


def test_render_refuses_a_material_without_pdf_source(db):
    class Fake:
        content = {}
        storage_key = "materials/x.mp4"

    assert render.render_page(Fake(), 1) is None
