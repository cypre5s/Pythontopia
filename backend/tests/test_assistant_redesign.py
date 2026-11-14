"""Conversation boundaries, secret settings, real graph tools and compression."""
import asyncio
import json

import pytest
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from sqlalchemy import select
from sqlalchemy.orm import sessionmaker

from app import assistant
from app.assistant_context import MessageInput, build_tools, state_text, system_prompt, LocalTextRetrieval
from app.assistant_runtime import AgentFactory, complete_history, serialise, restore_messages
from app.assistant_settings import DEFAULTS, configuration, ModelInput
from app.integration_models import AssistantCall, AssistantSession, AssistantModelSettings
from app.models import User, Npc, Submission
from conftest import make_question
from test_assistant import create_session


class Model(BaseChatModel):
    calls: int = 0
    inputs: list = []
    fail_summary: bool = False
    use_tools: bool = False

    @property
    def _llm_type(self):
        return "isolated-test"

    def bind_tools(self, tools, **kwargs):
        return self

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        self.calls += 1
        self.inputs.append(messages)
        summarizing = "整理成简短续聊摘要" in messages[0].content
        if summarizing and self.fail_summary:
            raise RuntimeError("test summary failure")
        if self.use_tools and not any(isinstance(m, ToolMessage) for m in messages):
            reply = AIMessage(content="", tool_calls=[{"name": "read_material_page", "args": {"page_no": 42}, "id": "read-page", "type": "tool_call"}])
        else:
            reply = AIMessage(content="已经讨论循环，学生正在检查边界。" if summarizing else "先检查循环结束的位置。")
        reply.usage_metadata = {"input_tokens": 20, "output_tokens": 10, "total_tokens": 30}
        return ChatResult(generations=[ChatGeneration(message=reply)])


def context():
    return {"kind": "learning", "role": {"name": "馆员", "persona": "耐心的图书管理员"}, "snapshot": {"当前页": "第 42 页"},
            "pages": [{"material_id": 186, "page_no": 42, "title": "循环", "excerpt": "循环逐步执行"}],
            "activity": {"title": "阅读", "questions": []}, "progress": {},
            "model_config": {**DEFAULTS, "api_key": "test", "model": "test", "base_url": "https://example.invalid", "reasoning_effort": ""}}


def test_tool_sets_long_search_and_segments():
    data = context()
    data["pages"][0]["excerpt"] = "普通文字" * 2000 + "罕见知识关键词" + "尾部" * 500
    retrieval = LocalTextRetrieval(data["pages"])
    assert "罕见知识关键词" in retrieval.search("罕见知识关键词")[0]["excerpt"]
    tools = {t.name: t for t in build_tools(data, {})}
    assert set(tools) == {"list_material_pages", "search_material_pages", "read_material_page", "get_reading_progress"}
    page = tools["read_material_page"].invoke({"page_no": 42, "offset": 8000, "limit": 9})
    assert page["text"] == "罕见知识关键词尾部" and page["next_offset"] == 8009
    assert "error" in tools["read_material_page"].invoke({"page_no": 999})
    data.update(kind="practice", state={"code": "print(1)", "answer": "", "displayed": "local_run", "selected_case": 0},
                current_question={"kind": "python"}, local_run={"source": "浏览器", "code_changed": True}, submission=None)
    tools = {t.name: t for t in build_tools(data, {})}
    assert set(tools) == {"list_activity_questions", "read_activity_question", "read_current_attempt", "get_current_question_feedback", "search_practice_materials", "read_practice_material"}
    assert "浏览器" in tools["get_current_question_feedback"].invoke({})["text"]
    assert "暂无" in tools["get_current_question_feedback"].invoke({"source": "submission"})["message"]
    assert tools["read_current_attempt"].invoke({"offset": 0, "limit": 5})["text"] == "print"


def test_delta_and_prompt_have_stable_prefix():
    data = context()
    prompt = system_prompt(data)
    data["snapshot"] = {"当前页": "第 99 页"}
    assert system_prompt(data) == prompt and "耐心的图书管理员" in prompt
    assert "第 99 页" not in prompt
    assert state_text({"页码": 3, "作答": "B"}, {"页码": 3, "作答": "A"}) == "作答：B"
    assert "无变化" in state_text({"页码": 3}, {"页码": 3})


def test_current_question_only_and_client_run_is_not_evidence(client, db, student, classroom):
    q = make_question(db, chapter_ids=[179], activity_id="ch1-practice", id="python-helper", title="测试代码", kind="python",
                      prompt="输出名字", samples=[{"input": "小林", "output": "小林"}], source={})
    db.add(Submission(user_id=student["id"], classroom_id=classroom["id"], activity_id="ch1-practice", question_id=q.id,
                      idempotency_key="helper-current", status="wrong_answer", code="print('old')", feedback={"passed_count": 0}))
    db.commit()
    sid = create_session(client, student, classroom, "ch1-practice").json()["id"]
    user = db.get(User, student["id"])
    session, activity = assistant.authorized_session(db, user, sid)
    body = MessageInput(message="为什么", state={"question_id": q.id, "code": "print('new')", "local_run": {
        "question_id": q.id, "run_id": "local-one", "code": "print('old')", "completed_at": "2026-11-14", "duration_ms": 5,
        "cases": [{"status": "accepted", "stdout": "小林"}], "stopped": False}})
    data = assistant.build_context(db, user, session, activity, body)
    assert data["submission"]["code_changed"] and data["local_run"]["code_changed"]
    assert data["local_run"]["cases"][0]["expected"] == "小林"
    assert len(list(db.scalars(select(Submission)))) == 1
    assert "hidden_cases" not in json.dumps(data)
    with pytest.raises(Exception, match="当前活动"):
        assistant.build_context(db, user, session, activity, MessageInput(message="问", state={"question_id": "foreign"}))
    body.state.local_run.question_id = "foreign"
    with pytest.raises(Exception, match="当前编程题"):
        assistant.build_context(db, user, session, activity, body)


def test_real_graph_persists_tool_chain_idempotency_and_role_snapshot(client, db, student, classroom, monkeypatch):
    import langchain_openai
    model = Model(use_tools=True)
    monkeypatch.setattr(langchain_openai, "ChatOpenAI", lambda **kwargs: model)
    monkeypatch.setenv("REBORN_AI_API_KEY", "test-only")
    monkeypatch.setenv("REBORN_AI_MODEL", "test-only")
    monkeypatch.setattr(assistant, "SessionLocal", sessionmaker(db.bind, expire_on_commit=False))
    sid = create_session(client, student, classroom).json()["id"]
    url = f"/api/assistant/sessions/{sid}/messages"
    body = {"message": "解释", "state": {"page_no": 42}, "client_request_id": "one", "expected_revision": 0}
    response = client.post(url, json=body, headers=student["headers"])
    assert "event: done" in response.text, response.text
    db.expire_all()
    session = db.get(AssistantSession, sid)
    messages = restore_messages(session.runtime["messages"])
    assert any(isinstance(m, ToolMessage) for m in messages)
    prefix = session.runtime["system_prompt"]
    calls = model.calls
    assert "replayed" in client.post(url, json=body, headers=student["headers"]).text
    assert model.calls == calls
    assert client.post(url, json={**body, "message": "不同内容"}, headers=student["headers"]).status_code == 409
    assert client.post(url, json={**body, "client_request_id": "two"}, headers=student["headers"]).status_code == 409
    npc = db.scalar(select(Npc).where(Npc.name == "图书管理员"))
    npc.persona = "新角色人设"
    db.commit()
    history = client.get(f"/api/assistant/sessions/{sid}", headers=student["headers"]).json()
    assert history["role_changed"] and history["revision"] == 1
    response = client.post(url, json={**body, "client_request_id": "two", "expected_revision": 1}, headers=student["headers"])
    assert "event: done" in response.text
    db.expire_all()
    assert db.get(AssistantSession, sid).runtime["system_prompt"] == prefix
    assert len(list(db.scalars(select(AssistantCall)))) == 2
    assert "学习状态" not in history["messages"][0]["content"]


@pytest.mark.parametrize("fail", [False, True])
def test_compression_keeps_recent_turns_and_original_on_failure(monkeypatch, fail):
    import langchain_openai
    model = Model(fail_summary=fail)
    monkeypatch.setattr(langchain_openai, "ChatOpenAI", lambda **kwargs: model)
    data = context()
    data["model_config"] = {**data["model_config"], "compression_threshold": 1000, "context_window": 100000}
    messages = []
    for i in range(5):
        messages += [HumanMessage(content=f"问题{i}" + "很长的讨论" * 100), AIMessage(content=f"提示{i}")]
    messages.append(HumanMessage(content="当前问题"))
    original = serialise(messages)
    stats = {"model_calls": 0}
    result = asyncio.run(AgentFactory.create(data, {}, stats).ainvoke({"messages": messages}))
    assert stats["summary_calls"] == 1
    assert "问题4" in str(result["messages"])
    assert "当前问题" in str(result["messages"])
    if fail:
        assert stats["compression_failed"]
        assert "问题0" in str(result["messages"])
    else:
        assert stats["compression"]["removed_messages"] == 6
        assert "当前学习状态" in result["messages"][-2].content
        assert "问题0" not in str(result["messages"])
    assert [m.content for m in messages] == [m['data']['content'] for m in original]


def test_cancelled_tool_pairs_are_not_replayed():
    request = AIMessage(content="", tool_calls=[{"name": "read", "args": {}, "id": "a", "type": "tool_call"}, {"name": "read", "args": {}, "id": "b", "type": "tool_call"}])
    partial = [HumanMessage(content="问"), request, ToolMessage(content="一", tool_call_id="a")]
    assert len(complete_history(partial)) == 1
    assert len(complete_history([*partial, ToolMessage(content="二", tool_call_id="b")])) == 4


def test_admin_model_settings_encrypted_and_teacher_denied(client, db, teacher, student, tmp_path, monkeypatch):
    monkeypatch.setenv("REBORN_AI_MASTER_KEY_FILE", str(tmp_path / "master.key"))
    path = "/api/teacher/assistant/model"
    assert client.get(path, headers=teacher["headers"]).status_code == 403
    assert client.get(path, headers=student["headers"]).status_code == 403
    db.get(User, teacher["id"]).role = "admin"
    db.commit()
    secret = "test-private-secret-never-returned"
    body = {"expected_revision": 0, "base_url": "https://example.invalid/v1", "model": "test", "api_key": secret}
    rejected = client.put(path, json={**body, "compression_threshold": 32000}, headers=teacher["headers"])
    assert rejected.status_code == 422 and secret not in rejected.text
    response = client.put(path, json=body, headers=teacher["headers"])
    assert response.status_code == 200, response.text
    assert secret not in response.text and response.json()["key_configured"]
    db.expire_all()
    row = db.get(AssistantModelSettings, 1)
    assert secret not in row.encrypted_key and secret not in json.dumps(row.configuration)
    assert configuration(db)["api_key"] == secret
    monkeypatch.setenv("REBORN_AI_API_KEY", "different-environment-key")
    assert configuration(db)["api_key"] == secret
    assert client.put(path, json=body, headers=teacher["headers"]).status_code == 409
    body.update(expected_revision=1, api_key=None, clear_key=True)
    assert client.put(path, json=body, headers=teacher["headers"]).status_code == 200
    assert not configuration(db)["api_key"]


def test_model_settings_validate_context_headroom():
    with pytest.raises(ValueError, match="预留"):
        ModelInput(expected_revision=0, base_url="https://example.invalid", model="test", compression_threshold=32000)
    with pytest.raises(ValueError, match="地址"):
        ModelInput(expected_revision=0, base_url="https://secret@example.invalid", model="test")
