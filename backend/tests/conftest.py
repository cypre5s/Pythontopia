import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
from app.db import Base, get_db
from app.main import app
from app.models import User
from app.auth import hash_password, issue
from app.seed import seed


@pytest.fixture
def db():
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    with sessionmaker(engine, expire_on_commit=False)() as session:
        seed(session)
        yield session
    Base.metadata.drop_all(engine)
    engine.dispose()


@pytest.fixture
def client(db):
    def override():
        yield db

    app.dependency_overrides[get_db] = override
    client = TestClient(app, raise_server_exceptions=True)
    yield client
    client.close()
    app.dependency_overrides.clear()


@pytest.fixture
def student(db):
    row = User(email="student@example.test", name="学生", role="student", password_hash=hash_password("student-password-123"))
    db.add(row)
    db.commit()
    token = issue(db, row)["access_token"]
    return {"id": row.id, "email": row.email, "headers": {"Authorization": "Bearer " + token}}


@pytest.fixture
def teacher(db):
    row = User(email="teacher@example.test", name="教师", role="teacher", password_hash=hash_password("teacher-password-123"))
    db.add(row)
    db.commit()
    token = issue(db, row)["access_token"]
    return {"id": row.id, "email": row.email, "headers": {"Authorization": "Bearer " + token}}


@pytest.fixture
def classroom(client, teacher, student):
    result = client.post("/api/classrooms", json={"name": "测试班级"}, headers=teacher["headers"])
    assert result.status_code == 201, result.text
    classroom = result.json()
    assert client.post("/api/classrooms/join", json={"join_code": classroom["join_code"]}, headers=student["headers"]).status_code == 200
    for chapter in (179, 180):
        assert (
            client.put(
                f"/api/teacher/classrooms/{classroom['id']}/releases/{chapter}",
                json={"status": "published", "version": 1},
                headers=teacher["headers"],
            ).status_code
            == 200
        )
    return classroom
