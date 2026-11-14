import json
from datetime import datetime, timezone
from sqlalchemy import select, text, update, func
from sqlalchemy.orm import sessionmaker
from app import assistant
from app.integration_models import AssistantSession, AssistantCall
from app.models import User, Activity, Material
from test_curriculum_migration import migrate, sqlite_engine, populate_baseline


def create(client, student, classroom, **fields):
    return client.post('/api/assistant/sessions', headers=student['headers'],
                       json={'classroom_id': classroom['id'], 'material_id': 186, **fields})


def test_material_session_authorization_and_revocation(client, db, student, teacher, classroom):
    material = db.get(Material, 186)
    material.content = {**material.content, 'library_available': True, 'source': {'kind': 'course_media_pack', 'course_id': 43}}
    db.execute(update(Activity).where(Activity.material_id == 186).values(material_id=None))
    db.commit()
    activity_count = db.scalar(select(func.count()).select_from(Activity))
    assert create(client, student, classroom, activity_id='ch1-lesson').status_code == 422
    assert create(client, student, classroom, material_id=999999).status_code == 404
    response = create(client, student, classroom)
    assert response.status_code == 201
    sid = response.json()['id']
    row = db.get(AssistantSession, sid)
    assert row.activity_id is None and row.material_id == 186
    assert db.scalar(select(func.count()).select_from(Activity)) == activity_count
    assert client.get(f'/api/assistant/sessions/{sid}', headers=teacher['headers']).status_code == 404
    assert client.get('/api/assistant/status', headers=student['headers']).json()['model_calls'] == 0
    client.put(f"/api/teacher/classrooms/{classroom['id']}/releases/179", headers=teacher['headers'], json={'status': 'paused'})
    assert client.get(f'/api/assistant/sessions/{sid}', headers=student['headers']).status_code == 403
    assert create(client, student, classroom).status_code == 403
    assert client.post(f'/api/assistant/sessions/{sid}/messages', headers=student['headers'], json={'message': '解释此页'}).status_code == 403


def test_library_context_and_stream_contract(client, db, student, classroom, monkeypatch):
    from langchain_core.messages import AIMessageChunk
    monkeypatch.setenv('REBORN_AI_API_KEY', 'test-only-no-network')
    monkeypatch.setenv('REBORN_AI_MODEL', 'test-only')
    monkeypatch.setattr(assistant, 'SessionLocal', sessionmaker(db.bind, expire_on_commit=False))
    class TestAgent:
        async def astream(self, request, **kwargs):
            yield AIMessageChunk(content='测试响应', id='library-test', usage_metadata={'input_tokens': 1, 'output_tokens': 1, 'total_tokens': 2}), {'langgraph_node': 'model'}
    def factory(context, references, stats):
        assert context['role']['name'] == '图书管理员'
        assert context['activity']['id'] is None
        assert context['activity']['questions'] == [] and context['submission'] is None
        assert all(p['material_id'] == 186 for p in context['pages'])
        assert 'hidden_cases' not in json.dumps(context)
        return TestAgent()
    monkeypatch.setattr(assistant.AgentFactory, 'create', factory)
    sid = create(client, student, classroom).json()['id']
    bad = client.post(f'/api/assistant/sessions/{sid}/messages', headers=student['headers'], json={'message': '解释', 'page_no': 99999})
    assert bad.status_code == 422
    result = client.post(f'/api/assistant/sessions/{sid}/messages', headers=student['headers'], json={'message': '解释', 'page_no': 42})
    assert result.status_code == 200 and 'event: done' in result.text
    db.expire_all()
    assert db.scalar(select(AssistantCall)).model_calls == 1


def test_library_no_credentials_is_honestly_unavailable(client, student, classroom, monkeypatch):
    monkeypatch.delenv('REBORN_AI_API_KEY', raising=False)
    monkeypatch.delenv('REBORN_AI_MODEL', raising=False)
    sid = create(client, student, classroom).json()['id']
    assert client.post(f'/api/assistant/sessions/{sid}/messages', headers=student['headers'], json={'message': '解释'}).status_code == 503


def test_migration_keeps_existing_assistant_calls(tmp_path):
    path = tmp_path / 'assistant.sqlite3'
    url = 'sqlite:///' + path.as_posix()
    migrate(url, 'upgrade', '221d0f8caf6e')
    engine = sqlite_engine(path)
    populate_baseline(engine)
    migrate(url, 'upgrade', 'a91c7d20e648')
    with engine.begin() as c:
        c.execute(text("INSERT INTO assistant_sessions (id,user_id,classroom_id,activity_id,material_id,messages,created_at) VALUES ('saved',2,1,'historic-lesson',186,'[]',:now)"), {'now': datetime.now(timezone.utc)})
        c.execute(text("INSERT INTO assistant_calls (id,user_id,classroom_id,session_id,status,model_calls,tokens,elapsed_ms,created_at) VALUES ('saved-call',2,1,'saved','complete',1,12,50,:now)"), {'now': datetime.now(timezone.utc)})
    migrate(url, 'upgrade', 'head')
    migrate(url, 'check')
    with engine.begin() as c:
        assert c.execute(text("SELECT tokens FROM assistant_calls WHERE id='saved-call'")).scalar() == 12
        c.execute(text("UPDATE assistant_sessions SET activity_id=NULL WHERE id='saved'"))
        assert not c.execute(text('PRAGMA foreign_key_check')).all()
        assert c.scalar(select(User.id).where(User.id == 2)) == 2
    engine.dispose()
