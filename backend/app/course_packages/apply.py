"""Apply validated content and media mappings inside the caller-owned transaction."""

import hashlib
from copy import deepcopy
from datetime import datetime
from uuid import uuid4

from sqlalchemy import delete

from app.course_content.snapshot import FIELDS, content_rows, row_key, stable_id
from app.course_exchange_models import CoursePackageState
from app.course_instances import CONTENT_MODELS, MODEL_NAMES, content_mapping, lock_content_tables, remap_references
from app.course_packages.manifest import PackageError
from app.materials.storage import get_storage
from app.models import (
    Activity,
    ActivityQuestion,
    ActivityMaterial,
    Chapter,
    ChapterRelease,
    CourseExample,
    CourseInstance,
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


def _delete_content(db, instance, incoming):
    rows = content_rows(db, instance)
    removing = {model: [row_key(row) for row in values if stable_id(row) not in incoming[MODEL_NAMES[model]]] for model, values in rows.items()}
    # All relation removals are explicit; never depend on database cascades for learner rows.
    for relation, field, model in (
        (ActivityQuestion, ActivityQuestion.activity_id, Activity),
        (ActivityMaterial, ActivityMaterial.activity_id, Activity),
        (ActivityMaterial, ActivityMaterial.material_id, Material),
        (ActivityQuestion, ActivityQuestion.question_id, Question),
        (QuestionSecret, QuestionSecret.question_id, Question),
        (QuestionKnowledgePoint, QuestionKnowledgePoint.question_id, Question),
        (QuestionKnowledgePoint, QuestionKnowledgePoint.knowledge_point_id, KnowledgePoint),
        (QuestionChapter, QuestionChapter.question_id, Question),
        (QuestionChapter, QuestionChapter.chapter_id, Chapter),
        (MaterialChapter, MaterialChapter.material_id, Material),
        (MaterialChapter, MaterialChapter.chapter_id, Chapter),
    ):
        if removing[model]:
            db.execute(delete(relation).where(field.in_(removing[model])))
    for model in (StoryDefinition, QuestDefinition, Activity, Question, KnowledgePoint, Material, Npc, CourseExample, Chapter):
        if removing[model]:
            db.execute(delete(model).where((model.key if model is Npc else model.id).in_(removing[model])))
    db.flush()


def import_bundle(db, classroom, instance, bundle, root):
    """Caller holds exclusive classroom gate; caller owns the single commit/rollback."""
    manifest, data = bundle["manifest"], bundle["data"]
    replace = instance is not None and instance.package_id != manifest["package_id"]
    if instance is None:
        instance = CourseInstance(
            classroom_id=classroom.id,
            package_id=manifest["package_id"],
            name=manifest["name"],
            package_version=manifest["package_version"],
            created_at=datetime.fromisoformat(manifest["created_at"]),
            revision=0,
        )
        db.add(instance)
        db.flush()
    incoming = {kind: {row["id"] for row in rows} if not replace else set() for kind, rows in data.items()}
    db.execute(delete(ChapterRelease).where(ChapterRelease.classroom_id == instance.classroom_id))
    if replace:
        _delete_content(db, instance, incoming)
        instance.created_at = datetime.fromisoformat(manifest["created_at"])
    existing = {model: {stable_id(row): row for row in rows} for model, rows in content_rows(db, instance).items()}
    maps = content_mapping(db, instance)
    lock_content_tables(db)
    # Vacate order slots first, so swapping chapter positions is atomic and conflict-free.
    for index, row in enumerate(existing[Chapter].values()):
        row.order_index = -index - 1
    db.flush()
    pairs = {}
    for model in CONTENT_MODELS:
        kind, pairs[model] = MODEL_NAMES[model], []
        for source in data[kind]:
            row = existing[model].get(source["id"])
            if row is None:
                values = {field: deepcopy(source[field]) for field in FIELDS[model]}
                for field, target in (("chapter_id", "chapter"), ("material_id", "material"), ("quest_id", "quest"), ("npc_key", "npc")):
                    if field in values and values[field] is not None:
                        values[field] = maps[target][values[field]]
                for field in ("content", "definition"):
                    if field in values:
                        values[field] = {}
                if hasattr(model, "owner_id"):
                    values["owner_id"] = classroom.teacher_id
                if model in {Question, Npc, Activity, QuestDefinition, StoryDefinition}:
                    values["key" if model is Npc else "id"] = f"c{instance.id}-{uuid4().hex[:16]}"
                row = model(course_instance_id=instance.id, stable_id=source["id"], **values)
                db.add(row)
                db.flush()
            maps[kind][source["id"]] = row_key(row)
            pairs[model].append((source, row))
    adapter, assets = get_storage(), {}
    character_refs = {row[kind][6:] for row in data["npc"] for kind in ("portrait", "sprite") if row[kind].startswith("asset:")}
    for asset in bundle["assets"]:
        if asset.get("character_kind") and asset["id"] not in character_refs:
            continue
        source = root / asset["path"]
        key = f"course-assets/{asset['sha256']}{source.suffix.lower()}"
        if asset.get("character_kind"):
            identity = hashlib.sha256(asset["id"].encode()).hexdigest()[:24]
            key = f"characters/{instance.id}/{identity}-{asset['sha256']}{source.suffix.lower()}"
        try:
            size = adapter.size(key)
        except Exception as exc:
            code = getattr(exc, "response", {}).get("Error", {}).get("Code")
            if not isinstance(exc, (FileNotFoundError, KeyError)) and code not in {"404", "NoSuchKey", "NotFound"}:
                raise PackageError("私有资源存储暂不可用") from exc
            _store_file(adapter, key, source)
        else:
            digest = hashlib.sha256()
            for chunk in adapter.chunks(key, 0, size - 1):
                digest.update(chunk)
            if size != asset["size_bytes"] or digest.hexdigest() != asset["sha256"]:
                raise PackageError("私有资源存储校验失败，未覆盖已有资源")
        assets[asset["id"]] = {**asset, "storage_key": key}
    for model, values in pairs.items():
        for source, row in values:
            fields = {field: source[field] for field in FIELDS[model]}
            converted = deepcopy(fields) if model is Npc else remap_references(fields, maps)
            if model is QuestDefinition:
                converted["definition"]["id"] = row.id
            if model in {KnowledgePoint, CourseExample} and "id" in converted["content"]:
                converted["content"]["id"] = row.id
            for field, value in converted.items():
                setattr(row, field, value)
            if model is Material:
                row.storage_key = assets[source["asset_id"]]["storage_key"] if source["asset_id"] else None
                if source["preview_asset_id"]:
                    row.content = {**row.content, "preview_key": assets[source["preview_asset_id"]]["storage_key"]}
    db.flush()
    relations = (
        (Material, MaterialChapter, "material_id", "chapter_ids", "chapter_id", "chapter"),
        (Question, QuestionChapter, "question_id", "chapter_ids", "chapter_id", "chapter"),
        (Question, QuestionKnowledgePoint, "question_id", "knowledge_point_ids", "knowledge_point_id", "knowledge_point"),
        (Activity, ActivityQuestion, "activity_id", "question_ids", "question_id", "question"),
    )
    for model, relation, field, source_field, target_field, kind in relations:
        for source, row in pairs[model]:
            db.execute(delete(relation).where(getattr(relation, field) == row_key(row)))
            for index, key in enumerate(source[source_field]):
                values = {field: row_key(row), target_field: maps[kind][key]}
                if relation is ActivityQuestion:
                    values["order_index"] = index
                db.add(relation(**values))
    for source, row in pairs[Activity]:
        from app.course_content.resources import set_resources
        set_resources(db, row, remap_references(source["resources"], maps))
    for source in bundle["secrets"]:
        key = maps["question"][source["id"]]
        row = db.get(QuestionSecret, key)
        if row is None:
            row = QuestionSecret(question_id=key)
            db.add(row)
        row.answer, row.hidden_cases = deepcopy(source["answer"]), deepcopy(source["hidden_cases"])
    if not replace:
        db.flush()
        _delete_content(db, instance, incoming)
    for source in bundle["course"]["releases"]:
        values = remap_references(source, maps)
        values["opens_at"] = datetime.fromisoformat(values["opens_at"]) if values["opens_at"] else None
        db.add(ChapterRelease(classroom_id=classroom.id, **values))
    state = db.get(CoursePackageState, instance.id)
    if state is None:
        state = CoursePackageState(course_instance_id=instance.id)
        db.add(state)
    previous_config = state.configuration or {}
    live_keys = {asset["storage_key"] for asset in assets.values()}
    garbage = [
        *previous_config.get("_character_garbage", []),
        *(
            asset["storage_key"]
            for asset in previous_config.get("_assets", [])
            if asset.get("character_kind") and asset["storage_key"] not in live_keys
        ),
    ]
    for npc in data["npc"]:
        for kind in ("portrait", "sprite"):
            if npc[kind].startswith("asset:"):
                assets[npc[kind][6:]]["owner_npc_id"] = npc["id"]
    state.configuration = {**deepcopy(bundle["configuration"]), "_assets": list(assets.values()), "_character_garbage": list(dict.fromkeys(garbage))}
    instance.package_id, instance.name, instance.package_version = manifest["package_id"], manifest["name"], manifest["package_version"]
    instance.revision += 1
    db.flush()
    return instance


def _store_file(adapter, key, source):
    try:
        adapter.put(key, source)
    except Exception as exc:
        raise PackageError("私有资源写入失败，课程未发布") from exc
