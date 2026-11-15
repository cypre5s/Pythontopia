"""Present public questions and current learner progress without private answer data."""

from sqlalchemy import select

from app.course_content.learning import learning_material, learning_questions, reading_completed
from app.learning_rules import required_pages, video_watched
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
    material = learning_material(db, activity)
    progress = db.get(ActivityProgress, (user.id, classroom_id, activity.id))
    required = required_pages(activity, material)
    visited = progress.visited_pages if progress else []
    media = (progress.media_progress or {}) if progress else {}
    return {
        "page_no": progress.page_no if progress else None,
        "visited_pages": visited,
        "completed": reading_completed(activity, progress, material),
        "required_page_ids": required,
        "read_count": len(set(required) & set(visited)),
        "read_total": len(required),
        "watched_seconds": video_watched(media, activity.material_id),
        "required_seconds": activity.content.get("learning", {}).get("video", {}).get("minimum_seconds", 0),
    }
