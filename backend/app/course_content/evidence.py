"""Server-verified reading, practice and assessment completion evidence."""

from fastapi import HTTPException
from sqlalchemy import select

from app.models import ActivityProgress, Classroom, LearningRecord, Submission


def validate_learning_evidence(db, user, classroom_id, node, evidence):
    kind = node["type"]
    if kind == "STUDY_LESSON":
        from app.course_content.access import authorize_activity
        from app.course_content.learning import reading_completed
        from app.course_content.resources import learning_materials
        from app.learning_rules import has_completion_rule

        activity = authorize_activity(db, user, classroom_id, node["activity_id"])
        if has_completion_rule(activity):
            progress = db.get(ActivityProgress, (user.id, classroom_id, activity.id))
            if not reading_completed(activity, progress, learning_materials(db, activity)):
                raise HTTPException(422, "请先完成规定资料页的阅读参与")
        record = db.get(LearningRecord, evidence.get("record_id", ""))
        if not record or (record.user_id, record.classroom_id, record.activity_id) != (
            user.id,
            classroom_id,
            node["activity_id"],
        ):
            raise HTTPException(422, "缺少当前活动的真实阅读记录")
    elif kind in {"COMPLETE_PRACTICE", "PASS_ASSESSMENT"}:
        from app.course_content.access import authorize_activity
        from app.course_content.learning import learning_questions

        activity = authorize_activity(db, user, classroom_id, node["activity_id"])
        classroom = db.get(Classroom, classroom_id)
        questions = learning_questions(db, activity, classroom.teacher_id) if activity.kind != "assessment" else []
        if not questions and activity.kind != "assessment":
            raise HTTPException(422, "题组为空")
        accepted = {
            s.question_id
            for s in db.scalars(
                select(Submission).where(
                    Submission.user_id == user.id,
                    Submission.classroom_id == classroom_id,
                    Submission.activity_id == node["activity_id"],
                    Submission.mode == "submit",
                    Submission.status.in_(["accepted", "AC", "ac"]),
                )
            ).all()
        }
        if activity.kind == "assessment":
            from app.assessments import assessment_payload

            if not assessment_payload(db, user, classroom_id, activity)["completed"]:
                raise HTTPException(422, "请先提交试卷并等待全部题目判定完成")
        elif any(q.id not in accepted for q in questions):
            raise HTTPException(422, "请先通过此活动的全部正式练习")
        if evidence.get("submission_id"):
            submission = db.get(Submission, evidence["submission_id"])
            if (
                not submission
                or submission.user_id != user.id
                or submission.classroom_id != classroom_id
                or submission.activity_id != node["activity_id"]
            ):
                raise HTTPException(422, "提交证据不匹配")
