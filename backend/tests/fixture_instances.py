"""Explicit cloning of retained current sources into one isolated classroom course.

The caller authorizes the teacher and owns the outer transaction. This service
never seeds content, calls external services, deletes source rows, or grades work.
"""
from copy import deepcopy
from uuid import uuid4

from sqlalchemy import select, or_, text
from sqlalchemy.orm.attributes import flag_modified

from app.models import (
    Activity, ActivityProgress, ActivityQuestion, AssessmentAttempt, Chapter, ChapterRelease,
    Classroom, CourseExample, CourseInstance, KnowledgePoint, LearningEvent, LearningRecord,
    Material, MaterialChapter, NodeState, Npc, PlayerQuest, Question, QuestionChapter,
    QuestionKnowledgePoint, QuestionSecret, QuestDefinition, StoryDefinition, StorySession,
    Submission, WorldFlag, WORLD_FLAG_KEY_MAX_LENGTH,
)
from app.integration_models import AssistantCall, AssistantSession

CONTENT_MODELS = (Chapter, KnowledgePoint, Material, Question, Npc, Activity, QuestDefinition, StoryDefinition, CourseExample)
MODEL_NAMES = {Chapter: "chapter", KnowledgePoint: "knowledge_point", Material: "material", Question: "question",
               Npc: "npc", Activity: "activity", QuestDefinition: "quest", StoryDefinition: "story", CourseExample: "example"}


class CourseCloneError(ValueError):
    pass


def classroom_course(db, classroom_id):
    return db.scalar(select(CourseInstance).where(CourseInstance.classroom_id == classroom_id))


def content_mapping(db, instance):
    """Stable source IDs to runtime PKs, independently for each content kind."""
    return {MODEL_NAMES[model]: {row.stable_id: getattr(row, "key" if model is Npc else "id")
            for row in db.scalars(select(model).where(model.course_instance_id == instance.id))} for model in CONTENT_MODELS}


def _key(row):
    return row.key if isinstance(row, Npc) else row.id


def _source_rows(db, model, teacher_id):
    query = select(model).where(model.course_instance_id.is_(None))
    if hasattr(model, "owner_id"):
        query = query.where(or_(model.owner_id.is_(None), model.owner_id == teacher_id))
    return list(db.scalars(query.order_by(*model.__table__.primary_key.columns).with_for_update()))


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
            raise CourseCloneError(f"课程引用缺失或不属于当前教师：{kind}")
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


def _clone_rows(db, instance, sources):
    maps = {name: {} for name in MODEL_NAMES.values()}
    pairs = {model: [] for model in CONTENT_MODELS}
    # First allocate all keys in dependency order. JSON references are replaced
    # only after every target has a new key.
    for model in CONTENT_MODELS:
        for source in sources[model]:
            values = {column.name: deepcopy(getattr(source, column.name)) for column in model.__table__.columns
                      if column.name not in {"id", "key", "course_instance_id", "stable_id"}}
            for field, kind in (("chapter_id", "chapter"), ("material_id", "material"), ("quest_id", "quest"), ("npc_key", "npc")):
                if field in values:
                    values[field] = _lookup(maps, kind, values[field])
            if hasattr(model, "owner_id"):
                values["owner_id"] = instance._teacher_id
            pk = "key" if model is Npc else "id"
            if model in {Question, Npc, Activity, QuestDefinition, StoryDefinition}:
                values[pk] = f"c{instance.id}-{uuid4().hex[:16]}"
            row = model(course_instance_id=instance.id, stable_id=source.stable_id or str(_key(source)), **values)
            db.add(row)
            db.flush()
            maps[MODEL_NAMES[model]][_key(source)] = _key(row)
            pairs[model].append((source, row))
    for model, rows in pairs.items():
        for old, new in rows:
            for field in ("content", "source", "dialogue", "definition"):
                if hasattr(new, field):
                    transformed = _json(getattr(old, field), maps)
                    if field == "definition":
                        transformed["id"] = new.id
                    if model is KnowledgePoint and field == "content" and "id" in transformed:
                        transformed["id"] = new.id
                    if model is CourseExample and field == "content" and "id" in transformed:
                        transformed["id"] = new.id
                    setattr(new, field, transformed)
    db.flush()
    return maps


def _relations(db, maps):
    for model, fields in (
        (MaterialChapter, {"material_id": "material", "chapter_id": "chapter"}),
        (QuestionChapter, {"question_id": "question", "chapter_id": "chapter"}),
        (QuestionKnowledgePoint, {"question_id": "question", "knowledge_point_id": "knowledge_point"}),
        (ActivityQuestion, {"activity_id": "activity", "question_id": "question"}),
    ):
        first = next(iter(fields))
        rows = list(db.scalars(select(model).where(getattr(model, first).in_(maps[fields[first]]))))
        for row in rows:
            values = {field: _lookup(maps, kind, getattr(row, field)) for field, kind in fields.items()}
            if model is ActivityQuestion:
                values["order_index"] = row.order_index
            db.add(model(**values))
    for secret in db.scalars(select(QuestionSecret).where(QuestionSecret.question_id.in_(maps["question"]))):
        db.add(QuestionSecret(question_id=maps["question"][secret.question_id], answer=deepcopy(secret.answer),
                              hidden_cases=deepcopy(secret.hidden_cases)))
    db.flush()


def _flag(value, maps):
    for prefix in ("dialogue:completed:", "dialogue:visited:", "dialogue:choice:"):
        for old, new in maps["npc"].items():
            start = f"{prefix}{old}:"
            if value.startswith(start):
                return f"{prefix}{new}:" + _marker(value[len(start):], maps)
    return value


def _learner_records(db, classroom_id, maps):
    # Scalar PK identities for submissions, records and attempts remain unchanged.
    # Preserve timestamps explicitly where mapped onupdate handlers otherwise fire.
    for model, fields in (
        (ActivityProgress, {"activity_id": "activity"}),
        (LearningRecord, {"activity_id": "activity"}),
        (Submission, {"activity_id": "activity", "question_id": "question"}),
        (AssessmentAttempt, {"activity_id": "activity"}),
        (AssistantSession, {"activity_id": "activity", "material_id": "material"}),
        (StorySession, {"npc_key": "npc", "pending_activity_id": "activity"}),
    ):
        for row in db.scalars(select(model).where(model.classroom_id == classroom_id)
                              .order_by(*model.__table__.primary_key.columns).with_for_update()):
            stamp = getattr(row, "updated_at", None)
            for field, kind in fields.items():
                setattr(row, field, _lookup(maps, kind, getattr(row, field)))
            if model is AssessmentAttempt:
                row.question_ids = [_lookup(maps, "question", key) for key in row.question_ids]
                row.answers = {_lookup(maps, "question", key): deepcopy(value) for key, value in row.answers.items()}
                row.submission_ids = {_lookup(maps, "question", key): value for key, value in row.submission_ids.items()}
            elif model is LearningRecord:
                row.data = _json(row.data, maps)
            elif model is AssistantSession:
                # Message prose and call audit remain untouched; citation IDs are typed.
                row.messages = _json(row.messages, maps)
            elif model is StorySession:
                row.story_id = _lookup(maps, "story", row.story_id, required=False)
                row.return_mapping = _json(row.return_mapping, maps)
                row.receipts = _json(row.receipts, maps)
            if stamp is not None:
                row.updated_at = stamp
                flag_modified(row, "updated_at")
    for row in db.scalars(select(PlayerQuest).where(PlayerQuest.classroom_id == classroom_id).order_by(PlayerQuest.id).with_for_update()):
        row.quest_id = _lookup(maps, "quest", row.quest_id, required=False)
        for state in db.scalars(select(NodeState).where(NodeState.player_quest_id == row.id).order_by(NodeState.node_id).with_for_update()):
            state.evidence = _json(state.evidence, maps)
    for event in db.scalars(select(LearningEvent).where(LearningEvent.classroom_id == classroom_id).order_by(LearningEvent.id).with_for_update()):
        event.evidence = _json(event.evidence, maps)
    for flag in db.scalars(select(WorldFlag).where(WorldFlag.classroom_id == classroom_id).order_by(WorldFlag.user_id, WorldFlag.key).with_for_update()):
        new = _flag(flag.key, maps)
        if len(new) > WORLD_FLAG_KEY_MAX_LENGTH:
            raise CourseCloneError("迁移后的对白证据标识超长，需要先修正课程标识")
        flag.key = new
    for release in db.scalars(select(ChapterRelease).where(ChapterRelease.classroom_id == classroom_id).order_by(ChapterRelease.chapter_id).with_for_update()):
        release.chapter_id = _lookup(maps, "chapter", release.chapter_id)
        release.prerequisite_chapter_id = _lookup(maps, "chapter", release.prerequisite_chapter_id)
    db.flush()


def clone_current_course(db, classroom, *, package_id="legacy-current", name="当前课程", package_version="1"):
    """Explicit current-source migration, idempotent per class; caller commits.

    Authorization must precede this call. A savepoint rolls back this operation on
    invalid references without deleting the sources or another classroom's work.
    Existing instances are returned without cloning or changing their content.
    """
    classroom_id = classroom.id if isinstance(classroom, Classroom) else classroom
    from app.course_scope import lock_course

    lock_course(db, classroom_id, exclusive=True)
    locked = db.scalar(select(Classroom).where(Classroom.id == classroom_id).with_for_update())
    if locked is None:
        raise CourseCloneError("班级不存在")
    existing = classroom_course(db, classroom_id)
    if existing:
        return existing
    if db.scalar(select(AssistantCall.id).where(AssistantCall.classroom_id == classroom_id,
            AssistantCall.status.in_(["running", "cancel_requested"]))):
        raise CourseCloneError("班级仍有进行中的助教回复，请等待结束后迁移课程")
    with db.begin_nested():
        _lock_numeric_tables(db)
        sources = {model: _source_rows(db, model, locked.teacher_id) for model in CONTENT_MODELS}
        if any(row.status in {"queued", "processing"} for row in sources[Material]):
            raise CourseCloneError("资料仍在转换，请等待转换结束后再迁移班级课程")
        chapter_ids = {_key(row) for row in sources[Chapter]}
        for model in (KnowledgePoint, Activity, QuestDefinition):
            if any(row.chapter_id not in chapter_ids for row in sources[model]):
                raise CourseCloneError("内容引用了其他教师的章节")
        quest_ids = {_key(row) for row in sources[QuestDefinition]}
        sources[StoryDefinition] = [row for row in sources[StoryDefinition]
            if (row.chapter_id is None or row.chapter_id in chapter_ids) and (row.quest_id is None or row.quest_id in quest_ids)]
        material_ids = {_key(row) for row in sources[Material]}
        sources[CourseExample] = [row for row in sources[CourseExample]
            if row.content.get("document_id") is None or row.content["document_id"] in material_ids]
        instance = CourseInstance(classroom_id=classroom_id, package_id=package_id, name=name, package_version=package_version)
        instance._teacher_id = locked.teacher_id
        db.add(instance)
        db.flush()
        maps = _clone_rows(db, instance, sources)
        _relations(db, maps)
        _learner_records(db, classroom_id, maps)
        from fixture_instances import configure_fixture_world
        configure_fixture_world(db, instance)
    return instance


def configure_fixture_world(db, instance):
    """Explicit clone-time migration of current teaching anchors and legacy access."""
    from app.world_config import interaction_anchors, load_world_config
    from app.world_authoring import scoped_key
    from app.course_exchange_models import CoursePackageState
    from app.models import BuildingUnlock
    interactions = []
    for key, anchor in load_world_config()["interactions"].items():
        points = interaction_anchors(anchor)
        if len({p["map_key"] for p in points}) != 1:
            raise ValueError("旧交互锚点跨地图，需先整理")
        interactions.append({"id": key, "label": anchor["label"], "prompt": "阅读" if key == "library_shelf" else "查看",
            "trigger": "click", "map_key": points[0]["map_key"], "target": {"object_id": key}})
        stable = f"legacy-world-{key}"
        content = {"schema_version": 1, "mode": "public", "title": anchor["label"], "priority": 0, "bindings": {},
            "entries": {f"interaction:{key}": [{"when": {"always": True}, "node": "start"}]},
            "nodes": [{"id": "start", "type": "action", "action": "open_workspace", "workspace": "library" if key == "library_shelf" else "journal",
                       "next": "end"}, {"id": "end", "type": "end"}]}
        db.add(StoryDefinition(id=scoped_key(instance.id, "story", stable), stable_id=stable, course_instance_id=instance.id,
                               chapter_id=None, quest_id=None, content=content))
    state = CoursePackageState(course_instance_id=instance.id, configuration={"world": {"interactions": interactions},
        "buildings": {"library": {"enabled": True, "default_unlocked": False, "blocked_message": "请先与镇长交谈，了解图书馆。"}}, "assistant": {"library_npc_id": "librarian_doc"}, "editor": {}})
    db.add(state)
    for user_id in db.scalars(select(WorldFlag.user_id).where(WorldFlag.classroom_id == instance.classroom_id, WorldFlag.key == "library_unlocked")):
        if not db.get(BuildingUnlock, (user_id, instance.classroom_id, instance.id, "library")):
            db.add(BuildingUnlock(user_id=user_id, classroom_id=instance.classroom_id, course_instance_id=instance.id, building_id="library"))
