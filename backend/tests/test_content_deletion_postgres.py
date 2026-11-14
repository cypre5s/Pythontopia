"""Real course locks make repeated confirmations atomic on PostgreSQL."""
import os
from concurrent.futures import ThreadPoolExecutor
from uuid import uuid4
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, select, text
from sqlalchemy.engine import make_url
from sqlalchemy.orm import sessionmaker
from app.auth import issue
from app.db import Base, get_db
from app.main import create_app
from app.models import Chapter, Classroom, CourseInstance, User
from app.deletion_models import ContentDeletionPreview


@pytest.mark.skipif(not os.getenv('REBORN_TEST_DATABASE_URL'), reason='Requires explicit isolated PostgreSQL')
def test_postgres_simultaneous_confirmation_is_idempotent():
    url = make_url(os.environ['REBORN_TEST_DATABASE_URL'])
    assert url.get_backend_name() == 'postgresql' and 'reborn' in url.database.lower()
    admin = create_engine(url)
    schema = 'reborn_test_' + uuid4().hex
    with admin.begin() as conn:
        conn.execute(text(f'CREATE SCHEMA "{schema}"'))
    engine = create_engine(url, connect_args={'options': f'-csearch_path={schema}'})
    factory = sessionmaker(engine, expire_on_commit=False)
    application = create_app()
    def connection():
        with factory() as db:
            yield db
    application.dependency_overrides[get_db] = connection
    try:
        Base.metadata.create_all(engine)
        with factory() as db:
            teacher = User(student_id='991415001',name='删除并发测试',role='teacher',password_hash='unused')
            db.add(teacher)
            db.flush()
            classroom = Classroom(name='隔离测试',teacher_id=teacher.id,join_code=uuid4().hex[:20])
            db.add(classroom)
            db.flush()
            instance = CourseInstance(classroom_id=classroom.id,package_id='isolated',name='隔离课',package_version='1')
            db.add(instance)
            db.flush()
            chapter = Chapter(course_instance_id=instance.id,stable_id='chapter',title='并发章节',order_index=1,owner_id=teacher.id)
            db.add(chapter)
            db.commit()
            class_id, instance_id, chapter_id, revision = classroom.id, instance.id, chapter.id, instance.revision
            headers = {'Authorization':'Bearer '+issue(db, teacher)['access_token']}
        client = TestClient(application)
        plan = client.post('/api/teacher/content-deletions/preview',headers=headers,json={'classroom_id':class_id,'targets':[{'kind':'chapter','id':str(chapter_id)}]})
        assert plan.status_code == 200, plan.text
        plan = plan.json()
        assert not plan['blockers'], plan
        # Do not start application background workers against the non-test schema.
        def confirm_once(_):
            caller = TestClient(application)
            try:
                return caller.post(f"/api/teacher/content-deletions/{plan['id']}/confirm",headers=headers,
                                   json={'classroom_id':class_id,'confirmation':plan['confirmation'],'confirmed':True})
            finally:
                caller.close()
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(confirm_once, range(2)))
        assert [r.status_code for r in results] == [200,200], [r.text for r in results]
        assert results[0].json() == results[1].json()
        with factory() as db:
            assert db.get(CourseInstance,instance_id).revision == revision+1
            assert db.scalar(select(Chapter).where(Chapter.id == chapter_id)) is None
            assert db.get(ContentDeletionPreview,plan['id']).result == results[0].json()
        client.close()
    finally:
        engine.dispose()
        with admin.begin() as conn:
            conn.execute(text(f'DROP SCHEMA "{schema}" CASCADE'))
        admin.dispose()
