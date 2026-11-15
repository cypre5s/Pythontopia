"""Capture a consistent portable content snapshot for editing and package exchange."""

import hashlib
from copy import deepcopy

from sqlalchemy import select

from app.auth import as_utc
from app.course_exchange_models import CoursePackageState
from app.course_instances import CONTENT_MODELS, MODEL_NAMES, remap_references
from app.models import (
    Activity,
    ActivityQuestion,
    Chapter,
    ChapterRelease,
    CourseExample,
    KnowledgePoint,
    Material,
    MaterialChapter,
    Npc,
    QuestDefinition,
    Question,
    QuestionChapter,
    QuestionKnowledgePoint,
    QuestionSecret,
    StoryDefinition,
)

FIELDS = {
    Chapter: ("title", "order_index"),
    KnowledgePoint: ("chapter_id", "title", "content"),
    Material: ("title", "kind", "content", "status", "error"),
    Question: ("title", "kind", "prompt", "options", "samples", "hints", "starter_code", "source", "source_type", "status"),
    Npc: ("name", "portrait", "sprite", "persona", "placement", "appearance_condition"),
    Activity: ("chapter_id", "title", "kind", "material_id", "npc_key", "assistant_allowed", "content"),
    QuestDefinition: ("chapter_id", "title", "definition"),
    StoryDefinition: ("chapter_id", "quest_id", "content"),
    CourseExample: ("content",),
}


def content_rows(db, instance):
    return {model: list(db.scalars(select(model).where(model.course_instance_id == instance.id))) for model in CONTENT_MODELS}


def row_key(row):
    return row.key if isinstance(row, Npc) else row.id


def stable_id(row):
    return row.stable_id or str(row_key(row))


def related_ids(db, row, relation, target):
    source = relation.activity_id if isinstance(row, Activity) else relation.material_id if isinstance(row, Material) else relation.question_id
    query = select(relation).where(source == row_key(row))
    if relation is ActivityQuestion:
        query = query.order_by(ActivityQuestion.order_index)
    return [getattr(item, target) for item in db.scalars(query)]


def snapshot_content(db, instance):
    rows = content_rows(db, instance)
    state = db.get(CoursePackageState, instance.id)
    configuration = deepcopy(state.configuration) if state else {}
    stored_assets = {asset["storage_key"]: asset for asset in configuration.get("_assets", [])}
    inverse = {MODEL_NAMES[model]: {row_key(row): stable_id(row) for row in items} for model, items in rows.items()}
    data = {kind: [] for kind in MODEL_NAMES.values()}
    secrets = []
    for model, items in rows.items():
        for row in items:
            values = {field: deepcopy(getattr(row, field)) for field in FIELDS[model]}
            if model is Material:
                asset_id = stored_assets.get(row.storage_key, {}).get("id", "material-" + hashlib.sha256(stable_id(row).encode()).hexdigest()[:32])
                preview_key = values["content"].pop("preview_key", None)
                preview_id = stored_assets.get(preview_key, {}).get("id", "preview-" + hashlib.sha256(stable_id(row).encode()).hexdigest()[:32])
                values.update(
                    chapter_ids=related_ids(db, row, MaterialChapter, "chapter_id"),
                    asset_id=asset_id if row.storage_key else None,
                    preview_asset_id=preview_id if preview_key else None,
                )
            elif model is Question:
                values.update(
                    chapter_ids=related_ids(db, row, QuestionChapter, "chapter_id"),
                    knowledge_point_ids=related_ids(db, row, QuestionKnowledgePoint, "knowledge_point_id"),
                )
                secret = db.get(QuestionSecret, row.id)
                secrets.append(
                    {
                        "id": stable_id(row),
                        "answer": deepcopy(secret.answer) if secret else None,
                        "hidden_cases": deepcopy(secret.hidden_cases) if secret else [],
                    }
                )
            elif model is Activity:
                values["question_ids"] = related_ids(db, row, ActivityQuestion, "question_id")
            converted = values if model is Npc else remap_references(values, inverse)
            if model is QuestDefinition:
                converted["definition"]["id"] = stable_id(row)
            if model in {KnowledgePoint, CourseExample} and "id" in converted["content"]:
                converted["content"]["id"] = stable_id(row)
            data[MODEL_NAMES[model]].append({"id": stable_id(row), **converted})
    releases = []
    for row in db.scalars(select(ChapterRelease).where(ChapterRelease.classroom_id == instance.classroom_id)):
        if row.chapter_id not in inverse["chapter"]:
            continue
        releases.append(
            {
                "chapter_id": inverse["chapter"][row.chapter_id],
                "status": row.status,
                "opens_at": as_utc(row.opens_at).isoformat() if row.opens_at else None,
                "prerequisite_chapter_id": inverse["chapter"].get(row.prerequisite_chapter_id),
            }
        )
    return data, secrets, releases, configuration, rows
