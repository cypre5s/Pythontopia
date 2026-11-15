"""Aggregate authorized classroom learner progress and evidence."""

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.auth import as_utc
from app.classrooms.access import own_classroom
from app.models import LearningEvent, LearningRecord, Membership, PlayerQuest, Submission, TownPreviewStudent, User


def progress(classroom_id: int, db: Session, user: User):
    own_classroom(db, user, classroom_id)
    from app.course_content.access import chapter_completion_requirements

    requirements = chapter_completion_requirements(db, classroom_id)
    completed_by_user: dict[int, set[str]] = {}
    for user_id, quest_id in db.execute(
        select(PlayerQuest.user_id, PlayerQuest.quest_id).where(PlayerQuest.classroom_id == classroom_id, PlayerQuest.status == "complete")
    ):
        completed_by_user.setdefault(user_id, set()).add(quest_id)
    members = db.scalars(select(User).join(Membership, Membership.user_id == User.id).where(Membership.classroom_id == classroom_id)).all()
    preview_ids = select(TownPreviewStudent.student_user_id).where(TownPreviewStudent.classroom_id == classroom_id)
    events = db.scalars(select(LearningEvent).where(LearningEvent.classroom_id == classroom_id, LearningEvent.user_id.not_in(preview_ids))).all()
    submissions = db.scalars(
        select(Submission).where(Submission.classroom_id == classroom_id, Submission.mode == "submit", Submission.user_id.not_in(preview_ids))
    ).all()
    records = db.scalars(select(LearningRecord).where(LearningRecord.classroom_id == classroom_id, LearningRecord.user_id.not_in(preview_ids))).all()
    distribution = {}
    for item in submissions:
        distribution[item.status] = distribution.get(item.status, 0) + 1
    return {
        "members": [
            {
                "id": m.id,
                "name": m.name,
                "student_id": m.student_id,
                "completed_chapters": sum(ids.issubset(completed_by_user.get(m.id, set())) for ids in requirements.values()),
                "completed_nodes": len([e for e in events if e.user_id == m.id]),
                "submissions": len([s for s in submissions if s.user_id == m.id]),
                "records": len([r for r in records if r.user_id == m.id]),
            }
            for m in members
        ],
        "events": [
            {
                "id": e.id,
                "user_id": e.user_id,
                "node_id": e.node_id,
                "created_at": as_utc(e.created_at).isoformat(),
            }
            for e in events
        ],
        "submissions": [
            {"id": s.id, "user_id": s.user_id, "question_id": s.question_id, "status": s.status, "created_at": as_utc(s.created_at).isoformat()}
            for s in submissions
        ],
        "error_distribution": distribution,
    }


def student_record(classroom_id: int, student_id: int, db: Session, user: User):
    own_classroom(db, user, classroom_id)
    if not db.get(Membership, (student_id, classroom_id)):
        raise HTTPException(404, "学生不在当前班级")
    student = db.get(User, student_id)
    return {
        "student": {"id": student.id, "name": student.name, "student_id": student.student_id},
        "events": [
            {
                "id": e.id,
                "node_id": e.node_id,
                "player_quest_id": e.player_quest_id,
                "evidence": e.evidence,
                "created_at": as_utc(e.created_at).isoformat(),
            }
            for e in db.scalars(
                select(LearningEvent)
                .where(LearningEvent.classroom_id == classroom_id, LearningEvent.user_id == student_id)
                .order_by(LearningEvent.created_at)
            )
        ],
        "participation": [
            {
                "id": r.id,
                "activity_id": r.activity_id,
                "kind": r.kind,
                "data": r.data,
                "created_at": as_utc(r.created_at).isoformat(),
            }
            for r in db.scalars(select(LearningRecord).where(LearningRecord.classroom_id == classroom_id, LearningRecord.user_id == student_id))
        ],
        "submissions": [
            {
                "id": s.id,
                "question_id": s.question_id,
                "mode": s.mode,
                "status": s.status,
                "answer": s.answer,
                "code": s.code,
                "feedback": s.feedback,
                "created_at": as_utc(s.created_at).isoformat(),
            }
            for s in db.scalars(
                select(Submission).where(Submission.classroom_id == classroom_id, Submission.user_id == student_id).order_by(Submission.created_at)
            )
        ],
    }
