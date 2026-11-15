"""Class-scoped active tasks and durable execution history."""
from datetime import timedelta
from typing import Annotated, Any, Literal

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, ConfigDict
from sqlalchemy import case, func, select
from sqlalchemy.orm import Session, load_only

from ..auth import require_teacher
from ..db import get_db
from ..models import Material, User, now
from ..teacher_scope import teacher_instance_id
from . import repository
from .access import teacher_material
from .request_contracts import GenerateInput, PageGenerateInput, RangeInput
from .task_commands import (calibrate_deck, generate_narration, generate_range, synthesize_audio, synthesize_audio_batch)
from .models import NarrationJob, NarrationRun

router = APIRouter(prefix="/teacher/classrooms/{classroom_id}/synthesis", tags=["narration"])
OPERATIONS = {"generate_page", "generate_range", "audio_page", "audio_batch", "calibrate"}


def snapshot(material: Material, job: NarrationJob | NarrationRun | None) -> dict[str, Any]:
    progress = (job.progress or {}) if job else {}
    request = progress.get("request") or {}
    running = bool(job and repository.job_running(job))
    status = "interrupted" if job and job.status == "running" and not running else job.status if job else "idle"
    retryable = bool(job and status == "failed" and request.get("operation") in OPERATIONS
                     and progress.get("retry_fingerprint"))
    if request.get("operation") in {"generate_range", "audio_batch"} and not progress.get("retry_pages"):
        retryable = False
    return {"id": job.id if job else None, "material_id": material.id, "title": material.title, "material_kind": material.kind,
            "kind": job.kind if job else "", "status": status, "running": running,
            "done": progress.get("done", 0), "total": progress.get("total", 0),
            "message": job.message if job else "", "failed": progress.get("failed", []),
            "started_at": job.started_at.isoformat() if job and job.started_at else None,
            "finished_at": job.finished_at.isoformat() if job and job.finished_at else None,
            "retryable": retryable,
            "retry_note": "" if retryable else ("任务结果待核查，请打开资料确认" if status == "interrupted"
                                                  else "请打开资料审校后重新发起" if status == "failed" else "")}


@router.get("")
def list_tasks(classroom_id: int, db: Annotated[Session, Depends(get_db)],
               user: Annotated[User, Depends(require_teacher)],
               query: str = "", status: Literal["all", "running", "done", "failed", "interrupted"] = "all",
               view: Literal["all", "running", "history"] = "all",
               page: Annotated[int, Query(ge=1)] = 1, size: Annotated[int, Query(ge=1, le=100)] = 20) -> dict[str, Any]:
    instance_id = teacher_instance_id(db, user, classroom_id)
    cutoff = now() - timedelta(seconds=repository.JOB_STALE_SECONDS)
    moment = func.coalesce(NarrationRun.heartbeat_at, NarrationRun.started_at)
    state = case(((NarrationRun.status == "running") & ((moment < cutoff) | moment.is_(None)), "interrupted"),
        else_=NarrationRun.status)
    statement = select(NarrationRun, Material, User.name).join(Material, NarrationRun.material_id == Material.id).outerjoin(
        User, NarrationRun.requested_by == User.id).options(load_only(Material.id, Material.title, Material.kind, Material.deleted_at)).where(
        Material.course_instance_id == instance_id)
    if query.strip():
        statement = statement.where(Material.title.icontains(query.strip(), autoescape=True))
    counts: dict[str, int] = dict(db.execute(statement.with_only_columns(state, func.count()).group_by(state)).all())
    if view != "all":
        statement = statement.where(state == "running" if view == "running" else state != "running")
    if status != "all":
        statement = statement.where(state == status)
    total = db.scalar(select(func.count()).select_from(statement.subquery())) or 0
    items = []
    for record, material, name in db.execute(statement.order_by(NarrationRun.started_at.desc(), NarrationRun.id.desc()).offset((page - 1) * size).limit(size)):
        item = snapshot(material, record)
        latest = repository.job_of(db, material.id)
        is_latest = bool(latest and latest.started_at == record.started_at)
        item.update(requested_by=name or "—", material_available=material.deleted_at is None)
        if not is_latest or material.deleted_at is not None:
            item.update(retryable=False, retry_note="")
        request = (record.progress or {}).get("request") or {}
        body = request.get("body") or {}
        item["scope"] = (f"第 {request['page_no']} 页" if request.get("page_no") else
                         f"第 {body.get('first_page') or 1}–{body['last_page']} 页" if body.get("last_page") else "整份资料")
        items.append(item)
    return {"items": items, "total": total,
            "counts": {key: counts.get(key, 0) for key in ("running", "done", "failed", "interrupted")}}


class RetryInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    expected_started_at: str


@router.post("/{material_id}/retry")
def retry_task(classroom_id: int, material_id: int, body: RetryInput,
               db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]) -> dict[str, Any]:
    material = teacher_material(db, user, classroom_id, material_id)
    job = repository.job_of(db, material_id)
    if (not job or not snapshot(material, job)["retryable"] or not job.started_at
            or job.started_at.isoformat() != body.expected_started_at):
        raise HTTPException(409, "任务状态已变化或不支持直接重试，请刷新并打开资料核查")
    progress = job.progress or {}
    request = progress["request"]
    db.info["narration_retry_expected"] = body.expected_started_at
    db.info["narration_retry_pages"] = progress.get("retry_pages")
    operation = request["operation"]
    args: dict[str, Any] = {"classroom_id": classroom_id, "material_id": material_id, "db": db, "user": user}
    try:
        if operation == "generate_page":
            generate_narration(page_no=request["page_no"], body=PageGenerateInput(**request["body"]), **args)
        elif operation == "generate_range":
            generate_range(body=GenerateInput(**request["body"]), **args)
        elif operation == "audio_page":
            synthesize_audio(page_no=request["page_no"], **args)
        elif operation == "audio_batch":
            synthesize_audio_batch(body=RangeInput(**request["body"]), **args)
        else:
            calibrate_deck(**args)
        return snapshot(material, repository.job_of(db, material_id))
    finally:
        db.info.pop("narration_retry_expected", None)
        db.info.pop("narration_retry_pages", None)
