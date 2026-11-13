"""Stable identifiers and typed references for isolated classroom courses."""
from copy import deepcopy

from sqlalchemy import select, text

from .models import (
    Activity, Chapter, CourseExample, CourseInstance, KnowledgePoint, Material, Npc, Question, QuestDefinition, StoryDefinition,
)

CONTENT_MODELS = (Chapter, KnowledgePoint, Material, Question, Npc, Activity, QuestDefinition, StoryDefinition, CourseExample)
MODEL_NAMES = {Chapter: "chapter", KnowledgePoint: "knowledge_point", Material: "material", Question: "question",
               Npc: "npc", Activity: "activity", QuestDefinition: "quest", StoryDefinition: "story", CourseExample: "example"}


def classroom_course(db, classroom_id):
    return db.scalar(select(CourseInstance).where(CourseInstance.classroom_id == classroom_id))


def content_mapping(db, instance):
    """Stable source IDs to runtime PKs, independently for each content kind."""
    return {MODEL_NAMES[model]: {row.stable_id: getattr(row, "key" if model is Npc else "id")
            for row in db.scalars(select(model).where(model.course_instance_id == instance.id))} for model in CONTENT_MODELS}


def _lock_numeric_tables(db):
    if db.get_bind().dialect.name != "postgresql":
        return
    # Import sources have explicit integer IDs; do not let an unadvanced sequence
    # collide with those retained sources. Table names are internal constants.
    for table in ("chapters", "knowledge_points", "materials", "course_examples"):
        db.execute(text(f"LOCK TABLE {table} IN SHARE ROW EXCLUSIVE MODE"))
        db.execute(text(f"""SELECT setval(pg_get_serial_sequence('{table}', 'id'),
            GREATEST(COALESCE((SELECT MAX(id) FROM {table}), 1),
                     nextval(pg_get_serial_sequence('{table}', 'id'))), true)"""))


def _lookup(maps, kind, value, *, required=True):
    if value is None:
        return None
    result = maps[kind].get(value)
    if result is None:
        if required:
            raise ValueError(f"课程引用缺失或不属于当前教师：{kind}")
        return value
    return result


def _marker(value, maps):
    if not isinstance(value, str):
        return value
    for old, new in maps["story"].items():
        prefix = f"story:{old}:"
        if value.startswith(prefix):
            return f"story:{new}:" + value[len(prefix):]
    return value


def _json(value, maps, context=None):
    """Rewrite only typed references; never replace text, answers, node or event IDs."""
    scalar = {"chapter_id": "chapter", "prerequisite_chapter_id": "chapter", "material_id": "material",
              "document_id": "material", "activity_id": "activity", "pending_activity_id": "activity",
              "question_id": "question", "knowledge_point_id": "knowledge_point", "quest_id": "quest",
              "story_id": "story", "npc_key": "npc", "speaker_npc_id": "npc", "activity_completed": "activity"}
    lists = {"chapter_ids": "chapter", "question_ids": "question", "knowledge_point_ids": "knowledge_point"}
    if isinstance(value, list):
        return [_json(item, maps, context) for item in value]
    if not isinstance(value, dict):
        return deepcopy(value)
    result = {}
    for key, item in value.items():
        if key in scalar:
            result[key] = _lookup(maps, scalar[key], item)
        elif key in lists and isinstance(item, list):
            result[key] = [_lookup(maps, lists[key], ref) for ref in item]
        elif key == "id" and context in {"chapter", "quest"}:
            result[key] = _lookup(maps, context, item)
        elif key == "entries" and isinstance(item, dict):
            result[key] = {npc if npc.startswith("interaction:") else _lookup(maps, "npc", npc): _json(routes, maps) for npc, routes in item.items()}
        elif key in {"target", "navigation_target"} and isinstance(item, str):
            result[key] = _lookup(maps, "npc", item, required=False)
        elif key == "dialogue_id":
            result[key] = _marker(item, maps)
        else:
            result[key] = _json(item, maps, key)
    return result
