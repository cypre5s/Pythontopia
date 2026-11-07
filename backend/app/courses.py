from datetime import datetime, timezone
from math import isfinite
from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.orm import Session
from .auth import as_utc, current_user
from .db import get_db
from .models import (
    Activity,
    ActivityProgress,
    Chapter,
    ChapterRelease,
    Classroom,
    LearningRecord,
    KnowledgePoint,
    Material,
    Membership,
    PlayerQuest,
    QuestDefinition,
    Question,
    Submission,
    User,
)

router = APIRouter(tags=["courses"])


def authorize_classroom(db: Session, user: User, classroom_id: int):
    classroom = db.get(Classroom, classroom_id)
    if not classroom:
        raise HTTPException(404, "班级不存在")
    if user.role == "admin" or classroom.teacher_id == user.id or db.get(Membership, (user.id, classroom_id)):
        return classroom
    raise HTTPException(403, "无权访问此班级")


def chapter_completion_requirements(db: Session, classroom_id: int, chapter_id: int | None = None) -> dict[int, set[str]]:
    """Required main quest IDs for the current class configuration, shared by gates and reports."""
    classroom = db.get(Classroom, classroom_id)
    if not classroom:
        return {}
    query = (
        select(QuestDefinition)
        .join(ChapterRelease, ChapterRelease.chapter_id == QuestDefinition.chapter_id)
        .where(
            ChapterRelease.classroom_id == classroom_id,
            (QuestDefinition.owner_id.is_(None)) | (QuestDefinition.owner_id == classroom.teacher_id),
        )
    )
    if chapter_id is not None:
        query = query.where(QuestDefinition.chapter_id == chapter_id)
    requirements: dict[int, set[str]] = {}
    for item in db.scalars(query):
        if item.definition.get("kind", "main") == "main":
            requirements.setdefault(item.chapter_id, set()).add(item.id)
    return requirements


def chapter_available(db: Session, user: User, classroom_id: int, chapter_id: int, visiting=None) -> bool:
    chapter = db.get(Chapter, chapter_id)
    classroom = db.get(Classroom, classroom_id)
    if not chapter or not classroom or (chapter.owner_id is not None and chapter.owner_id != classroom.teacher_id and user.role != "admin"):
        return False
    release = db.get(ChapterRelease, (classroom_id, chapter_id))
    if not release or release.status != "published" or (release.opens_at and as_utc(release.opens_at) > datetime.now(timezone.utc)):
        return False
    visiting = set() if visiting is None else visiting
    if chapter_id in visiting:
        return False
    if release.prerequisite_chapter_id:
        visiting.add(chapter_id)
        if not chapter_available(db, user, classroom_id, release.prerequisite_chapter_id, visiting):
            return False
        ids = chapter_completion_requirements(db, classroom_id, release.prerequisite_chapter_id).get(release.prerequisite_chapter_id, set())
        completed = set(
            db.scalars(
                select(PlayerQuest.quest_id).where(
                    PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == classroom_id, PlayerQuest.status == "complete"
                )
            )
        )
        if not ids or not ids.issubset(completed):
            return False
    return True


def authorize_chapter(db: Session, user: User, classroom_id: int, chapter_id: int):
    classroom = authorize_classroom(db, user, classroom_id)
    chapter = db.get(Chapter, chapter_id)
    if not chapter:
        raise HTTPException(404, "章节不存在")
    if chapter.owner_id is not None and chapter.owner_id != classroom.teacher_id and user.role != "admin":
        raise HTTPException(403, "章节不属于当前班级教师")
    if user.role == "admin" or classroom.teacher_id == user.id:
        return
    if not chapter_available(db, user, classroom_id, chapter_id):
        raise HTTPException(403, "章节未开放或尚未完成前置任务")


def authorize_activity(db: Session, user: User, classroom_id: int, activity_id: str) -> Activity:
    activity = db.get(Activity, activity_id)
    if not activity:
        raise HTTPException(404, "活动不存在")
    authorize_chapter(db, user, classroom_id, activity.chapter_id)
    classroom = db.get(Classroom, classroom_id)
    if user.role != "admin" and activity.owner_id is not None and activity.owner_id != classroom.teacher_id:
        raise HTTPException(403, "无权访问其他教师的活动")
    can_preview = user.role == "admin" or (user.role == "teacher" and classroom.teacher_id == user.id)
    if not can_preview:
        if activity.content.get("published") is False or (activity.owner_id and activity.owner_id != classroom.teacher_id):
            raise HTTPException(403, "此活动不在当前班级发布范围")
    return activity


def available_in_library(material: Material) -> bool:
    """Only an explicit publication flag can publish materials without an activity."""
    if material.content.get("library_available") is not True:
        return False
    if material.owner_id is not None:
        return True
    source = material.content.get("source")
    return bool(isinstance(source, dict) and source.get("kind") == "course_media_pack"
                and type(source.get("course_id")) is int and source["course_id"] == 43)


def authorize_material(db: Session, user: User, classroom_id: int, material_id: int) -> Material:
    material = db.get(Material, material_id)
    if not material:
        raise HTTPException(404, "资料不存在")
    authorize_chapter(db, user, classroom_id, material.chapter_id)
    classroom = db.get(Classroom, classroom_id)
    if material.owner_id and material.owner_id != classroom.teacher_id and user.role != "admin":
        raise HTTPException(403, "无权访问其他班级教师资料")
    can_preview = user.role == "admin" or (user.role == "teacher" and classroom.teacher_id == user.id)
    if not can_preview and not available_in_library(material):
        published = db.scalars(select(Activity).where(Activity.material_id == material.id)).all()
        if not any(a.owner_id in {None, classroom.teacher_id} and a.content.get("published") is not False
                   and a.chapter_id == material.chapter_id for a in published):
            raise HTTPException(403, "此资料未绑定当前班级已发布活动")
    return material


def public_question(question: Question):
    return {
        "id": question.id,
        "title": question.title,
        "kind": question.kind,
        "prompt": question.prompt,
        "options": question.options,
        "samples": question.samples,
        "source": question.source,
    }


def material_payload(db, material):
    duration = material.content.get("duration_seconds")
    if (material.kind != "video" or type(duration) not in (int, float)
            or (type(duration) is float and not isfinite(duration)) or duration <= 0):
        duration = None
    return {
        "id": material.id, "title": material.title, "kind": material.kind,
        "chapter_id": material.chapter_id, "status": material.status,
        "pages": material.content.get("pages", []), "notice": material.content.get("notice"),
        "media_url": f"/api/materials/{material.id}/file" if material.storage_key and material.status == "ready" else None,
        "error": material.error,
        "supplementary": material.kind == "video" and material.content.get("supplementary") is True,
        "duration_seconds": duration,
    }


@router.get("/activities/{activity_id}")
def get_activity(
    activity_id: str, classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]
):
    activity = authorize_activity(db, user, classroom_id, activity_id)
    material = db.get(Material, activity.material_id) if activity.material_id else None
    if material:
        authorize_material(db, user, classroom_id, material.id)
    classroom = db.get(Classroom, classroom_id)
    points = []
    for point_id in activity.content.get("knowledge_point_ids", []):
        point = db.get(KnowledgePoint, point_id)
        if not point or point.chapter_id != activity.chapter_id:
            raise HTTPException(409, "活动知识点关联无效")
        if point.owner_id not in {None, classroom.teacher_id} and user.role != "admin":
            raise HTTPException(403, "知识点不属于当前班级发布范围")
        points.append(
            {
                "id": point.id,
                "title": point.title,
                "chapter_id": point.chapter_id,
                "source": {"kc_code": point.content.get("kc_code"), "page_ids": point.content.get("page_refs", [])},
            }
        )
    return {
        "id": activity.id,
        "chapter_id": activity.chapter_id,
        "title": activity.title,
        "kind": activity.kind,
        "npc_key": activity.npc_key,
        "assistant_allowed": activity.assistant_allowed,
        "completion": activity.content.get("completion"),
        "progress": progress_payload(db, user, classroom_id, activity),
        "knowledge_point_ids": activity.content.get("knowledge_point_ids", []),
        "knowledge_points": points,
        "material": material_payload(db, material) if material else None,
        "questions": [
            public_question(q)
            for q in db.scalars(select(Question).where(Question.activity_id == activity.id)).all()
            if activity.content.get("question_ids") is None or q.id in activity.content["question_ids"]
        ],
    }


def reading_completed(activity, progress):
    required = activity.content.get("completion", {}).get("required_page_ids", [])
    return bool(progress and required and set(required).issubset(progress.visited_pages))


def progress_payload(db, user, classroom_id, activity):
    if activity.kind in {"practice", "assessment"}:
        questions = [question for question in db.scalars(select(Question).where(Question.activity_id == activity.id))
                     if activity.content.get("question_ids") is None or question.id in activity.content["question_ids"]]
        accepted = {submission.question_id for submission in db.scalars(select(Submission).where(
            Submission.user_id == user.id, Submission.classroom_id == classroom_id, Submission.activity_id == activity.id,
            Submission.mode == "submit", Submission.status.in_(["accepted", "AC", "ac"])))}
        passed = [question.id for question in questions if question.id in accepted]
        return {"page_no": None, "visited_pages": [], "completed": bool(questions) and len(passed) == len(questions), "accepted_question_ids": passed}
    progress = db.get(ActivityProgress, (user.id, classroom_id, activity.id))
    return {
        "page_no": progress.page_no if progress else None,
        "visited_pages": progress.visited_pages if progress else [],
        "completed": reading_completed(activity, progress),
    }


class ProgressInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    page_no: int = Field(ge=1)


@router.get("/activities/{activity_id}/progress")
def get_progress(
    activity_id: str, classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]
):
    activity = authorize_activity(db, user, classroom_id, activity_id)
    return {**progress_payload(db, user, classroom_id, activity), "participation_only": activity.kind in {"lesson", "reading"}}


@router.post("/activities/{activity_id}/progress")
def save_progress(activity_id: str, body: ProgressInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    activity = authorize_activity(db, user, body.classroom_id, activity_id)
    if user.role != "student":
        raise HTTPException(403, "教师预览不能写入学生学习进度")
    if activity.kind not in {"lesson", "reading"} or not activity.material_id:
        raise HTTPException(422, "此活动不支持阅读进度")
    material = authorize_material(db, user, body.classroom_id, activity.material_id)
    payload = material_payload(db, material)
    if payload["status"] != "ready":
        raise HTTPException(409, "资料尚未就绪")
    page = next((item for item in payload["pages"] if item["page_no"] == body.page_no), None)
    if not page:
        raise HTTPException(422, "资料中不存在此页")
    required = activity.content.get("completion", {}).get("required_page_ids", [])
    if required and (any(type(key) is not int for key in required) or not set(required).issubset({item["id"] for item in payload["pages"]})):
        raise HTTPException(409, "阅读完成规则引用了不存在的资料页")
    db.execute(select(User.id).where(User.id == user.id).with_for_update())
    progress = db.get(ActivityProgress, (user.id, body.classroom_id, activity.id))
    if not progress:
        progress = ActivityProgress(user_id=user.id, classroom_id=body.classroom_id, activity_id=activity.id, visited_pages=[])
        db.add(progress)
    first_visit = page["id"] not in progress.visited_pages
    progress.page_no = body.page_no
    progress.visited_pages = sorted(set(progress.visited_pages) | {page["id"]})
    progress.completed = reading_completed(activity, progress)
    if first_visit:
        db.add(LearningRecord(user_id=user.id, classroom_id=body.classroom_id, activity_id=activity.id,
                              kind="participation", data={"page_no": body.page_no, "page_id": page["id"]}))
    db.commit()
    return {**progress_payload(db, user, body.classroom_id, activity), "participation_only": True}


class ParticipationInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    page_no: int | None = Field(default=None, ge=1)
    video_time: float | None = Field(default=None, ge=0)


@router.post("/activities/{activity_id}/participation")
def participation(activity_id: str, body: ParticipationInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    activity = authorize_activity(db, user, body.classroom_id, activity_id)
    if activity.kind not in {"lesson", "reading"}:
        raise HTTPException(422, "此活动不支持阅读记录")
    if activity.material_id:
        material = authorize_material(db, user, body.classroom_id, activity.material_id)
        payload = material_payload(db, material)
        if payload["status"] != "ready":
            raise HTTPException(409, "资料尚未就绪")
        if body.page_no is not None and body.page_no not in {p["page_no"] for p in payload["pages"]}:
            raise HTTPException(422, "资料中不存在此页")
        if body.video_time is not None and material.kind != "video":
            raise HTTPException(422, "此资料不是视频")
    record = LearningRecord(
        user_id=user.id,
        classroom_id=body.classroom_id,
        activity_id=activity.id,
        kind="participation",
        data=body.model_dump(exclude={"classroom_id"}, exclude_none=True),
    )
    db.add(record)
    db.commit()
    return {"record_id": record.id, "participation_only": True}


@router.get("/materials/{material_id}")
def get_material(
    material_id: int, classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]
):
    return material_payload(db, authorize_material(db, user, classroom_id, material_id))


@router.get("/library")
def library(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    authorize_classroom(db, user, classroom_id)
    results = []
    for material in db.scalars(select(Material).order_by(Material.chapter_id, Material.id)):
        try:
            authorize_material(db, user, classroom_id, material.id)
        except HTTPException:
            continue
        payload = material_payload(db, material)
        results.append({key: payload[key] for key in ("id", "title", "chapter_id", "kind", "status", "supplementary", "duration_seconds")})
    return results
