"""Authorized classroom course exchange. Packages contain definitions, never learner state."""
from copy import deepcopy
from datetime import datetime, timedelta
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import tempfile
from typing import Annotated
from uuid import UUID, uuid4
import zipfile

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import delete, func, select
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session
import yaml
from yaml.tokens import AliasToken, AnchorToken

from .auth import as_utc, require_teacher
from .config import ROOT
from .course_exchange_models import CourseImportPreview, CoursePackageState
from .course_instances import CONTENT_MODELS, MODEL_NAMES, _json, _lock_numeric_tables, classroom_course, content_mapping
from .course_package import PackageError, PackageLimits, ManifestLoader, _relative, _unique_pairs, validate_package
from .course_scope import lock_course
from .db import get_db
from .integration_models import AssistantCall, AssistantSession
from .models import (Activity, ActivityProgress, ActivityQuestion, AssessmentAttempt, Chapter, ChapterRelease, Classroom,
                     CourseExample, CourseInstance, KnowledgePoint, LearningRecord, Material, MaterialChapter, Npc, PlayerQuest,
                     Question, QuestionChapter, QuestionKnowledgePoint, QuestionSecret, QuestDefinition, StoryDefinition,
                     StorySession, Submission, User, now)
from .storage import get_storage, validate_upload

router = APIRouter(prefix="/teacher/classrooms/{classroom_id}/course", tags=["course-packages"])
LIMITS = PackageLimits()
MAX_ARCHIVE = int(os.getenv("REBORN_COURSE_UPLOAD_BYTES", str(2 * 1024**3)))
FIELDS = {
    Chapter: ("title", "order_index"), KnowledgePoint: ("chapter_id", "title", "content"),
    Material: ("title", "kind", "content", "status", "error"),
    Question: ("title", "kind", "prompt", "options", "samples", "source", "source_type", "status"),
    Npc: ("name", "map_key", "tile_x", "tile_y", "portrait", "dialogue"),
    Activity: ("chapter_id", "title", "kind", "material_id", "npc_key", "assistant_allowed", "content"),
    QuestDefinition: ("chapter_id", "title", "definition"), StoryDefinition: ("chapter_id", "quest_id", "content"),
    CourseExample: ("content",),
}
EXTRAS = {Material: {"chapter_ids", "asset_id", "preview_asset_id"}, Question: {"chapter_ids", "knowledge_point_ids"}, Activity: {"question_ids"}}
JSONL_FILES = {Material: "content/materials.jsonl", Question: "content/questions/questions-001.jsonl",
               KnowledgePoint: "content/knowledge-points.jsonl", CourseExample: "content/examples.jsonl"}
CONFIG_FILES = {"world": "world/interactions.yaml", "buildings": "world/buildings.yaml",
                "assistant": "assistant/roles.yaml", "editor": "editor/layouts.json"}


def exchange_root():
    root = Path(os.getenv("REBORN_COURSE_EXCHANGE_ROOT", str(ROOT / "data/course"))).resolve()
    root.mkdir(parents=True, exist_ok=True)
    return root


def exchange_path(key):
    root = exchange_root()
    path = (root / _relative(key)).resolve()
    if not path.is_relative_to(root):
        raise PackageError("课程交换路径越界")
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def _teacher(db, user, classroom_id):
    classroom = db.get(Classroom, classroom_id)
    if not classroom:
        raise HTTPException(404, "班级不存在")
    if user.role != "admin" and classroom.teacher_id != user.id:
        raise HTTPException(403, "只能管理自己的班级课程")
    return classroom


def _rows(db, instance):
    return {model: list(db.scalars(select(model).where(model.course_instance_id == instance.id))) for model in CONTENT_MODELS}


def _pk(row):
    return row.key if isinstance(row, Npc) else row.id


def _stable(row):
    return row.stable_id or str(_pk(row))


def _refs(db, row, relation, target):
    source = relation.activity_id if isinstance(row, Activity) else relation.material_id if isinstance(row, Material) else relation.question_id
    query = select(relation).where(source == _pk(row))
    if relation is ActivityQuestion:
        query = query.order_by(ActivityQuestion.order_index)
    return [getattr(item, target) for item in db.scalars(query)]


def _snapshot(db, instance):
    rows = _rows(db, instance)
    state = db.get(CoursePackageState, instance.id)
    configuration = deepcopy(state.configuration) if state else {}
    stored_assets = {asset["storage_key"]: asset for asset in configuration.get("_assets", [])}
    inverse = {MODEL_NAMES[model]: {_pk(row): _stable(row) for row in items} for model, items in rows.items()}
    data = {kind: [] for kind in MODEL_NAMES.values()}
    secrets = []
    for model, items in rows.items():
        for row in items:
            values = {field: deepcopy(getattr(row, field)) for field in FIELDS[model]}
            if model is Material:
                asset_id = stored_assets.get(row.storage_key, {}).get("id", "material-" + hashlib.sha256(_stable(row).encode()).hexdigest()[:32])
                preview_key = values["content"].pop("preview_key", None)
                preview_id = stored_assets.get(preview_key, {}).get("id", "preview-" + hashlib.sha256(_stable(row).encode()).hexdigest()[:32])
                values.update(chapter_ids=_refs(db, row, MaterialChapter, "chapter_id"), asset_id=asset_id if row.storage_key else None,
                              preview_asset_id=preview_id if preview_key else None)
            elif model is Question:
                values.update(chapter_ids=_refs(db, row, QuestionChapter, "chapter_id"),
                              knowledge_point_ids=_refs(db, row, QuestionKnowledgePoint, "knowledge_point_id"))
                secret = db.get(QuestionSecret, row.id)
                secrets.append({"id": _stable(row), "answer": deepcopy(secret.answer) if secret else None,
                                "hidden_cases": deepcopy(secret.hidden_cases) if secret else []})
            elif model is Activity:
                values["question_ids"] = _refs(db, row, ActivityQuestion, "question_id")
            converted = _json(values, inverse)
            if model is QuestDefinition:
                converted["definition"]["id"] = _stable(row)
            if model in {KnowledgePoint, CourseExample} and "id" in converted["content"]:
                converted["content"]["id"] = _stable(row)
            data[MODEL_NAMES[model]].append({"id": _stable(row), **converted})
    releases = []
    for row in db.scalars(select(ChapterRelease).where(ChapterRelease.classroom_id == instance.classroom_id)):
        if row.chapter_id not in inverse["chapter"]:
            continue
        releases.append({"chapter_id": inverse["chapter"][row.chapter_id], "status": row.status,
                         "opens_at": as_utc(row.opens_at).isoformat() if row.opens_at else None,
                         "prerequisite_chapter_id": inverse["chapter"].get(row.prerequisite_chapter_id)})
    return data, secrets, releases, configuration, rows


def _write_yaml(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(yaml.safe_dump(value, allow_unicode=True, sort_keys=False), encoding="utf-8")


def _write_jsonl(path, values):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(row, ensure_ascii=False, allow_nan=False) + "\n" for row in values), encoding="utf-8")


def write_package(db, instance, root):
    data, secrets, releases, config, rows = _snapshot(db, instance)
    if any(material.status in {"queued", "processing"} for material in rows[Material]):
        raise PackageError("班级资料仍在转换，请完成后导出课程")
    created = as_utc(instance.created_at).isoformat()
    manifest = {"schema_version": 1, "package_id": instance.package_id, "name": instance.name,
                "package_version": instance.package_version, "created_at": created, "exported_at": now().isoformat(),
                "requires": {"world_id": "pythontopia-town", "world_schema_version": 1,
                             "capabilities": ["reading", "practice", "assessment", "dialogue", "tasks", "library"]},
                "entry": "curriculum/course.yaml"}
    if any(material.kind == "video" for material in rows[Material]):
        manifest["requires"]["capabilities"].append("video")
    _write_yaml(root / "manifest.yaml", manifest)
    _write_yaml(root / "curriculum/course.yaml", {"schema_version": 1, "title": instance.name, "releases": releases})
    chapter_paths = {row["id"]: f"curriculum/chapters/{index:04d}" for index, row in enumerate(sorted(data["chapter"], key=lambda r: r["order_index"]))}
    for row in data["chapter"]:
        _write_yaml(root / chapter_paths[row["id"]] / "chapter.yaml", row)
    for model, path in JSONL_FILES.items():
        _write_jsonl(root / path, data[MODEL_NAMES[model]])
    _write_jsonl(root / "content/secrets/answers.jsonl", secrets)
    _write_yaml(root / "world/npcs.yaml", data["npc"])
    for index, row in enumerate(data["activity"]):
        _write_yaml(root / f"content/activities/{index:06d}.yaml", row)
    for index, row in enumerate(data["quest"]):
        _write_yaml(root / chapter_paths[row["chapter_id"]] / f"quests/{index:06d}.yaml", row)
    for index, row in enumerate(data["story"]):
        prefix = chapter_paths[row["chapter_id"]] + "/flows" if row["chapter_id"] else "world/flows"
        _write_yaml(root / prefix / f"{index:06d}.yaml", row)
    for key, path in CONFIG_FILES.items():
        _write_yaml(root / path, config.get(key, {})) if key != "editor" else (root / "editor").mkdir(exist_ok=True)
        if key == "editor":
            (root / path).write_text(json.dumps(config.get(key, {}), ensure_ascii=False), encoding="utf-8")
    adapter, assets = get_storage(), []
    original_assets = {asset["id"]: asset for asset in config.get("_assets", [])}
    asset_ids = {row["id"]: row["asset_id"] for row in data["material"]}
    preview_ids = {row["id"]: row["preview_asset_id"] for row in data["material"]}
    export_assets = []
    for material in rows[Material]:
        if not material.storage_key:
            if material.kind in {"pdf", "video", "presentation"}:
                raise PackageError("课程包含缺失媒体，不能完整导出")
            continue
        asset_id = asset_ids[_stable(material)]
        export_assets.append({**original_assets.get(asset_id, {}), "id": asset_id, "storage_key": material.storage_key})
        if material.content.get("preview_key"):
            preview_id = preview_ids[_stable(material)]
            export_assets.append({**original_assets.get(preview_id, {}), "id": preview_id, "storage_key": material.content["preview_key"]})
    exported_ids = {asset["id"] for asset in export_assets}
    export_assets.extend(asset for key, asset in original_assets.items() if key not in exported_ids)
    for source in {asset["id"]: asset for asset in export_assets}.values():
        suffix = Path(source["storage_key"]).suffix.lower()
        from .course_package import _MEDIA
        media_type = _MEDIA.get(suffix)
        if media_type is None:
            raise PackageError("课程含不支持导出的媒体格式")
        asset_id = source["id"]
        # Hash-derived names never reveal a server path and never depend on a user filename.
        path = root / "assets/documents" / (hashlib.sha256(asset_id.encode()).hexdigest() + suffix)
        path.parent.mkdir(parents=True, exist_ok=True)
        try:
            if adapter.size(source["storage_key"]) > LIMITS.file_bytes:
                raise PackageError("媒体超过导出单文件限制")
            adapter.copy_to(source["storage_key"], path)
        except Exception as exc:
            raise PackageError("课程媒体缺失或无法读取") from exc
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        assets.append({"id": asset_id, "path": path.relative_to(root).as_posix(), "media_type": media_type,
                       "size_bytes": path.stat().st_size, "sha256": digest.hexdigest(),
                       "source": source.get("source", "教师授权班级当前资料"),
                       "license": source.get("license", "仅按原资料授权使用；本清单不新增授权")})
    _write_jsonl(root / "assets/index.jsonl", assets)
    validate_package(root)
    return manifest


def unpack_archive(archive, root):
    if archive.stat().st_size > MAX_ARCHIVE:
        raise PackageError("课程压缩包超过上传限制")
    try:
        with zipfile.ZipFile(archive) as source:
            members = source.infolist()
            if len(members) > LIMITS.files:
                raise PackageError("压缩包条目过多")
            seen, total = set(), 0
            for item in members:
                name = _relative(item.orig_filename[:-1] if item.is_dir() else item.orig_filename)
                folded = name.casefold()
                if folded in seen or item.flag_bits & 1:
                    raise PackageError("压缩包包含重复路径或加密文件")
                seen.add(folded)
                mode = item.external_attr >> 16
                if stat.S_ISLNK(mode) or (stat.S_IFMT(mode) and not (stat.S_ISDIR(mode) or stat.S_ISREG(mode))):
                    raise PackageError("压缩包不能包含链接或特殊文件")
                total += item.file_size
                if item.file_size > LIMITS.file_bytes or total > LIMITS.total_bytes or len(Path(name).parts) > LIMITS.depth:
                    raise PackageError("压缩包解压大小或深度超限")
                target = root / name
                if item.is_dir():
                    target.mkdir(parents=True, exist_ok=True)
                    continue
                target.parent.mkdir(parents=True, exist_ok=True)
                size = 0
                with source.open(item) as reader, target.open("xb") as writer:
                    while chunk := reader.read(1024 * 1024):
                        size += len(chunk)
                        if size > item.file_size:
                            raise PackageError("压缩包文件大小不一致")
                        writer.write(chunk)
    except (zipfile.BadZipFile, RuntimeError, OSError, NotImplementedError) as exc:
        raise PackageError("课程ZIP文件无效或不可读取") from exc


def _load(path, *, json_lines=False):
    if not path.is_file() or path.stat().st_size > LIMITS.index_bytes:
        raise PackageError("课程配置缺失或超过16MiB")
    try:
        raw = path.read_text(encoding="utf-8-sig")
        if json_lines:
            values = [json.loads(line, object_pairs_hook=_unique_pairs, parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))
                      for line in raw.splitlines() if line.strip()]
            if len(values) > 10000:
                raise ValueError()
            return values
        for index, token in enumerate(yaml.scan(raw)):
            if index > 200000 or isinstance(token, (AliasToken, AnchorToken)):
                raise ValueError()
        return yaml.load(raw, Loader=ManifestLoader)
    except (UnicodeError, ValueError, yaml.YAMLError, RecursionError) as exc:
        raise PackageError("课程配置格式无效、含重复键或超限") from exc


def load_bundle(root):
    validate_package(root)
    accepted = {"manifest.yaml", "curriculum/course.yaml", "world/npcs.yaml", "assets/index.jsonl",
                "content/secrets/answers.jsonl", *JSONL_FILES.values(), *CONFIG_FILES.values()}
    patterns = ("curriculum/chapters/*/chapter.yaml", "curriculum/chapters/*/quests/*.yaml",
                "curriculum/chapters/*/flows/*.yaml", "world/flows/*.yaml", "content/activities/*.yaml",
                "content/questions/*.jsonl")
    for pattern in patterns:
        accepted.update(path.relative_to(root).as_posix() for path in root.glob(pattern) if path.is_file())
    asset_rows = _load(root / "assets/index.jsonl", json_lines=True)
    accepted.update(row["path"] for row in asset_rows)
    if any(path.relative_to(root).as_posix() not in accepted for path in root.rglob("*") if path.is_file()):
        raise PackageError("课程包含未识别配置文件，不能静默丢弃")
    data = {kind: [] for kind in MODEL_NAMES.values()}
    data["chapter"] = [_load(path) for path in sorted(root.glob("curriculum/chapters/*/chapter.yaml"))]
    data["quest"] = [_load(path) for path in sorted(root.glob("curriculum/chapters/*/quests/*.yaml"))]
    data["story"] = [_load(path) for path in sorted(root.glob("curriculum/chapters/*/flows/*.yaml"))] + [_load(path) for path in sorted(root.glob("world/flows/*.yaml"))]
    data["activity"] = [_load(path) for path in sorted(root.glob("content/activities/*.yaml"))]
    data["npc"] = _load(root / "world/npcs.yaml")
    for model, path in JSONL_FILES.items():
        if model is Question:
            data["question"] = [row for file in sorted((root / "content/questions").glob("*.jsonl")) for row in _load(file, json_lines=True)]
        else:
            data[MODEL_NAMES[model]] = _load(root / path, json_lines=True)
    secrets = _load(root / "content/secrets/answers.jsonl", json_lines=True)
    assets = asset_rows
    config = {key: _load(root / path) for key, path in CONFIG_FILES.items()}
    course = _load(root / "curriculum/course.yaml")
    manifest = _load(root / "manifest.yaml")
    bundle = {"data": data, "secrets": secrets, "assets": assets, "configuration": config, "course": course, "manifest": manifest}
    validate_bundle(bundle, root)
    return bundle


def _object(value, keys, required=None):
    if not isinstance(value, dict) or set(value) - set(keys) or not set(required or []).issubset(value):
        raise PackageError("课程对象字段缺失或包含未知字段")


def _short(value, maximum=100):
    return isinstance(value, str) and bool(value.strip()) and len(value) <= maximum


def _bounded(value, depth=0, budget=None):
    budget = [0] if budget is None else budget
    budget[0] += 1
    if depth > 24 or budget[0] > 200000:
        raise PackageError("课程配置嵌套或对象数量超限")
    if isinstance(value, dict):
        if any(not isinstance(key, str) for key in value):
            raise PackageError("配置字段必须为字符串")
        for item in value.values():
            _bounded(item, depth + 1, budget)
    elif isinstance(value, list):
        for item in value:
            _bounded(item, depth + 1, budget)
    elif value is not None and type(value) not in (str, int, float, bool):
        raise PackageError("配置只支持JSON值")
    elif isinstance(value, float):
        import math
        if not math.isfinite(value):
            raise PackageError("配置不能包含非有限数字")


def validate_bundle(bundle, root):
    """Validate portable references and full graphs before any database/storage writes."""
    from types import SimpleNamespace
    from .classrooms import QuestionInput
    from .dialogues import validate_dialogue
    from .learning_rules import validate_learning
    from .quests import validate_definition
    from .story import validate_story
    from .story_conditions import validate_condition
    from .world_config import valid_portrait, validate_point
    from .world_authoring import reachable_cells
    try:
        _bounded(bundle)
        data = bundle["data"]
        indexed, maps = {}, {}
        for model, fields in FIELDS.items():
            kind, items = MODEL_NAMES[model], data[MODEL_NAMES[model]]
            if not isinstance(items, list) or len(items) > 10000:
                raise PackageError("课程领域条目超限")
            indexed[kind] = {}
            for row in items:
                keys = {"id", *fields, *EXTRAS.get(model, set())}
                _object(row, keys, keys)
                if not _short(row["id"]) or row["id"] in indexed[kind]:
                    raise PackageError("课程稳定ID无效或重复")
                if "title" in row and not _short(row["title"], 200):
                    raise PackageError("课程标题无效")
                if any(not isinstance(row[field], dict) for field in ("content", "definition", "source") if field in row):
                    raise PackageError("课程内容必须为对象")
                indexed[kind][row["id"]] = row
            maps[kind] = {key: (i + 1 if model in {Chapter, Material, KnowledgePoint, CourseExample} else key)
                          for i, key in enumerate(indexed[kind])}
        # Typed conversion resolves every declared foreign reference, including conditions.
        converted = {kind: {key: _json(row, maps) for key, row in items.items()} for kind, items in indexed.items()}
        for model in (KnowledgePoint, Material, Activity, QuestDefinition):
            for row in indexed[MODEL_NAMES[model]].values():
                if "chapter_id" in row and row["chapter_id"] is None:
                    raise PackageError("此类课程条目必须绑定章节")
        orders = [row["order_index"] for row in data["chapter"]]
        if any(type(order) is not int or not 0 <= order <= 100000 for order in orders) or len(set(orders)) != len(orders):
            raise PackageError("章节顺序无效或重复")
        secret_map = {}
        for row in bundle["secrets"]:
            _object(row, {"id", "answer", "hidden_cases"}, {"id", "answer", "hidden_cases"})
            if row["id"] not in indexed["question"] or row["id"] in secret_map:
                raise PackageError("私有答案引用缺失或重复")
            secret_map[row["id"]] = row
        if set(secret_map) != set(indexed["question"]):
            raise PackageError("每道题必须具有私有答案记录（草稿可为空）")
        assets = {asset["id"]: asset for asset in bundle["assets"]}
        for row in data["material"]:
            if row["kind"] not in {"text", "pdf", "video", "presentation"} or row["status"] not in {"ready", "failed", "missing"}:
                raise PackageError("资料类型或状态无效")
            if row["error"] is not None and not isinstance(row["error"], str):
                raise PackageError("资料错误信息无效")
            if row["asset_id"] is not None and row["asset_id"] not in assets:
                raise PackageError("资料引用缺失资源")
            if "preview_key" in row["content"] or "storage_key" in row["content"]:
                raise PackageError("课程包不能包含服务器存储键")
            if row["preview_asset_id"] is not None:
                preview_asset = assets.get(row["preview_asset_id"])
                if not preview_asset or preview_asset["media_type"] != "application/pdf":
                    raise PackageError("转换预览必须引用实际PDF资源")
                validate_upload(root / preview_asset["path"], ".pdf")
            if row["kind"] == "presentation" and row["status"] == "ready" and not row["preview_asset_id"]:
                raise PackageError("已就绪演示文稿缺少转换预览PDF")
            if row["kind"] != "text" and row["asset_id"] is None:
                raise PackageError("完整课程包不能包含缺失媒体")
            pages = row["content"].get("pages", [])
            if not isinstance(pages, list) or len(pages) > 10000:
                raise PackageError("资料页数据无效")
            ids, numbers = set(), set()
            for page in pages:
                if not isinstance(page, dict) or type(page.get("id")) is not int or type(page.get("page_no")) is not int:
                    raise PackageError("资料页需要原始整数ID与页码")
                if page["id"] in ids or page["page_no"] in numbers or page["id"] < 1 or page["page_no"] < 1:
                    raise PackageError("资料页重复或页码无效")
                ids.add(page["id"])
                numbers.add(page["page_no"])
            if len(row["chapter_ids"]) != len(set(row["chapter_ids"])):
                raise PackageError("资料章节关联重复")
            if row["asset_id"]:
                asset = assets[row["asset_id"]]
                from .storage import KINDS
                suffix = Path(asset["path"]).suffix.lower()
                if KINDS.get(suffix) != row["kind"]:
                    raise PackageError("资料类型与资源文件不匹配")
                validate_upload(root / asset["path"], suffix)
        for key, row in converted["question"].items():
            QuestionInput.model_validate({**row, **{field: secret_map[key][field] for field in ("answer", "hidden_cases")}}, strict=True)
            portable = indexed["question"][key]
            for point_id in portable["knowledge_point_ids"]:
                if indexed["knowledge_point"][point_id]["chapter_id"] not in portable["chapter_ids"]:
                    raise PackageError("题目知识点必须属于关联章节")
            source = portable["source"]
            if "page_no" in source and ("material_id" not in source or source["page_no"] not in
                    {page["page_no"] for page in indexed["material"][source["material_id"]]["content"].get("pages", [])}):
                raise PackageError("题目来源页不存在")
        for row in data["npc"]:
            if not _short(row["name"], 80) or not valid_portrait(row["portrait"]):
                raise PackageError("NPC名称或立绘无效")
            validate_point(row["map_key"], row["tile_x"], row["tile_y"])
            if (row["tile_x"], row["tile_y"]) not in reachable_cells(row["map_key"]):
                raise PackageError("NPC位置无法从地图入口到达")
            if row["dialogue"]:
                validate_dialogue(row["dialogue"])
        for row in data["activity"]:
            if row["kind"] not in {"lesson", "reading", "practice", "assessment"} or type(row["assistant_allowed"]) is not bool:
                raise PackageError("学习活动类型或助教开关无效")
            if "question_ids" in row["content"]:
                raise PackageError("活动题集只能声明于结构化question_ids关联")
            if row["npc_key"] not in indexed["npc"]:
                raise PackageError("活动NPC不存在")
            material = indexed["material"].get(row["material_id"])
            if material and row["chapter_id"] not in material["chapter_ids"]:
                raise PackageError("活动资料必须属于活动章节")
            validate_learning(row["content"], row["kind"], SimpleNamespace(**material) if material else None, resolve=True)
            if len(set(row["question_ids"])) != len(row["question_ids"]):
                raise PackageError("活动题目重复")
            for question_id in row["question_ids"]:
                question = indexed["question"][question_id]
                if row["chapter_id"] not in question["chapter_ids"] or question["status"] != "ready":
                    raise PackageError("活动题目必须已就绪并属于当前章节")
        for key, row in converted["quest"].items():
            definition = row["definition"]
            if definition.get("id") != key or definition.get("chapter_id") != row["chapter_id"]:
                raise PackageError("任务定义与外层ID/章节不一致")
            from .world_config import interaction_points
            validate_definition(definition, objects=set(interaction_points()) | {
                item["id"] for item in bundle["configuration"].get("world", {}).get("interactions", [])})
            for node in definition["nodes"]:
                if node.get("navigation_target") and node["navigation_target"] not in indexed["npc"]:
                    raise PackageError("任务导航NPC不存在")
                if node["type"] in {"TALK_TO_NPC", "PLAY_DIALOGUE", "REPORT_BACK"} and node.get("target") not in indexed["npc"]:
                    raise PackageError("任务NPC不存在")
                if node.get("activity_id"):
                    activity = converted["activity"][node["activity_id"]]
                    expected = {"STUDY_LESSON": {"lesson", "reading"}, "COMPLETE_PRACTICE": {"practice"}, "PASS_ASSESSMENT": {"assessment"}}
                    if node["type"] in expected and activity["kind"] not in expected[node["type"]]:
                        raise PackageError("任务节点活动类型不匹配")
                    if activity["chapter_id"] != row["chapter_id"]:
                        raise PackageError("任务活动必须属于当前章节")
        class Catalog:
            def get(self, model, key):
                kind = MODEL_NAMES[model]
                row = next((value for stable, value in converted[kind].items() if maps[kind][stable] == key), None)
                return SimpleNamespace(**row) if row else None
        catalog = Catalog()
        def conditions(value, bindings, definition):
            if isinstance(value, dict):
                for key, item in value.items():
                    if key == "when":
                        validate_condition(item, bindings, definition, catalog)
                    else:
                        conditions(item, bindings, definition)
            elif isinstance(value, list):
                for item in value:
                    conditions(item, bindings, definition)
        for key, row in converted["story"].items():
            quest = converted["quest"].get(row["quest_id"])
            definition = quest["definition"] if quest else None
            if quest and quest["chapter_id"] != row["chapter_id"]:
                raise PackageError("剧情任务必须属于同一章节")
            validate_story(row["content"], quest_definition=definition, story_id=key)
            conditions(row["content"], row["content"]["bindings"], definition)
        course = bundle["course"]
        _object(course, {"schema_version", "title", "releases"}, {"schema_version", "title", "releases"})
        if course["schema_version"] != 1 or not _short(course["title"], 200) or not isinstance(course["releases"], list):
            raise PackageError("课程目录格式无效")
        releases = {}
        for row in course["releases"]:
            _object(row, {"chapter_id", "status", "opens_at", "prerequisite_chapter_id"}, {"chapter_id", "status", "opens_at", "prerequisite_chapter_id"})
            _json(row, maps)
            if row["chapter_id"] in releases or row["status"] not in {"published", "paused"}:
                raise PackageError("章节发布配置重复或无效")
            if row["opens_at"] is not None and datetime.fromisoformat(row["opens_at"]).tzinfo is None:
                raise PackageError("开放时间需要时区")
            releases[row["chapter_id"]] = row
        for key in releases:
            visited, current = set(), key
            while current:
                if current in visited:
                    raise PackageError("章节前置依赖存在循环")
                visited.add(current)
                current = releases.get(current, {}).get("prerequisite_chapter_id")
        from .world_authoring import validate_configuration
        validate_configuration(bundle["configuration"], data)
    except (ValueError, TypeError, KeyError, AttributeError, HTTPException, RecursionError) as exc:
        if isinstance(exc, PackageError):
            raise
        raise PackageError("课程语义校验失败：引用、数据类型或流程不合法") from exc


def _has_history(db, classroom_id):
    from .models import BuildingUnlock, WorldFlag, PlayerPosition
    return any(db.scalar(select(model).where(model.classroom_id == classroom_id).limit(1)) is not None
               for model in (PlayerQuest, StorySession, ActivityProgress, LearningRecord, Submission,
                             AssessmentAttempt, AssistantSession, WorldFlag, PlayerPosition, BuildingUnlock))


def _impact(db, instance, bundle):
    snapshot = _snapshot(db, instance) if instance else None
    previous = snapshot[0] if snapshot else {kind: [] for kind in MODEL_NAMES.values()}
    diff = {}
    def compare(kind, old, new):
        diff[kind] = {"added": sorted(new.keys() - old.keys()), "removed": sorted(old.keys() - new.keys()),
                      "updated": sorted(key for key in old.keys() & new.keys() if old[key] != new[key])}
    for kind, incoming in bundle["data"].items():
        compare(kind, {row["id"]: row for row in previous[kind]}, {row["id"]: row for row in incoming})
    compare("answers", {row["id"]: row for row in snapshot[1]} if snapshot else {},
            {row["id"]: row for row in bundle["secrets"]})
    old_config = snapshot[3] if snapshot else {}
    compare("configuration", {key: old_config.get(key, {}) for key in CONFIG_FILES}, bundle["configuration"])
    compare("releases", {row["chapter_id"]: row for row in snapshot[2]} if snapshot else {},
            {row["chapter_id"]: row for row in bundle["course"]["releases"]})
    old_assets = {row["id"]: row["sha256"] for row in old_config.get("_assets", [])}
    if snapshot:
        material_assets = {row["id"]: row["asset_id"] for row in previous["material"]}
        adapter = get_storage()
        for material in snapshot[4][Material]:
            if material.storage_key:
                try:
                    digest = hashlib.sha256()
                    for chunk in adapter.chunks(material.storage_key, 0, adapter.size(material.storage_key) - 1):
                        digest.update(chunk)
                    old_assets[material_assets[_stable(material)]] = digest.hexdigest()
                except Exception:
                    old_assets[material_assets[_stable(material)]] = None
    compare("assets", old_assets, {row["id"]: row["sha256"] for row in bundle["assets"]})
    compare("manifest", {"package_id": instance.package_id, "name": instance.name, "package_version": instance.package_version} if instance else {},
            {key: bundle["manifest"][key] for key in ("package_id", "name", "package_version")})
    blockers, learning_impact = [], []
    if instance and db.scalar(select(Material.id).where(Material.course_instance_id == instance.id,
                                                       Material.status.in_(["queued", "processing"])).limit(1)):
        blockers.append("班级资料仍在转换，请完成后重新预览")
    if instance and _has_history(db, instance.classroom_id):
        if instance.package_id != bundle["manifest"]["package_id"]:
            blockers.append("班级已有学习记录，不能替换为另一课程；请创建新班级")
        elif any(diff[kind]["removed"] for kind in MODEL_NAMES.values()):
            blockers.append("班级已有学习记录，本次更新包含删除定义；请保留原ID并停用内容后重新预览")
    if instance:
        incoming = {kind: {row["id"]: row for row in values} for kind, values in bundle["data"].items()}
        current = {kind: {row["id"]: row for row in values} for kind, values in previous.items()}
        maps = content_mapping(db, instance)
        attempts = list(db.scalars(select(AssessmentAttempt).where(AssessmentAttempt.classroom_id == instance.classroom_id)))
        submissions = list(db.scalars(select(Submission).where(Submission.classroom_id == instance.classroom_id)))
        active_stories = list(db.scalars(select(StorySession).where(StorySession.classroom_id == instance.classroom_id,
                                                                   StorySession.status.in_(["active", "waiting"]))))
        def protect(kind, stable_id, count, reason):
            if count:
                learning_impact.append({"kind": kind, "stable_id": stable_id, "affected_records": count, "reason": reason})
                blockers.append(reason)
        from .authoring_protection import changed_quest_structure
        for stable in diff["quest"]["updated"]:
            old, new = current["quest"][stable], incoming["quest"][stable]
            if old["chapter_id"] != new["chapter_id"] or changed_quest_structure(old["definition"], new["definition"]):
                progress_count = db.scalar(select(func.count()).select_from(PlayerQuest).where(
                    PlayerQuest.classroom_id == instance.classroom_id, PlayerQuest.quest_id == maps["quest"][stable]))
                protect("quest", stable, progress_count, f"任务「{old['title']}」已有班级进度，请在教学流程编辑器中确认重置后再调整任务结构")
        for stable in diff["question"]["updated"]:
            key = maps["question"][stable]
            if current["question"][stable]["kind"] != incoming["question"][stable]["kind"]:
                count = sum(row.question_id == key for row in submissions) + sum(key in row.question_ids for row in attempts)
                protect("question", stable, count, "已有正式题目证据，改变题型需要新的题目ID")
        for stable in diff["activity"]["updated"]:
            old, new = current["activity"][stable], incoming["activity"][stable]
            key = maps["activity"][stable]
            if old["kind"] != new["kind"] or (old["kind"] == "assessment" and old["question_ids"] != new["question_ids"]):
                count = sum(row.activity_id == key for row in attempts) + sum(row.activity_id == key for row in submissions)
                count += len(list(db.scalars(select(ActivityProgress).where(ActivityProgress.classroom_id == instance.classroom_id,
                                                                           ActivityProgress.activity_id == key))))
                count += len(list(db.scalars(select(LearningRecord).where(LearningRecord.classroom_id == instance.classroom_id,
                                                                         LearningRecord.activity_id == key))))
                protect("activity", stable, count, "活动已有学习或测验证据，改变活动类型或测验题集需要新的活动ID")
        changed_activities = {maps["activity"][stable] for stable in diff["activity"]["updated"]}
        changed_assets = set(diff["assets"]["updated"] + diff["assets"]["removed"])
        changed_materials = set(diff["material"]["updated"]) | {row["id"] for row in previous["material"]
            if row["asset_id"] in changed_assets or row["preview_asset_id"] in changed_assets}
        changed_questions = set(diff["question"]["updated"] + diff["answers"]["updated"])
        changed_activities.update(maps["activity"][stable] for stable, row in current["activity"].items()
                                  if row["material_id"] in changed_materials or changed_questions.intersection(row["question_ids"]))
        for stable in maps["story"]:
            affected = [session for session in active_stories if session.story_id == maps["story"][stable] and (
                        stable in diff["story"]["updated"] or stable in diff["story"]["removed"]
                        or session.pending_activity_id in changed_activities)]
            protect("story", stable, len(affected), "流程仍有进行中或等待活动的会话，请先完成或关闭后重新预览更新")
    return {"diff": diff, "blockers": list(dict.fromkeys(blockers)), "learning_impact": learning_impact,
            "warnings": ["同ID内容更新保留历史证据与成绩，不重新评分"]}


def _delete_content(db, instance, incoming):
    rows = _rows(db, instance)
    removing = {model: [_pk(row) for row in values if _stable(row) not in incoming[MODEL_NAMES[model]]]
                for model, values in rows.items()}
    # All relation removals are explicit; never depend on database cascades for learner rows.
    for relation, field, model in ((ActivityQuestion, ActivityQuestion.activity_id, Activity),
                                    (ActivityQuestion, ActivityQuestion.question_id, Question),
                                    (QuestionSecret, QuestionSecret.question_id, Question),
                                    (QuestionKnowledgePoint, QuestionKnowledgePoint.question_id, Question),
                                    (QuestionKnowledgePoint, QuestionKnowledgePoint.knowledge_point_id, KnowledgePoint),
                                    (QuestionChapter, QuestionChapter.question_id, Question),
                                    (QuestionChapter, QuestionChapter.chapter_id, Chapter),
                                    (MaterialChapter, MaterialChapter.material_id, Material),
                                    (MaterialChapter, MaterialChapter.chapter_id, Chapter)):
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
        instance = CourseInstance(classroom_id=classroom.id, package_id=manifest["package_id"], name=manifest["name"],
                                  package_version=manifest["package_version"], created_at=datetime.fromisoformat(manifest["created_at"]), revision=0)
        db.add(instance)
        db.flush()
    incoming = {kind: {row["id"] for row in rows} if not replace else set() for kind, rows in data.items()}
    db.execute(delete(ChapterRelease).where(ChapterRelease.classroom_id == instance.classroom_id))
    if replace:
        _delete_content(db, instance, incoming)
        instance.created_at = datetime.fromisoformat(manifest["created_at"])
    existing = {model: {_stable(row): row for row in rows} for model, rows in _rows(db, instance).items()}
    maps = content_mapping(db, instance)
    _lock_numeric_tables(db)
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
                if "dialogue" in values:
                    values["dialogue"] = []
                if hasattr(model, "owner_id"):
                    values["owner_id"] = classroom.teacher_id
                if model in {Question, Npc, Activity, QuestDefinition, StoryDefinition}:
                    values["key" if model is Npc else "id"] = f"c{instance.id}-{uuid4().hex[:16]}"
                row = model(course_instance_id=instance.id, stable_id=source["id"], **values)
                db.add(row)
                db.flush()
            maps[kind][source["id"]] = _pk(row)
            pairs[model].append((source, row))
    adapter, assets = get_storage(), {}
    for asset in bundle["assets"]:
        source = root / asset["path"]
        key = f"course-assets/{asset['sha256']}{source.suffix.lower()}"
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
            converted = _json({field: source[field] for field in FIELDS[model]}, maps)
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
    relations = ((Material, MaterialChapter, "material_id", "chapter_ids", "chapter_id", "chapter"),
                 (Question, QuestionChapter, "question_id", "chapter_ids", "chapter_id", "chapter"),
                 (Question, QuestionKnowledgePoint, "question_id", "knowledge_point_ids", "knowledge_point_id", "knowledge_point"),
                 (Activity, ActivityQuestion, "activity_id", "question_ids", "question_id", "question"))
    for model, relation, field, source_field, target_field, kind in relations:
        for source, row in pairs[model]:
            db.execute(delete(relation).where(getattr(relation, field) == _pk(row)))
            for index, key in enumerate(source[source_field]):
                values = {field: _pk(row), target_field: maps[kind][key]}
                if relation is ActivityQuestion:
                    values["order_index"] = index
                db.add(relation(**values))
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
        values = _json(source, maps)
        values["opens_at"] = datetime.fromisoformat(values["opens_at"]) if values["opens_at"] else None
        db.add(ChapterRelease(classroom_id=classroom.id, **values))
    state = db.get(CoursePackageState, instance.id)
    if state is None:
        state = CoursePackageState(course_instance_id=instance.id)
        db.add(state)
    state.configuration = {**deepcopy(bundle["configuration"]), "_assets": list(assets.values())}
    instance.package_id, instance.name, instance.package_version = manifest["package_id"], manifest["name"], manifest["package_version"]
    instance.revision += 1
    db.flush()
    return instance


def _digest(path):
    checksum = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.hexdigest()


def _store_file(adapter, key, source):
    try:
        adapter.put(key, source)
    except Exception as exc:
        raise PackageError("私有资源写入失败，课程未发布") from exc


def _read_staged(key, destination):
    try:
        shutil.copyfile(exchange_path(key), destination)
    except Exception as exc:
        raise PackageError("暂存课程包不可读取，请重新上传") from exc


@router.get("/export")
def export_course(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    # Exclusive gate ensures one consistent export while blocking both edits and learner writes.
    lock_course(db, classroom_id, exclusive=True)
    _teacher(db, user, classroom_id)
    instance = classroom_course(db, classroom_id)
    if instance is None:
        raise HTTPException(409, "班级尚未导入课程")
    temporary = Path(tempfile.mkdtemp(prefix="course-export-", dir=exchange_root()))
    root = temporary / "package"
    root.mkdir()
    try:
        write_package(db, instance, root)
        load_bundle(root)
        destination = exchange_path(f"export-{classroom_id}-{uuid4().hex}")
        os.replace(root, destination)
        shutil.rmtree(temporary)
        db.commit()
    except (PackageError, OSError, ValueError) as exc:
        db.rollback()
        shutil.rmtree(temporary, ignore_errors=True)
        raise HTTPException(422, str(exc) if isinstance(exc, PackageError) else "课程导出失败") from exc
    return {"directory": destination.name, "path": f"data/course/{destination.name}"}



def _save_preview(db, user, classroom_id, bundle, archive, digest=None):
    instance = classroom_course(db, classroom_id)
    impact = _impact(db, instance, bundle)
    preview = CourseImportPreview(id=str(uuid4()), teacher_id=user.id, classroom_id=classroom_id,
                                  expected_revision=instance.revision if instance else 0,
                                  archive_sha256=digest or _digest(archive), storage_key=f"imports/{uuid4().hex}" + ("" if digest else ".zip"),
                                  summary={**impact, "package_id": bundle["manifest"]["package_id"],
                                           "package_version": bundle["manifest"]["package_version"],
                                           "name": bundle["manifest"]["name"]}, expires_at=now() + timedelta(hours=24))
    os.replace(archive, exchange_path(preview.storage_key))
    db.add(preview)
    db.commit()
    return {"id": preview.id, "expected_revision": preview.expected_revision, "expires_at": preview.expires_at,
            "can_apply": not impact["blockers"], **preview.summary}


@router.get("/packages")
def available_packages(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    from .course_catalog import mounted_catalog

    _teacher(db, user, classroom_id)
    root = exchange_root()
    try:
        mounted, unavailable = mounted_catalog(root, LIMITS, MAX_ARCHIVE)
        items = [{key: item[key] for key in ("key", "name", "package_id", "package_version", "source")} for item in mounted
                 if not item["path"].name.startswith("export-") or item["path"].name.startswith(f"export-{classroom_id}-")]
        return JSONResponse({"items": items, "unavailable": unavailable}, headers={"Cache-Control": "private, no-store"})
    except (PackageError, OSError) as exc:
        raise HTTPException(422, str(exc) if isinstance(exc, PackageError) else "无法读取可选课程包，请稍后刷新") from exc


class AvailablePackageInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    package_key: str = Field(pattern=r"^(mounted-[0-9a-f]{64}|uploaded-[0-9a-f-]{36})$", max_length=80)


@router.post("/imports/available/preview")
def preview_available_course(classroom_id: int, body: AvailablePackageInput, db: Annotated[Session, Depends(get_db)],
                             user: Annotated[User, Depends(require_teacher)]):
    from .course_catalog import copy_directory, mounted_catalog

    lock_course(db, classroom_id, exclusive=True)
    _teacher(db, user, classroom_id)
    exchange = exchange_root()
    try:
        with tempfile.TemporaryDirectory(prefix="course-catalog-", dir=exchange) as temporary:
            root = Path(temporary) / "package"
            items, _ = mounted_catalog(exchange, LIMITS, MAX_ARCHIVE)
            selected = next((item for item in items if item["key"] == body.package_key), None)
            if selected is None:
                raise HTTPException(404, "课程包已变化或不存在，请刷新列表")
            if selected["path"].name.startswith("export-") and not selected["path"].name.startswith(f"export-{classroom_id}-"):
                raise HTTPException(404, "课程包不存在或不可访问")
            digest = copy_directory(selected["path"], root, exchange, LIMITS)
            bundle = load_bundle(root)
            return _save_preview(db, user, classroom_id, bundle, root, digest)
    except HTTPException:
        db.rollback()
        raise
    except (PackageError, OSError) as exc:
        db.rollback()
        raise HTTPException(422, str(exc) if isinstance(exc, PackageError) else "课程包读取失败，请刷新列表或重新上传") from exc


@router.post("/imports/preview")
async def preview_course(classroom_id: int, file: Annotated[UploadFile, File()], db: Annotated[Session, Depends(get_db)],
                         user: Annotated[User, Depends(require_teacher)]):
    _teacher(db, user, classroom_id)
    raise HTTPException(410, "ZIP上传已关闭，请将解压后的课程包放入data/course并从目录选择")



class ApplyInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    request_id: UUID
    expected_revision: int = Field(ge=0, strict=True)


@router.post("/imports/{preview_id}/apply")
def apply_course(classroom_id: int, preview_id: str, body: ApplyInput, db: Annotated[Session, Depends(get_db)],
                 user: Annotated[User, Depends(require_teacher)]):
    lock_course(db, classroom_id, exclusive=True)
    classroom = _teacher(db, user, classroom_id)
    preview = db.scalar(select(CourseImportPreview).where(CourseImportPreview.id == preview_id).with_for_update())
    if not preview or preview.classroom_id != classroom_id or preview.teacher_id != user.id:
        raise HTTPException(404, "导入预览不存在")
    if preview.applied_request_id:
        if preview.applied_request_id != str(body.request_id) or body.expected_revision != preview.expected_revision:
            raise HTTPException(409, "此预览已由另一次操作导入")
        return {**preview.result, "duplicate": True}
    receipt = db.scalar(select(CourseImportPreview).where(CourseImportPreview.classroom_id == classroom_id,
                        CourseImportPreview.applied_request_id == str(body.request_id)))
    if receipt:
        if receipt.archive_sha256 != preview.archive_sha256:
            raise HTTPException(409, "请求ID已经用于不同的课程包")
        return {**receipt.result, "duplicate": True}
    if as_utc(preview.expires_at) <= now():
        raise HTTPException(409, "导入预览已过期，请重新上传预览")
    instance = classroom_course(db, classroom_id)
    revision = instance.revision if instance else 0
    if body.expected_revision != preview.expected_revision or revision != preview.expected_revision:
        raise HTTPException(409, "班级课程已修改，请重新预览差异")
    if db.scalar(select(AssistantCall.id).where(AssistantCall.classroom_id == classroom_id,
                                              AssistantCall.status.in_(["running", "cancel_requested"])).limit(1)):
        raise HTTPException(409, "班级助教会话仍在运行，请完成后重试导入")
    try:
        with tempfile.TemporaryDirectory(prefix="course-apply-", dir=exchange_root()) as temporary:
            root, archive = Path(temporary) / "package", Path(temporary) / "course.zip"
            staged = exchange_path(preview.storage_key)
            if staged.is_dir():
                from .course_catalog import copy_directory
                digest = copy_directory(staged, root, exchange_root(), LIMITS)
                if digest != preview.archive_sha256:
                    raise PackageError("暂存目录校验失败，请重新预览")
            else:
                root.mkdir()
                _read_staged(preview.storage_key, archive)
                if _digest(archive) != preview.archive_sha256:
                    raise PackageError("暂存包校验失败，请重新预览")
                unpack_archive(archive, root)
            bundle = load_bundle(root)
            impact = _impact(db, instance, bundle)
            if impact["blockers"]:
                raise HTTPException(409, {"message": "课程更新影响已有学习记录", "blockers": impact["blockers"]})
            instance = import_bundle(db, classroom, instance, bundle, root)
            preview.applied_request_id = str(body.request_id)
            preview.result = {"instance_id": instance.id, "revision": instance.revision,
                              "package_id": instance.package_id, "package_version": instance.package_version, "duplicate": False}
            db.commit()
            return preview.result
    except HTTPException:
        db.rollback()
        raise
    except (PackageError, ValueError, OSError, SQLAlchemyError) as exc:
        db.rollback()
        raise HTTPException(422, str(exc) if isinstance(exc, PackageError) else "课程包导入失败，未修改班级课程") from exc
