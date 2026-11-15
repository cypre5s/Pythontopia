"""Present public questions and current learner progress without private answer data."""

from sqlalchemy import select

from app.course_content.learning import learning_questions
from app.course_content.resources import learning_materials, reading_progress
from app.models import (
    ActivityProgress,
    Classroom,
    Question,
    Submission,
)


def public_question(question: Question):
    return {
        "id": question.id,
        "title": question.title,
        "kind": question.kind,
        "prompt": question.prompt,
        "options": question.options,
        "samples": question.samples,
        "hints": question.hints,
        "starter_code": question.starter_code,
        "source": question.source,
    }


def progress_payload(db, user, classroom_id, activity):
    if activity.kind == "assessment":
        from app.assessments import assessment_payload

        paper = assessment_payload(db, user, classroom_id, activity)
        return {
            "page_no": None,
            "visited_pages": [],
            "completed": paper["completed"],
            "accepted_question_ids": [row["question_id"] for row in paper["results"] if row["status"] in {"accepted", "AC", "ac"}],
            "assessment_status": paper["status"],
            "locked": paper["locked"],
            "passed": paper["passed"],
            "correct_count": paper["correct_count"],
            "total": paper["total"],
            "score": paper["score"],
            "max_score": paper["max_score"],
        }
    if activity.kind in {"practice", "assessment"}:
        classroom = db.get(Classroom, classroom_id)
        questions = learning_questions(db, activity, classroom.teacher_id)
        accepted = {
            submission.question_id
            for submission in db.scalars(
                select(Submission).where(
                    Submission.user_id == user.id,
                    Submission.classroom_id == classroom_id,
                    Submission.activity_id == activity.id,
                    Submission.mode == "submit",
                    Submission.status.in_(["accepted", "AC", "ac"]),
                )
            )
        }
        passed = [question.id for question in questions if question.id in accepted]
        return {"page_no": None, "visited_pages": [], "completed": bool(questions) and len(passed) == len(questions), "accepted_question_ids": passed}
    progress = db.get(ActivityProgress, (user.id, classroom_id, activity.id))
    return reading_progress(activity, progress, learning_materials(db, activity))
