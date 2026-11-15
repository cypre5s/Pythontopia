"""Authorize and publish task definitions."""

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Activity, Npc, QuestDefinition, User
from app.teaching.quests.contracts import DefinitionInput
from app.teaching.quests.validation import validate_definition


def _authoring_definition(db, user, definition, instance_id):
    from app.curriculum import authoring_chapter
    from app.teacher_scope import require_scope

    authoring_chapter(db, user, definition["chapter_id"], instance_id)
    for node in definition["nodes"]:
        if node.get("activity_id"):
            activity = require_scope(db.get(Activity, node["activity_id"]), instance_id)
            authoring_chapter(db, user, activity.chapter_id, instance_id)
            if activity.owner_id and activity.owner_id != user.id and user.role != "admin":
                raise HTTPException(403, "任务不能绑定其他教师的活动")
        if node.get("navigation_target"):
            require_scope(db.get(Npc, node["navigation_target"]), instance_id)
        if node["type"] in {"TALK_TO_NPC", "PLAY_DIALOGUE", "REPORT_BACK"}:
            require_scope(db.get(Npc, node["target"]), instance_id)


def validate_quest(body: DefinitionInput, db: Session, user: User, classroom_id: int | None = None):
    from app.teacher_scope import teacher_instance_id

    instance_id = teacher_instance_id(db, user, classroom_id)
    try:
        definition = validate_definition(body.definition, db)
        _authoring_definition(db, user, definition, instance_id)
        return {"valid": True, "definition": definition}
    except (ValueError, KeyError, TypeError) as exc:
        raise HTTPException(422, str(exc))


def create_quest(body: DefinitionInput, db: Session, user: User, classroom_id: int | None = None):
    from app.teacher_scope import commit_content, scoped_key, teacher_instance_id

    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    try:
        definition = validate_definition(body.definition, db)
    except (ValueError, KeyError, TypeError) as exc:
        raise HTTPException(422, str(exc))
    _authoring_definition(db, user, definition, instance_id)
    stable_id = definition["id"]
    key = scoped_key(instance_id, "quest", stable_id)
    if db.get(QuestDefinition, key) or (
        instance_id is not None
        and db.scalar(select(QuestDefinition.id).where(QuestDefinition.course_instance_id == instance_id, QuestDefinition.stable_id == stable_id))
    ):
        raise HTTPException(409, "任务ID已存在")
    definition = {**definition, "id": key}
    db.add(
        QuestDefinition(
            id=key,
            chapter_id=definition["chapter_id"],
            title=definition["title"],
            owner_id=user.id,
            course_instance_id=instance_id,
            stable_id=stable_id,
            definition=definition,
        )
    )
    commit_content(db, instance_id)
    return definition


def list_quests(db: Session, user: User, classroom_id: int | None = None):
    from app.teacher_scope import teacher_instance_id

    instance_id = teacher_instance_id(db, user, classroom_id)
    query = select(QuestDefinition).where(QuestDefinition.course_instance_id == instance_id)
    if user.role != "admin":
        query = query.where((QuestDefinition.owner_id.is_(None)) | (QuestDefinition.owner_id == user.id))
    return [q.definition for q in db.scalars(query.order_by(QuestDefinition.id))]
