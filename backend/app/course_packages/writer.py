"""Serialize current course content and authorized media into a portable package."""

import hashlib
import json
from pathlib import Path

import yaml
from sqlalchemy import select

from app.auth import as_utc
from app.course_content.snapshot import snapshot_content, stable_id
from app.course_instances import MODEL_NAMES
from app.course_packages.contracts import CONFIG_FILES, JSONL_FILES, LIMITS
from app.course_packages.manifest import PackageError, validate_package
from app.materials.storage import get_storage
from app.models import Material, now


def _write_yaml(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(yaml.safe_dump(value, allow_unicode=True, sort_keys=False), encoding="utf-8")


def _write_jsonl(path, values):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(row, ensure_ascii=False, allow_nan=False) + "\n" for row in values), encoding="utf-8")


def write_package(db, instance, root):
    data, secrets, releases, config, rows = snapshot_content(db, instance)
    if any(material.status in {"queued", "processing"} for material in rows[Material]):
        raise PackageError("班级资料仍在转换，请完成后导出课程")
    created = as_utc(instance.created_at).isoformat()
    manifest = {
        "schema_version": 3,
        "origin": "exported",
        "package_id": instance.package_id,
        "name": instance.name,
        "package_version": instance.package_version,
        "created_at": created,
        "exported_at": now().isoformat(),
        "requires": {
            "world_id": "pythontopia-town",
            "world_schema_version": 1,
            "capabilities": ["reading", "practice", "assessment", "dialogue", "tasks", "library"],
        },
        "entry": "curriculum/course.yaml",
    }
    if any(material.kind == "video" for material in rows[Material]):
        manifest["requires"]["capabilities"].append("video")
    _write_yaml(root / "manifest.yaml", manifest)
    _write_yaml(root / "curriculum/course.yaml", {"schema_version": 1, "title": instance.name, "releases": releases})
    chapter_paths = {
        row["id"]: f"curriculum/chapters/{index:04d}" for index, row in enumerate(sorted(data["chapter"], key=lambda r: r["order_index"]))
    }
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
        asset_id = asset_ids[stable_id(material)]
        export_assets.append({**original_assets.get(asset_id, {}), "id": asset_id, "storage_key": material.storage_key})
        if material.content.get("preview_key"):
            preview_id = preview_ids[stable_id(material)]
            export_assets.append({**original_assets.get(preview_id, {}), "id": preview_id, "storage_key": material.content["preview_key"]})
    exported_ids = {asset["id"] for asset in export_assets}
    retired_materials = db.scalars(
        select(Material).where(Material.course_instance_id == instance.id, Material.deleted_at.is_not(None)).execution_options(include_deleted=True)
    )
    retired_keys = {key for row in retired_materials for key in (row.storage_key, row.content.get("preview_key")) if key}
    character_assets = {row[kind][6:] for row in data["npc"] for kind in ("portrait", "sprite") if row[kind].startswith("asset:")}
    export_assets.extend(
        asset
        for key, asset in original_assets.items()
        if key not in exported_ids and asset["storage_key"] not in retired_keys and (not asset.get("character_kind") or key in character_assets)
    )
    for source in {asset["id"]: asset for asset in export_assets}.values():
        suffix = Path(source["storage_key"]).suffix.lower()
        from app.course_packages.manifest import MEDIA_TYPES

        media_type = MEDIA_TYPES.get(suffix)
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
        assets.append(
            {
                "id": asset_id,
                "path": path.relative_to(root).as_posix(),
                "media_type": media_type,
                "size_bytes": path.stat().st_size,
                "sha256": digest.hexdigest(),
                "source": source.get("source", "教师授权班级当前资料"),
                "license": source.get("license", "仅按原资料授权使用；本清单不新增授权"),
                **{
                    key: source[key] for key in ("character_kind", "width", "height", "frame_width", "frame_height", "original_name") if key in source
                },
            }
        )
    _write_jsonl(root / "assets/index.jsonl", assets)
    validate_package(root)
    return manifest
