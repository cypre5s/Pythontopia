"""Developer experience HTTP routes and environment gates."""

from fastapi import APIRouter, Request

from app.experience.checkpoints import delete_checkpoint as _delete_checkpoint
from app.experience.checkpoints import preview_restore as _preview_restore
from app.experience.checkpoints import restore_checkpoint as _restore_checkpoint
from app.experience.checkpoints import save_checkpoint as _save_checkpoint
from app.experience.contracts import Actor, ChapterInput, Db, OpenInput, RestoreInput, SaveInput, Scope, StageInput
from app.experience.navigation import open_content as _open_content
from app.experience.navigation import overview as _overview
from app.experience.navigation import return_to_spawn as _return_to_spawn
from app.experience.navigation import switch_chapter as _switch_chapter
from app.experience.stages import locate_stage as _locate_stage
from app.experience.stages import preview_stage as _preview_stage

router = APIRouter(prefix="/experience", tags=["experience tools"])


@router.post("/chapter")
def switch_chapter(body: ChapterInput, request: Request, db: Db, user: Actor):
    return _switch_chapter(body=body, request=request, db=db, user=user)


@router.get("")
def overview(classroom_id: int, request: Request, db: Db, user: Actor):
    return _overview(classroom_id=classroom_id, request=request, db=db, user=user)


@router.post("/open")
def open_content(body: OpenInput, request: Request, db: Db, user: Actor):
    return _open_content(body=body, request=request, db=db, user=user)


@router.post("/stage/preview")
def preview_stage(body: StageInput, request: Request, db: Db, user: Actor):
    return _preview_stage(body=body, request=request, db=db, user=user)


@router.post("/stage")
def locate_stage(body: StageInput, request: Request, db: Db, user: Actor):
    return _locate_stage(body=body, request=request, db=db, user=user)


@router.post("/checkpoints")
def save_checkpoint(body: SaveInput, request: Request, db: Db, user: Actor):
    return _save_checkpoint(body=body, request=request, db=db, user=user)


@router.get("/checkpoints/{checkpoint_id}/preview")
def preview_restore(checkpoint_id: str, classroom_id: int, request: Request, db: Db, user: Actor):
    return _preview_restore(checkpoint_id=checkpoint_id, classroom_id=classroom_id, request=request, db=db, user=user)


@router.post("/checkpoints/{checkpoint_id}/restore")
def restore_checkpoint(checkpoint_id: str, body: RestoreInput, request: Request, db: Db, user: Actor):
    return _restore_checkpoint(checkpoint_id=checkpoint_id, body=body, request=request, db=db, user=user)


@router.delete("/checkpoints/{checkpoint_id}")
def delete_checkpoint(checkpoint_id: str, classroom_id: int, request: Request, db: Db, user: Actor):
    return _delete_checkpoint(checkpoint_id=checkpoint_id, classroom_id=classroom_id, request=request, db=db, user=user)


@router.post("/spawn")
def return_to_spawn(body: Scope, request: Request, db: Db, user: Actor):
    return _return_to_spawn(body=body, request=request, db=db, user=user)
