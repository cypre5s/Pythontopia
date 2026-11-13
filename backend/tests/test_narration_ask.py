"""Students asking about the current page: scoping, caps and answer validation."""
import json

import pytest

from app.narration import client as model_client


@pytest.fixture(autouse=True)
def keys(monkeypatch):
    # Both surfaces share the same client module, so both credentials must exist.
    monkeypatch.setenv("REBORN_AI_API_KEY", "student-key")
    monkeypatch.setenv("REBORN_AI_MODEL", "deepseek-flash")
    monkeypatch.setenv("REBORN_AUTHORING_AI_API_KEY", "authoring-key")
    monkeypatch.setenv("REBORN_NARRATION_ASK_DAILY", "3")


@pytest.fixture
def prompts(monkeypatch):
    """One dispatcher for both surfaces: page questions vs page narration."""
    seen = {"ask": [], "generate": []}

    def fake_chat(messages, *, settings, model, max_tokens=None, effort=None, require_text=True):
        payload = messages[1]["content"]
        if "approved_narration" in payload:
            seen["ask"].append(payload)
            return {"raw": {"answer": "这一页讲监督学习，先看标签。", "cites": [1, 99]},
                    "text": "", "bridge_in": "", "anchors": [], "usage": {}, "model": model}
        seen["generate"].append(payload)
        return {"raw": {}, "text": "这一页讲监督学习。", "bridge_in": "", "anchors": [], "usage": {}, "model": model}

    monkeypatch.setattr(model_client, "chat", fake_chat)
    return seen


def doc_material(db, classroom, *texts):
    from test_narration_generation import doc_material as build

    return build(db, classroom, *texts)


def url(classroom, material, suffix=""):
    from test_narration import url as build

    return build(classroom, material, suffix)


def student_ask(client, classroom, material, page_no, question, student):
    return client.post(f"/api/materials/{material.id}/narrations/{page_no}/ask?classroom_id={classroom['id']}",
                       json={"question": question}, headers=student["headers"])


def test_answers_from_the_current_page_and_keeps_only_nearby_citations(client, db, classroom, student, teacher, prompts):
    material = doc_material(db, classroom, "第一页正文" * 30, "第二页正文" * 30)
    db.commit()
    client.post(url(classroom, material, "/1/generate"), json=None, headers=teacher["headers"])
    client.post(url(classroom, material, "/1/accept"), headers=teacher["headers"])

    result = student_ask(client, classroom, material, 1, "这一页的标签是什么意思？", student)
    assert result.status_code == 200, result.text
    body = result.json()
    assert body["answer"].startswith("这一页讲监督学习")
    assert body["cites"] == [1], "超出邻近范围的引用必须被丢弃"
    assert body["checks"] == {"page_text": True, "narration": True}
    assert body["remaining"] == 2
    # The prompt carries the approved narration and this page only.
    assert "这一页讲监督学习。" in prompts["ask"][0]
    assert "第二页正文" not in prompts["ask"][0]


def test_drafts_are_not_given_to_the_assistant(client, db, classroom, student, teacher, prompts, monkeypatch):
    monkeypatch.setattr(model_client, "chat", lambda messages, **kwargs: (
        {"raw": {}, "text": "未审校的草稿。", "bridge_in": "", "anchors": [], "usage": {}, "model": "x"}
        if "approved_narration" not in messages[1]["content"]
        else {"raw": {"answer": "按页面原文回答。", "cites": [1]}, "text": "", "bridge_in": "", "anchors": [],
              "usage": {}, "model": "x"}))
    material = doc_material(db, classroom, "第一页正文" * 30)
    db.commit()
    client.post(url(classroom, material, "/1/generate"), json=None, headers=teacher["headers"])

    captured = {}

    def capture(messages, **kwargs):
        captured["payload"] = json.loads(messages[1]["content"].rsplit("\n\n", 1)[1])
        return {"raw": {"answer": "按页面原文回答。", "cites": [1]}, "text": "", "bridge_in": "", "anchors": [],
                "usage": {}, "model": "x"}

    monkeypatch.setattr(model_client, "chat", capture)
    assert student_ask(client, classroom, material, 1, "这段什么意思？", student).status_code == 200
    assert captured["payload"]["approved_narration"] == "", "草稿不能进助教上下文"


def test_daily_cap_returns_429(client, db, classroom, student, prompts):
    material = doc_material(db, classroom, "第一页正文" * 30)
    db.commit()
    for _ in range(3):
        assert student_ask(client, classroom, material, 1, "问题", student).status_code == 200
    blocked = student_ask(client, classroom, material, 1, "再问一次", student)
    assert blocked.status_code == 429
    assert "次数已用完" in blocked.json()["detail"]


def test_question_validation_and_missing_credentials(client, db, classroom, student, prompts, monkeypatch):
    material = doc_material(db, classroom, "第一页正文" * 30)
    db.commit()
    assert student_ask(client, classroom, material, 1, "   ", student).status_code == 422
    assert student_ask(client, classroom, material, 1, "问" * 501, student).status_code == 422
    assert student_ask(client, classroom, material, 9, "越界页", student).status_code == 404

    monkeypatch.setenv("REBORN_AI_API_KEY", "")
    monkeypatch.setenv("REBORN_AUTHORING_AI_API_KEY", "")
    refused = student_ask(client, classroom, material, 1, "没有凭据时能问吗", student)
    assert refused.status_code == 409
    assert "未配置助教模型凭据" in refused.json()["detail"]


def test_empty_answer_is_reported_as_bad_gateway(client, db, classroom, student, monkeypatch):
    def broken(messages, **kwargs):
        raise model_client.ModelFailed("助教没有给出回答，请重试")

    monkeypatch.setattr(model_client, "chat", broken)
    material = doc_material(db, classroom, "第一页正文" * 30)
    db.commit()
    result = student_ask(client, classroom, material, 1, "提问", student)
    assert result.status_code == 502


def test_page_without_text_or_narration_cannot_be_asked(client, db, classroom, student, prompts):
    material = doc_material(db, classroom, "")
    db.commit()
    refused = student_ask(client, classroom, material, 1, "这页讲什么", student)
    assert refused.status_code == 404
    assert "还没有可提问的内容" in refused.json()["detail"]
