"""Present material metadata and authenticated delivery links."""

from math import isfinite

from app.content_relations import material_chapter_ids


def material_payload(db, material, *, chapter_ids=None):
    chapters = material_chapter_ids(db, material.id) if chapter_ids is None else chapter_ids
    duration = material.content.get("duration_seconds")
    if material.kind != "video" or type(duration) not in (int, float) or (type(duration) is float and not isfinite(duration)) or duration <= 0:
        duration = None
    return {
        "id": material.id,
        "title": material.title,
        "kind": material.kind,
        "chapter_id": chapters[0] if chapters else None,
        "chapter_ids": chapters,
        "status": material.status,
        "pages": material.content.get("pages", []),
        "notice": material.content.get("notice"),
        "media_url": f"/api/materials/{material.id}/file" if material.storage_key and material.status == "ready" else None,
        "error": material.error,
        "duration_seconds": duration,
    }
