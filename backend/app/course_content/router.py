"""Teacher course content HTTP routes and authenticated dependencies."""

from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.auth import require_teacher
from app.course_content.activities import ActivityInput, ActivityQuestionsInput, ActivityUpdate
from app.course_content.activities import create_activity as _create_activity
from app.course_content.activities import delete_activity as _delete_activity
from app.course_content.activities import update_activity as _update_activity
from app.course_content.activities import update_activity_questions as _update_activity_questions
from app.course_content.catalog import teacher_courses as _teacher_courses
from app.course_content.progress import progress as _progress
from app.course_content.progress import student_record as _student_record
from app.course_content.question_contracts import QuestionInput, QuestionUpdate
from app.course_content.questions import create_question as _create_question
from app.course_content.questions import delete_question as _delete_question
from app.course_content.questions import list_questions as _list_questions
from app.course_content.questions import update_question as _update_question
from app.course_content.releases import ReleaseInput
from app.course_content.releases import list_releases as _list_releases
from app.course_content.releases import release_chapter as _release_chapter
from app.db import get_db
from app.models import User

router = APIRouter(tags=["classrooms"])


@router.put("/teacher/classrooms/{classroom_id}/releases/{chapter_id}")
def release_chapter(
    classroom_id: int, chapter_id: int, body: ReleaseInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]
):
    return _release_chapter(classroom_id=classroom_id, chapter_id=chapter_id, body=body, db=db, user=user)


@router.get("/teacher/courses")
def teacher_courses(db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None):
    return _teacher_courses(db=db, user=user, classroom_id=classroom_id)


@router.get("/teacher/classrooms/{classroom_id}/progress")
def progress(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    return _progress(classroom_id=classroom_id, db=db, user=user)


@router.post("/teacher/activities", status_code=201)
def create_activity(
    body: ActivityInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None
):
    return _create_activity(body=body, db=db, user=user, classroom_id=classroom_id)


@router.put("/teacher/activities/{activity_id}")
def update_activity(
    activity_id: str,
    body: ActivityUpdate,
    db: Annotated[Session, Depends(get_db)],
    user: Annotated[User, Depends(require_teacher)],
    classroom_id: int | None = None,
):
    return _update_activity(activity_id=activity_id, body=body, db=db, user=user, classroom_id=classroom_id)


@router.delete("/teacher/activities/{activity_id}")
def delete_activity(
    activity_id: str, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None
):
    return _delete_activity(activity_id=activity_id, db=db, user=user, classroom_id=classroom_id)


@router.put("/teacher/activities/{activity_id}/questions")
def update_activity_questions(
    activity_id: str,
    body: ActivityQuestionsInput,
    db: Annotated[Session, Depends(get_db)],
    user: Annotated[User, Depends(require_teacher)],
    classroom_id: int | None = None,
):
    return _update_activity_questions(activity_id=activity_id, body=body, db=db, user=user, classroom_id=classroom_id)


@router.post("/teacher/questions", status_code=201)
def create_question(
    body: QuestionInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None
):
    return _create_question(body=body, db=db, user=user, classroom_id=classroom_id)


@router.get("/teacher/questions")
def list_questions(db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None):
    return _list_questions(db=db, user=user, classroom_id=classroom_id)


@router.put("/teacher/questions/{question_id}")
def update_question(
    question_id: str,
    body: QuestionUpdate,
    db: Annotated[Session, Depends(get_db)],
    user: Annotated[User, Depends(require_teacher)],
    classroom_id: int | None = None,
):
    return _update_question(question_id=question_id, body=body, db=db, user=user, classroom_id=classroom_id)


@router.delete("/teacher/questions/{question_id}")
def delete_question(
    question_id: str, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None
):
    return _delete_question(question_id=question_id, db=db, user=user, classroom_id=classroom_id)


@router.get("/teacher/classrooms/{classroom_id}/releases")
def list_releases(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    return _list_releases(classroom_id=classroom_id, db=db, user=user)


@router.get("/teacher/classrooms/{classroom_id}/students/{student_id}")
def student_record(classroom_id: int, student_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    return _student_record(classroom_id=classroom_id, student_id=student_id, db=db, user=user)
