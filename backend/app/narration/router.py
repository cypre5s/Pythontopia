"""Teacher review and student reading endpoints for page narration.

Mounting is deliberately additive: `app.main` only includes this router, and the
package keeps its own model, prompts and (later) authoring client.
"""

from typing import Annotated, Any

from fastapi import APIRouter, Depends, HTTPException, Response
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from ..auth import current_user, require_teacher
from ..db import get_db
from ..models import Material, User
from ..teacher_scope import require_scope, teacher_instance_id
from . import audio, calibrate, task_commands
from .access import teacher_material, require_narratable, require_page
from .request_contracts import NarrationInput, GenerateInput, RangeInput, PageGenerateInput
from . import render, repository, service
from . import tts
from .contracts import AppliedCalibration, AudioRecord, GeneratedPage, Quota

router = APIRouter(prefix="/teacher/classrooms/{classroom_id}/materials/{material_id}/narrations", tags=["narration"])
student_router = APIRouter(prefix="/materials/{material_id}/narrations", tags=["narration"])
















@router.post("/{page_no}/generate")
def generate_narration(page_no: int, classroom_id: int, material_id: int,
                       db: Annotated[Session, Depends(get_db)],
                       user: Annotated[User, Depends(require_teacher)],
                       body: PageGenerateInput | None = None) -> GeneratedPage:
    """One explicit authoring request = at most one model call. Never runs on page turns."""
    return task_commands.generate_narration(page_no=page_no, classroom_id=classroom_id, material_id=material_id, db=db, user=user, body=body)


@router.post("/generate")
def generate_range(classroom_id: int, material_id: int, body: GenerateInput,
                   db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]) -> dict[str, list[GeneratedPage]]:
    """Serial batch: each page commits on its own, so a failure keeps earlier pages."""
    return task_commands.generate_range(classroom_id=classroom_id, material_id=material_id, body=body, db=db, user=user)


@router.get("/job")
def read_job(classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
             user: Annotated[User, Depends(require_teacher)]) -> dict[str, Any]:
    """前端轮询它来置灰按钮：同一份资料同时只允许一个生成/校准/音频任务。"""
    material = teacher_material(db, user, classroom_id, material_id)
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
    teacher_material(db, user, classroom_id, material_id)
    return service.quota(db, instance_id)


@router.post("/calibrate")
def calibrate_deck(classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
                   user: Annotated[User, Depends(require_teacher)]) -> dict[str, Any]:
    """Walk the whole deck with a rolling summary; returns a report, rewrites nothing."""
    return task_commands.calibrate_deck(classroom_id=classroom_id, material_id=material_id, db=db, user=user)


@router.get("/calibrate")
def read_calibration(classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
                     user: Annotated[User, Depends(require_teacher)]) -> dict[str, Any]:
    instance_id = teacher_instance_id(db, user, classroom_id)
    material = require_scope(db.get(Material, material_id), instance_id)
    require_narratable(material)
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
    require_narratable(material)
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
    material = teacher_material(db, user, classroom_id, material_id)
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
    material = teacher_material(db, user, classroom_id, material_id, exclusive=True)
    require_page(material, page_no)
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
    material = teacher_material(db, user, classroom_id, material_id, exclusive=True)
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
    material = teacher_material(db, user, classroom_id, material_id, exclusive=True)
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
    material = teacher_material(db, user, classroom_id, material_id, exclusive=True)
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
    material = teacher_material(db, user, classroom_id, material_id, exclusive=True)
    if not repository.remove(db, material, page_no):
        raise HTTPException(404, "本页还没有讲解草稿")
    db.commit()
    return {"page_no": page_no, "removed": True}


@router.post("/{page_no}/audio")
def synthesize_audio(page_no: int, classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
                     user: Annotated[User, Depends(require_teacher)]) -> AudioRecord:
    """Speak one approved page. Draft wording is refused, not spoken."""
    return task_commands.synthesize_audio(page_no=page_no, classroom_id=classroom_id, material_id=material_id, db=db, user=user)


@router.get("/{page_no}/page-image")
def read_page_image(page_no: int, classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
                    user: Annotated[User, Depends(require_teacher)]) -> Response:
    """The same rendered page the model saw, so review can compare wording against the slide."""
    material = teacher_material(db, user, classroom_id, material_id)
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
    return task_commands.synthesize_audio_batch(classroom_id=classroom_id, material_id=material_id, db=db, user=user, body=body)


@router.get("/{page_no}/audio/file")
def stream_teacher_audio(page_no: int, classroom_id: int, material_id: int, db: Annotated[Session, Depends(get_db)],
                         user: Annotated[User, Depends(require_teacher)]) -> FileResponse:
    """Let the teacher hear exactly what the student will hear."""
    instance_id = teacher_instance_id(db, user, classroom_id)
    material = require_scope(db.get(Material, material_id), instance_id)
    require_narratable(material)
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
    require_narratable(material)
    repository.sync_staleness(db, material)
    db.commit()
    engine = tts.settings(db)
    rows = repository.rows(db, material.id)
    return {
        "available": tts.unavailable_reason(db) is None,
        "voice": engine["voice"],
        # DeepSeek 没有模型概念，显示固定默认值会误导教师，直接回协议名。
        "model": engine["model"] if tts.protocol(db) == "openai" else tts.protocol(db),
        "pages": [{"page_no": item.page_no, "status": item.status,
                   "ready": audio.current_audio(db, material, item.page_no) is not None,
                   "seconds": item.audio_seconds} for item in rows],
    }


@student_router.get("")
def read_narrations(material_id: int, classroom_id: int, db: Annotated[Session, Depends(get_db)],
                    user: Annotated[User, Depends(current_user)]) -> dict[str, Any]:
    """Students only ever receive teacher-approved wording."""

    from app.course_content.access import authorize_material

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

    from app.course_content.access import authorize_material

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

    from app.course_content.access import authorize_material

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
