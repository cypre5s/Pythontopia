"""Read and review narration rows; staleness is derived from the material's current pages."""

import hashlib
import json
from contextlib import contextmanager
from collections.abc import Iterator
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from datetime import datetime, timedelta, timezone

from ..models import Material, User, now
from .models import VISIBLE_STATUSES, NarrationJob, PageNarration

MAX_TEXT = 4000
MIN_SECONDS, MAX_SECONDS = 30, 300


def text_hash(text: str) -> str:
    return hashlib.sha256((text or "").strip().encode("utf-8")).hexdigest()


def page_map(material: Material) -> dict[int, dict[str, Any]]:
    """Pages keyed by the page number the reading workspace actually shows."""
    pages = (material.content or {}).get("pages") or []
    result: dict[int, dict[str, Any]] = {}
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


def source_hash(material: Material, page_no: int) -> str:
    """Include the uploaded asset, including image-only pages, in review validity."""
    content = material.content or {}
    payload = {"page": page_map(material).get(page_no), "file": material.storage_key,
               "sha256": content.get("sha256"), "preview": content.get("preview_key")}
    return text_hash(json.dumps(payload, ensure_ascii=False, sort_keys=True))


def revision(item: PageNarration | None) -> str:
    if item is None:
        return "missing"
    return text_hash(json.dumps([item.id, item.text, item.status, item.page_hash,
                                item.updated_at.isoformat() if item.updated_at else None]))


def rows(db: Session, material_id: int) -> list[PageNarration]:
    return list(db.scalars(select(PageNarration).where(PageNarration.material_id == material_id)
                           .order_by(PageNarration.page_no)))


def row(db: Session, material_id: int, page_no: int) -> PageNarration | None:
    return db.scalar(select(PageNarration).where(PageNarration.material_id == material_id,
                                                 PageNarration.page_no == page_no))


def sync_staleness(db: Session, material: Material) -> bool:
    """Void a review when the page it was written against changed or disappeared.

    A replacement upload rewrites `Material.content` wholesale; rows live in their own
    table, so they survive — but they must not stay approved against different text.
    """
    pages = page_map(material)
    changed = False
    for item in rows(db, material.id):
        current = pages.get(item.page_no)
        if current is None or source_hash(material, item.page_no) != item.page_hash:
            if item.status in {"draft", "accepted", "rejected"}:
                item.status = "stale"
                changed = True
    if changed:
        db.flush()
    return changed


def snapshot(material: Material, item: PageNarration | None, page: dict[str, Any] | None) -> dict[str, Any]:
    text = str((page or {}).get("text") or "")
    narration: dict[str, Any] | None = None
    if item is not None:
        narration = {
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
        }
    return {
        "page_no": (page or {}).get("page_no") or (page or {}).get("id"),
        "title": (page or {}).get("title") or "",
        "has_text": bool(text.strip()),
        "source_chars": len(text),
        # Teacher review compares the wording against the page, so send a bounded copy.
        "source_text": text[:2000],
        "narration": narration,
    }


def coverage(pages: dict[int, dict[str, Any]], items: list[PageNarration]) -> dict[str, int]:
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


def upsert(db: Session, material: Material, page_no: int, *, text: str, target_seconds: int | None = None,
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
    item.page_hash = source_hash(material, page_no)
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


def decide(db: Session, material: Material, page_no: int, status: str, user: User) -> PageNarration:
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


def remove(db: Session, material: Material, page_no: int) -> bool:
    item = row(db, material.id, page_no)
    if item is None:
        return False
    db.delete(item)
    db.flush()
    return True


def visible(db: Session, material: Material) -> list[PageNarration]:
    """Only teacher-approved wording reaches a student."""
    return [item for item in rows(db, material.id) if item.status in VISIBLE_STATUSES and item.text.strip()
            and item.page_hash == source_hash(material, item.page_no)]


# 心跳超过这个时间就认为上一次任务已经死掉（进程被杀、容器重启），允许接管。
JOB_STALE_SECONDS = 300


class JobBusy(RuntimeError):
    """Another narration task is already running for this material."""

    def __init__(self, kind: str, started_at: datetime | None) -> None:
        self.kind = kind
        self.started_at = started_at
        super().__init__("这份资料正在处理中，请等待当前任务结束")


@contextmanager
def task(db: Session, material: Material, kind: str, user: User) -> Iterator[None]:
    from fastapi import HTTPException

    try:
        acquire(db, material, kind, user)
    except JobBusy as exc:
        raise HTTPException(409, str(exc)) from exc
    try:
        yield
        db.commit()
    except BaseException:
        db.rollback()
        release(db, material.id, status="failed", message="任务未完成，请查看错误后重试")
        raise
    else:
        release(db, material.id)


def job_running(job: NarrationJob) -> bool:
    """活着的任务才算“正在运行”；心跳过期说明进程已经死了，可以接管。"""
    if job.status != "running":
        return False
    moment = job.heartbeat_at or job.started_at
    if moment is None:
        return False
    if moment.tzinfo is None:
        moment = moment.replace(tzinfo=timezone.utc)
    return datetime.now(timezone.utc) - moment < timedelta(seconds=JOB_STALE_SECONDS)


def job_of(db: Session, material_id: int) -> NarrationJob | None:
    return db.scalar(select(NarrationJob).where(NarrationJob.material_id == material_id))


def acquire(db: Session, material: Material, kind: str, user: User | None = None) -> NarrationJob:
    """Take the material lock for one task. Raises JobBusy while another task is alive."""
    # A missing job row cannot be locked; serialize first acquisition on its parent.
    db.scalar(select(Material).where(Material.id == material.id).with_for_update())
    job = db.scalar(select(NarrationJob).where(NarrationJob.material_id == material.id)
                    .with_for_update().execution_options(populate_existing=True))
    if job is None:
        job = NarrationJob(material_id=material.id)
        db.add(job)
    elif job_running(job):
        db.rollback()
        raise JobBusy(job.kind, job.started_at)
    job.kind = kind
    job.status = "running"
    job.requested_by = user.id if user else None
    job.started_at = now()
    job.heartbeat_at = job.started_at
    job.finished_at = None
    job.progress = {}
    job.message = ""
    db.commit()
    db.info.setdefault("narration_leases", {})[material.id] = job.started_at
    return job


def owned_job(db: Session, material_id: int) -> NarrationJob | None:
    lease = db.info.get("narration_leases", {}).get(material_id)
    job = db.scalar(select(NarrationJob).where(NarrationJob.material_id == material_id)
                    .with_for_update().execution_options(populate_existing=True))
    if job is None or lease is None or job.started_at is None or job.status != "running":
        return None
    if job.started_at.replace(tzinfo=None) != lease.replace(tzinfo=None):
        return None
    return job


def ensure_lease(db: Session, material_id: int) -> None:
    if material_id in db.info.get("narration_leases", {}) and owned_job(db, material_id) is None:
        raise ValueError("任务已被接管，请刷新后查看最新结果")


def beat(db: Session, material_id: int, *, done: int | None = None, total: int | None = None,
         message: str | None = None) -> None:
    """Refresh the heartbeat together with the page commit, so a live task never looks stale."""
    job = owned_job(db, material_id)
    if job is None or job.status != "running":
        return
    job.heartbeat_at = now()
    if done is not None or total is not None:
        job.progress = {**(job.progress or {}),
                        **({"done": done} if done is not None else {}),
                        **({"total": total} if total is not None else {})}
    if message is not None:
        job.message = message[:200]


def release(db: Session, material_id: int, *, status: str = "done", message: str = "",
            extra: dict[str, Any] | None = None) -> None:
    if status == "failed":
        db.rollback()
    job = owned_job(db, material_id)
    if job is None:
        return
    job.status = status
    job.finished_at = now()
    job.message = message[:200]
    if extra:
        job.progress = {**(job.progress or {}), **extra}
    db.commit()
    db.info.get("narration_leases", {}).pop(material_id, None)
