"""Manage ordered activities and their shared question associations."""

from typing import Literal

from fastapi import HTTPException
from pydantic import BaseModel, ConfigDict, Field, field_validator
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.content_relations import activity_questions, question_chapter_ids, set_activity_questions
from app.models import Activity, AssessmentAttempt, Npc, Question, User
from app.teacher_scope import commit_content, require_scope, scoped_key, teacher_instance_id


class ActivityInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id: str = Field(min_length=1, max_length=100)
    chapter_id: int
    title: str = Field(min_length=1, max_length=200)
    kind: Literal["lesson", "reading", "practice", "assessment"]
    resources: list[dict] = Field(default_factory=list, max_length=64)
    npc_key: str | None = None
    assistant_allowed: bool = True
    knowledge_point_ids: list[int] = Field(default_factory=list, max_length=65)

    @field_validator("knowledge_point_ids", mode="before")
    @classmethod
    def valid_points(cls, value):
        from app.curriculum import validate_id_list

        return validate_id_list(value)


def create_activity(body: ActivityInput, db: Session, user: User, classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)

    runtime_id = scoped_key(instance_id, "activity", body.id)
    if db.get(Activity, runtime_id, execution_options={"include_deleted": True}):
        raise HTTPException(409, "活动ID已存在")
    from app.curriculum import validate_activity_points

    validate_activity_points(db, user, body.chapter_id, body.knowledge_point_ids, instance_id)
    if body.npc_key is not None and (not db.get(Npc, body.npc_key) or db.get(Npc, body.npc_key).course_instance_id != instance_id):
        raise HTTPException(422, "NPC不存在")
    content = {"published": True, "knowledge_point_ids": body.knowledge_point_ids}
    item = Activity(
        **body.model_dump(exclude={"id", "knowledge_point_ids", "resources"}),
        id=runtime_id,
        stable_id=body.id,
        course_instance_id=instance_id,
        owner_id=user.id,
        content=content,
    )
    db.add(item)
    db.flush()
    from app.course_content.resources import set_resources
    try:
        set_resources(db, item, body.resources)
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    commit_content(db, instance_id)
    return {"id": item.id}


class ActivityUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: str | None = Field(default=None, min_length=1, max_length=200)
    npc_key: str | None = None
    assistant_allowed: bool | None = None
    resources: list[dict] | None = Field(default=None, max_length=64)
    knowledge_point_ids: list[int] | None = Field(default=None, max_length=65)

    @field_validator("knowledge_point_ids", mode="before")
    @classmethod
    def valid_points(cls, value):
        from app.curriculum import validate_id_list

        return validate_id_list(value)


def update_activity(activity_id: str, body: ActivityUpdate, db: Session, user: User, classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)

    item = db.scalar(select(Activity).where(Activity.id == activity_id).with_for_update())
    if not item:
        raise HTTPException(404, "活动不存在")
    require_scope(item, instance_id)
    if item.owner_id != user.id and user.role != "admin":
        raise HTTPException(403, "共享种子不可覆盖，请创建教师自己的活动并绑定任务")
    changes = body.model_dump(exclude_unset=True)
    resources = changes.pop("resources", None)
    content = dict(item.content)
    new_points = changes.pop("knowledge_point_ids", None)
    if new_points is not None:
        from app.curriculum import validate_activity_points

        validate_activity_points(db, user, item.chapter_id, new_points, instance_id)
    if any(key in changes and changes[key] is None for key in ("title", "assistant_allowed")):
        raise HTTPException(422, "活动必填字段不能设为空")
    if (
        "npc_key" in changes
        and changes["npc_key"] is not None
        and (not db.get(Npc, changes["npc_key"]) or db.get(Npc, changes["npc_key"]).course_instance_id != instance_id)
    ):
        raise HTTPException(422, "NPC不存在")
    if "resources" in body.model_fields_set:
        from app.course_content.resources import set_resources
        try:
            set_resources(db, item, resources)
        except ValueError as exc:
            raise HTTPException(422, str(exc)) from exc
    for key, value in changes.items():
        setattr(item, key, value)
    if new_points is not None:
        content["knowledge_point_ids"] = new_points
    item.content = content
    commit_content(db, instance_id)
    return {"id": item.id}


def delete_activity(activity_id: str, db: Session, user: User, classroom_id: int | None = None):
    from app.curriculum import editable

    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    editable(db.scalar(select(Activity).where(Activity.id == activity_id).with_for_update()), user, instance_id)
    raise HTTPException(409, "请先预览删除影响并完成二次确认")


class ActivityQuestionsInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    question_ids: list[str] = Field(max_length=5000)

    @field_validator("question_ids")
    @classmethod
    def unique_questions(cls, value):
        if any(not key or len(key) > 100 for key in value) or len(set(value)) != len(value):
            raise ValueError("题目ID必须有效且不能重复")
        return value


def update_activity_questions(activity_id: str, body: ActivityQuestionsInput, db: Session, user: User, classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    from app.curriculum import editable

    activity = editable(db.scalar(select(Activity).where(Activity.id == activity_id).with_for_update()), user, instance_id)
    if activity.kind not in {"practice", "assessment"}:
        raise HTTPException(422, "只有练习与测验活动可以选题")
    if activity.kind == "assessment" and len(body.question_ids) > 100:
        raise HTTPException(422, "章节测验最多选择100道题")
    if (
        activity.kind == "assessment"
        and body.question_ids != [q.id for q in activity_questions(db, activity)]
        and db.scalar(select(AssessmentAttempt.id).where(AssessmentAttempt.activity_id == activity.id).limit(1)) is not None
    ):
        raise HTTPException(409, "测验已有试卷记录，不能更换题集或顺序；请新建测验活动")
    for question_id in sorted(body.question_ids):
        question = db.scalar(select(Question).where(Question.id == question_id).with_for_update())
        if not question:
            raise HTTPException(422, "选中的题目不存在")
        require_scope(question, instance_id)
        if question.owner_id not in {None, activity.owner_id}:
            raise HTTPException(403, "题目必须属于活动教师或共享题库")
        if question.status != "ready":
            raise HTTPException(422, "只有已就绪题目可以用于活动")
        if activity.chapter_id not in question_chapter_ids(db, question.id):
            raise HTTPException(422, "题目必须关联活动所在章节")
    try:
        set_activity_questions(db, activity, body.question_ids)
        commit_content(db, instance_id)
    except IntegrityError as exc:
        db.rollback()
        raise HTTPException(409, "内容同时更新，请刷新后重试") from exc
    return {"id": activity.id, "question_ids": [q.id for q in activity_questions(db, activity)]}
