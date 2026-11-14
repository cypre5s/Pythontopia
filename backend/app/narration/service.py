"""Generate one page of narration: idempotent, per-page retryable, never incidental."""

import base64
import hashlib
import logging
from datetime import datetime, timezone
from typing import Any

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..models import Material, now
from . import client as model_client
from . import config, prompts, render, repository
from .contracts import AuthoringSettings, GeneratedPage, Quota
from .models import PageNarration

log = logging.getLogger("narration")

# Slides with a long text layer do not need the (much) more expensive image input.
TEXT_ONLY_THRESHOLD = 200


class NarrationUnavailable(RuntimeError):
    """Authoring cannot run right now: no credentials, no quota, no renderer."""


def with_image(material: Material, page_text: str) -> bool:
    if material.kind == "presentation":
        return True
    return len(page_text.strip()) < TEXT_ONLY_THRESHOLD


def input_hash(*, page_hash: str, image: bytes | None, settings: AuthoringSettings, model: str,
               target_seconds: int) -> str:
    """Re-running the same page with the same inputs must not spend another call."""
    parts = "\n".join([
        page_hash, prompts.PROMPT_VERSION, model, settings["effort"],
        str(target_seconds), "image" if image else "text",
        hashlib.sha256(image).hexdigest() if image else "",
    ])
    return hashlib.sha256(parts.encode("utf-8")).hexdigest()


def previous_tail(db: Session, material: Material, page_no: int) -> str:
    """Carry the teacher-approved ending of the previous page to keep one voice."""
    previous = repository.row(db, material.id, page_no - 1)
    if previous is None or previous.status != "accepted" or not previous.text.strip():
        return ""
    text = previous.text.strip()
    return text[-120:]


def used_today(db: Session, instance_id: int) -> int:
    start = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)
    return db.scalar(select(func.count()).select_from(PageNarration).join(
        Material, Material.id == PageNarration.material_id).where(
        Material.course_instance_id == instance_id, PageNarration.source == "ai_generated",
        PageNarration.updated_at >= start)) or 0


def generate_page(db: Session, material: Material, page_no: int, *, instance_id: int,
                  target_seconds: int | None = None) -> GeneratedPage:
    """One page, one model call. Returns the stored row's public state."""
    reason = config.unavailable_reason()
    if reason:
        raise NarrationUnavailable(reason)
    page_text = repository.page_text(material, page_no)
    if page_text is None:
        raise LookupError("本页不存在或页码超出范围")
    settings = config.settings()
    seconds = repository.trim_seconds(target_seconds)
    use_image = with_image(material, page_text)
    image = render.render_page(material, page_no) if use_image else None
    if use_image and image is None:
        # A presentation without a readable source cannot be narrated from an empty text layer.
        raise NarrationUnavailable("本页没有可读的页面来源，无法生成讲解")
    model = settings["vision_model"] if image else settings["model"]
    page_hash = repository.text_hash(page_text)
    fingerprint = input_hash(page_hash=page_hash, image=image, settings=settings, model=model, target_seconds=seconds)
    existing = repository.row(db, material.id, page_no)
    if existing is not None and existing.input_hash == fingerprint and existing.status in {"draft", "accepted"}:
        return {"page_no": page_no, "status": existing.status, "reused": True, "model": existing.model}
    if used_today(db, instance_id) >= settings["daily_pages"]:
        raise NarrationUnavailable("本班今日讲解生成额度已用完，请明天再试")

    messages: list[dict[str, Any]] = [{"role": "system", "content": prompts.SYSTEM},
                {"role": "user", "content": prompts.user_parts(
                    material_title=material.title, page_no=page_no, page_text=page_text,
                    target_seconds=seconds, previous_tail=previous_tail(db, material, page_no - 1),
                    image_b64=base64.b64encode(image).decode("ascii") if image else None)}]
    result = model_client.chat(messages, settings=settings, model=model)

    item = existing or PageNarration(material_id=material.id, page_no=page_no)
    if existing is None:
        db.add(item)
    item.text = repository.trim_text(result["text"])
    item.bridge_in = result["bridge_in"]
    item.target_seconds = seconds
    # A regenerated page is a new draft: the old approval no longer applies to the new wording.
    item.status = "draft"
    item.source = "ai_generated"
    item.page_hash = page_hash
    item.input_hash = fingerprint
    item.prompt_version = prompts.PROMPT_VERSION
    item.model = result["model"]
    item.image_used = bool(image)
    item.anchors = result["anchors"]
    item.edited_by_teacher = False
    item.reviewed_by = None
    item.reviewed_at = None
    item.updated_at = now()
    db.flush()
    usage = result["usage"] or {}
    log.info("narration page %s of material %s: model=%s prompt=%s completion=%s reasoning=%s",
             page_no, material.id, item.model, usage.get("prompt_tokens"),
             usage.get("completion_tokens"), (usage.get("completion_tokens_details") or {}).get("reasoning_tokens"))
    return {"page_no": page_no, "status": item.status, "reused": False, "model": item.model,
            "chars": len(item.text), "image_used": item.image_used}


def generate_range(db: Session, material: Material, *, first: int, last: int, instance_id: int,
                   target_seconds: int | None = None) -> list[GeneratedPage]:
    """Serial generation; one failing page must not discard the pages that worked."""
    pages = repository.page_map(material)
    wanted = [page_no for page_no in range(first, last + 1) if page_no in pages]
    if not wanted:
        raise LookupError("指定范围内没有可生成的页面")
    results: list[GeneratedPage] = []
    for page_no in wanted:
        try:
            results.append(generate_page(db, material, page_no, instance_id=instance_id, target_seconds=target_seconds))
            db.commit()
        except (NarrationUnavailable, LookupError):
            db.rollback()
            raise
        except Exception as exc:  # noqa: BLE001 -- one page must not abort the batch
            db.rollback()
            log.warning("narration failed for page %s: %s", page_no, exc)
            results.append({"page_no": page_no, "status": "failed", "error": str(exc)[:200]})
    return results


def quota(db: Session, instance_id: int) -> Quota:
    settings = config.settings()
    return {"used_today": used_today(db, instance_id), "daily_limit": settings["daily_pages"],
            "available": config.unavailable_reason() is None}
