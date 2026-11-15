"""Capture and replace scoped learner experience state."""

import hashlib
import json

from sqlalchemy import select

from app.course_scope import course_clause, instance_id
from app.models import (
    Activity,
    ActivityProgress,
    ActivityQuestion,
    BuildingUnlock,
    Chapter,
    ChapterRelease,
    CourseInstance,
    Material,
    NodeState,
    Npc,
    PlayerPosition,
    PlayerQuest,
    QuestDefinition,
    Question,
    StoryDefinition,
    StorySession,
    WorldFlag,
)
from app.world_config import load_world_config


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False, default=str).encode()).hexdigest()


def definition_hash(db, classroom_id):
    # Include current definitions, not only authoring revision: imports and media replacements also invalidate saves.
    result = {"world": load_world_config()}
    for model in (Chapter, Material, Activity, Question, QuestDefinition, StoryDefinition, Npc):
        result[model.__tablename__] = sorted(
            [
                dict((c.name, getattr(row, c.name)) for c in model.__table__.columns)
                for row in db.scalars(select(model).where(course_clause(db, model, classroom_id)))
            ],
            key=lambda row: str(row.get("id", row.get("key"))),
        )
    result["releases"] = [
        dict((c.name, getattr(row, c.name)) for c in ChapterRelease.__table__.columns)
        for row in db.scalars(select(ChapterRelease).where(ChapterRelease.classroom_id == classroom_id).order_by(ChapterRelease.chapter_id))
    ]
    result["revision"] = db.get(CourseInstance, instance_id(db, classroom_id)).revision
    result["activity_questions"] = [
        (row.activity_id, row.question_id, row.order_index)
        for row in db.scalars(
            select(ActivityQuestion)
            .join(Activity)
            .where(course_clause(db, Activity, classroom_id))
            .order_by(ActivityQuestion.activity_id, ActivityQuestion.order_index)
        )
    ]
    return digest(result)


STATE_FIELDS = {
    WorldFlag: ("key",),
    BuildingUnlock: ("course_instance_id", "building_id"),
    PlayerPosition: ("map_key", "tile_x", "tile_y", "direction"),
    ActivityProgress: ("activity_id", "page_no", "visited_pages", "completed", "media_progress"),
    StorySession: ("story_id", "npc_key", "interaction_id", "anchor", "node_id", "status", "pending_activity_id", "return_mapping"),
}


def own_rows(db, model, user, classroom_id):
    return list(db.scalars(select(model).where(model.user_id == user.id, model.classroom_id == classroom_id)))


def snapshot(db, user, classroom_id):
    data = {}
    for model, fields in STATE_FIELDS.items():
        rows = own_rows(db, model, user, classroom_id)
        if model is StorySession:
            rows = [row for row in rows if row.status in {"active", "waiting"}]
        data[model.__tablename__] = sorted([{field: getattr(row, field) for field in fields} for row in rows], key=lambda row: digest(row))
    data["quests"] = []
    for quest in sorted(own_rows(db, PlayerQuest, user, classroom_id), key=lambda row: row.quest_id):
        states = db.scalars(select(NodeState).where(NodeState.player_quest_id == quest.id).order_by(NodeState.node_id))
        data["quests"].append(
            {
                "quest_id": quest.quest_id,
                "status": quest.status,
                "completed_at": quest.completed_at.isoformat() if quest.completed_at else None,
                "states": [{k: getattr(row, k) for k in ("node_id", "status", "choice", "evidence")} for row in states],
            }
        )
    return data


def state_token(db, user, classroom_id):
    return digest([definition_hash(db, classroom_id), snapshot(db, user, classroom_id)])
