"""Practice endpoints and durable, bounded Judge0 reconciliation."""

from __future__ import annotations

import asyncio
import json
import logging
from typing import Annotated, Literal
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select, update
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.orm import Session
from .auth import as_utc, current_user
from .db import SessionLocal, get_db
from .integration_models import SubmissionPayload
from .judge import JudgeAdapter, JudgeSubmissionUnknown, JudgeUnavailable
from .models import Classroom, QuestionSecret, Submission, User

router = APIRouter(tags=["practice"])


class SubmissionInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    activity_id: str = Field(max_length=100)
    question_id: str = Field(max_length=100)
    idempotency_key: str = Field(min_length=8, max_length=100)
    mode: Literal["submit"] = "submit"
    answer: dict | list | str | bool | None = None
    code: str | None = Field(default=None, max_length=20000)


def public_submission(row: Submission) -> dict:
    return {
        "id": row.id,
        "activity_id": row.activity_id,
        "question_id": row.question_id,
        "mode": row.mode,
        "status": row.status,
        "feedback": row.feedback,
        "created_at": as_utc(row.created_at).isoformat(),
    }


def grade_answer(answer, expected, kind: str) -> bool:
    if isinstance(expected, dict) and "value" in expected:
        expected = expected["value"]
    if kind == "boolean":
        return isinstance(answer, bool) and isinstance(expected, bool) and answer is expected
    if kind == "fill":
        if not isinstance(answer, str):
            return False
        accepted = expected if isinstance(expected, list) else [expected]
        return answer.strip().casefold() in [str(x).strip().casefold() for x in accepted]
    return type(answer) is type(expected) and answer == expected


def stage_submission(db, user, classroom_id, activity, question, answer, code, key, mode):
    """Stage existing grading evidence inside the caller's atomic transaction."""
    secret = db.get(QuestionSecret, question.id)
    if not secret:
        raise HTTPException(503, "题目尚未配置评分规则")
    row = Submission(user_id=user.id, classroom_id=classroom_id, activity_id=activity.id,
        question_id=question.id, idempotency_key=key, mode=mode, answer=answer, code=code)
    if question.kind == "python":
        if not code or not code.strip():
            raise HTTPException(422, "请填写代码")
        cases = list(question.samples)
        public_count = len(cases)
        cases += list(secret.hidden_cases)
        if not cases or len(cases) > 20:
            raise HTTPException(503, "题目未配置有效且有界的测试用例")
        row.status, row.feedback = "prepared", {"detail": "已保存，等待 Judge0"}
    else:
        row.status = "accepted" if grade_answer(answer, secret.answer, question.kind) else "wrong_answer"
        row.feedback = {"detail": "回答正确" if row.status == "accepted" else "请结合课件重新思考", "status": row.status}
    db.add(row)
    db.flush()
    if question.kind == "python":
        db.add(SubmissionPayload(submission_id=row.id, cases=cases, public_count=public_count))
    return row


@router.post("/submissions", status_code=201)
def create_submission(body: SubmissionInput, user: Annotated[User, Depends(current_user)], db: Annotated[Session, Depends(get_db)]):
    from .courses import authorize_activity, learning_questions

    activity = authorize_activity(db, user, body.classroom_id, body.activity_id)
    if user.role != "student":
        raise HTTPException(403, "教师预览不能写入学生成绩")
    query = select(Submission).where(
        Submission.user_id == user.id, Submission.classroom_id == body.classroom_id, Submission.idempotency_key == body.idempotency_key
    )
    existing = db.scalar(query)
    if existing:
        if (existing.activity_id, existing.question_id, existing.mode, existing.answer, existing.code) != (
            body.activity_id,
            body.question_id,
            body.mode,
            body.answer,
            body.code,
        ):
            raise HTTPException(409, "幂等键已用于不同提交")
        return public_submission(existing)
    classroom = db.get(Classroom, body.classroom_id)
    question = next((item for item in learning_questions(db, activity, classroom.teacher_id) if item.id == body.question_id), None)
    if not question:
        raise HTTPException(404, "题目不属于当前活动")
    db.execute(select(User.id).where(User.id == user.id).with_for_update())
    if activity.kind == "assessment":
        raise HTTPException(409, "测验需要完成所有题目后统一提交试卷")
    try:
        row = stage_submission(db, user, body.classroom_id, activity, question, body.answer, body.code, body.idempotency_key, body.mode)
        db.commit()
    except IntegrityError:
        db.rollback()
        existing = db.scalar(query)
        if not existing:
            raise
        if (existing.activity_id, existing.question_id, existing.mode, existing.answer, existing.code) != (
            body.activity_id,
            body.question_id,
            body.mode,
            body.answer,
            body.code,
        ):
            raise HTTPException(409, "幂等键已用于不同提交")
        return public_submission(existing)
    return public_submission(row)


@router.get("/submissions/{submission_id}")
def get_submission(submission_id: str, classroom_id: int, user: Annotated[User, Depends(current_user)], db: Annotated[Session, Depends(get_db)]):
    from .courses import authorize_activity

    row = db.get(Submission, submission_id)
    if not row or row.user_id != user.id or row.classroom_id != classroom_id:
        raise HTTPException(404, "提交不存在")
    authorize_activity(db, user, classroom_id, row.activity_id)
    return public_submission(row)


def _claim_submission(submission_id):
    with SessionLocal() as db:
        changed = db.execute(update(Submission).where(Submission.id == submission_id, Submission.status == "prepared").values(status="dispatching"))
        db.commit()
        if not changed.rowcount:
            return None
        row = db.get(Submission, submission_id)
        payload = db.get(SubmissionPayload, submission_id)
        return row.code, payload.cases


def _save_dispatch(submission_id, tokens, status, feedback):
    with SessionLocal() as db:
        row = db.get(Submission, submission_id)
        row.status, row.feedback = status, feedback
        row.judge_token = json.dumps(tokens) if tokens else None
        db.commit()


async def dispatch_one(submission_id: str, adapter: JudgeAdapter | None = None):
    claimed = await asyncio.to_thread(_claim_submission, submission_id)
    if claimed is None:
        return
    code, cases = claimed
    try:
        tokens = await (adapter or JudgeAdapter()).submit(code, cases)
        status, feedback = "queued", {"detail": "Judge0 已受理"}
    except JudgeUnavailable as exc:
        tokens, status, feedback = [], "infrastructure_error", {"detail": str(exc)}
    except JudgeSubmissionUnknown as exc:
        tokens, status, feedback = [], "submission_unknown", {"detail": str(exc)}
    await asyncio.to_thread(_save_dispatch, submission_id, tokens, status, feedback)


def _read_pending(submission_id):
    with SessionLocal() as db:
        row = db.get(Submission, submission_id)
        if not row or not row.judge_token or row.status not in {"queued", "running"}:
            return None
        payload = db.get(SubmissionPayload, row.id)
        return json.loads(row.judge_token), payload.public_count


def _save_result(submission_id, result):
    with SessionLocal() as db:
        row = db.get(Submission, submission_id)
        if row.status in {"queued", "running"}:
            row.status, row.feedback = result.status, result.feedback
            db.commit()


async def poll_one(submission_id: str, adapter: JudgeAdapter | None = None):
    pending = await asyncio.to_thread(_read_pending, submission_id)
    if pending is None:
        return
    tokens, public_count = pending
    try:
        result = await (adapter or JudgeAdapter()).poll(tokens, public_count)
    except JudgeUnavailable:
        return  # The durable token survives service outages; no WA or resubmission.
    await asyncio.to_thread(_save_result, submission_id, result)


def _pending_ids():
    with SessionLocal() as db:
        prepared = list(db.scalars(select(Submission.id).where(Submission.status == "prepared").limit(20)))
        pending = list(db.scalars(select(Submission.id).where(Submission.status.in_(["queued", "running"])).limit(20)))
        return prepared, pending


async def reconcile_once():
    prepared, pending = await asyncio.to_thread(_pending_ids)
    for submission_id in prepared:
        await dispatch_one(submission_id)
    # Bound poll concurrency independently from page/API traffic.
    semaphore = asyncio.Semaphore(4)

    async def poll(submission_id):
        async with semaphore:
            await poll_one(submission_id)

    await asyncio.gather(*(poll(sid) for sid in pending))


def _recover_dispatching():
    # Never replay a POST that could have reached Judge0 before a DB outage/crash.
    with SessionLocal() as db:
        db.execute(
            update(Submission)
            .where(Submission.status == "dispatching")
            .values(status="submission_unknown", feedback={"detail": "服务中断时送交状态不确定，需要管理员对账；不会自动重复提交"})
        )
        db.commit()


async def judge_worker():
    recovered, delay = False, 1
    while True:
        try:
            if not recovered:
                await asyncio.to_thread(_recover_dispatching)
                recovered = True
            await reconcile_once()
            delay = 1
        except SQLAlchemyError:
            # Log no connection string, credentials, source code or hidden tests.
            logging.getLogger(__name__).warning("Judge worker database unavailable; retrying in %ss", delay)
            recovered = False
            await asyncio.sleep(delay)
            delay = min(delay * 2, 30)
            continue
        await asyncio.sleep(2)
