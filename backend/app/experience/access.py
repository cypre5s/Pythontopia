"""Restrict developer experience tools to the authorized isolated identity."""

from fastapi import HTTPException
from sqlalchemy import select

from app.auth import preview_classroom
from app.config import dev_tools_enabled
from app.course_scope import instance_id, lock_course
from app.dev_tools import PENDING_SUBMISSIONS
from app.integration_models import AssistantCall
from app.models import Membership, Submission, TownPreviewStudent, User


def guard(db, user, classroom_id, request, *, mutate=False):
    if request.url.path.startswith("/api/dev/") and not dev_tools_enabled():
        raise HTTPException(404, "开发工具未启用")
    lock_course(db, classroom_id, exclusive=mutate)
    actor = db.scalar(select(User).where(User.id == user.id).with_for_update()) if mutate else user
    if not actor or actor.role != "student":
        raise HTTPException(403, "仅允许操作自己的体验记录")
    preview = db.get(TownPreviewStudent, user.id)
    if request.url.path.startswith("/api/teacher-town/"):
        if not preview or preview_classroom(db, preview).id != classroom_id:
            raise HTTPException(403, "只能操作本人当前班级的教师体验存档")
    elif not dev_tools_enabled() or not db.get(Membership, (user.id, classroom_id)):
        raise HTTPException(403, "开发工具仅对开发环境中已加入的班级开放")
    instance_id(db, classroom_id)
    if mutate:
        if db.scalar(
            select(Submission.id)
            .where(Submission.user_id == user.id, Submission.classroom_id == classroom_id, Submission.status.in_(PENDING_SUBMISSIONS))
            .with_for_update()
            .limit(1)
        ):
            raise HTTPException(409, "仍有未完成或结果待确认的判题提交")
        if db.scalar(
            select(AssistantCall.id)
            .where(
                AssistantCall.user_id == user.id,
                AssistantCall.classroom_id == classroom_id,
                AssistantCall.status.in_(["running", "cancel_requested"]),
            )
            .limit(1)
        ):
            raise HTTPException(409, "助教仍在回复，请结束后再操作")
