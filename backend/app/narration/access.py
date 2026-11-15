"""Authorize narratable materials and page access within a classroom course."""

from typing import Any, cast

from fastapi import HTTPException
from sqlalchemy.orm import Session

from app.models import Material, User
from app.teacher_scope import require_scope, teacher_instance_id

from . import repository


def teacher_material(db: Session, user: User, classroom_id: int, material_id: int, *, exclusive: bool = False) -> Material:
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=exclusive)
    material = cast(Material, require_scope(db.get(Material, material_id), instance_id))
    require_narratable(material)
    if exclusive:
        db.refresh(material, with_for_update=True)
    return material


def require_narratable(material: Material) -> None:
    if material.kind not in {"pdf", "presentation"}:
        raise HTTPException(422, "只有 PDF 和演示文稿支持逐页 AI 讲解")


def require_page(material: Material, page_no: int) -> dict[str, Any]:
    page = repository.page_map(material).get(page_no)
    if page is None:
        raise HTTPException(404, "本页不存在或页码超出范围")
    return page
