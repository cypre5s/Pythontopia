"""Teacher review and student reading endpoints for page narration.

Mounting is deliberately additive: `app.main` only includes this router, and the
package keeps its own model, prompts and (later) authoring client.
"""

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict
from sqlalchemy.orm import Session

from ..auth import current_user, require_teacher
from ..db import get_db
from ..models import Material, User
from ..teacher_scope import require_scope, teacher_instance_id
from . import repository

router = APIRouter(prefix="/teacher/classrooms/{classroom_id}/materials/{material_id}/narrations", tags=["narration"])
student_router = APIRouter(prefix="/materials/{material_id}/narrations", tags=["narration"])


class NarrationInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    text: str
    target_seconds: int | None = None


def _material(db: Session, user: User, classroom_id: int, material_id: int, *, exclusive: bool = False) -> Material:
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=exclusive)
    return require_scope(db.get(Material, material_id), instance_id)


def _page(material: Material, page_no: int) -> dict:
    page = repository.page_map(material).get(page_no)
    if page is None:
        raise HTTPException(404, "本页不存在或页码超出范围")
    return page


@router.get("")
def list_narrations(classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
                    user: Annotated[User, Depends(require_teacher)]):
    material = _material(db, user, classroom_id, material_id)
    repository.sync_staleness(db, material)
    db.commit()
    pages = repository.page_map(material)
    items = repository.rows(db, material.id)
    by_page = {item.page_no: item for item in items}
    return {
        "material_id": material.id,
        "title": material.title,
        "coverage": repository.coverage(pages, items),
        "pages": [repository.snapshot(material, by_page.get(page_no), pages[page_no]) for page_no in sorted(pages)],
    }


@router.put("/{page_no}")
def author_narration(page_no: int, classroom_id: int, material_id: int, body: NarrationInput,
                     db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    material = _material(db, user, classroom_id, material_id, exclusive=True)
    _page(material, page_no)
    repository.sync_staleness(db, material)
    try:
        item = repository.upsert(db, material, page_no, text=body.text, target_seconds=body.target_seconds)
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    db.commit()
    return {"page_no": page_no, "status": item.status, "text": item.text}


@router.post("/{page_no}/accept")
def accept_narration(page_no: int, classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
                     user: Annotated[User, Depends(require_teacher)]):
    material = _material(db, user, classroom_id, material_id, exclusive=True)
    repository.sync_staleness(db, material)
    try:
        item = repository.decide(db, material, page_no, "accepted", user)
    except LookupError as exc:
        raise HTTPException(404, str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(409, str(exc)) from exc
    db.commit()
    return {"page_no": page_no, "status": item.status, "reviewed_at": item.reviewed_at.isoformat()}


@router.post("/{page_no}/reject")
def reject_narration(page_no: int, classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
                     user: Annotated[User, Depends(require_teacher)]):
    material = _material(db, user, classroom_id, material_id, exclusive=True)
    repository.sync_staleness(db, material)
    try:
        item = repository.decide(db, material, page_no, "rejected", user)
    except LookupError as exc:
        raise HTTPException(404, str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(409, str(exc)) from exc
    db.commit()
    return {"page_no": page_no, "status": item.status}


@router.delete("/{page_no}")
def delete_narration(page_no: int, classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
                     user: Annotated[User, Depends(require_teacher)]):
    material = _material(db, user, classroom_id, material_id, exclusive=True)
    if not repository.remove(db, material, page_no):
        raise HTTPException(404, "本页还没有讲解草稿")
    db.commit()
    return {"page_no": page_no, "removed": True}


@student_router.get("")
def read_narrations(material_id: int, classroom_id: int, db: Annotated[Session, Depends(get_db)],
                    user: Annotated[User, Depends(current_user)]):
    """Students only ever receive teacher-approved wording."""
    from ..courses import authorize_material

    material = authorize_material(db, user, classroom_id, material_id)
    repository.sync_staleness(db, material)
    db.commit()
    return {
        "material_id": material.id,
        "pages": [{"page_no": item.page_no, "text": item.text, "bridge_in": item.bridge_in,
                   "target_seconds": item.target_seconds} for item in repository.visible(db, material)],
    }
