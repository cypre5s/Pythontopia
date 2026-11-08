"""One authenticated answer sheet per classroom/activity, locked on hand-in."""
from hashlib import sha256
import json
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field, StrictBool, StrictStr
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from .auth import current_user
from .db import get_db
from .models import AssessmentAttempt, Question, Submission, User, now
from .practice import public_submission, stage_submission

router = APIRouter(tags=["assessments"])
ACCEPTED = {"accepted", "AC", "ac"}
GRADED = ACCEPTED | {"wrong_answer", "compile_error", "runtime_error", "time_limit", "memory_limit"}
PENDING = {"prepared", "dispatching", "queued", "running"}


def assessment_questions(db, activity):
    questions = list(db.scalars(select(Question).where(Question.activity_id == activity.id).order_by(Question.id)))
    configured = activity.content.get("question_ids")
    if configured is None:
        return questions
    mapping = {question.id: question for question in questions}
    return [mapping[key] for key in configured if key in mapping]


def _attempt(db, user_id, classroom_id, activity_id):
    return db.scalar(select(AssessmentAttempt).where(AssessmentAttempt.user_id == user_id,
        AssessmentAttempt.classroom_id == classroom_id, AssessmentAttempt.activity_id == activity_id)
        .execution_options(populate_existing=True))


def _latest_legacy(db, user_id, classroom_id, activity_id, question_ids):
    latest = {}
    for row in db.scalars(select(Submission).where(Submission.user_id == user_id, Submission.classroom_id == classroom_id,
            Submission.activity_id == activity_id, Submission.mode == "submit").order_by(Submission.created_at.desc(), Submission.id.desc())):
        if row.question_id in question_ids and row.question_id not in latest:
            latest[row.question_id] = row
    return latest


def _answers_from_rows(rows):
    return {key: {"answer": row.answer, "code": row.code} for key, row in rows.items()}


def assessment_payload(db, user, classroom_id, activity):
    questions = assessment_questions(db, activity)
    ids = [question.id for question in questions]
    attempt = _attempt(db, user.id, classroom_id, activity.id)
    legacy, rows = False, {}
    if attempt and attempt.state in {"submitted", "legacy"}:
        ids = attempt.question_ids
        rows = {key: db.get(Submission, sid) for key, sid in attempt.submission_ids.items()}
        locked, answers = True, attempt.answers
        legacy = attempt.state == "legacy"
    else:
        rows = _latest_legacy(db, user.id, classroom_id, activity.id, ids)
        pending = any(row.status not in GRADED for row in rows.values())
        locked = bool(rows) and (pending or len(rows) == len(ids))
        legacy = locked
        answers = attempt.answers if attempt else _answers_from_rows(rows)
        if locked:
            answers = _answers_from_rows(rows)
    if not locked:
        status, results = "draft", []
    else:
        statuses = [row.status for row in rows.values() if row]
        if len(statuses) == len(ids) and statuses and all(status in GRADED for status in statuses):
            status = "graded"
        elif any(value not in GRADED | PENDING for value in statuses) or len(statuses) != len(ids):
            status = "attention" if any(value not in GRADED | PENDING for value in statuses) else "grading"
        else:
            status = "grading"
        results = [public_submission(rows[key]) for key in ids if rows.get(key)]
    correct = sum(row is not None and row.status in ACCEPTED for row in rows.values()) if locked else 0
    return {"id": attempt.id if attempt else None, "status": status, "revision": attempt.revision if attempt else 0,
        "locked": locked, "legacy": legacy, "question_ids": ids,
        "answers": [{"question_id": key, **answers[key]} for key in ids if key in answers],
        "results": results, "correct_count": correct, "total": len(ids),
        "score": correct * 100 // len(ids) if status == "graded" else None, "max_score": 100, "completed": status == "graded",
        "passed": status == "graded" and correct == len(ids)}


class AnswerInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    question_id: str = Field(min_length=1, max_length=100)
    answer: dict | list | StrictStr | StrictBool | None = None
    code: str | None = Field(default=None, max_length=20000)


class DraftInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    revision: int = Field(strict=True, ge=0)
    answers: list[AnswerInput] = Field(max_length=100)


class HandInInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    idempotency_key: str = Field(min_length=8, max_length=100)
    answers: list[AnswerInput] = Field(max_length=100)


def _authorize(db, user, classroom_id, activity_id, *, write=False):
    from .courses import authorize_activity
    activity = authorize_activity(db, user, classroom_id, activity_id)
    if activity.kind != "assessment":
        raise HTTPException(422, "此活动不是测验")
    if write:
        if user.role != "student":
            raise HTTPException(403, "教师预览不能写入学生试卷")
        db.execute(select(User.id).where(User.id == user.id).with_for_update())
    return activity


def _normalize(entries, questions, *, complete=False):
    mapping = {question.id: question for question in questions}
    answers = {}
    for item in entries:
        if item.question_id not in mapping or item.question_id in answers:
            raise HTTPException(422, "试卷题目不匹配或重复")
        question = mapping[item.question_id]
        if item.answer is not None and len(json.dumps(item.answer, ensure_ascii=False)) > 20000:
            raise HTTPException(422, "单题答案过长")
        if complete:
            if question.kind == "python":
                valid = bool(item.code and item.code.strip())
            elif question.kind == "boolean":
                valid = type(item.answer) is bool
            elif question.kind == "fill":
                valid = isinstance(item.answer, str) and bool(item.answer.strip())
            else:
                valid = item.answer is not None and (not question.options or item.answer in question.options)
            if not valid:
                raise HTTPException(422, "请先完成所有题目再提交试卷")
        answers[item.question_id] = {"answer": item.answer, "code": item.code}
    if complete and (not mapping or set(answers) != set(mapping)):
        raise HTTPException(422, "请先完成所有题目再提交试卷")
    return answers


def _signature(answers):
    return json.dumps(answers, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


@router.get("/activities/{activity_id}/assessment")
def get_assessment(activity_id: str, classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    activity = _authorize(db, user, classroom_id, activity_id)
    payload = assessment_payload(db, user, classroom_id, activity)
    if user.role == "student" and payload["legacy"] and payload["completed"]:
        # Bind an existing complete historical sheet once; never recreate grades.
        # Other progress/world callers keep assessment_payload strictly read-only.
        db.execute(select(User.id).where(User.id == user.id).with_for_update())
        payload = assessment_payload(db, user, classroom_id, activity)
        attempt = _attempt(db, user.id, classroom_id, activity_id)
        if payload["legacy"] and payload["completed"] and (not attempt or attempt.state == "draft"):
            rows = _latest_legacy(db, user.id, classroom_id, activity_id, payload["question_ids"])
            if not attempt:
                attempt = AssessmentAttempt(user_id=user.id, classroom_id=classroom_id, activity_id=activity_id, revision=0)
                db.add(attempt)
            attempt.state, attempt.question_ids = "legacy", payload["question_ids"]
            attempt.answers = _answers_from_rows(rows)
            attempt.submission_ids = {key: row.id for key, row in rows.items()}
            attempt.idempotency_key = None
            attempt.revision += 1
            try:
                db.commit()
            except IntegrityError:
                db.rollback()
    return assessment_payload(db, user, classroom_id, activity)


@router.put("/activities/{activity_id}/assessment")
def save_assessment(activity_id: str, body: DraftInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    activity = _authorize(db, user, body.classroom_id, activity_id, write=True)
    payload = assessment_payload(db, user, body.classroom_id, activity)
    if payload["locked"]:
        raise HTTPException(409, "试卷已提交，不能修改答案")
    if body.revision != payload["revision"]:
        raise HTTPException(409, "试卷草稿已在其他页面更新，请重新读取")
    questions = assessment_questions(db, activity)
    answers = _normalize(body.answers, questions)
    attempt = _attempt(db, user.id, body.classroom_id, activity_id)
    if not attempt:
        attempt = AssessmentAttempt(user_id=user.id, classroom_id=body.classroom_id, activity_id=activity_id, revision=0)
        db.add(attempt)
    attempt.question_ids, attempt.answers = [question.id for question in questions], answers
    attempt.revision += 1
    try:
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        raise HTTPException(409, "试卷草稿已在其他页面更新，请重新读取") from exc
    return assessment_payload(db, user, body.classroom_id, activity)


@router.post("/activities/{activity_id}/assessment/submit", status_code=201)
def submit_assessment(activity_id: str, body: HandInInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    activity = _authorize(db, user, body.classroom_id, activity_id, write=True)
    attempt = _attempt(db, user.id, body.classroom_id, activity_id)
    if attempt and attempt.state in {"submitted", "legacy"}:
        retried = {entry.question_id: {"answer": entry.answer, "code": entry.code} for entry in body.answers}
        if (len(retried) == len(body.answers) and attempt.idempotency_key == body.idempotency_key
                and _signature(attempt.answers) == _signature(retried)):
            return assessment_payload(db, user, body.classroom_id, activity)
        raise HTTPException(409, "试卷已提交，不能再次交卷或修改答案")
    questions = assessment_questions(db, activity)
    answers = _normalize(body.answers, questions, complete=True)
    if assessment_payload(db, user, body.classroom_id, activity)["locked"]:
        raise HTTPException(409, "此测验已有正式记录，不能重复交卷")
    if not attempt:
        attempt = AssessmentAttempt(user_id=user.id, classroom_id=body.classroom_id, activity_id=activity_id, revision=0)
        db.add(attempt)
    attempt.state, attempt.answers = "submitted", answers
    attempt.question_ids = [question.id for question in questions]
    attempt.idempotency_key, attempt.submitted_at = body.idempotency_key, now()
    attempt.revision += 1
    try:
        db.flush()
        submissions = {}
        for question in questions:
            entry = answers[question.id]
            key = f"paper:{attempt.id}:{sha256(question.id.encode()).hexdigest()[:32]}"
            row = stage_submission(db, user, body.classroom_id, activity, question, entry["answer"], entry["code"], key, "submit")
            db.flush()
            submissions[question.id] = row.id
        attempt.submission_ids = submissions
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        attempt = _attempt(db, user.id, body.classroom_id, activity_id)
        if attempt and attempt.state == "submitted" and attempt.idempotency_key == body.idempotency_key and _signature(attempt.answers) == _signature(answers):
            return assessment_payload(db, user, body.classroom_id, activity)
        raise HTTPException(409, "试卷已经在另一请求中提交，请重新读取") from exc
    except Exception:
        db.rollback()
        raise
    return assessment_payload(db, user, body.classroom_id, activity)
