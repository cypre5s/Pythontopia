"""Read and review narration rows; staleness is derived from the material's current pages."""

import hashlib

from sqlalchemy import delete, select

from ..models import Material, now
from .models import VISIBLE_STATUSES, PageNarration

MAX_TEXT = 4000
MIN_SECONDS, MAX_SECONDS = 30, 300


def text_hash(text: str) -> str:
    return hashlib.sha256((text or "").strip().encode("utf-8")).hexdigest()


def page_map(material: Material) -> dict[int, dict]:
    """Pages keyed by the page number the reading workspace actually shows."""
    pages = (material.content or {}).get("pages") or []
    result = {}
    for item in pages:
        if not isinstance(item, dict):
            continue
        page_no = item.get("page_no") or item.get("id")
        if isinstance(page_no, int):
            result[page_no] = item
    return result


def page_text(material: Material, page_no: int) -> str | None:
    page = page_map(material).get(page_no)
    if page is None:
        return None
    return str(page.get("text") or "")


def rows(db, material_id: int) -> list[PageNarration]:
    return list(db.scalars(select(PageNarration).where(PageNarration.material_id == material_id).order_by(PageNarration.page_no)))


def row(db, material_id: int, page_no: int) -> PageNarration | None:
    return db.scalar(select(PageNarration).where(PageNarration.material_id == material_id, PageNarration.page_no == page_no))


def sync_staleness(db, material: Material) -> bool:
    """Void a review when the page it was written against changed or disappeared.

    A replacement upload rewrites `Material.content` wholesale; rows live in their own
    table, so they survive — but they must not stay approved against different text.
    """
    pages = page_map(material)
    changed = False
    for item in rows(db, material.id):
        current = pages.get(item.page_no)
        if current is None or text_hash(str(current.get("text") or "")) != item.page_hash:
            if item.status in {"draft", "accepted", "rejected"}:
                item.status = "stale"
                changed = True
    if changed:
        db.flush()
    return changed


def snapshot(material: Material, item: PageNarration | None, page: dict | None) -> dict:
    text = str((page or {}).get("text") or "")
    return {
        "page_no": (page or {}).get("page_no") or (page or {}).get("id"),
        "title": (page or {}).get("title") or "",
        "has_text": bool(text.strip()),
        "source_chars": len(text),
        # Teacher review compares the wording against the page, so send a bounded copy.
        "source_text": text[:2000],
        "narration": None if item is None else {
            "text": item.text,
            "bridge_in": item.bridge_in,
            "target_seconds": item.target_seconds,
            "status": item.status,
            "source": item.source,
            "model": item.model,
            "image_used": item.image_used,
            "anchors": item.anchors or [],
            "edited_by_teacher": item.edited_by_teacher,
            "reviewed_at": item.reviewed_at.isoformat() if item.reviewed_at else None,
            "stale_reason": "本页原文已变化，需要重新生成或重新审校" if item.status == "stale" else None,
        },
    }


def coverage(pages: dict[int, dict], items: list[PageNarration]) -> dict:
    by_page = {item.page_no: item for item in items}
    counts = {status: 0 for status in ("draft", "accepted", "rejected", "unavailable", "stale")}
    covered = 0
    for page_no in pages:
        item = by_page.get(page_no)
        if item is None:
            continue
        covered += 1
        counts[item.status] = counts.get(item.status, 0) + 1
    return {"pages": len(pages), "covered": covered, "missing": len(pages) - covered, **counts}


def trim_text(value: str) -> str:
    text = (value or "").strip()
    if not text:
        raise ValueError("讲解不能为空；要移除请删除本页讲解")
    if len(text) > MAX_TEXT:
        raise ValueError(f"讲解不能超过 {MAX_TEXT} 字")
    return text


def trim_seconds(value: int | None) -> int:
    if value is None:
        return 90
    if not isinstance(value, int) or not MIN_SECONDS <= value <= MAX_SECONDS:
        raise ValueError(f"目标时长必须在 {MIN_SECONDS} 到 {MAX_SECONDS} 秒之间")
    return value


def upsert(db, material: Material, page_no: int, *, text: str, target_seconds: int | None = None,
           source: str = "teacher", keep_review: bool = False) -> PageNarration:
    """Teacher-authored wording. An approved page stays approved; a void or refused one returns to draft."""
    clean = trim_text(text)
    seconds = trim_seconds(target_seconds)
    item = row(db, material.id, page_no)
    if item is None:
        item = PageNarration(material_id=material.id, page_no=page_no)
        db.add(item)
    item.text = clean
    item.target_seconds = seconds
    item.source = source
    item.page_hash = text_hash(page_text(material, page_no) or "")
    item.input_hash = ""
    item.prompt_version = ""
    item.model = ""
    item.image_used = False
    item.anchors = []
    item.edited_by_teacher = True
    item.updated_at = now()
    if not keep_review and item.status in {"stale", "rejected", "unavailable"}:
        item.status = "draft"
        item.reviewed_by = None
        item.reviewed_at = None
    db.flush()
    return item


def decide(db, material: Material, page_no: int, status: str, user) -> PageNarration:
    """Accept or reject one page. A stale row must be regenerated or edited first."""
    item = row(db, material.id, page_no)
    if item is None:
        raise LookupError("本页还没有讲解草稿")
    if item.status == "stale":
        raise ValueError("本页原文已变化，请先重新生成或编辑后再审校")
    if not (item.text or "").strip() and status == "accepted":
        raise ValueError("讲解为空，不能接受")
    item.status = status
    item.reviewed_by = user.id
    item.reviewed_at = now()
    item.updated_at = now()
    db.flush()
    return item


def remove(db, material: Material, page_no: int) -> bool:
    result = db.execute(delete(PageNarration).where(PageNarration.material_id == material.id, PageNarration.page_no == page_no))
    db.flush()
    return bool(result.rowcount)


def visible(db, material: Material) -> list[PageNarration]:
    """Only teacher-approved wording reaches a student."""
    return [item for item in rows(db, material.id) if item.status in VISIBLE_STATUSES and item.text.strip()]
