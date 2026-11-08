import pytest
from sqlalchemy import select
from app.models import User
from app.auth import hash_password, issue
from test_curriculum_migration import migrate, sqlite_engine, populate_baseline


def test_student_id_login_preserves_zeroes_and_rejects_old_contract(client):
    body = {"student_id": " 00123456 ", "name": "测试学生", "password": "student-password-123"}
    created = client.post("/api/auth/register", json=body)
    assert created.status_code == 201
    assert created.json()["user"]["student_id"] == "00123456"
    assert "email" not in created.json()["user"]
    assert client.post("/api/auth/register", json=body).status_code == 409
    logged_in = client.post("/api/auth/login", json={k: body[k] for k in ("student_id", "password")})
    assert logged_in.status_code == 200
    assert client.post("/api/auth/login", json={"student_id": "123456", "password": body["password"]}).status_code == 401
    assert client.post("/api/auth/login", json={"email": "old@example.test", "password": body["password"]}).status_code == 422
    assert client.post("/api/auth/login", json={"student_id": "00123456", "password": "incorrect-password"}).status_code == 401


@pytest.mark.parametrize("student_id", ["", " ", "a123", "１２３４", "123@school.test", "1 2", "1" * 33, 123])
def test_invalid_student_ids_rejected(client, student_id):
    body = {"student_id": student_id, "password": "student-password-123"}
    assert client.post("/api/auth/login", json=body).status_code == 422
    assert client.post("/api/auth/register", json={**body, "name": "测试"}).status_code == 422


@pytest.mark.parametrize("role", ["teacher", "admin"])
def test_staff_uses_same_student_id_contract(client, db, role):
    user = User(student_id="009001", name="教师", role=role, password_hash=hash_password("staff-password-123"))
    db.add(user)
    db.commit()
    login = client.post("/api/auth/login", json={"student_id": "009001", "password": "staff-password-123"})
    assert login.status_code == 200
    assert login.json()["user"]["role"] == role


def test_unassigned_legacy_user_cannot_use_a_session(client, db):
    user = User(student_id=None, name="待分配", role="student", password_hash=hash_password("legacy-password-123"))
    db.add(user)
    db.commit()
    token = issue(db, user)["access_token"]
    assert client.get("/api/auth/me", headers={"Authorization": "Bearer " + token}).status_code == 401


def test_cli_assigns_real_student_id_without_overwriting(db, monkeypatch):
    import sys
    from contextlib import nullcontext
    from app import cli
    user = User(student_id=None, name="历史用户", role="student", password_hash=hash_password("legacy-password-123"))
    db.add(user)
    db.commit()
    monkeypatch.setattr(cli, "SessionLocal", lambda: nullcontext(db))
    monkeypatch.setattr(sys, "argv", ["cli", "assign-student-id", "--user-id", str(user.id), "--student-id", "001234"])
    cli.main()
    assert user.student_id == "001234"
    with pytest.raises(SystemExit):
        cli.main()
    assert user.student_id == "001234"


def test_cli_rejects_invalid_and_duplicate_ids(db, monkeypatch):
    import sys
    from contextlib import nullcontext
    from app import cli
    monkeypatch.setattr(cli, "SessionLocal", lambda: nullcontext(db))
    monkeypatch.setenv("TEST_ACCOUNT_PASSWORD", "test-account-password")
    args = ["cli", "provision-user", "--password-env", "TEST_ACCOUNT_PASSWORD", "--student-id"]
    monkeypatch.setattr(sys, "argv", [*args, "old@example.test"])
    with pytest.raises(SystemExit):
        cli.main()
    monkeypatch.setattr(sys, "argv", [*args, "000321"])
    cli.main()
    with pytest.raises(SystemExit):
        cli.main()


def test_existing_database_upgrade_preserves_users_and_learning(tmp_path):
    from sqlalchemy import text, inspect
    url = "sqlite:///" + (tmp_path / "identity.sqlite3").as_posix()
    migrate(url, "upgrade", "221d0f8caf6e")
    engine = sqlite_engine(tmp_path / "identity.sqlite3")
    populate_baseline(engine)
    migrate(url, "upgrade", "f4b92c71a806")
    with engine.connect() as conn:
        before = {table: conn.execute(text(f'SELECT count(*) FROM "{table}"')).scalar()
                  for table in inspect(engine).get_table_names() if table not in {"auth_sessions", "alembic_version"}}
    migrate(url, "upgrade", "head")
    migrate(url, "check")
    with engine.connect() as conn:
        assert conn.execute(text("SELECT count(*) FROM auth_sessions")).scalar() == 0
        assert conn.execute(text("SELECT count(*) FROM users WHERE student_id IS NULL")).scalar() == 2
        assert "email" not in {c["name"] for c in inspect(engine).get_columns("users")}
        assert not conn.execute(text("PRAGMA foreign_key_check")).all()
        for table, count in before.items():
            assert conn.execute(text(f'SELECT count(*) FROM "{table}"')).scalar() == count
    with engine.begin() as conn:
        conn.execute(text("UPDATE users SET student_id='001234' WHERE id=1"))
        assert conn.execute(select(User.student_id).where(User.id == 1)).scalar() == "001234"
    engine.dispose()
