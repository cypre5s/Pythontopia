"""Coordinate authorized material uploads, replacement and processing status."""

from __future__ import annotations

import asyncio
import hashlib
import json
import tempfile
from pathlib import Path
from uuid import uuid4

from fastapi import HTTPException, UploadFile
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.content_relations import set_material_chapters
from app.materials.access import teacher_material, teacher_material_chapters, teacher_material_payload, validate_material_links
from app.materials.contracts import MaterialUpdate
from app.materials.storage import get_storage, storage_root
from app.materials.uploads import KINDS, MAX_UPLOAD, UploadValidationError, validate_upload
from app.models import Material, User
from app.teacher_scope import commit_content, new_stable_id, require_scope, teacher_instance_id


async def upload_material(
    user: User,
    db: Session,
    title: str,
    file: UploadFile,
    material_id: int | None = None,
    chapter_ids: str | None = None,
    classroom_id: int | None = None,
):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    name = file.filename or ""
    if not name or "/" in name or "\\" in name or ":" in name or len(name) > 200 or not title.strip() or len(title) > 200:
        raise HTTPException(422, "文件名或标题无效")
    suffix = Path(name).suffix.lower()
    if suffix not in KINDS:
        raise HTTPException(415, "不支持此文件类型")
    from app.curriculum import authoring_chapter

    selected_chapters = None
    if chapter_ids is not None:
        from app.curriculum import validate_id_list

        try:
            selected_chapters = validate_id_list(json.loads(chapter_ids))
        except (ValueError, TypeError):
            raise HTTPException(422, "chapter_ids 必须是有效的章节ID JSON数组")
        for chapter_id in selected_chapters:
            authoring_chapter(db, user, chapter_id, instance_id)
    material = db.scalar(select(Material).where(Material.id == material_id).with_for_update()) if material_id else None
    if material_id and (not material or (material.owner_id != user.id and user.role != "admin")):
        raise HTTPException(403, "只能更新自己上传的资料")
    if material:
        require_scope(material, instance_id)
    if material and material.kind != KINDS[suffix]:
        raise HTTPException(422, "替换文件必须保留材料类型")
    if material and selected_chapters is not None:
        validate_material_links(db, material.id, selected_chapters)
    with tempfile.TemporaryDirectory(prefix="upload-", dir=storage_root()) as tmp:
        path = Path(tmp) / ("source" + suffix)
        size, digest = 0, hashlib.sha256()
        with path.open("wb") as output:
            while chunk := await file.read(65536):
                size += len(chunk)
                if size > MAX_UPLOAD:
                    raise HTTPException(413, "文件超过上传大小限制")
                digest.update(chunk)
                output.write(chunk)
        if size == 0:
            raise HTTPException(422, "文件为空")
        try:
            await asyncio.to_thread(validate_upload, path, suffix)
        except UploadValidationError as exc:
            raise HTTPException(415, str(exc)) from exc
        key = f"materials/{uuid4().hex}{suffix}"
        await asyncio.to_thread(get_storage().put, key, path)
    if not material:
        material = Material(title=title.strip(), kind=KINDS[suffix], owner_id=user.id, course_instance_id=instance_id, stable_id=new_stable_id())
        db.add(material)
        db.flush()
    if selected_chapters is not None:
        set_material_chapters(db, material.id, selected_chapters)
    material.title = title.strip()
    material.status = "queued"
    material.error = None
    material.storage_key = key
    material.content = {"filename": name, "size": size, "sha256": digest.hexdigest(), "pages": []}
    try:
        commit_content(db, instance_id)
    except IntegrityError:
        db.rollback()
        raise HTTPException(409, "资料同时更新，请刷新后重试")
    return {"id": material.id, "status": material.status, "title": material.title}


def teacher_materials(user: User, db: Session, classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id)
    query = select(Material).where(Material.course_instance_id == instance_id)
    if user.role != "admin":
        query = query.where(Material.owner_id.is_(None) | (Material.owner_id == user.id))
    return [teacher_material_payload(db, material, user) for material in db.scalars(query.order_by(Material.id))]


def teacher_material_preview(material_id: int, user: User, db: Session, classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id)
    from app.materials.presentation import material_payload

    material = teacher_material(db, user, material_id, instance_id=instance_id)
    result = material_payload(db, material, chapter_ids=teacher_material_chapters(db, material, user))
    result["owner_id"] = material.owner_id
    scope_query = f"?classroom_id={classroom_id}" if classroom_id is not None else ""
    result["media_url"] = f"/api/teacher/materials/{material.id}/file{scope_query}" if material.storage_key and material.status == "ready" else None
    return result


def update_material(material_id: int, body: MaterialUpdate, user: User, db: Session, classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    from app.curriculum import authoring_chapter

    material = teacher_material(db, user, material_id, edit=True, instance_id=instance_id)
    if "chapter_ids" in body.model_fields_set:
        for chapter_id in body.chapter_ids:
            authoring_chapter(db, user, chapter_id, instance_id)
        validate_material_links(db, material.id, body.chapter_ids)
        set_material_chapters(db, material.id, body.chapter_ids)
    if "title" in body.model_fields_set:
        material.title = body.title
    commit_content(db, instance_id)
    return teacher_material_payload(db, material, user)


def delete_material(material_id: int, user: User, db: Session, classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    teacher_material(db, user, material_id, edit=True, instance_id=instance_id)
    raise HTTPException(409, "请先预览删除影响并完成二次确认")
