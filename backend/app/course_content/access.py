"""Authorize classroom course visibility, releases and learner content access."""

from datetime import datetime, timezone

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.auth import as_utc, preview_classroom
from app.content_relations import material_chapter_ids
from app.course_scope import belongs_to_course, course_clause, require_course_content
from app.models import (
    Activity,
    Chapter,
    ChapterRelease,
    Classroom,
    Material,
    Membership,
    PlayerQuest,
    QuestDefinition,
    TownPreviewStudent,
    User,
)


def authorize_classroom(db: Session, user: User, classroom_id: int):
    from app.course_scope import lock_course

    lock_course(db, classroom_id)
    classroom = db.get(Classroom, classroom_id)
    if not classroom:
        raise HTTPException(404, "班级不存在")
    if user.role == "admin" or classroom.teacher_id == user.id or db.get(Membership, (user.id, classroom_id)):
        return classroom
    preview = db.get(TownPreviewStudent, user.id)
    if preview and preview.classroom_id == classroom_id:
        return preview_classroom(db, preview)
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
            course_clause(db, QuestDefinition, classroom_id),
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
    if (
        not belongs_to_course(db, chapter, classroom_id)
        or not classroom
        or (chapter.owner_id is not None and chapter.owner_id != classroom.teacher_id and user.role != "admin")
    ):
        return False
    release = db.get(ChapterRelease, (classroom_id, chapter_id))
    if not release or release.status != "published" or (release.opens_at and as_utc(release.opens_at) > datetime.now(timezone.utc)):
        return False
    from app.config import dev_tools_enabled
    from app.models import WorldFlag

    if (
        user.role == "student"
        and (dev_tools_enabled() or db.get(TownPreviewStudent, user.id))
        and db.get(WorldFlag, (user.id, classroom_id, f"dev:chapter:{chapter_id}"))
    ):
        return True
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
    require_course_content(db, chapter, classroom_id)
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
    require_course_content(db, activity, classroom_id)
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
    require_course_content(db, material, classroom_id)
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
        published = db.scalars(select(Activity).where(Activity.resources.any(material_id=material.id), course_clause(db, Activity, classroom_id))).all()
        chapters = [
            chapter_id
            for chapter_id in chapters
            if any(
                activity.chapter_id == chapter_id
                and activity.owner_id in {None, classroom.teacher_id}
                and activity.content.get("published") is not False
                for activity in published
            )
        ]
    if not chapters:
        raise HTTPException(403, "资料未关联当前班级可访问的已发布章节或活动")
    return chapters


def authorize_material(db: Session, user: User, classroom_id: int, material_id: int) -> Material:
    material = db.get(Material, material_id)
    if not material:
        raise HTTPException(404, "资料不存在")
    authorized_material_chapters(db, user, classroom_id, material)
    return material
