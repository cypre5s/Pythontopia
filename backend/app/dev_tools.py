"""Self-only replay for local development and isolated teacher town saves."""

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field, field_validator
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from .auth import current_user, preview_classroom
from .config import dev_tools_enabled
from .db import get_db
from .integration_models import AssistantCall, SubmissionPayload
from .models import (
    AssessmentAttempt,
    BuildingUnlock,
    ActivityProgress,
    LearningEvent,
    LearningRecord,
    Membership,
    NodeState,
    PlayerPosition,
    PlayerQuest,
    StorySession,
    Submission,
    TownPreviewStudent,
    User,
    WorldFlag,
)

router = APIRouter(prefix="/dev", tags=["development"])
preview_router = APIRouter(prefix="/teacher-town", tags=["town preview"])
PENDING_SUBMISSIONS = {"prepared", "dispatching", "queued", "running", "submission_unknown"}


def require_dev_tools():
    # Registration is checked at startup; retain a runtime guard as defense in depth.
    if not dev_tools_enabled():
        raise HTTPException(404, "开发工具未启用")


class ResetProgressInput(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    classroom_id: int = Field(gt=0)
    confirmed: bool

    @field_validator("confirmed")
    @classmethod
    def must_confirm(cls, value):
        if not value:
            raise ValueError("请先确认重新开始")
        return value


def _delete_rows(db, model, *conditions):
    result = db.execute(delete(model).where(*conditions).execution_options(synchronize_session=False))
    return result.rowcount


@router.post("/progress/reset", dependencies=[Depends(require_dev_tools)])
def reset_progress(
    body: ResetProgressInput,
    user: Annotated[User, Depends(current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    return reset_classroom_progress(body, user, db, town_preview=False)


@preview_router.post("/progress/reset")
def reset_preview_progress(
    body: ResetProgressInput,
    user: Annotated[User, Depends(current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    return reset_classroom_progress(body, user, db, town_preview=True)


def reset_classroom_progress(body: ResetProgressInput, user: User, db: Session, *, town_preview: bool):
    try:
        from .course_scope import lock_course
        lock_course(db, body.classroom_id)
        # Serialize repeated resets for this actor, and share the assistant reservation lock.
        actor = db.scalar(select(User).where(User.id == user.id).with_for_update().execution_options(populate_existing=True))
        if not actor or actor.role != "student":
            raise HTTPException(403, "只有学生可以重新开始自己的学习")
        if town_preview:
            preview = db.get(TownPreviewStudent, actor.id)
            if not preview or preview_classroom(db, preview).id != body.classroom_id:
                raise HTTPException(403, "只能重新开始本班的教师体验存档")
        else:
            membership = db.scalar(
                select(Membership).where(Membership.user_id == actor.id, Membership.classroom_id == body.classroom_id).with_for_update()
            )
            if not membership:
                raise HTTPException(403, "只能重新开始已加入班级的学习")

        user_id, classroom_id = actor.id, body.classroom_id
        # Lock existing results while checking: a worker must not dispatch or finish a deleted row.
        statuses = db.execute(
            select(Submission.id, Submission.status)
            .where(Submission.user_id == user_id, Submission.classroom_id == classroom_id)
            .with_for_update()
        ).all()
        if any(status in PENDING_SUBMISSIONS for _, status in statuses):
            raise HTTPException(409, "还有未完成或状态待确认的判题提交，请处理完成后再重新开始")
        assistant_running = db.scalar(
            select(AssistantCall.id).where(
                AssistantCall.user_id == user_id,
                AssistantCall.classroom_id == classroom_id,
                AssistantCall.status.in_(["running", "cancel_requested"]),
            ).limit(1)
        )
        if assistant_running:
            raise HTTPException(409, "助教仍在回复，请结束回复后再重新开始")

        quest_ids = select(PlayerQuest.id).where(PlayerQuest.user_id == user_id, PlayerQuest.classroom_id == classroom_id)
        submission_ids = [submission_id for submission_id, _ in statuses]
        deleted = {}
        for model, conditions in (
            (LearningEvent, (LearningEvent.user_id == user_id, LearningEvent.classroom_id == classroom_id)),
            (NodeState, (NodeState.player_quest_id.in_(quest_ids),)),
            (PlayerQuest, (PlayerQuest.user_id == user_id, PlayerQuest.classroom_id == classroom_id)),
            (StorySession, (StorySession.user_id == user_id, StorySession.classroom_id == classroom_id)),
            (ActivityProgress, (ActivityProgress.user_id == user_id, ActivityProgress.classroom_id == classroom_id)),
            (LearningRecord, (LearningRecord.user_id == user_id, LearningRecord.classroom_id == classroom_id)),
            (SubmissionPayload, (SubmissionPayload.submission_id.in_(submission_ids),)),
            (AssessmentAttempt, (AssessmentAttempt.user_id == user_id, AssessmentAttempt.classroom_id == classroom_id)),
            (Submission, (Submission.user_id == user_id, Submission.classroom_id == classroom_id)),
            (WorldFlag, (WorldFlag.user_id == user_id, WorldFlag.classroom_id == classroom_id)),
            (BuildingUnlock, (BuildingUnlock.user_id == user_id, BuildingUnlock.classroom_id == classroom_id)),
            (PlayerPosition, (PlayerPosition.user_id == user_id, PlayerPosition.classroom_id == classroom_id)),
        ):
            deleted[model.__tablename__] = _delete_rows(db, model, *conditions)
        db.commit()
    except Exception:
        db.rollback()
        raise
    return {"ok": True, "user_id": user_id, "classroom_id": classroom_id, "deleted": deleted}
