"""Authorize material storage and content reads."""

from __future__ import annotations

from fastapi import HTTPException
from sqlalchemy import select

from app.content_relations import material_chapter_ids
from app.models import Activity, Chapter, Material
from app.teacher_scope import require_scope


def teacher_material(db, user, material_id, *, edit=False, instance_id=None):
    query = select(Material).where(Material.id == material_id)
    material = db.scalar(query.with_for_update() if edit else query)
    if not material:
        raise HTTPException(404, "资料不存在")
    require_scope(material, instance_id)
    if user.role != "admin" and material.owner_id not in ({user.id} if edit else {None, user.id}):
        raise HTTPException(403, "无权访问或编辑此资料")
    return material


def teacher_material_chapters(db, material, user):
    chapter_ids = material_chapter_ids(db, material.id)
    if user.role == "admin":
        return chapter_ids
    return list(db.scalars(select(Chapter.id).where(Chapter.id.in_(chapter_ids), Chapter.owner_id.is_(None) | (Chapter.owner_id == user.id))))


def teacher_material_payload(db, material, user):
    activities = select(Activity).where(Activity.material_id == material.id)
    if user.role != "admin":
        activities = activities.where(Activity.owner_id.is_(None) | (Activity.owner_id == user.id))
    return {
        "id": material.id,
        "title": material.title,
        "chapter_ids": teacher_material_chapters(db, material, user),
        "kind": material.kind,
        "status": material.status,
        "error": material.error,
        "owner_id": material.owner_id,
        "pages": len(material.content.get("pages", [])),
        "activities": [{"id": a.id, "title": a.title, "chapter_id": a.chapter_id} for a in db.scalars(activities)],
    }


def validate_material_links(db, material_id, chapter_ids):
    if db.scalar(select(Activity).where(Activity.material_id == material_id, Activity.chapter_id.not_in(chapter_ids)).limit(1)):
        raise HTTPException(409, "资料仍被活动引用，不能移除所用章节")
