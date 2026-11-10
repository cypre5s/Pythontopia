"""Small teacher-owned curriculum authoring within the single Python course."""

import json
from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field, field_validator
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session
from .auth import require_teacher
from .db import get_db
from .teacher_scope import teacher_instance_id, require_scope, new_stable_id, commit_content
from .models import Activity, Chapter, ChapterRelease, KnowledgePoint, MaterialChapter, QuestionChapter, QuestionKnowledgePoint, QuestDefinition, User

router = APIRouter(prefix="/teacher", tags=["curriculum"])


def authoring_chapter(db, user, chapter_id, instance_id=None):
    chapter = db.get(Chapter, chapter_id)
    if not chapter:
        raise HTTPException(404, "章节不存在")
    require_scope(chapter, instance_id)
    if user.role != "admin" and chapter.owner_id not in {None, user.id}:
        raise HTTPException(403, "无权使用其他教师的私有章节")
    return chapter


def editable(item, user, instance_id=None):
    if not item:
        raise HTTPException(404, "条目不存在")
    require_scope(item, instance_id)
    if user.role != "admin" and item.owner_id != user.id:
        raise HTTPException(403, "只能编辑自己创建的条目，共享课程由管理员维护")
    return item


def validate_id_list(value):
    if not isinstance(value, list) or len(value) > 65 or any(type(item) is not int or item < 1 for item in value) or len(set(value)) != len(value):
        raise ValueError("知识点ID必须为最多65个不重复的正整数")
    return value


def validate_activity_points(db, user, chapter_id, ids, instance_id=None):
    authoring_chapter(db, user, chapter_id, instance_id)
    for point_id in ids:
        point = db.get(KnowledgePoint, point_id)
        if not point or point.course_instance_id != instance_id:
            raise HTTPException(422, "知识点必须存在且属于当前班级课程")
        authoring_chapter(db, user, point.chapter_id, instance_id)
        if user.role != "admin" and point.owner_id not in {None, user.id}:
            raise HTTPException(403, "无权绑定其他教师的私有知识点")


def chapter_payload(chapter):
    return {
        "id": chapter.id,
        "title": chapter.title,
        "order_index": chapter.order_index,
        "owner_id": chapter.owner_id,
        "course_id": chapter.course_id,
    }


def point_payload(point):
    return {"id": point.id, "chapter_id": point.chapter_id, "title": point.title, "content": point.content, "owner_id": point.owner_id}


class ChapterInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: str = Field(min_length=1, max_length=200)
    order_index: int | None = Field(default=None, ge=1, strict=True)

    @field_validator("title")
    @classmethod
    def nonblank(cls, value):
        if not value.strip():
            raise ValueError("标题不能为空白")
        return value.strip()


class ChapterUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: str | None = Field(default=None, min_length=1, max_length=200)
    order_index: int | None = Field(default=None, ge=1, strict=True)

    @field_validator("title")
    @classmethod
    def nonblank(cls, value):
        if value is None or not value.strip():
            raise ValueError("标题不能为空")
        return value.strip()

    @field_validator("order_index")
    @classmethod
    def nonnull(cls, value):
        if value is None:
            raise ValueError("顺序不能为空")
        return value


def commit_metadata(db, instance_id=None):
    try:
        commit_content(db, instance_id)
    except IntegrityError:
        db.rollback()
        raise HTTPException(409, "排序冲突或条目仍被引用，请刷新后重试")


@router.post("/chapters", status_code=201)
def create_chapter(body: ChapterInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    order = body.order_index if body.order_index is not None else (db.scalar(select(func.max(Chapter.order_index)).where(Chapter.course_instance_id == instance_id)) or 0) + 1
    chapter = Chapter(title=body.title, order_index=order, owner_id=user.id, course_id=43,
                      course_instance_id=instance_id, stable_id=new_stable_id())
    db.add(chapter)
    commit_metadata(db, instance_id)
    return chapter_payload(chapter)


@router.put("/chapters/{chapter_id}")
def update_chapter(chapter_id: int, body: ChapterUpdate, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    chapter = editable(db.get(Chapter, chapter_id), user, instance_id)
    for key, value in body.model_dump(exclude_unset=True).items():
        setattr(chapter, key, value)
    commit_metadata(db, instance_id)
    return chapter_payload(chapter)


@router.delete("/chapters/{chapter_id}")
def delete_chapter(chapter_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    chapter = editable(db.get(Chapter, chapter_id), user, instance_id)
    for model in (Activity, MaterialChapter, QuestionChapter, KnowledgePoint, QuestDefinition, ChapterRelease):
        if db.scalar(select(model).where(model.chapter_id == chapter_id).limit(1)):
            raise HTTPException(409, "章节已有知识点、材料、活动、任务或发布记录，不能删除")
    if db.scalar(select(ChapterRelease).where(ChapterRelease.prerequisite_chapter_id == chapter_id).limit(1)):
        raise HTTPException(409, "章节仍作为发布前置，不能删除")
    db.delete(chapter)
    commit_metadata(db, instance_id)
    return {"ok": True}


class PointInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    chapter_id: int = Field(ge=1, strict=True)
    title: str = Field(min_length=1, max_length=200)
    content: dict = Field(default_factory=dict)

    @field_validator("title")
    @classmethod
    def nonblank(cls, value):
        if not value.strip():
            raise ValueError("标题不能为空白")
        return value.strip()

    @field_validator("content")
    @classmethod
    def bounded(cls, value):
        if len(json.dumps(value, ensure_ascii=False)) > 30000:
            raise ValueError("知识点内容不能超过30000字符")
        return value


class PointUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: str | None = Field(default=None, min_length=1, max_length=200)
    content: dict | None = None

    @field_validator("title")
    @classmethod
    def nonblank(cls, value):
        if value is None:
            raise ValueError("标题不能为空")
        return PointInput.nonblank(value)

    @field_validator("content")
    @classmethod
    def bounded(cls, value):
        if value is None:
            raise ValueError("知识点内容不能为null")
        return PointInput.bounded(value)


@router.post("/knowledge-points", status_code=201)
def create_point(body: PointInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    authoring_chapter(db, user, body.chapter_id, instance_id)
    point = KnowledgePoint(**body.model_dump(), owner_id=user.id, course_instance_id=instance_id, stable_id=new_stable_id())
    db.add(point)
    commit_metadata(db, instance_id)
    return point_payload(point)


@router.put("/knowledge-points/{point_id}")
def update_point(point_id: int, body: PointUpdate, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    point = editable(db.get(KnowledgePoint, point_id), user, instance_id)
    authoring_chapter(db, user, point.chapter_id, instance_id)
    for key, value in body.model_dump(exclude_unset=True).items():
        setattr(point, key, value)
    commit_metadata(db, instance_id)
    return point_payload(point)


@router.delete("/knowledge-points/{point_id}")
def delete_point(point_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    point = editable(db.get(KnowledgePoint, point_id), user, instance_id)
    if (db.scalar(select(QuestionKnowledgePoint).where(QuestionKnowledgePoint.knowledge_point_id == point_id).limit(1))
            or any(point_id in a.content.get("knowledge_point_ids", []) for a in db.scalars(select(Activity)))):
        raise HTTPException(409, "知识点已被活动引用，不能删除")
    db.delete(point)
    commit_metadata(db, instance_id)
    return {"ok": True}
