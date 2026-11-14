"""Teacher-only evidence and administrator controls for the isolated executor."""
from __future__ import annotations

import asyncio
import os
import time
from datetime import datetime
from typing import Annotated

import httpx
from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import func, or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from .auth import as_utc, require_teacher
from .classrooms import own_classroom
from .db import SessionLocal, get_db
from .integration_models import JudgeService, SubmissionDiagnostic, SubmissionPayload
from .models import Activity, Question, Submission, User, now

router = APIRouter(prefix="/teacher/judge", tags=["judge-management"])
Teacher = Annotated[User, Depends(require_teacher)]
Database = Annotated[Session, Depends(get_db)]
STATUSES = {"prepared", "dispatching", "accepted", "wrong_answer", "runtime_error", "compile_error",
            "time_limit", "memory_limit", "infrastructure_error", "submission_unknown", "queued", "running"}


def time_range(since: datetime | None = None, until: datetime | None = None):
    if (since is None) != (until is None):
        raise HTTPException(422, "请同时提供开始与结束时间")
    if since is not None:
        if since.tzinfo is None or until.tzinfo is None:
            raise HTTPException(422, "筛选时间必须包含时区")
        since, until = as_utc(since), as_utc(until)
        if since >= until:
            raise HTTPException(422, "结束时间必须晚于开始时间")
    return since, until


DateRange = Annotated[tuple, Depends(time_range)]


def within_period(query, period):
    since, until = period
    return query.where(Submission.created_at >= since, Submission.created_at < until) if since is not None else query


def events_in_period(events: list, period):
    since, until = period
    if since is None:
        return events
    return [item for item in events if since <= as_utc(datetime.fromisoformat(item["at"])) < until]


def service_row(db: Session) -> JudgeService:
    row = db.get(JudgeService, 1, with_for_update=True)
    if row is None:
        try:
            with db.begin_nested():
                db.add(JudgeService(id=1, paused=False, health={}, events=[]))
                db.flush()
        except IntegrityError:
            pass
        row = db.get(JudgeService, 1, with_for_update=True)
    return row


def event(row: JudgeService, message: str):
    row.events = [*row.events[-49:], {"at": now().isoformat(), "message": message}]


async def probe_service(transport=None) -> dict:
    """No submitted code, file contents, host paths or credentials in health responses."""
    base_url = os.getenv("REBORN_GO_JUDGE_URL", "").rstrip("/")
    if not base_url:
        return {"status": "unavailable", "detail": "尚未配置评测服务"}
    start = time.monotonic()
    try:
        async with httpx.AsyncClient(base_url=base_url, transport=transport, trust_env=False, timeout=2,
                                     headers={"Authorization": "Bearer " + os.getenv("REBORN_GO_JUDGE_AUTH_TOKEN", "")}) as client:
            response = await client.get("/config")
            response.raise_for_status()
            config = response.json()["runnerConfig"]
            # /config is public; a protected read also verifies our execution credential.
            auth = await client.get("/file")
            auth.raise_for_status()
        safe = (config["cgroupType"] in (1, 2) and config["seccomp"] is True and isinstance(config["uid"], int) and config["uid"] > 0
                and {"cpu", "memory", "pids"}.issubset(config["cgroupControllers"]))
        return {"status": "healthy" if safe else "unsafe", "detail": "连接与隔离检查通过" if safe else "隔离配置检查未通过，已停止派发",
                "latency_ms": round((time.monotonic() - start) * 1000), "seccomp": config.get("seccomp") is True,
                "cgroup_type": config["cgroupType"] if config["cgroupType"] in (1, 2) else None}
    except httpx.HTTPStatusError as exc:
        return {"status": "unavailable", "detail": "评测服务认证失败" if exc.response.status_code in (401, 403) else "评测服务响应异常"}
    except (httpx.HTTPError, ValueError, KeyError, TypeError, AttributeError):
        return {"status": "unavailable", "detail": "无法连接评测服务或响应无效"}


def save_health(health: dict, *, worker=False):
    with SessionLocal() as db:
        row = service_row(db)
        if row.health.get("status") != health["status"]:
            event(row, health["detail"])
        row.health, row.checked_at = health, now()
        if worker:
            row.worker_seen_at = now()
        db.commit()


def queue_counts(db: Session, classroom_id: int | None = None, period=(None, None)):
    query = (select(Submission.status, func.count()).join(Question, Submission.question_id == Question.id)
             .where(python_submission()))
    if classroom_id is not None:
        query = query.where(Submission.classroom_id == classroom_id)
    return dict(db.execute(within_period(query, period).group_by(Submission.status).execution_options(include_deleted=True)).all())


@router.get("/service")
def get_service(classroom_id: int, user: Teacher, db: Database, period: DateRange):
    own_classroom(db, user, classroom_id)
    row = db.get(JudgeService, 1)
    heartbeat = row.worker_seen_at if row else None
    return {"paused": bool(row and row.paused), "health": row.health if row else {},
            "checked_at": row.checked_at if row else None, "worker_seen_at": heartbeat,
            "worker_alive": bool(heartbeat and (now() - as_utc(heartbeat)).total_seconds() < 180),
            "counts": queue_counts(db, classroom_id, period),
            "global_counts": queue_counts(db) if user.role == "admin" else None,
            "events": events_in_period(row.events, period) if row and user.role == "admin" else []}


class ControlInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    paused: bool
    reason: str = Field(min_length=1, max_length=200)


@router.put("/service")
def control_service(body: ControlInput, user: Teacher, db: Database):
    if user.role != "admin":
        raise HTTPException(403, "只有管理员可以管理全局评测服务")
    reason = body.reason.strip()
    if not reason:
        raise HTTPException(422, "请填写操作原因")
    row = service_row(db)
    row.paused = body.paused
    event(row, f"{user.name} {'暂停' if body.paused else '恢复'}派发：{reason}")
    db.commit()
    return {"paused": row.paused}


@router.post("/service/probe")
async def check_service(user: Teacher):
    if user.role != "admin":
        raise HTTPException(403, "只有管理员可以主动检测全局评测服务")
    health = await probe_service()
    await asyncio.to_thread(save_health, health)
    return health


def summary(row: Submission, student: User, question: Question, activity: Activity):
    return {"id": row.id, "student_name": student.name, "student_id": student.student_id,
            "question_title": question.title, "activity_title": activity.title, "status": row.status,
            "created_at": row.created_at, "updated_at": row.updated_at,
            "passed_count": row.feedback.get("passed_count"), "total_count": row.feedback.get("total_count")}


def submissions_query(classroom_id: int):
    return (select(Submission, User, Question, Activity).join(User, Submission.user_id == User.id)
            .join(Question, Submission.question_id == Question.id).join(Activity, Submission.activity_id == Activity.id)
            .where(Submission.classroom_id == classroom_id, python_submission()).execution_options(include_deleted=True))


def python_submission():
    # Payload is historical evidence even if the teacher later changes the question kind.
    # Legacy failures may predate payload storage; never count concept answers with code="".
    return or_(select(SubmissionPayload.submission_id).where(SubmissionPayload.submission_id == Submission.id).exists(),
               (Question.kind == "python") & Submission.code.is_not(None) & (Submission.code != ""))


@router.get("/submissions")
def list_submissions(classroom_id: int, user: Teacher, db: Database, period: DateRange, q: str = Query(default="", max_length=100),
                     status: str = "", page: int = Query(default=1, ge=1), size: int = Query(default=20, ge=1, le=100)):
    own_classroom(db, user, classroom_id)
    query = within_period(submissions_query(classroom_id), period)
    if status:
        if status not in STATUSES:
            raise HTTPException(422, "未知评测状态")
        query = query.where(Submission.status == status)
    if q.strip():
        query = query.where(or_(User.name.contains(q.strip(), autoescape=True), User.student_id.contains(q.strip(), autoescape=True),
                                Question.title.contains(q.strip(), autoescape=True), Submission.id == q.strip()))
    total = db.scalar(select(func.count()).select_from(query.subquery()).execution_options(include_deleted=True))
    rows = db.execute(query.order_by(Submission.created_at.desc(), Submission.id).offset((page - 1) * size).limit(size)).all()
    return {"items": [summary(*row) for row in rows], "total": total}


@router.get("/submissions/{submission_id}")
def submission_detail(submission_id: str, classroom_id: int, user: Teacher, db: Database):
    own_classroom(db, user, classroom_id)
    found = db.execute(submissions_query(classroom_id).where(Submission.id == submission_id)).first()
    if not found:
        raise HTTPException(404, "提交不存在")
    row = found[0]
    payload = db.get(SubmissionPayload, row.id)
    diagnostic = db.get(SubmissionDiagnostic, row.id)
    recorded = {case["index"]: case for case in diagnostic.cases} if diagnostic else {}
    # Older receipts can contain genuine public output even without private diagnostics.
    samples = row.feedback.get("samples", [])
    if not diagnostic and payload and isinstance(samples, list):
        for index, sample in enumerate(samples[:payload.public_count]):
            if not isinstance(sample, dict) or sample.get("status") not in STATUSES:
                continue
            recorded[index] = {"index": index, "status": sample["status"], "source": "public_receipt",
                               "stdout": str(sample.get("stdout", ""))[:16384], "stderr": str(sample.get("stderr", ""))[:8192],
                               "time_ns": None, "memory_bytes": None, "output_truncated": False}
    cases = [{"index": index, "public": index < payload.public_count, "input": str(case.get("input", "")),
              "expected_output": str(case.get("expected_output", case.get("output", ""))), "result": recorded.get(index)}
             for index, case in enumerate(payload.cases)] if payload else []
    return {**summary(*found), "code": row.code, "feedback": row.feedback, "cases": cases,
            "diagnostic": {"started_at": diagnostic.started_at, "finished_at": diagnostic.finished_at, "detail": diagnostic.detail}
            if diagnostic else None}
