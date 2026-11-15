"""Deliver authorized material files, pages and derived assets."""

from __future__ import annotations

import mimetypes
import re
from pathlib import Path

from fastapi import HTTPException, Request
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session

from app.materials.access import teacher_material
from app.materials.storage import get_storage
from app.models import User
from app.teacher_scope import teacher_instance_id


def byte_range(value: str | None, size: int) -> tuple[int, int, bool]:
    if not value:
        return 0, max(0, size - 1), False
    match = re.fullmatch(r"bytes=(\d*)-(\d*)", value)
    if not match or not any(match.groups()) or size <= 0:
        raise HTTPException(416, "Range 无效", headers={"Content-Range": f"bytes */{size}"})
    left, right = match.groups()
    if not left:
        length = int(right)
        if length <= 0:
            raise HTTPException(416, "Range 无效", headers={"Content-Range": f"bytes */{size}"})
        start, end = max(0, size - length), size - 1
    else:
        start, end = int(left), min(int(right), size - 1) if right else size - 1
    if start > end or start >= size:
        raise HTTPException(416, "Range 越界", headers={"Content-Range": f"bytes */{size}"})
    return start, end, True


def material_file(material_id: int, classroom_id: int, request: Request, user: User, db: Session):
    from app.course_content.access import authorize_material

    item = authorize_material(db, user, classroom_id, material_id)
    return stream_material(item, request)


def teacher_material_file(material_id: int, request: Request, user: User, db: Session, classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id)
    return stream_material(teacher_material(db, user, material_id, instance_id=instance_id), request)


def stream_material(item, request):
    if not item or not item.storage_key:
        raise HTTPException(404, "原媒体文件缺失")
    if item.status != "ready":
        raise HTTPException(409, "资料尚未就绪")
    key = item.content.get("preview_key", item.storage_key)
    adapter = get_storage()
    try:
        size = adapter.size(key)
    except (FileNotFoundError, OSError):
        raise HTTPException(404, "媒体文件缺失")
    start, end, partial = byte_range(request.headers.get("range"), size)
    headers = {
        "Accept-Ranges": "bytes",
        "Content-Length": str(end - start + 1),
        "Cache-Control": "private, no-store",
        "X-Content-Type-Options": "nosniff",
        "Content-Disposition": 'inline; filename="material' + Path(key).suffix + '"',
    }
    if partial:
        headers["Content-Range"] = f"bytes {start}-{end}/{size}"
    return StreamingResponse(
        adapter.chunks(key, start, end),
        status_code=206 if partial else 200,
        media_type=mimetypes.guess_type(key)[0] or "application/octet-stream",
        headers=headers,
    )
