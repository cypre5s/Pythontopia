from datetime import datetime, timezone
from math import isfinite
from typing import Annotated
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.orm import Session
from .auth import as_utc, current_user
from .db import get_db
from .content_relations import activity_questions, material_chapter_ids, material_in_chapter, question_chapter_ids
from .learning_rules import record_video, required_pages, validate_learning, video_duration, video_state_for_material, video_watched
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
    return material.content.get("library_available") is True


def authorized_material_chapters(db, user, classroom_id, material):
    """Return only usable associations; an unpublished sibling chapter stays private."""
    classroom = authorize_classroom(db, user, classroom_id)
    if material.owner_id not in {None, classroom.teacher_id} and user.role != "admin":
        raise HTTPException(403, "无权访问其他班级教师资料")
    chapters = []
    for chapter_id in material_chapter_ids(db, material.id):
        try:
            authorize_chapter(db, user, classroom_id, chapter_id)
        except HTTPException:
            continue
        chapters.append(chapter_id)
    can_preview = user.role == "admin" or (user.role == "teacher" and classroom.teacher_id == user.id)
    if not can_preview and not available_in_library(material):
        published = db.scalars(select(Activity).where(Activity.material_id == material.id)).all()
        chapters = [chapter_id for chapter_id in chapters if any(
            activity.chapter_id == chapter_id and activity.owner_id in {None, classroom.teacher_id}
            and activity.content.get("published") is not False for activity in published)]
    if not chapters:
        raise HTTPException(403, "资料未关联当前班级可访问的已发布章节或活动")
    return chapters


def authorize_material(db: Session, user: User, classroom_id: int, material_id: int) -> Material:
    material = db.get(Material, material_id)
    if not material:
        raise HTTPException(404, "资料不存在")
    authorized_material_chapters(db, user, classroom_id, material)
    return material


def learning_questions(db, activity, teacher_id=None):
    """Use the complete ordered binding, and reject invalid papers as a whole."""
    questions = activity_questions(db, activity)
    owner_id = teacher_id if teacher_id is not None else activity.owner_id
    for question in questions:
        if question.status != "ready":
            raise HTTPException(409, "活动存在尚未就绪的题目，请联系教师")
        if activity.chapter_id not in question_chapter_ids(db, question.id):
            raise HTTPException(409, "活动题目尚未关联当前章节")
        if question.owner_id not in {None, owner_id}:
            raise HTTPException(403, "活动题目不属于当前班级教师")
    return questions


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


def material_payload(db, material, *, chapter_ids=None):
    chapters = material_chapter_ids(db, material.id) if chapter_ids is None else chapter_ids
    duration = material.content.get("duration_seconds")
    if (material.kind != "video" or type(duration) not in (int, float)
            or (type(duration) is float and not isfinite(duration)) or duration <= 0):
        duration = None
    return {
        "id": material.id, "title": material.title, "kind": material.kind,
        "chapter_id": chapters[0] if chapters else None, "chapter_ids": chapters, "status": material.status,
        "pages": material.content.get("pages", []), "notice": material.content.get("notice"),
        "media_url": f"/api/materials/{material.id}/file" if material.storage_key and material.status == "ready" else None,
        "error": material.error,
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
        if not material_in_chapter(db, material.id, activity.chapter_id):
            raise HTTPException(409, "学习资料必须关联活动章节")
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
        "learning": activity.content.get("learning", {}),
        "progress": progress_payload(db, user, classroom_id, activity),
        "knowledge_point_ids": activity.content.get("knowledge_point_ids", []),
        "knowledge_points": points,
        "material": material_payload(db, material, chapter_ids=authorized_material_chapters(db, user, classroom_id, material)) if material else None,
        "questions": [
            public_question(q)
            for q in learning_questions(db, activity, classroom.teacher_id)
        ],
    }


def learning_material(db, activity):
    material = db.get(Material, activity.material_id) if activity.material_id else None
    if material and not material_in_chapter(db, material.id, activity.chapter_id):
        raise HTTPException(409, "学习资料必须属于活动章节")
    try:
        validate_learning(activity.content, activity.kind, material, resolve=True)
    except ValueError as exc:
        raise HTTPException(409, str(exc)) from exc
    return material


def reading_completed(activity, progress, material=None):
    video = activity.content.get("learning", {}).get("video")
    if video:
        return bool(progress and video_watched(progress.media_progress or {}, activity.material_id) >= video["minimum_seconds"])
    required = required_pages(activity, material)
    return bool(progress and required and set(required).issubset(progress.visited_pages))


def progress_payload(db, user, classroom_id, activity):
    if activity.kind == "assessment":
        from .assessments import assessment_payload
        paper = assessment_payload(db, user, classroom_id, activity)
        return {"page_no": None, "visited_pages": [], "completed": paper["completed"],
            "accepted_question_ids": [row["question_id"] for row in paper["results"] if row["status"] in {"accepted", "AC", "ac"}],
            "assessment_status": paper["status"], "locked": paper["locked"], "passed": paper["passed"],
            "correct_count": paper["correct_count"], "total": paper["total"], "score": paper["score"], "max_score": paper["max_score"]}
    if activity.kind in {"practice", "assessment"}:
        classroom = db.get(Classroom, classroom_id)
        questions = learning_questions(db, activity, classroom.teacher_id)
        accepted = {submission.question_id for submission in db.scalars(select(Submission).where(
            Submission.user_id == user.id, Submission.classroom_id == classroom_id, Submission.activity_id == activity.id,
            Submission.mode == "submit", Submission.status.in_(["accepted", "AC", "ac"])))}
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
        "read_count": len(set(required) & set(visited)), "read_total": len(required),
        "watched_seconds": video_watched(media, activity.material_id),
        "required_seconds": activity.content.get("learning", {}).get("video", {}).get("minimum_seconds", 0),
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
    learning_material(db, activity)
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
    progress.completed = reading_completed(activity, progress, material)
    if first_visit:
        db.add(LearningRecord(user_id=user.id, classroom_id=body.classroom_id, activity_id=activity.id,
                              kind="participation", data={"page_no": body.page_no, "page_id": page["id"]}))
    db.commit()
    return {**progress_payload(db, user, body.classroom_id, activity), "participation_only": True}


class VideoProgressInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    material_id: int = Field(strict=True, ge=1)
    playback_session_id: UUID
    sequence: int = Field(strict=True, ge=1, le=2**31 - 1)
    position_seconds: float = Field(strict=True, ge=0, le=86400, allow_inf_nan=False)
    playing: bool = Field(strict=True)
    playback_rate: float = Field(default=1.0, strict=True, ge=.25, le=4, allow_inf_nan=False)


@router.post("/activities/{activity_id}/video-progress")
def save_video_progress(activity_id: str, body: VideoProgressInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    activity = authorize_activity(db, user, body.classroom_id, activity_id)
    if user.role != "student":
        raise HTTPException(403, "教师预览不能写入学生学习进度")
    if activity.kind not in {"lesson", "reading"} or not activity.material_id:
        raise HTTPException(422, "此活动不支持视频进度")
    if body.material_id != activity.material_id:
        raise HTTPException(409, "活动资料已变化，请重新打开当前活动")
    material = authorize_material(db, user, body.classroom_id, activity.material_id)
    learning_material(db, activity)
    duration = video_duration(material)
    if material.status != "ready" or not material.storage_key:
        raise HTTPException(409, "视频尚未就绪")
    if material.kind != "video" or duration is None:
        raise HTTPException(422, "此活动没有可记录的可靠时长视频")
    if body.position_seconds > duration:
        raise HTTPException(422, "播放位置超出视频时长")
    db.execute(select(User.id).where(User.id == user.id).with_for_update())
    progress = db.get(ActivityProgress, (user.id, body.classroom_id, activity.id))
    if not progress:
        progress = ActivityProgress(user_id=user.id, classroom_id=body.classroom_id, activity_id=activity.id, visited_pages=[], media_progress={})
        db.add(progress)
    try:
        state = video_state_for_material(progress.media_progress or {}, material.id)
        media, changed = record_video(state, str(body.playback_session_id), body.sequence, body.position_seconds, body.playing, duration, body.playback_rate)
    except ValueError as exc:
        raise HTTPException(409, str(exc)) from exc
    if changed:
        first = (progress.media_progress or {}).get("material_id") != material.id
        progress.media_progress = media
        progress.completed = reading_completed(activity, progress, material)
        if first:
            db.add(LearningRecord(user_id=user.id, classroom_id=body.classroom_id, activity_id=activity.id,
                                  kind="participation", data={"video": True, "material_id": material.id}))
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
    if user.role != "student":
        raise HTTPException(403, "教师预览不能写入学生学习记录")
    if activity.content.get("learning", {}).get("video") and not progress_payload(db, user, body.classroom_id, activity)["completed"]:
        raise HTTPException(422, "请先完成当前活动的材料参与要求")
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
    material = authorize_material(db, user, classroom_id, material_id)
    return material_payload(db, material, chapter_ids=authorized_material_chapters(db, user, classroom_id, material))


@router.get("/library")
def library(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    authorize_classroom(db, user, classroom_id)
    results = []
    for material in db.scalars(select(Material).order_by(Material.id)):
        try:
            authorize_material(db, user, classroom_id, material.id)
        except HTTPException:
            continue
        payload = material_payload(db, material, chapter_ids=authorized_material_chapters(db, user, classroom_id, material))
        results.append({key: payload[key] for key in ("id", "title", "chapter_id", "chapter_ids", "kind", "status", "duration_seconds")})
    return results
