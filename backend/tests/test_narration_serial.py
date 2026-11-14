"""串行生成：后面的页要能看到前面已生成的讲解，幂等键要包含上下文。"""
import re

import pytest

from test_narration import url
from test_narration_generation import doc_material


@pytest.fixture(autouse=True)
def authoring(monkeypatch):
    monkeypatch.setenv("REBORN_AUTHORING_AI_API_KEY", "test-key")
    monkeypatch.setenv("REBORN_AUTHORING_AI_MODEL", "deepseek-flash")


def prompt_text(content) -> str:
    """user 消息是分段数组（文本 + 可选页图），这里只取文本部分。"""
    if isinstance(content, str):
        return content
    return "\n".join(part.get("text", "") for part in content if part.get("type") == "text")


def make_chat(seen: list[str]):
    def fake_chat(messages, *, settings, model, max_tokens=None, effort=None, require_text=True):
        payload = prompt_text(messages[1]["content"])
        seen.append(payload)
        match = re.search(r"第 (\d+) 页，目标朗读时长", payload)
        page_no = int(match.group(1)) if match else 0
        return {"raw": {}, "text": f"第 {page_no} 页讲这一点。", "bridge_in": "", "anchors": [],
                "usage": {}, "model": model}

    return fake_chat


def test_serial_mode_feeds_earlier_pages_into_later_prompts(client, db, classroom, teacher, monkeypatch):
    seen: list[str] = []
    monkeypatch.setattr("app.narration.service.model_client.chat", make_chat(seen))
    material = doc_material(db, classroom, "第一页正文" * 30, "第二页正文" * 30, "第三页正文" * 30)
    db.commit()

    response = client.post(url(classroom, material, "/generate"),
                           json={"first_page": 1, "last_page": 3, "mode": "serial"},
                           headers=teacher["headers"])
    assert response.status_code == 200, response.text
    assert len(seen) == 3
    # 第一页没有历史；第二页能看到第一页的讲解；第三页能看到前两页。
    assert "前面几页已经这样讲过" not in seen[0]
    assert "第 1 页讲这一点。" in seen[1]
    assert "第 1 页讲这一点。" in seen[2] and "第 2 页讲这一点。" in seen[2]


def test_independent_mode_keeps_prompts_isolated(client, db, classroom, teacher, monkeypatch):
    seen: list[str] = []
    monkeypatch.setattr("app.narration.service.model_client.chat", make_chat(seen))
    material = doc_material(db, classroom, "第一页正文" * 30, "第二页正文" * 30)
    db.commit()

    client.post(url(classroom, material, "/generate"), json={"first_page": 1, "last_page": 2},
                headers=teacher["headers"])
    assert "第 1 页讲这一点。" not in seen[1]


def test_serial_history_changes_the_idempotency_key(client, db, classroom, teacher, monkeypatch):
    calls: list[str] = []
    monkeypatch.setattr("app.narration.service.model_client.chat", make_chat(calls))
    material = doc_material(db, classroom, "第一页正文" * 30, "第二页正文" * 30)
    db.commit()

    client.post(url(classroom, material, "/generate"),
                json={"first_page": 1, "last_page": 2, "mode": "serial"}, headers=teacher["headers"])
    # 改第 1 页的讲解后，第 2 页的上下文变了，必须重新生成而不是复用。
    client.put(url(classroom, material, "/1"), json={"text": "换成新的第一页讲法。"}, headers=teacher["headers"])
    again = client.post(url(classroom, material, "/generate"),
                        json={"first_page": 2, "last_page": 2, "mode": "serial"}, headers=teacher["headers"])
    assert again.status_code == 200
    assert again.json()["results"][0]["reused"] is False
    assert "换成新的第一页讲法。" in calls[-1]


def test_serial_outline_carries_titles_of_older_pages(client, db, classroom, teacher, monkeypatch):
    seen: list[str] = []
    monkeypatch.setattr("app.narration.service.model_client.chat", make_chat(seen))
    texts = [f"第 {n} 页正文" + "一" * 60 for n in range(1, 8)]
    material = doc_material(db, classroom, *texts)
    db.commit()

    client.post(url(classroom, material, "/generate"),
                json={"first_page": 1, "last_page": 7, "mode": "serial"}, headers=teacher["headers"])
    # 最近 4 页给正文，更早的页只给标题占位，避免上下文无限增长。
    assert "更早讲过：" in seen[-1]
