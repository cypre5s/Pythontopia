"""Private character images shared by authoring, package exchange and the game."""
from copy import deepcopy
import hashlib
import logging
import time
from pathlib import Path
import tempfile
from typing import Annotated
from uuid import uuid4

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session

from .auth import current_user, require_teacher
from .characters import asset_descriptor, builtin_assets, stored_assets, validate_image
from .course_exchange_models import CoursePackageState
from .course_instances import classroom_course
from .courses import authorize_classroom
from .db import get_db
from .models import User, CourseInstance, Npc
from .storage import get_storage
from .teacher_scope import teacher_instance_id

router = APIRouter(tags=["character-assets"])


@router.get("/teacher/classrooms/{classroom_id}/character-assets")
def list_assets(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    instance_id = teacher_instance_id(db, user, classroom_id)
    assets = stored_assets(db, instance_id)
    return builtin_assets() + [asset_descriptor("asset:" + row["id"], row["character_kind"], assets, classroom_id)
                              for row in assets if row.get("character_kind")]


@router.post("/teacher/classrooms/{classroom_id}/character-assets", status_code=201)
def upload_asset(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)],
                       file: Annotated[UploadFile, File()], kind: Annotated[str, Form()],
                       owner_npc_id: Annotated[str, Form()], expected_revision: Annotated[int, Form()],
                       frame_width: Annotated[int, Form()] = 16, frame_height: Annotated[int, Form()] = 20,
                       ):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    if not 1 <= len(owner_npc_id.strip()) <= 100:
        raise HTTPException(422, "角色ID无效")
    if db.get(CourseInstance, instance_id).revision != expected_revision:
        raise HTTPException(409, "课程已修改，请刷新后重新上传")
    with tempfile.TemporaryDirectory(prefix="character-") as folder:
        path = Path(folder) / "upload"
        size, digest = 0, hashlib.sha256()
        with path.open("wb") as stream:
            while chunk := file.file.read(65536):
                size += len(chunk)
                if size > 10 * 1024 * 1024:
                    raise HTTPException(413, "角色素材最多10MB")
                stream.write(chunk)
                digest.update(chunk)
        try:
            metadata = validate_image(path, kind, frame_width, frame_height)
        except ValueError as exc:
            raise HTTPException(422, str(exc)) from exc
        suffix = {"image/png": ".png", "image/jpeg": ".jpg", "image/webp": ".webp"}[metadata["media_type"]]
        asset_id = uuid4().hex
        key = f"characters/{instance_id}/{asset_id}{suffix}"
        get_storage().put(key, path)
    row = {**metadata, "id": asset_id, "storage_key": key, "size_bytes": size, "sha256": digest.hexdigest(),
           "source": "教师自定义上传", "license": "未声明", "owner_npc_id": owner_npc_id,
           "staged_at": time.time(), "original_name": Path(file.filename or "图片").name[:200]}
    state = db.get(CoursePackageState, instance_id)
    if state is None:
        state = CoursePackageState(course_instance_id=instance_id, configuration={})
        db.add(state)
    config = deepcopy(state.configuration)
    config["_assets"] = [*config.get("_assets", []), row]
    state.configuration = config
    db.commit()
    return asset_descriptor("asset:" + asset_id, kind, [row], classroom_id)


def retire_unused_assets(state, npcs, *, expired_only=False):
    """Queue deletions in the same transaction as role edits; never delete before commit."""
    config = deepcopy(state.configuration)
    used = {row[kind][6:] for row in npcs for kind in ("portrait", "sprite") if row[kind].startswith("asset:")}
    kept, retired = [], list(config.get("_character_garbage", []))
    for asset in config.get("_assets", []):
        eligible = not expired_only or asset.get("staged_at", time.time()) < time.time() - 86400
        if asset.get("character_kind") and asset["id"] not in used and eligible:
            retired.append(asset["storage_key"])
        else:
            if asset["id"] in used:
                asset.pop("staged_at", None)
            kept.append(asset)
    config.update(_assets=kept, _character_garbage=list(dict.fromkeys(retired)))
    state.configuration = config


def cleanup_character_assets(classroom_id=None):
    """Retry durable deletions and expire abandoned uploads, under the course lock."""
    from sqlalchemy import select
    from .db import SessionLocal
    from .course_scope import lock_course
    with SessionLocal() as db:
        query = select(CourseInstance.id, CourseInstance.classroom_id)
        if classroom_id is not None:
            query = query.where(CourseInstance.classroom_id == classroom_id)
        instances = list(db.execute(query))
    for instance_id, cid in instances:
        with SessionLocal() as db:
            lock_course(db, cid, exclusive=True)
            state = db.get(CoursePackageState, instance_id)
            if not state:
                continue
            npcs = [{kind: getattr(npc, kind) for kind in ("portrait", "sprite")}
                    for npc in db.scalars(select(Npc).where(Npc.course_instance_id == instance_id))]
            retire_unused_assets(state, npcs, expired_only=True)
            db.commit()
            lock_course(db, cid, exclusive=True)
            db.refresh(state)
            drain_character_garbage(db, state)


def drain_character_garbage(db, state):
    config = deepcopy(state.configuration)
    pending = []
    live = {asset["storage_key"] for asset in config.get("_assets", [])}
    for key in config.get("_character_garbage", []):
        if key in live:
            continue
        if not key.startswith(f"characters/{state.course_instance_id}/"):
            logging.getLogger(__name__).warning("Refusing character cleanup outside owning course")
            continue
        try:
            get_storage().delete(key)
        except Exception:
            logging.getLogger(__name__).exception("Character image cleanup will retry")
            pending.append(key)
    config["_character_garbage"] = pending
    state.configuration = config
    db.commit()


@router.get("/classrooms/{classroom_id}/character-assets/{asset_id}")
def read_asset(classroom_id: int, asset_id: str, db: Annotated[Session, Depends(get_db)],
               user: Annotated[User, Depends(current_user)]):
    authorize_classroom(db, user, classroom_id)
    instance = classroom_course(db, classroom_id)
    row = next((row for row in stored_assets(db, instance.id) if row["id"] == asset_id and row.get("character_kind")), None) if instance else None
    if row is None:
        raise HTTPException(404, "角色素材不存在")
    if user.role == "student":
        from sqlalchemy import select
        from .models import Npc
        reference = "asset:" + asset_id
        if not db.scalar(select(Npc.key).where(Npc.course_instance_id == instance.id,
                                               (Npc.portrait == reference) | (Npc.sprite == reference))):
            raise HTTPException(404, "角色素材尚未发布")
    return StreamingResponse(get_storage().chunks(row["storage_key"], 0, row["size_bytes"] - 1),
                             media_type=row["media_type"], headers={"Cache-Control": "private, no-store",
                                                                  "X-Content-Type-Options": "nosniff"})
