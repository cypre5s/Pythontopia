"""Teacher review and student reading endpoints for page narration.

Mounting is deliberately additive: `app.main` only includes this router, and the
package keeps its own model, prompts and (later) authoring client.
"""

from typing import Annotated, Any, Literal, cast

from fastapi import APIRouter, Depends, HTTPException, Response
from fastapi.responses import FileResponse
from pydantic import BaseModel, ConfigDict
from sqlalchemy.orm import Session

from ..auth import current_user, require_teacher
from ..db import get_db
from ..models import Material, User
from ..teacher_scope import require_scope, teacher_instance_id
from . import audio, calibrate
from . import client as model_client
from . import render, repository, service
from . import tts
from .contracts import AppliedCalibration, AudioRecord, GeneratedPage, Quota

router = APIRouter(prefix="/teacher/classrooms/{classroom_id}/materials/{material_id}/narrations", tags=["narration"])
student_router = APIRouter(prefix="/materials/{material_id}/narrations", tags=["narration"])


class NarrationInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    text: str
    target_seconds: int | None = None


class GenerateInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    first_page: int
    last_page: int
    target_seconds: int | None = None
    # serial：把已生成的页作为上下文，过渡句才有依据；independent：每页独立（旧行为）。
    mode: Literal["independent", "serial"] = "independent"


class RangeInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    first_page: int | None = None
    last_page: int | None = None


class PageGenerateInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    target_seconds: int | None = None


def _material(db: Session, user: User, classroom_id: int, material_id: int, *, exclusive: bool = False) -> Material:
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=exclusive)
    material = cast(Material, require_scope(db.get(Material, material_id), instance_id))
    if exclusive:
        db.refresh(material, with_for_update=True)
    return material


def _page(material: Material, page_no: int) -> dict[str, Any]:
    page = repository.page_map(material).get(page_no)
    if page is None:
        raise HTTPException(404, "本页不存在或页码超出范围")
    return page


@router.post("/{page_no}/generate")
def generate_narration(page_no: int, classroom_id: int, material_id: int,
                       db: Annotated[Session, Depends(get_db)],
                       user: Annotated[User, Depends(require_teacher)],
                       body: PageGenerateInput | None = None) -> GeneratedPage:
    """One explicit authoring request = at most one model call. Never runs on page turns."""
    instance_id = teacher_instance_id(db, user, classroom_id)
    material = require_scope(db.get(Material, material_id), instance_id)
    repository.sync_staleness(db, material)
    db.commit()
    with repository.task(db, material, "generate", user):
        try:
            result = service.generate_page(db, material, page_no, instance_id=instance_id,
                                           target_seconds=body.target_seconds if body else None)
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


@router.post("/generate")
def generate_range(classroom_id: int, material_id: int, body: GenerateInput,
                   db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]) -> dict[str, list[GeneratedPage]]:
    """Serial batch: each page commits on its own, so a failure keeps earlier pages."""
    instance_id = teacher_instance_id(db, user, classroom_id)
    material = require_scope(db.get(Material, material_id), instance_id)
    repository.sync_staleness(db, material)
    db.commit()
    if body.first_page < 1 or body.last_page < body.first_page:
        raise HTTPException(422, "页码范围无效")
    with repository.task(db, material, "generate", user):
        try:
            results = service.generate_range(db, material, first=body.first_page, last=body.last_page,
                                             instance_id=instance_id, target_seconds=body.target_seconds,
                                             serial=body.mode == "serial")
        except service.NarrationUnavailable as exc:
            raise HTTPException(409, str(exc)) from exc
        except LookupError as exc:
            raise HTTPException(404, str(exc)) from exc
        except ValueError as exc:
            repository.release(db, material.id, status="failed", message=str(exc))
            raise HTTPException(422, str(exc)) from exc
        db.commit()
        done = sum(1 for item in results if item.get("status") != "failed")
        repository.release(db, material.id, message=f"已生成 {done} 页", extra={"done": done})
        return {"results": results}


@router.get("/job")
def read_job(classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
             user: Annotated[User, Depends(require_teacher)]) -> dict[str, Any]:
    """前端轮询它来置灰按钮：同一份资料同时只允许一个生成/校准/音频任务。"""
    material = _material(db, user, classroom_id, material_id)
    job = repository.job_of(db, material.id)
    if job is None:
        return {"running": False, "kind": "", "status": "idle", "done": 0, "total": 0, "message": ""}
    return {
        "running": repository.job_running(job),
        "kind": job.kind,
        "status": job.status,
        "done": (job.progress or {}).get("done", 0),
        "total": (job.progress or {}).get("total", 0),
        "message": job.message,
        "started_at": job.started_at.isoformat() if job.started_at else None,
    }


@router.get("/quota")
def read_quota(classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
               user: Annotated[User, Depends(require_teacher)]) -> Quota:
    instance_id = teacher_instance_id(db, user, classroom_id)
    require_scope(db.get(Material, material_id), instance_id)
    return service.quota(db, instance_id)


@router.post("/calibrate")
def calibrate_deck(classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
                   user: Annotated[User, Depends(require_teacher)]) -> dict[str, Any]:
    """Walk the whole deck with a rolling summary; returns a report, rewrites nothing."""
    instance_id = teacher_instance_id(db, user, classroom_id)
    material = require_scope(db.get(Material, material_id), instance_id)
    repository.sync_staleness(db, material)
    db.commit()
    with repository.task(db, material, "calibrate", user):
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


@router.get("/calibrate")
def read_calibration(classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
                     user: Annotated[User, Depends(require_teacher)]) -> dict[str, Any]:
    instance_id = teacher_instance_id(db, user, classroom_id)
    material = require_scope(db.get(Material, material_id), instance_id)
    entry = calibrate.latest(db, material.id)
    if entry is None:
        raise HTTPException(404, "还没有校准报告")
    return {"id": entry.id, "applied_at": entry.applied_at.isoformat() if entry.applied_at else None,
            "applied_pages": entry.applied_pages, **(entry.report or {})}


@router.post("/calibrate/apply")
def apply_calibration(classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
                      user: Annotated[User, Depends(require_teacher)]) -> AppliedCalibration:
    """Re-generate only the flagged drafts; approved pages stay as the teacher left them."""
    instance_id = teacher_instance_id(db, user, classroom_id)
    material = require_scope(db.get(Material, material_id), instance_id)
    entry = calibrate.latest(db, material.id)
    if entry is None:
        raise HTTPException(404, "还没有校准报告")

    def regenerate(page_no: int) -> None:
        service.generate_page(db, material, page_no, instance_id=instance_id)
        db.commit()

    with repository.task(db, material, "calibrate", user):
        applied = calibrate.apply_report(db, material, entry, regenerate=regenerate)
        db.commit()
        return applied


@router.get("")
def list_narrations(classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
                    user: Annotated[User, Depends(require_teacher)]) -> dict[str, Any]:
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
                     db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]) -> dict[str, Any]:
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
                     user: Annotated[User, Depends(require_teacher)]) -> dict[str, Any]:
    material = _material(db, user, classroom_id, material_id, exclusive=True)
    repository.sync_staleness(db, material)
    try:
        item = repository.decide(db, material, page_no, "accepted", user)
    except LookupError as exc:
        raise HTTPException(404, str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(409, str(exc)) from exc
    db.commit()
    return {"page_no": page_no, "status": item.status,
            "reviewed_at": item.reviewed_at.isoformat() if item.reviewed_at else None}


@router.post("/accept-batch")
def accept_batch(classroom_id: int, material_id: int,
                 db: Annotated[Session, Depends(get_db)],
                 user: Annotated[User, Depends(require_teacher)],
                 body: RangeInput | None = None) -> dict[str, Any]:
    """Accept every reviewable draft in the range: reviewers read the deck, not one page at a time."""
    material = _material(db, user, classroom_id, material_id, exclusive=True)
    repository.sync_staleness(db, material)
    first = body.first_page if body and body.first_page else 1
    last = body.last_page if body and body.last_page else 10**9
    accepted: list[int] = []
    skipped: list[dict[str, Any]] = []
    for item in repository.rows(db, material.id):
        if item.page_no < first or item.page_no > last:
            continue
        if item.status != "draft":
            skipped.append({"page_no": item.page_no, "reason": item.status})
            continue
        if not item.text.strip():
            skipped.append({"page_no": item.page_no, "reason": "empty"})
            continue
        repository.decide(db, material, item.page_no, "accepted", user)
        accepted.append(item.page_no)
    db.commit()
    return {"accepted": accepted, "count": len(accepted), "skipped": skipped[:50]}


@router.post("/{page_no}/reject")
def reject_narration(page_no: int, classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
                     user: Annotated[User, Depends(require_teacher)]) -> dict[str, Any]:
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
                     user: Annotated[User, Depends(require_teacher)]) -> dict[str, Any]:
    material = _material(db, user, classroom_id, material_id, exclusive=True)
    if not repository.remove(db, material, page_no):
        raise HTTPException(404, "本页还没有讲解草稿")
    db.commit()
    return {"page_no": page_no, "removed": True}


@router.post("/{page_no}/audio")
def synthesize_audio(page_no: int, classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
                     user: Annotated[User, Depends(require_teacher)]) -> AudioRecord:
    """Speak one approved page. Draft wording is refused, not spoken."""
    instance_id = teacher_instance_id(db, user, classroom_id)
    material = require_scope(db.get(Material, material_id), instance_id)
    repository.sync_staleness(db, material)
    db.commit()
    with repository.task(db, material, "audio", user):
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


@router.get("/{page_no}/page-image")
def read_page_image(page_no: int, classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
                    user: Annotated[User, Depends(require_teacher)]) -> Response:
    """The same rendered page the model saw, so review can compare wording against the slide."""
    material = _material(db, user, classroom_id, material_id)
    image = render.render_page(material, page_no)
    if image is None:
        raise HTTPException(404, "本页没有可预览的页面图像")
    return Response(content=image, media_type="image/jpeg",
                    headers={"Cache-Control": "private, max-age=300"})

@router.post("/audio-batch")
def synthesize_audio_batch(classroom_id: int, material_id: int,
                           db: Annotated[Session, Depends(get_db)],
                           user: Annotated[User, Depends(require_teacher)],
                           body: RangeInput | None = None) -> dict[str, Any]:
    """Synthesize every accepted page in the range; one failure must not abort the rest."""
    material = _material(db, user, classroom_id, material_id)
    repository.sync_staleness(db, material)
    db.commit()
    if tts.unavailable_reason():
        raise HTTPException(409, tts.unavailable_reason() or "语音合成不可用")
    with repository.task(db, material, "audio", user):
        first = body.first_page if body and body.first_page else 1
        last = body.last_page if body and body.last_page else 10**9
        made: list[int] = []
        reused: list[int] = []
        failed: list[dict[str, Any]] = []
        skipped: list[int] = []
        wanted = [item for item in repository.rows(db, material.id)
                  if first <= item.page_no <= last and item.status == "accepted" and item.text.strip()]
        for item in repository.rows(db, material.id):
            if item.page_no < first or item.page_no > last:
                continue
            if item.status != "accepted" or not item.text.strip():
                skipped.append(item.page_no)
                continue
            try:
                result = audio.synthesize_page(db, material, item.page_no)
                repository.beat(db, material.id, done=len(made) + len(reused) + len(failed),
                                total=len(wanted), message=f"音频第 {item.page_no} 页")
                db.commit()
                (reused if result["reused"] else made).append(item.page_no)
            except (tts.SpeechFailed, ValueError, LookupError) as exc:
                db.rollback()
                failed.append({"page_no": item.page_no, "error": str(exc)[:160]})
        repository.release(db, material.id, message=f"音频完成 {len(made) + len(reused)} 页",
                           extra={"done": len(made) + len(reused)})
        return {"made": made, "reused": reused, "failed": failed, "skipped": skipped[:50],
                "count": len(made) + len(reused)}


@router.get("/{page_no}/audio/file")
def stream_teacher_audio(page_no: int, classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
                         user: Annotated[User, Depends(require_teacher)]) -> FileResponse:
    """Let the teacher hear exactly what the student will hear."""
    instance_id = teacher_instance_id(db, user, classroom_id)
    material = require_scope(db.get(Material, material_id), instance_id)
    repository.sync_staleness(db, material)
    db.commit()
    found = audio.read_audio(db, material, page_no)
    if found is None:
        raise HTTPException(404, "本页还没有可播放的讲解音频")
    _, path = found
    return FileResponse(path, media_type=audio.media_type(path.name),
                        filename=audio.download_name(material.id, page_no, path.name),
                        headers={"Cache-Control": "private, max-age=60"})


@router.get("/audio")
def read_audio_status(classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
                      user: Annotated[User, Depends(require_teacher)]) -> dict[str, Any]:
    instance_id = teacher_instance_id(db, user, classroom_id)
    material = require_scope(db.get(Material, material_id), instance_id)
    repository.sync_staleness(db, material)
    db.commit()
    engine = tts.settings()
    rows = repository.rows(db, material.id)
    return {
        "available": tts.unavailable_reason() is None,
        "voice": engine["voice"],
        # DeepSeek 没有模型概念，显示固定默认值会误导教师，直接回协议名。
        "model": engine["model"] if tts.protocol() == "openai" else tts.protocol(),
        "pages": [{"page_no": item.page_no, "status": item.status,
                   "ready": audio.current_audio(db, material, item.page_no) is not None,
                   "seconds": item.audio_seconds} for item in rows],
    }


@student_router.get("")
def read_narrations(material_id: int, classroom_id: int, db: Annotated[Session, Depends(get_db)],
                    user: Annotated[User, Depends(current_user)]) -> dict[str, Any]:
    """Students only ever receive teacher-approved wording."""
    from ..courses import authorize_material

    material = authorize_material(db, user, classroom_id, material_id)
    repository.sync_staleness(db, material)
    db.commit()
    return {
        "material_id": material.id,
        "pages": [{"page_no": item.page_no, "text": item.text, "bridge_in": item.bridge_in,
                   "target_seconds": item.target_seconds,
                   "audio": audio.current_audio(db, material, item.page_no) is not None,
                   "audio_seconds": item.audio_seconds} for item in repository.visible(db, material)],
    }


@student_router.get("/{page_no}/page-image")
def read_student_page_image(page_no: int, material_id: int, classroom_id: int,
                            db: Annotated[Session, Depends(get_db)],
                            user: Annotated[User, Depends(current_user)]) -> Response:
    """Student 播放讲解时的大页面：与审校用同一份渲染，鉴权与讲解列表一致。"""
    from ..courses import authorize_material

    material = authorize_material(db, user, classroom_id, material_id)
    image = render.render_page(material, page_no)
    if image is None:
        raise HTTPException(404, "本页没有可预览的页面图像")
    return Response(content=image, media_type="image/jpeg",
                    headers={"Cache-Control": "private, max-age=300"})


@student_router.get("/{page_no}/audio")
def stream_narration_audio(page_no: int, material_id: int, classroom_id: int,
                           db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]) -> FileResponse:
    """Stream one approved page's audio; stale recordings are unavailable, not guessed."""
    from fastapi.responses import FileResponse
    from ..courses import authorize_material

    material = authorize_material(db, user, classroom_id, material_id)
    repository.sync_staleness(db, material)
    db.commit()
    found = audio.read_audio(db, material, page_no)
    if found is None:
        raise HTTPException(404, "本页还没有可播放的讲解音频")
    item, path = found
    return FileResponse(path, media_type=audio.media_type(path.name),
                        filename=audio.download_name(material.id, page_no, path.name),
                        headers={"Cache-Control": "private, max-age=600"})
