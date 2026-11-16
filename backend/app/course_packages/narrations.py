"""Snapshot, export and restore reviewed narration without model calls or local identities."""

import hashlib
import json
from copy import deepcopy
from datetime import datetime
from pathlib import Path

from sqlalchemy import select

from app.auth import as_utc
from app.course_content.snapshot import stable_id
from app.course_packages.contracts import LIMITS
from app.course_packages.manifest import PackageError
from app.course_packages.narration_contract import FIELDS, directory, fingerprint
from app.materials.storage import get_storage
from app.models import Material
from app.narration import repository
from app.narration.audio import MEDIA_TYPES
from app.narration.models import NarrationJob, PageNarration


def current_rows(db, instance):
    if instance is None:
        return []
    # Narration generation/review uses the same parent-row lock.
    materials = list(db.scalars(select(Material).where(Material.course_instance_id == instance.id).order_by(Material.id).with_for_update()))
    ids = [m.id for m in materials]
    if db.scalar(select(NarrationJob.id).where(NarrationJob.material_id.in_(ids), NarrationJob.status == "running").limit(1)):
        raise PackageError("资料讲稿或配音任务仍在执行，请完成后交换课程包")
    return [(material, item) for material in materials for item in repository.rows(db, material.id)]


def revision(db, instance):
    values = [[m.id, repository.source_hash(m, n.page_no), repository.revision(n), n.audio_key,
               n.audio_text_hash, n.audio_seconds] for m, n in current_rows(db, instance)]
    return repository.text_hash(json.dumps(values, ensure_ascii=False, sort_keys=True))


def metadata(material, item):
    result = {field: deepcopy(getattr(item, field)) for field in FIELDS}
    if item.page_hash != repository.source_hash(material, item.page_no):
        result["status"] = "stale"
    result.update(material_id=stable_id(material), page_no=item.page_no,
                  reviewed_at=as_utc(item.reviewed_at).isoformat() if item.reviewed_at else None)
    return result


def export_narrations(db, instance, root, materials, assets):
    adapter = get_storage()
    by_id = {m["id"]: m for m in materials}
    indexed_assets = {a["id"]: a for a in assets}
    grouped = {}
    for material, item in current_rows(db, instance):
        row = metadata(material, item)
        row["page_hash"] = None if row["status"] == "stale" else fingerprint(by_id[stable_id(material)], item.page_no, indexed_assets)
        row["audio"] = None
        if row["status"] == "accepted" and item.audio_key and item.audio_text_hash == repository.text_hash(item.text):
            extension = Path(item.audio_key).suffix.lower().lstrip(".")
            if extension not in MEDIA_TYPES:
                raise PackageError("讲稿音频格式不支持导出")
            folder = root / directory(stable_id(material)) / "audio"
            folder.mkdir(parents=True, exist_ok=True)
            temporary = folder / f"page-{item.page_no}.{extension}"
            try:
                if adapter.size(item.audio_key) > LIMITS.file_bytes:
                    raise PackageError("讲稿音频超过单文件限制")
                adapter.copy_to(item.audio_key, temporary)
            except Exception as exc:
                raise PackageError("讲稿音频缺失或无法读取，不能完整导出") from exc
            with temporary.open("rb") as stream:
                digest = hashlib.file_digest(stream, "sha256").hexdigest()
            # Page suffix ensures each page owns its audio even for identical recordings.
            path = folder / f"{digest}-{item.page_no}.{extension}"
            temporary.rename(path)
            identity = "narration-" + hashlib.sha256(f"{stable_id(material)}:{item.page_no}".encode()).hexdigest()
            assets.append({"id": identity, "path": path.relative_to(root).as_posix(), "media_type": MEDIA_TYPES[extension],
                           "size_bytes": path.stat().st_size, "sha256": digest,
                           "source": "课程审校讲稿配音", "license": "仅按原资料及语音服务授权使用"})
            row["audio"] = {"asset_id": identity, "text_hash": item.audio_text_hash, "seconds": item.audio_seconds}
        grouped.setdefault(stable_id(material), []).append(row)
    for key, pages in grouped.items():
        path = root / directory(key) / "pages.jsonl"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in pages), encoding="utf-8")


def import_narrations(db, bundle, maps, assets):
    # A package without narrations never erases independently authored target narration.
    # Supplied pages explicitly replace those same pages; absent pages remain and are
    # invalidated by the normal page/source fingerprint if material content changed.
    for source in bundle.get("narrations", []):
        material = db.get(Material, maps["material"][source["material_id"]])
        item = repository.row(db, material.id, source["page_no"])
        if item is None:
            item = PageNarration(material_id=material.id, page_no=source["page_no"])
            db.add(item)
        for field in FIELDS:
            setattr(item, field, deepcopy(source[field]))
        item.page_hash = "" if source["status"] == "stale" else repository.source_hash(material, item.page_no)
        item.reviewed_by = None
        item.reviewed_at = datetime.fromisoformat(source["reviewed_at"]) if source["reviewed_at"] else None
        item.input_hash = ""
        audio = source["audio"]
        item.audio_key = assets[audio["asset_id"]]["storage_key"] if audio else None
        item.audio_text_hash = audio["text_hash"] if audio else None
        item.audio_seconds = audio["seconds"] if audio else None
        item.audio_created_at = None
    db.flush()


def preview_narrations(db, instance, bundle):
    previous = {}
    for material, item in current_rows(db, instance):
        row = metadata(material, item)
        valid = row["status"] == "accepted" and item.audio_key and item.audio_text_hash == repository.text_hash(item.text)
        row["audio"] = {"text_hash": item.audio_text_hash, "seconds": item.audio_seconds} if valid else None
        previous[f"{stable_id(material)}:{item.page_no}"] = row
    incoming = {}
    for source in bundle.get("narrations", []):
        row = {key: source[key] for key in (*FIELDS, "material_id", "page_no", "reviewed_at")}
        row["audio"] = {k: source["audio"][k] for k in ("text_hash", "seconds")} if source["audio"] else None
        incoming[f"{source['material_id']}:{source['page_no']}"] = row
    # Omission preserves target-only authored pages, so it is not a deletion.
    return {"added": sorted(incoming.keys() - previous.keys()), "removed": [],
            "updated": sorted(key for key in incoming.keys() & previous.keys() if incoming[key] != previous[key])}
