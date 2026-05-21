"""演示班级与账号注入脚本。

通过真实 backend REST API 注册账号、创建班级、学生加入、教师发布任务。
脚本幂等：相同 username 第二次运行会跳过注册并继续走加入流程。
"""

from __future__ import annotations

import json
import os
import secrets
import string
import sys
import urllib.error
import urllib.request
from pathlib import Path


HERE = Path(__file__).parent
API_BASE = os.environ.get("PYT_API_BASE", "http://127.0.0.1:18080").rstrip("/")
TEACHER_INVITE = os.environ.get("PYT_TEACHER_INVITE_CODE", "pytopia-teacher")


def _request(method: str, path: str, *, token: str | None = None, body: dict | None = None) -> dict:
    url = f"{API_BASE}{path}"
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            payload = resp.read()
            return json.loads(payload) if payload else {}
    except urllib.error.HTTPError as ex:  # noqa: PERF203
        try:
            err_payload = ex.read().decode("utf-8")
        except Exception:  # noqa: BLE001
            err_payload = ex.reason
        raise SystemExit(f"[seed] {method} {path} -> {ex.code} {err_payload}") from ex


def _random_password() -> str:
    alphabet = string.ascii_letters + string.digits
    return "Demo!" + "".join(secrets.choice(alphabet) for _ in range(10))


def register(username: str, display_name: str, role: str, password: str) -> None:
    body = {
        "username": username,
        "password": password,
        "displayName": display_name,
        "role": role,
    }
    if role == "TEACHER":
        body["teacherInviteCode"] = TEACHER_INVITE
    try:
        _request("POST", "/api/auth/register", body=body)
        print(f"[seed] 新注册 {role}: {username}")
    except SystemExit as ex:
        if "USERNAME_TAKEN" in str(ex) or "DUPLICATE" in str(ex) or "409" in str(ex):
            print(f"[seed] {username} 已存在，跳过注册")
        else:
            raise


def login(username: str, password: str) -> str:
    payload = _request("POST", "/api/auth/login", body={"username": username, "password": password})
    return payload["token"]


def ensure_classroom(token: str, classroom_payload: dict) -> int:
    list_payload = _request("GET", "/api/teacher/classrooms", token=token)
    for entry in list_payload:
        if entry["name"] == classroom_payload["name"]:
            return entry["id"]
    created = _request(
        "POST",
        "/api/teacher/classrooms",
        token=token,
        body={
            "name": classroom_payload["name"],
            "description": classroom_payload["description"],
            "packId": classroom_payload["packId"],
        },
    )
    return created["id"]


def get_join_code(token: str, classroom_id: int) -> str:
    detail = _request("GET", f"/api/teacher/classrooms/{classroom_id}", token=token)
    return detail["joinCode"]


def join_classroom(token: str, code: str) -> None:
    try:
        _request("POST", "/api/classrooms/join", token=token, body={"joinCode": code})
    except SystemExit as ex:
        if "ALREADY" in str(ex) or "JOINED" in str(ex):
            return
        raise


def publish_quest(token: str, classroom_id: int, quest: dict) -> None:
    body = {
        "title": quest["title"],
        "description": quest["description"],
        "kcCodes": quest["kcCodes"],
        "nodeType": quest["nodeType"],
        "targetProblemId": quest.get("targetProblemId"),
        "targetResourceType": quest.get("targetResourceType"),
        "dueOffsetDays": quest["dueOffsetDays"],
    }
    _request(
        "POST",
        f"/api/teacher/classrooms/{classroom_id}/quests",
        token=token,
        body=body,
    )


def main() -> int:
    users = json.loads((HERE / "demo_users.json").read_text(encoding="utf-8"))
    classroom_payload = json.loads((HERE / "demo_classroom.json").read_text(encoding="utf-8"))
    quests = json.loads((HERE / "demo_quests.json").read_text(encoding="utf-8"))

    teacher_password = _random_password()
    student_password = _random_password()

    teacher = users["teacher"]
    register(teacher["username"], teacher["displayName"], "TEACHER", teacher_password)
    teacher_token = login(teacher["username"], teacher_password)
    classroom_id = ensure_classroom(teacher_token, classroom_payload)
    code = get_join_code(teacher_token, classroom_id)
    print(f"[seed] 班级 ID = {classroom_id}, join code = {code}")

    for student in users["students"]:
        register(student["username"], student["displayName"], "STUDENT", student_password)
        stu_token = login(student["username"], student_password)
        join_classroom(stu_token, code)
    for quest in quests:
        publish_quest(teacher_token, classroom_id, quest)
    print("[seed] 演示账号与班级任务就绪。")
    print(f"[seed] 教师演示密码 (一次性，仅本次输出): {teacher_password}")
    print(f"[seed] 学生演示密码 (一次性，仅本次输出): {student_password}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
