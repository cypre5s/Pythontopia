"""Authorize and persist chapter release schedules."""

from datetime import datetime, timezone
from typing import Literal

from fastapi import HTTPException
from pydantic import BaseModel, ConfigDict, field_validator
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.auth import as_utc
from app.classrooms.access import own_classroom
from app.models import Chapter, ChapterRelease, User
from app.teacher_scope import commit_content, require_scope, teacher_instance_id


class ReleaseInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    status: Literal["published", "paused"] = "published"
    opens_at: datetime | None = None
    prerequisite_chapter_id: int | None = None

    @field_validator("opens_at")
    @classmethod
    def timezone_required(cls, value):
        if value and value.tzinfo is None:
            raise ValueError("开放时间必须包含时区")
        return value.astimezone(timezone.utc) if value is not None else None


def release_chapter(classroom_id: int, chapter_id: int, body: ReleaseInput, db: Session, user: User):
    from app.course_scope import lock_course

    lock_course(db, classroom_id, exclusive=True)
    classroom = own_classroom(db, user, classroom_id)
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    chapter = db.get(Chapter, chapter_id)
    require_scope(chapter, instance_id)
    if not chapter:
        raise HTTPException(404, "章节不存在")
    if chapter.owner_id is not None and chapter.owner_id != classroom.teacher_id:
        raise HTTPException(403, "不能向本班发布其他教师的私有章节")
    if body.prerequisite_chapter_id == chapter_id or (body.prerequisite_chapter_id and not db.get(Chapter, body.prerequisite_chapter_id)):
        raise HTTPException(422, "前置章节无效")
    if body.prerequisite_chapter_id:
        prerequisite = require_scope(db.get(Chapter, body.prerequisite_chapter_id), instance_id)
        if prerequisite.owner_id is not None and prerequisite.owner_id != classroom.teacher_id:
            raise HTTPException(403, "不能引用其他教师的私有前置章节")
    visited = {chapter_id}
    previous = body.prerequisite_chapter_id
    while previous:
        if previous in visited:
            raise HTTPException(422, "章节前置不能成环")
        visited.add(previous)
        item = db.get(ChapterRelease, (classroom_id, previous))
        previous = item.prerequisite_chapter_id if item else None
    release = db.get(ChapterRelease, (classroom_id, chapter_id))
    if not release:
        release = ChapterRelease(classroom_id=classroom_id, chapter_id=chapter_id)
        db.add(release)
    for key, value in body.model_dump().items():
        setattr(release, key, value)
    commit_content(db, instance_id)
    return {
        "classroom_id": classroom_id,
        "chapter_id": chapter_id,
        **body.model_dump(),
        "opens_at": as_utc(release.opens_at).isoformat() if release.opens_at is not None else None,
        "pause_policy": "deny_all_new_requests_preserve_history",
    }


def list_releases(classroom_id: int, db: Session, user: User):
    own_classroom(db, user, classroom_id)
    return [
        {
            "classroom_id": r.classroom_id,
            "chapter_id": r.chapter_id,
            "status": r.status,
            "opens_at": as_utc(r.opens_at).isoformat() if r.opens_at is not None else None,
            "prerequisite_chapter_id": r.prerequisite_chapter_id,
            "pause_policy": "deny_all_new_requests_preserve_history",
        }
        for r in db.scalars(select(ChapterRelease).where(ChapterRelease.classroom_id == classroom_id))
    ]
