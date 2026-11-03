import json
from sqlalchemy import select
from sqlalchemy.orm import sessionmaker
from app import assistant
from app.integration_models import AssistantCall, AssistantSession
from app.models import Activity


def create_session(client, student, classroom, activity="ch1-lesson"):
    return client.post("/api/assistant/sessions", json={"classroom_id": classroom["id"], "activity_id": activity}, headers=student["headers"])


def test_world_reading_and_npc_do_not_call_models(client, student, classroom):
    before = client.get("/api/assistant/status", headers=student["headers"]).json()["model_calls"]
    assert client.get(f"/api/world?classroom_id={classroom['id']}", headers=student["headers"]).status_code == 200
    assert client.get(f"/api/activities/ch1-lesson?classroom_id={classroom['id']}", headers=student["headers"]).status_code == 200
    after = client.get("/api/assistant/status", headers=student["headers"]).json()["model_calls"]
    assert before == after == 0


def test_no_credentials_is_503_not_fake_success(client, student, classroom, monkeypatch):
    monkeypatch.delenv("REBORN_AI_API_KEY", raising=False)
    monkeypatch.delenv("REBORN_AI_MODEL", raising=False)
    sid = create_session(client, student, classroom).json()["id"]
    response = client.post(f"/api/assistant/sessions/{sid}/messages", json={"message": "解释计算思维"}, headers=student["headers"])
    assert response.status_code == 503
    assert not client.get("/api/assistant/status", headers=student["headers"]).json()["available"]


def test_sessions_enforce_user_activity_and_assessment_policy(client, db, student, teacher, classroom):
    sid = create_session(client, student, classroom).json()["id"]
    assert client.post(f"/api/assistant/sessions/{sid}/messages", json={"message": "read"}, headers=teacher["headers"]).status_code == 404
    activity = db.get(Activity, "ch1-lesson")
    activity.assistant_allowed = False
    db.commit()
    assert client.post(f"/api/assistant/sessions/{sid}/messages", json={"message": "read"}, headers=student["headers"]).status_code == 403
    assert create_session(client, student, classroom).status_code == 403


def test_retrieval_only_returns_real_scoped_pages():
    page = {"material_id": 186, "version": 1, "page_no": 42, "page_id": 10723, "title": "计算思维", "excerpt": "计算思维的本质是抽象和自动化"}
    retrieval = assistant.LocalTextRetrieval([page])
    assert retrieval.search("计算思维")[0]["page_id"] == 10723
    assert "error" in retrieval.excerpt(999, 1, 42)
    assert "error" in retrieval.excerpt(186, 2, 42)


def test_sse_records_versioned_session_and_daily_budget(client, db, student, classroom, monkeypatch):
    from langchain_core.messages import AIMessageChunk

    monkeypatch.setenv("REBORN_AI_API_KEY", "test-only-credential")
    monkeypatch.setenv("REBORN_AI_MODEL", "explicit-test-provider")
    monkeypatch.setenv("REBORN_AI_USER_DAILY_REQUESTS", "1")
    monkeypatch.setattr(assistant, "SessionLocal", sessionmaker(db.bind, expire_on_commit=False))

    class ExplicitFakeAgent:
        async def astream(self, request, **kwargs):
            assert "system_prompt" not in request
            yield (
                AIMessageChunk(
                    content="想想如何建立模型。", id="call-1", usage_metadata={"input_tokens": 20, "output_tokens": 8, "total_tokens": 28}
                ),
                {"langgraph_node": "model"},
            )

    def factory(context, references, stats):
        assert "hidden_cases" not in json.dumps(context)
        return ExplicitFakeAgent()

    monkeypatch.setattr(assistant.AgentFactory, "create", factory)
    sid = create_session(client, student, classroom).json()["id"]
    response = client.post(f"/api/assistant/sessions/{sid}/messages", json={"message": "解释此页", "page_no": 42}, headers=student["headers"])
    assert response.status_code == 200
    assert "event: token" in response.text and "event: references" in response.text and "event: done" in response.text
    assert '"page_id": 10723' in response.text
    db.expire_all()
    log = db.scalar(select(AssistantCall))
    assert log.status == "complete" and log.tokens == 28 and log.model_calls == 1
    session = db.get(AssistantSession, sid)
    assert session.user_id == student["id"] and session.classroom_id == classroom["id"] and session.material_version == 1
    assert client.post(f"/api/assistant/sessions/{sid}/messages", json={"message": "再问一次"}, headers=student["headers"]).status_code == 429


def test_foreign_page_and_client_privilege_fields_rejected(client, student, classroom, monkeypatch):
    monkeypatch.setenv("REBORN_AI_API_KEY", "test-only")
    monkeypatch.setenv("REBORN_AI_MODEL", "test-only")
    sid = create_session(client, student, classroom).json()["id"]
    assert (
        client.post(f"/api/assistant/sessions/{sid}/messages", json={"message": "read", "page_no": 99999}, headers=student["headers"]).status_code
        == 422
    )
    assert (
        client.post(f"/api/assistant/sessions/{sid}/messages", json={"message": "read", "teacher": True}, headers=student["headers"]).status_code
        == 422
    )


def test_real_agent_factory_compiles_without_model_request(monkeypatch):
    monkeypatch.setenv("REBORN_AI_API_KEY", "test-not-real")
    monkeypatch.setenv("REBORN_AI_MODEL", "test-not-real")
    context = {
        "pages": [],
        "activity": {"id": "test", "questions": []},
        "role": {"name": "教务", "tone": "清晰"},
        "current_page": None,
        "progress": [],
        "submissions": [],
        "student_code": None,
    }
    agent = assistant.AgentFactory.create(context, {}, {"model_calls": 0})
    assert "model" in agent.get_graph().nodes


def test_real_agent_runtime_tools_scope_and_pre_call_budget(monkeypatch):
    import asyncio
    from langchain_core.language_models.chat_models import BaseChatModel
    from langchain_core.messages import AIMessage, ToolMessage
    from langchain_core.outputs import ChatGeneration, ChatResult
    import langchain_openai

    class ExplicitTestModel(BaseChatModel):
        calls: int = 0

        @property
        def _llm_type(self):
            return "test-only-no-network"

        def bind_tools(self, tools, **kwargs):
            assert len(tools) == 5
            assert {t.name for t in tools} == {
                "search_course_materials",
                "get_material_excerpt",
                "get_activity_context",
                "get_my_learning_progress",
                "get_my_submission_feedback",
            }
            return self

        def _generate(self, messages, stop=None, run_manager=None, **kwargs):
            self.calls += 1
            if self.calls == 1:
                response = AIMessage(
                    content="",
                    tool_calls=[
                        {
                            "id": "forbidden-resource",
                            "name": "get_material_excerpt",
                            "args": {"material_id": 999, "version": 1, "page_no": 1},
                            "type": "tool_call",
                        }
                    ],
                )
            else:
                feedback = next(x.content for x in messages if isinstance(x, ToolMessage))
                assert "error" in feedback
                response = AIMessage(content="没有获准的材料，无法确认。")
            return ChatResult(generations=[ChatGeneration(message=response)])

    model = ExplicitTestModel()
    monkeypatch.setattr(langchain_openai, "ChatOpenAI", lambda **kwargs: model)
    context = {
        "pages": [],
        "activity": {"id": "test", "questions": []},
        "role": {"name": "教务", "tone": "清晰"},
        "current_page": None,
        "progress": [],
        "submissions": [],
        "student_code": None,
    }
    references, stats = {}, {"model_calls": 0}
    agent = assistant.AgentFactory.create(context, references, stats)
    result = asyncio.run(agent.ainvoke({"messages": [{"role": "user", "content": "尝试读取其他资料"}]}))
    assert stats["model_calls"] == model.calls == 2
    assert references == {}
    assert "无法确认" in result["messages"][-1].content
    monkeypatch.setenv("REBORN_AI_REQUEST_TOKEN_RESERVATION", "1")
    model.calls = 0
    tiny = assistant.AgentFactory.create(context, {}, {"model_calls": 0})
    import pytest

    with pytest.raises(RuntimeError, match="预算"):
        asyncio.run(tiny.ainvoke({"messages": [{"role": "user", "content": "hi"}]}))
    assert model.calls == 0


def test_stream_timeout_is_persisted_without_automatic_retry(client, db, student, classroom, monkeypatch):
    import asyncio

    monkeypatch.setenv("REBORN_AI_API_KEY", "test-only")
    monkeypatch.setenv("REBORN_AI_MODEL", "test-only")
    monkeypatch.setenv("REBORN_AI_TIMEOUT_SECONDS", "1")
    monkeypatch.setattr(assistant, "SessionLocal", sessionmaker(db.bind, expire_on_commit=False))

    class ExplicitStalledTestAgent:
        async def astream(self, *args, **kwargs):
            await asyncio.sleep(30)
            yield None

    monkeypatch.setattr(assistant.AgentFactory, "create", lambda *args: ExplicitStalledTestAgent())
    sid = create_session(client, student, classroom).json()["id"]
    response = client.post(f"/api/assistant/sessions/{sid}/messages", json={"message": "wait"}, headers=student["headers"])
    assert "event: error" in response.text
    db.expire_all()
    log = db.scalar(select(AssistantCall))
    assert log.status == "timeout"
    history = client.get(f"/api/assistant/sessions/{sid}", headers=student["headers"]).json()["messages"]
    assert len(history) == 2 and history[0]["role"] == "user" and history[1]["status"] == "timeout"


def test_cancel_request_enforces_owner_and_cancels_active_task(client, db, student, teacher, classroom):
    import asyncio
    from app.models import User

    sid = create_session(client, student, classroom).json()["id"]
    row = AssistantCall(user_id=student["id"], classroom_id=classroom["id"], session_id=sid)
    db.add(row)
    db.commit()
    assert client.post(f"/api/assistant/requests/{row.id}/cancel", headers=teacher["headers"]).status_code == 404

    async def scenario():
        task = asyncio.create_task(asyncio.sleep(30))
        assistant._active_tasks[row.id] = task
        result = assistant.cancel(row.id, db.get(User, student["id"]), db)
        assert result["ok"] is True
        try:
            await task
            assert False
        except asyncio.CancelledError:
            pass
        finally:
            assistant._active_tasks.pop(row.id, None)

    asyncio.run(scenario())


def test_stale_assistant_call_after_database_outage_does_not_lock_session_forever(client, db, student, classroom):
    from datetime import datetime, timedelta, timezone
    from app.models import User

    sid = create_session(client, student, classroom).json()["id"]
    session = db.get(AssistantSession, sid)
    interrupted = AssistantCall(
        user_id=student["id"],
        classroom_id=classroom["id"],
        session_id=sid,
        status="running",
        created_at=datetime.now(timezone.utc) - timedelta(minutes=3),
    )
    db.add(interrupted)
    db.commit()
    fresh = assistant.reserve_call(db, db.get(User, student["id"]), session)
    db.expire_all()
    assert db.get(AssistantCall, interrupted.id).status == "interrupted"
    assert fresh.id != interrupted.id and fresh.status == "running"
