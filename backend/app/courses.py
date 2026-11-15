from app.course_content.presentation import progress_payload
from app.materials.presentation import material_payload
from app.course_content.presentation import public_question
from app.course_content.learning import reading_completed
from app.course_content.learning import learning_material
from app.course_content.learning import learning_questions
from app.course_content.access import authorize_material
from app.course_content.access import authorized_material_chapters
from app.course_content.access import authorize_activity
from app.course_content.access import authorize_classroom
from .course_assistant import activity_npc
from .characters import presentation
from typing import Annotated
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.orm import Session
from .auth import current_user
from .db import get_db
from .course_scope import course_clause
from .content_relations import material_in_chapter
from .learning_rules import record_video, video_duration, video_state_for_material
from .models import (
    ActivityProgress,
    Chapter,
    Classroom,
    LearningRecord,
    KnowledgePoint,
    Material,
    Question,
    User,
)

router = APIRouter(tags=["courses"])
























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
        if not point or point.course_instance_id != activity.course_instance_id:
            raise HTTPException(409, "活动知识点关联无效")
        point_chapter = db.get(Chapter, point.chapter_id)
        if not point_chapter or point_chapter.course_instance_id != activity.course_instance_id:
            raise HTTPException(409, "活动知识点章节关联无效")
        if point_chapter.owner_id not in {None, classroom.teacher_id} and user.role != "admin":
            raise HTTPException(403, "知识点章节不属于当前班级发布范围")
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
    questions = learning_questions(db, activity, classroom.teacher_id)
    if activity.kind == "assessment" and user.role == "student":
        from .models import AssessmentAttempt
        attempt = db.scalar(select(AssessmentAttempt).where(AssessmentAttempt.user_id == user.id,
            AssessmentAttempt.classroom_id == classroom_id, AssessmentAttempt.activity_id == activity.id))
        if attempt and attempt.state in {"submitted", "legacy"}:
            historical = {q.id: q for q in db.scalars(select(Question).where(Question.id.in_(attempt.question_ids),
                Question.course_instance_id == activity.course_instance_id).execution_options(include_deleted=True))}
            questions = [historical[key] for key in attempt.question_ids if key in historical]
    return {
        "id": activity.id,
        "chapter_id": activity.chapter_id,
        "title": activity.title,
        "kind": activity.kind,
        "npc_key": activity.npc_key,
        "role": presentation(db, classroom_id, activity_npc(db, classroom_id, activity)),
        "assistant_allowed": activity.assistant_allowed,
        "completion": activity.content.get("completion"),
        "learning": activity.content.get("learning", {}),
        "progress": progress_payload(db, user, classroom_id, activity),
        "knowledge_point_ids": activity.content.get("knowledge_point_ids", []),
        "knowledge_points": points,
        "material": material_payload(db, material, chapter_ids=authorized_material_chapters(db, user, classroom_id, material)) if material else None,
        "questions": [
            public_question(q)
            for q in questions
        ],
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
    for material in db.scalars(select(Material).where(course_clause(db, Material, classroom_id)).order_by(Material.id)):
        try:
            authorize_material(db, user, classroom_id, material.id)
        except HTTPException:
            continue
        payload = material_payload(db, material, chapter_ids=authorized_material_chapters(db, user, classroom_id, material))
        results.append({key: payload[key] for key in ("id", "title", "chapter_id", "chapter_ids", "kind", "status", "duration_seconds")})
    return results
