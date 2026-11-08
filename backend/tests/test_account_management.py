import io
import pytest
from openpyxl import Workbook
from sqlalchemy import select
from app.auth import hash_password, verify_password
from app.bootstrap import bootstrap_admin
from app.models import AuthSession, Membership, User
from app.student_accounts import parse_table


def test_bootstrap_admin_once_and_collision(db, monkeypatch):
    monkeypatch.setenv("REBORN_ADMIN_STUDENT_ID", "000900")
    monkeypatch.setenv("REBORN_ADMIN_PASSWORD", "first-admin-password")
    assert bootstrap_admin(db)
    db.commit()
    user = db.scalar(select(User).where(User.student_id == "000900"))
    assert user.role == "admin" and verify_password("first-admin-password", user.password_hash)
    monkeypatch.setenv("REBORN_ADMIN_PASSWORD", "different-password")
    assert not bootstrap_admin(db)
    assert verify_password("first-admin-password", user.password_hash)


def test_bootstrap_rejects_existing_student_and_invalid_config(db, student, monkeypatch):
    monkeypatch.setenv("REBORN_ADMIN_STUDENT_ID", student["student_id"])
    monkeypatch.setenv("REBORN_ADMIN_PASSWORD", "initial-admin-password")
    with pytest.raises(ValueError, match="占用"):
        bootstrap_admin(db)
    assert db.get(User, student["id"]).role == "student"
    monkeypatch.setenv("REBORN_ADMIN_PASSWORD", "short")
    with pytest.raises(ValueError, match="配置无效"):
        bootstrap_admin(db)


@pytest.mark.parametrize("role", ["student", "teacher"])
def test_self_password_and_session_revocation(client, db, student, teacher, role):
    account = student if role == "student" else teacher
    password = role + "-password-123"
    bad = client.post("/api/auth/password", headers=account["headers"], json={"current_password": "wrong", "new_password": "new-password-456"})
    assert bad.status_code == 400
    result = client.post("/api/auth/password", headers=account["headers"], json={"current_password": password, "new_password": "new-password-456"})
    assert result.status_code == 200, result.text
    assert client.get("/api/auth/me", headers=account["headers"]).status_code == 401
    assert client.post("/api/auth/login", json={"student_id": account["student_id"], "password": password}).status_code == 401
    assert client.get("/api/auth/me", headers={"Authorization": "Bearer " + result.json()["access_token"]}).status_code == 200


def test_provision_reset_force_change_and_remove(client, db, teacher, classroom):
    base = f"/api/teacher/classrooms/{classroom['id']}/students"
    body = {"students": [{"student_id": "00012", "name": "新同学"}]}
    assert client.post(base, headers=teacher["headers"], json=body).json()["created"] == 1
    assert client.post(base, headers=teacher["headers"], json=body).json()["existing"] == 1
    login = client.post("/api/auth/login", json={"student_id": "00012", "password": "00012"}).json()
    headers = {"Authorization": "Bearer " + login["access_token"]}
    assert login["user"]["must_change_password"]
    assert client.get("/api/classrooms", headers=headers).status_code == 403
    result = client.post("/api/auth/password", headers=headers, json={"current_password": "00012", "new_password": "private-password-123"})
    assert result.status_code == 200
    headers = {"Authorization": "Bearer " + result.json()["access_token"]}
    assert client.get("/api/classrooms", headers=headers).status_code == 200
    reset = {"student_ids": ["00012"], "action": "reset-password"}
    assert client.post(base + "/batch", headers=teacher["headers"], json=reset).json()["affected"] == 1
    assert client.get("/api/auth/me", headers=headers).status_code == 401
    assert client.post("/api/auth/login", json={"student_id": "00012", "password": "private-password-123"}).status_code == 401
    login = client.post("/api/auth/login", json={"student_id": "00012", "password": "00012"}).json()
    assert login["user"]["must_change_password"]
    assert client.post(base + "/batch", headers=teacher["headers"], json={**reset, "action": "remove"}).status_code == 200
    assert db.get(User, login["user"]["id"])
    assert not db.get(Membership, (login["user"]["id"], classroom["id"]))


def test_account_management_permissions_and_atomicity(client, db, teacher, student, classroom):
    outsider = User(student_id="00099", name="其他班学生", password_hash=hash_password("private-password-123"), role="student")
    other_teacher = User(student_id="00098", name="其他老师", password_hash=hash_password("private-password-123"), role="teacher")
    db.add_all([outsider, other_teacher])
    db.commit()
    base = f"/api/teacher/classrooms/{classroom['id']}/students"
    body = {"students": [{"student_id": "00013", "name": "不应创建"}, {"student_id": "00099", "name": "已有"}]}
    assert client.post(base, headers=teacher["headers"], json=body).status_code == 422
    assert not db.scalar(select(User).where(User.student_id == "00013"))
    assert client.post(base, headers=student["headers"], json=body).status_code == 403
    login = client.post("/api/auth/login", json={"student_id": "00098", "password": "private-password-123"}).json()
    assert client.get(base, headers={"Authorization": "Bearer " + login["access_token"]}).status_code == 403
    original = db.get(User, student["id"]).password_hash
    assert (
        client.post(
            base + "/batch", headers=teacher["headers"], json={"student_ids": [student["student_id"], "00099"], "action": "reset-password"}
        ).status_code
        == 403
    )
    assert db.get(User, student["id"]).password_hash == original
    assert db.scalar(select(AuthSession).where(AuthSession.user_id == student["id"]))


def test_import_preview_and_invalid_rows(client, teacher, classroom):
    path = f"/api/teacher/classrooms/{classroom['id']}/students/import-preview"
    content = "学号,姓名\n000123,小明\n000124,小红\n".encode("utf-8-sig")
    result = client.post(path, headers=teacher["headers"], files={"file": ("students.csv", content, "text/csv")})
    assert result.status_code == 200, result.text
    assert result.json()["students"][0]["student_id"] == "000123"
    assert client.get(path.removesuffix("/import-preview"), headers=teacher["headers"]).json().__len__() == 1
    for content in ["学号,姓名\n12,甲\n12,乙", "学号,姓名\nwrong,甲", "邮箱,姓名\na,甲"]:
        assert client.post(path, headers=teacher["headers"], files={"file": ("students.csv", content.encode())}).status_code == 422


def workbook_bytes(value):
    workbook = Workbook()
    sheet = workbook.active
    sheet.append(["学号", "姓名"])
    sheet.append([value, "同学"])
    buffer = io.BytesIO()
    workbook.save(buffer)
    workbook.close()
    return buffer.getvalue()


def test_excel_preserves_text_and_rejects_formulas_numbers_limits():
    assert parse_table("students.xlsx", workbook_bytes("000123"))[0].student_id == "000123"
    for value in [123, "=1+2"]:
        with pytest.raises(ValueError):
            parse_table("students.xlsx", workbook_bytes(value))
    with pytest.raises(ValueError, match="500"):
        parse_table("students.csv", ("学号,姓名\n" + "12,同学\n" * 501).encode())
    with pytest.raises(ValueError, match="2MB"):
        parse_table("students.csv", b"x" * (2 * 1024 * 1024 + 1))
