from datetime import datetime, timezone
from types import SimpleNamespace
from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.orm import Session
from .auth import as_utc, current_user
from .db import get_db
from .models import (
    Activity,
    ActivityVersion,
    ChapterRelease,
    Classroom,
    LearningRecord,
    Material,
    MaterialVersion,
    Membership,
    PlayerQuest,
    QuestDefinition,
    Question,
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


def chapter_available(db: Session, user: User, classroom_id: int, chapter_id: int, visiting=None) -> bool:
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
        classroom = db.get(Classroom, classroom_id)
        prerequisite_release = db.get(ChapterRelease, (classroom_id, release.prerequisite_chapter_id))
        definitions = db.scalars(
            select(QuestDefinition).where(
                QuestDefinition.chapter_id == release.prerequisite_chapter_id,
                QuestDefinition.version <= prerequisite_release.version,
                (QuestDefinition.owner_id.is_(None)) | (QuestDefinition.owner_id == classroom.teacher_id),
            )
        ).all()
        ids = {item.id for item in definitions}
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
    if user.role == "admin" or classroom.teacher_id == user.id:
        return
    if not chapter_available(db, user, classroom_id, chapter_id):
        raise HTTPException(403, "章节未开放或尚未完成前置任务")


def activity_versions(db, user, classroom_id, activity):
    release = db.get(ChapterRelease, (classroom_id, activity.chapter_id))
    if not release:
        return set(), []
    versions = {min(activity.version, release.version)}
    pinned = []
    for instance in db.scalars(select(PlayerQuest).where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == classroom_id)):
        definition = db.get(QuestDefinition, (instance.quest_id, instance.quest_version))
        for node in definition.definition["nodes"]:
            if node.get("activity_id") == activity.id:
                version = node.get("activity_version", 1)
                versions.add(version)
                pinned.append(version)
    return versions, pinned


def snapshot_activity(db, activity, version):
    if version == activity.version:
        return activity
    snapshot = db.get(ActivityVersion, (activity.id, version))
    if not snapshot:
        raise HTTPException(404, "活动版本不存在")
    values = {
        key: getattr(activity, key)
        for key in ("id", "chapter_id", "owner_id", "title", "kind", "material_id", "npc_key", "assistant_allowed", "content")
    }
    values.update(snapshot.snapshot)
    values["version"] = version
    return SimpleNamespace(**values)


def authorize_activity(db: Session, user: User, classroom_id: int, activity_id: str, version: int | None = None) -> Activity:
    activity = db.get(Activity, activity_id)
    if not activity:
        raise HTTPException(404, "活动不存在")
    authorize_chapter(db, user, classroom_id, activity.chapter_id)
    classroom = db.get(Classroom, classroom_id)
    if user.role != "admin" and activity.owner_id is not None and activity.owner_id != classroom.teacher_id:
        raise HTTPException(403, "无权访问其他教师的活动")
    can_preview = user.role == "admin" or (user.role == "teacher" and classroom.teacher_id == user.id)
    if not can_preview:
        versions, pinned = activity_versions(db, user, classroom_id, activity)
        chosen = version if version is not None else min(pinned) if pinned else max(versions)
        if chosen not in versions:
            raise HTTPException(403, "此活动版本尚未发布")
        activity = snapshot_activity(db, activity, chosen)
        if activity.content.get("published") is False or (activity.owner_id and activity.owner_id != classroom.teacher_id):
            raise HTTPException(403, "此活动不在当前班级发布范围")
    elif version is not None:
        activity = snapshot_activity(db, activity, version)
    return activity


def material_versions_for_student(db, user, classroom_id, material_id):
    allowed = set()
    for activity in db.scalars(select(Activity)):
        if not chapter_available(db, user, classroom_id, activity.chapter_id):
            continue
        versions, _ = activity_versions(db, user, classroom_id, activity)
        for version in versions:
            try:
                item = authorize_activity(db, user, classroom_id, activity.id, version)
            except HTTPException:
                continue
            if item.material_id == material_id:
                allowed.add(item.content.get("material_version", 1))
    return allowed


def authorize_material(db: Session, user: User, classroom_id: int, material_id: int, version: int | None = None) -> Material:
    material = db.get(Material, material_id)
    if not material:
        raise HTTPException(404, "资料不存在")
    authorize_chapter(db, user, classroom_id, material.chapter_id)
    classroom = db.get(Classroom, classroom_id)
    if material.owner_id and material.owner_id != classroom.teacher_id and user.role != "admin":
        raise HTTPException(403, "无权访问其他班级教师资料")
    can_preview = user.role == "admin" or (user.role == "teacher" and classroom.teacher_id == user.id)
    if not can_preview:
        allowed = material_versions_for_student(db, user, classroom_id, material_id)
        if not allowed or (version is not None and version not in allowed):
            raise HTTPException(403, "此资料版本未绑定当前班级已发布活动")
        material._authorized_versions = allowed
    else:
        material._authorized_versions = None
    return material


def public_question(question: Question):
    return {
        "id": question.id,
        "title": question.title,
        "kind": question.kind,
        "prompt": question.prompt,
        "options": question.options,
        "samples": question.samples,
        "version": question.version,
        "source": question.source,
    }


def material_payload(db, material, version=None):
    query = select(MaterialVersion).where(MaterialVersion.material_id == material.id)
    if version is not None:
        query = query.where(MaterialVersion.version == version)
    allowed = getattr(material, "_authorized_versions", None)
    if allowed is not None:
        query = query.where(MaterialVersion.version.in_(allowed))
    item = db.scalar(query.order_by(MaterialVersion.version.desc()))
    if not item:
        raise HTTPException(404, "资料版本不存在")
    return {
        "id": material.id,
        "title": material.title,
        "kind": material.kind,
        "chapter_id": material.chapter_id,
        "version": item.version,
        "status": item.status,
        "pages": item.content.get("pages", []),
        "notice": item.content.get("notice"),
        "media_url": f"/api/materials/{material.id}/versions/{item.version}/file" if item.storage_key and item.status == "ready" else None,
        "error": item.error,
    }


@router.get("/activities/{activity_id}")
def get_activity(activity_id: str, classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    activity = authorize_activity(db, user, classroom_id, activity_id)
    material = db.get(Material, activity.material_id) if activity.material_id else None
    if material:
        authorize_material(db, user, classroom_id, material.id)
    return {
        "id": activity.id,
        "chapter_id": activity.chapter_id,
        "title": activity.title,
        "kind": activity.kind,
        "version": activity.version,
        "npc_key": activity.npc_key,
        "assistant_allowed": activity.assistant_allowed,
        "material": material_payload(db, material, activity.content.get("material_version")) if material else None,
        "questions": [
            public_question(q)
            for q in db.scalars(select(Question).where(Question.activity_id == activity.id)).all()
            if activity.content.get("question_ids") is None or q.id in activity.content["question_ids"]
        ],
    }


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
        payload = material_payload(db, material, activity.content.get("material_version"))
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
        version=activity.version,
        kind="participation",
        data=body.model_dump(exclude={"classroom_id"}, exclude_none=True),
    )
    db.add(record)
    db.commit()
    return {"record_id": record.id, "participation_only": True}


@router.get("/materials/{material_id}/versions/{version}")
def get_material(
    material_id: int, version: int, classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]
):
    return material_payload(db, authorize_material(db, user, classroom_id, material_id, version), version)


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
        results.append({key: payload[key] for key in ("id", "title", "chapter_id", "kind", "version", "status")})
    return results
