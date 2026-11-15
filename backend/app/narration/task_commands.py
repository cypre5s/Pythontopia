"""Explicit narration generation commands shared by HTTP actions and task retry.

Own task leases, durable progress and per-page commits; page reads never call these.
"""

from typing import Any

from fastapi import HTTPException
from sqlalchemy.orm import Session

from ..models import Material, User
from ..teacher_scope import require_scope, teacher_instance_id
from . import audio, calibrate, repository, service, tts
from . import client as model_client
from .access import require_narratable, teacher_material
from .contracts import AudioRecord, GeneratedPage
from .request_contracts import GenerateInput, PageGenerateInput, RangeInput


def generate_narration(
    page_no: int, classroom_id: int, material_id: int, db: Session, user: User, body: PageGenerateInput | None = None
) -> GeneratedPage:
    """One explicit authoring request = at most one model call. Never runs on page turns."""
    instance_id = teacher_instance_id(db, user, classroom_id)
    material = require_scope(db.get(Material, material_id), instance_id)
    require_narratable(material)
    repository.sync_staleness(db, material)
    db.commit()
    with repository.task(
        db, material, "generate", user, request={"operation": "generate_page", "page_no": page_no, "body": body.model_dump() if body else {}}
    ):
        try:
            result = service.generate_page(db, material, page_no, instance_id=instance_id, target_seconds=body.target_seconds if body else None)
        except service.NarrationUnavailable as exc:
            raise HTTPException(409, str(exc)) from exc
        except LookupError as exc:
            raise HTTPException(404, str(exc)) from exc
        except ValueError as exc:
            raise HTTPException(422, str(exc)) from exc
        except model_client.ModelUnavailable as exc:
            raise HTTPException(409, str(exc)) from exc
        except model_client.ModelFailed as exc:
            repository.release(db, material.id, status="failed", message=str(exc))
            raise HTTPException(502, str(exc)) from exc
        except Exception:
            repository.release(db, material.id, status="failed", message="生成本页失败")
            raise
        db.commit()
        repository.release(db, material.id, message="本页已生成")
        return result


def generate_range(classroom_id: int, material_id: int, body: GenerateInput, db: Session, user: User) -> dict[str, list[GeneratedPage]]:
    """Serial batch: each page commits on its own, so a failure keeps earlier pages."""
    instance_id = teacher_instance_id(db, user, classroom_id)
    material = require_scope(db.get(Material, material_id), instance_id)
    require_narratable(material)
    repository.sync_staleness(db, material)
    db.commit()
    if body.first_page < 1 or body.last_page < body.first_page:
        raise HTTPException(422, "页码范围无效")
    with repository.task(db, material, "generate", user, request={"operation": "generate_range", "body": body.model_dump()}):
        try:
            results = service.generate_range(
                db,
                material,
                first=body.first_page,
                last=body.last_page,
                instance_id=instance_id,
                target_seconds=body.target_seconds,
                serial=body.mode == "serial",
                only_pages=db.info.get("narration_retry_pages"),
            )
        except service.NarrationUnavailable as exc:
            raise HTTPException(409, str(exc)) from exc
        except LookupError as exc:
            raise HTTPException(404, str(exc)) from exc
        except ValueError as exc:
            repository.release(db, material.id, status="failed", message=str(exc))
            raise HTTPException(422, str(exc)) from exc
        db.commit()
        done = sum(1 for item in results if item.get("status") != "failed")
        failed = [item for item in results if item.get("status") == "failed"]
        repository.release(
            db,
            material.id,
            status="failed" if failed else "done",
            message=f"已生成 {done} 页，失败 {len(failed)} 页" if failed else f"已生成 {done} 页",
            extra={"done": len(results), "total": len(results), "failed": failed, "retry_pages": [item["page_no"] for item in failed]},
        )
        return {"results": results}


def calibrate_deck(classroom_id: int, material_id: int, db: Session, user: User) -> dict[str, Any]:
    """Walk the whole deck with a rolling summary; returns a report, rewrites nothing."""
    instance_id = teacher_instance_id(db, user, classroom_id)
    material = require_scope(db.get(Material, material_id), instance_id)
    require_narratable(material)
    repository.sync_staleness(db, material)
    db.commit()
    with repository.task(db, material, "calibrate", user, request={"operation": "calibrate"}):
        try:
            entry = calibrate.analyse(db, material, instance_id=instance_id)
        except model_client.ModelUnavailable as exc:
            repository.release(db, material.id, status="failed", message=str(exc))
            raise HTTPException(409, str(exc)) from exc
        except model_client.ModelFailed as exc:
            repository.release(db, material.id, status="failed", message=str(exc))
            raise HTTPException(502, str(exc)) from exc
        except RuntimeError as exc:
            repository.release(db, material.id, status="failed", message=str(exc))
            raise HTTPException(409, str(exc)) from exc
        except LookupError as exc:
            repository.release(db, material.id, status="failed", message=str(exc))
            raise HTTPException(404, str(exc)) from exc
        db.commit()
        repository.release(db, material.id, message="校准完成")
        return {"id": entry.id, **(entry.report or {})}


def synthesize_audio(page_no: int, classroom_id: int, material_id: int, db: Session, user: User) -> AudioRecord:
    """Speak one approved page. Draft wording is refused, not spoken."""
    instance_id = teacher_instance_id(db, user, classroom_id)
    material = require_scope(db.get(Material, material_id), instance_id)
    require_narratable(material)
    repository.sync_staleness(db, material)
    db.commit()
    with repository.task(db, material, "audio", user, request={"operation": "audio_page", "page_no": page_no}):
        try:
            result = audio.synthesize_page(db, material, page_no)
        except tts.SpeechUnavailable as exc:
            repository.release(db, material.id, status="failed", message=str(exc))
            raise HTTPException(409, str(exc)) from exc
        except tts.SpeechFailed as exc:
            repository.release(db, material.id, status="failed", message=str(exc))
            raise HTTPException(502, str(exc)) from exc
        except LookupError as exc:
            repository.release(db, material.id, status="failed", message=str(exc))
            raise HTTPException(404, str(exc)) from exc
        except ValueError as exc:
            repository.release(db, material.id, status="failed", message=str(exc))
            raise HTTPException(409, str(exc)) from exc
        db.commit()
        repository.release(db, material.id, message="本页音频已生成")
        return result


def synthesize_audio_batch(classroom_id: int, material_id: int, db: Session, user: User, body: RangeInput | None = None) -> dict[str, Any]:
    """Synthesize every accepted page in the range; one failure must not abort the rest."""
    material = teacher_material(db, user, classroom_id, material_id)
    repository.sync_staleness(db, material)
    db.commit()
    if tts.unavailable_reason(db):
        raise HTTPException(409, tts.unavailable_reason(db) or "语音合成不可用")
    with repository.task(db, material, "audio", user, request={"operation": "audio_batch", "body": body.model_dump() if body else {}}):
        first = body.first_page if body and body.first_page else 1
        last = body.last_page if body and body.last_page else 10**9
        made: list[int] = []
        reused: list[int] = []
        failed: list[dict[str, Any]] = []
        skipped: list[int] = []
        wanted = [
            item
            for item in repository.rows(db, material.id)
            if first <= item.page_no <= last
            and item.status == "accepted"
            and item.text.strip()
            and (db.info.get("narration_retry_pages") is None or item.page_no in db.info["narration_retry_pages"])
        ]
        repository.beat(db, material.id, done=0, total=len(wanted))
        db.commit()
        for item in repository.rows(db, material.id):
            if (
                item.page_no < first
                or item.page_no > last
                or (db.info.get("narration_retry_pages") is not None and item.page_no not in db.info["narration_retry_pages"])
            ):
                continue
            if item.status != "accepted" or not item.text.strip():
                skipped.append(item.page_no)
                continue
            try:
                result = audio.synthesize_page(db, material, item.page_no)
                (reused if result["reused"] else made).append(item.page_no)
                repository.beat(db, material.id, done=len(made) + len(reused) + len(failed), total=len(wanted), message=f"音频第 {item.page_no} 页")
                db.commit()
            except (tts.SpeechFailed, ValueError, LookupError) as exc:
                db.rollback()
                failed.append({"page_no": item.page_no, "error": str(exc)[:160]})
                repository.beat(db, material.id, done=len(made) + len(reused) + len(failed), total=len(wanted))
                db.commit()
        repository.release(
            db,
            material.id,
            status="failed" if failed else "done",
            message=f"音频完成 {len(made) + len(reused)} 页，失败 {len(failed)} 页",
            extra={
                "done": len(made) + len(reused) + len(failed),
                "total": len(wanted),
                "failed": failed,
                "retry_pages": [item["page_no"] for item in failed],
            },
        )
        return {"made": made, "reused": reused, "failed": failed, "skipped": skipped[:50], "count": len(made) + len(reused)}
