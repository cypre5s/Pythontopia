"""Claim and process durable material conversion jobs."""

from __future__ import annotations

import asyncio
import logging
import tempfile
from pathlib import Path
from uuid import uuid4

from sqlalchemy import select, update
from sqlalchemy.exc import SQLAlchemyError

from app.db import SessionLocal
from app.materials.conversion import convert_presentation, parse_bounded
from app.materials.storage import get_storage, storage_root
from app.models import CourseInstance, Material
from app.teacher_scope import commit_content


def _material_course_lock(db, material_id):
    from app.course_scope import lock_course

    instance = db.scalar(select(CourseInstance).join(Material, Material.course_instance_id == CourseInstance.id).where(Material.id == material_id))
    if instance:
        lock_course(db, instance.classroom_id)
    return instance.id if instance else None


def process_material(material_id: int):
    with SessionLocal() as db:
        instance_id = _material_course_lock(db, material_id)
        claimed = db.execute(
            update(Material)
            .where(Material.id == material_id, Material.status == "queued")
            .values(status="processing")
            .returning(Material.storage_key, Material.content)
        ).first()
        commit_content(db, instance_id if claimed else None)
        if not claimed:
            return
        key, content = claimed
        metadata = dict(content)
    try:
        with tempfile.TemporaryDirectory(prefix="parse-", dir=storage_root()) as tmp_name:
            tmp = Path(tmp_name)
            source = tmp / ("source" + Path(key).suffix)
            adapter = get_storage()
            adapter.copy_to(key, source)
            if source.suffix in {".mp4", ".webm"}:
                result = {"pages": [], "notice": "教师上传的视频；观看参与记录不等同于掌握"}
            else:
                if source.suffix in {".ppt", ".pptx"}:
                    source = convert_presentation(source, tmp)
                    preview_key = f"previews/{uuid4().hex}.pdf"
                    adapter.put(preview_key, source)
                    metadata["preview_key"] = preview_key
                result = parse_bounded(source, tmp)
        status, error = "ready", None
        metadata.update(result)
    except Exception as exc:
        status = "failed"
        error = str(exc) if isinstance(exc, ValueError) else "资料处理失败，请检查存储或处理依赖"
    with SessionLocal() as db:
        instance_id = _material_course_lock(db, material_id)
        # A replacement upload may have arrived while this file was processing.
        # The unique storage key identifies the claimed file, without retaining history.
        result = db.execute(
            update(Material)
            .where(Material.id == material_id, Material.storage_key == key, Material.status == "processing")
            .values(status=status, error=error, content=metadata)
        )
        commit_content(db, instance_id if result.rowcount else None)


def _recover_material_jobs():
    with SessionLocal() as db:
        from app.course_scope import lock_course

        instances = list(
            db.scalars(
                select(CourseInstance)
                .join(Material, Material.course_instance_id == CourseInstance.id)
                .where(Material.status == "processing")
                .distinct()
                .order_by(CourseInstance.classroom_id)
            )
        )
        for instance in instances:
            lock_course(db, instance.classroom_id)
        changed = list(
            db.scalars(update(Material).where(Material.status == "processing").values(status="queued").returning(Material.course_instance_id))
        )
        for key in set(changed) - {None}:
            db.execute(update(CourseInstance).where(CourseInstance.id == key).values(revision=CourseInstance.revision + 1))
        db.commit()


def _material_job_ids():
    with SessionLocal() as db:
        return list(db.scalars(select(Material.id).where(Material.status == "queued").limit(5)))


async def material_worker():
    recovered, delay = False, 1
    while True:
        try:
            if not recovered:
                await asyncio.to_thread(_recover_material_jobs)
                recovered = True
            ids = await asyncio.to_thread(_material_job_ids)
            for material_id in ids:
                await asyncio.to_thread(process_material, material_id)
            delay = 1
        except SQLAlchemyError:
            logging.getLogger(__name__).warning("Material worker database unavailable; retrying in %ss", delay)
            recovered = False
            await asyncio.sleep(delay)
            delay = min(delay * 2, 30)
            continue
        await asyncio.sleep(2)
