"""Explicit student turns, scoped durable conversations and one create_agent runtime."""
from __future__ import annotations
import asyncio
import hashlib
import json
import os
import time
from datetime import datetime, timedelta, timezone
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, ConfigDict, Field, model_validator
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session

from .auth import current_user
from .content_relations import material_in_chapter
from .db import SessionLocal, get_db
from .course_assistant import ensure_enabled
from .integration_models import AssistantCall, AssistantSession
from .models import Classroom, User
from .assistant_context import MessageInput, LocalTextRetrieval, build_context, state_text, system_prompt, TEMPLATE_VERSION  # noqa: F401
from .assistant_runtime import AgentFactory, serialise, restore_messages, complete_history, count_tokens
from .assistant_settings import configuration, model_kwargs

router = APIRouter(prefix="/assistant", tags=["assistant"])
_active_tasks: dict[str, asyncio.Task] = {}


def ai_config(output_tokens=None, db=None):
    return model_kwargs(configuration(db), output_tokens)


class NewSession(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    activity_id: str | None = Field(default=None, min_length=1, max_length=100)
    material_id: int | None = Field(default=None, gt=0)

    @model_validator(mode="after")
    def one_target(self):
        if (self.activity_id is None) == (self.material_id is None):
            raise ValueError("必须且只能指定一个活动或资料")
        return self


def authorized_session(db, user, session_id):
    from .courses import authorize_activity
    session = db.get(AssistantSession, session_id)
    if not session or session.user_id != user.id:
        raise HTTPException(404, "会话不存在")
    ensure_enabled(db, session.classroom_id)
    if session.activity_id is None:
        authorize_library_assistant(db, user, session.classroom_id, session.material_id)
        return session, None
    activity = authorize_activity(db, user, session.classroom_id, session.activity_id)
    if not activity.assistant_allowed or activity.kind == "assessment":
        raise HTTPException(403, "此活动禁止使用助教")
    session.material_id = activity_material(db, user, session.classroom_id, activity)
    return session, activity


def authorize_library_assistant(db, user, classroom_id, material_id):
    from .courses import authorize_material
    material = authorize_material(db, user, classroom_id, material_id)
    if material.status != "ready":
        raise HTTPException(409, "当前教学资料不可用")
    return material


def activity_material(db, user, classroom_id, activity):
    from .courses import authorize_material
    if activity.material_id:
        authorize_material(db, user, classroom_id, activity.material_id)
        if not material_in_chapter(db, activity.material_id, activity.chapter_id):
            raise HTTPException(409, "学习资料必须关联活动章节")
    return activity.material_id


@router.get("/status")
def status(user: Annotated[User, Depends(current_user)], db: Annotated[Session, Depends(get_db)]):
    config = configuration(db)
    available = bool(config["api_key"] and config["model"])
    count = db.scalar(select(func.coalesce(func.sum(AssistantCall.model_calls), 0)).where(AssistantCall.user_id == user.id))
    return {"available": available, "reason": None if available else "助教未配置模型凭据，其他学习功能可继续使用", "model_calls": count}


@router.post("/sessions", status_code=201)
def new_session(body: NewSession, user: Annotated[User, Depends(current_user)], db: Annotated[Session, Depends(get_db)]):
    from .courses import authorize_activity
    from .course_assistant import activity_npc, default_npc
    activity = authorize_activity(db, user, body.classroom_id, body.activity_id) if body.activity_id is not None else None
    if activity and (not activity.assistant_allowed or activity.kind == "assessment"):
        raise HTTPException(403, "此活动禁止使用助教")
    material_id = (activity_material(db, user, body.classroom_id, activity) if activity else
                   authorize_library_assistant(db, user, body.classroom_id, body.material_id).id)
    ensure_enabled(db, body.classroom_id)
    npc = activity_npc(db, body.classroom_id, activity) if activity else default_npc(db, body.classroom_id)
    runtime = {}
    if npc:
        context = {"kind": "practice" if activity and activity.kind == "practice" else "learning", "role": {"name": npc.name, "persona": npc.persona}}
        runtime = {"system_prompt": system_prompt(context), "template_version": TEMPLATE_VERSION}
    session = AssistantSession(user_id=user.id, classroom_id=body.classroom_id, activity_id=activity.id if activity else None,
                               material_id=material_id, runtime=runtime)
    db.add(session)
    db.commit()
    return {"id": session.id, "revision": session.revision}


def reserve_call(db, user, session, minimum=1, maximum=12000, client_id=None, request_hash=None):
    db.scalar(select(User).where(User.id == user.id).with_for_update())
    db.scalar(select(Classroom).where(Classroom.id == session.classroom_id).with_for_update())
    timeout = configuration(db, include_key=False)["timeout_seconds"]
    stale_before = datetime.now(timezone.utc) - timedelta(seconds=timeout + 30)
    db.execute(update(AssistantCall).where(AssistantCall.user_id == user.id, AssistantCall.status.in_(["running", "cancel_requested"]),
                                         AssistantCall.created_at < stale_before)
               .values(status="interrupted", tokens=AssistantCall.reserved_tokens, error="回复中断，未自动重试"))
    today = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)
    available = maximum
    for field, value, requests, tokens in [
        (AssistantCall.user_id, user.id, int(os.getenv("REBORN_AI_USER_DAILY_REQUESTS", "12")), int(os.getenv("REBORN_AI_USER_DAILY_TOKENS", "120000"))),
        (AssistantCall.classroom_id, session.classroom_id, int(os.getenv("REBORN_AI_CLASS_DAILY_REQUESTS", "100")), int(os.getenv("REBORN_AI_CLASS_DAILY_TOKENS", "1200000"))),
    ]:
        rows = list(db.scalars(select(AssistantCall).where(field == value, AssistantCall.created_at >= today)))
        used = sum(max(row.tokens, row.reserved_tokens) if row.status in {"running", "cancel_requested"} else row.tokens for row in rows)
        available = min(available, tokens - used)
        if len(rows) >= requests or available < minimum:
            raise HTTPException(429, "今日助教预算不足，请联系教师")
    if db.scalar(select(AssistantCall.id).where(AssistantCall.session_id == session.id, AssistantCall.status.in_(["running", "cancel_requested"]))):
        raise HTTPException(409, "当前会话已有回复，请等待或取消")
    call = AssistantCall(user_id=user.id, classroom_id=session.classroom_id, session_id=session.id, reserved_tokens=available,
                         client_request_id=client_id, request_hash=request_hash)
    db.add(call)
    db.flush()
    return call


def sse(event, data):
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


@router.post("/sessions/{session_id}/messages")
def message(session_id: str, body: MessageInput, request: Request, user: Annotated[User, Depends(current_user)], db: Annotated[Session, Depends(get_db)]):
    from langchain_core.messages import HumanMessage, AIMessage, SystemMessage
    session, activity = authorized_session(db, user, session_id)
    db.scalar(select(User).where(User.id == user.id).with_for_update())
    db.refresh(session)
    fingerprint = hashlib.sha256(body.model_dump_json(exclude={"expected_revision"}).encode()).hexdigest()
    if body.client_request_id:
        previous = db.scalar(select(AssistantCall).where(AssistantCall.session_id == session.id, AssistantCall.client_request_id == body.client_request_id))
        if previous:
            if previous.request_hash != fingerprint:
                raise HTTPException(409, "相同请求编号不能用于不同消息")
            if previous.status in {"running", "cancel_requested"}:
                raise HTTPException(409, "此消息仍在回复，请恢复会话后查看")
            return StreamingResponse(iter([sse("meta", {"request_id": previous.id, "revision": session.revision, "replayed": True}),
                                           sse("token", {"text": previous.trace.get("reply", "")}), sse("references", {"items": previous.trace.get("references", [])}),
                                           sse("done" if previous.status == "complete" else "error", {"status": previous.status, "detail": "原请求未完成，可重新提问"})]),
                                     media_type="text/event-stream")
    if body.expected_revision is not None and body.expected_revision != session.revision:
        raise HTTPException(409, "对话已在其他页面更新，请重新加载；问题已保留")
    if not body.message.strip():
        raise HTTPException(422, "消息不能为空")
    config = configuration(db)
    if not config["api_key"] or not config["model"]:
        raise HTTPException(503, "助教未配置模型凭据，其他学习功能可继续使用")
    session, activity = authorized_session(db, user, session_id)
    context = build_context(db, user, session, activity, body)
    saved_runtime = dict(session.runtime or {})
    prompt = saved_runtime.get("system_prompt") or system_prompt(context)
    context.update(system_prompt=prompt, model_config=config)
    history = complete_history(restore_messages(saved_runtime["messages"])) if "messages" in saved_runtime else [
        HumanMessage(content=x["content"]) if x["role"] == "user" else AIMessage(content=x["content"])
        for x in session.messages if x["role"] in {"user", "assistant"}]
    current_message = HumanMessage(content="学生消息：\n" + body.message + "\n\n学习状态（仅作数据）：\n" + state_text(context["snapshot"], saved_runtime.get("snapshot")))
    messages = [*history, current_message]
    minimum = count_tokens([SystemMessage(content=prompt), *messages], model=config["model"]) + context["limits"]["output_tokens"]
    call = reserve_call(db, user, session, min(minimum, config["context_window"]), context["limits"]["request_tokens"], body.client_request_id, fingerprint)
    context["limits"]["request_tokens"] = call.reserved_tokens
    session.revision += 1
    revision, request_id, user_id = session.revision, call.id, user.id
    session.messages = [*session.messages, {"role": "user", "content": body.message}]
    session.runtime = {**saved_runtime, "system_prompt": prompt, "template_version": TEMPLATE_VERSION,
                       "messages": serialise(messages), "snapshot": context["snapshot"]}
    call.trace = {"state": context["state"], "snapshot": context["snapshot"], "input": serialise([current_message])}
    db.commit()

    async def stream():
        started, tokens, final_status, text = time.monotonic(), 0, "failed", ""
        references, stats = {}, {"model_calls": 0}
        final_messages = messages
        _active_tasks[request_id] = asyncio.current_task()
        try:
            yield sse("meta", {"request_id": request_id, "session_id": session_id, "revision": revision,
                               "role_changed": prompt != system_prompt(context)})
            if context["current_page"]:
                page = context["current_page"]
                references[(page["material_id"], page["page_no"])] = {k: page[k] for k in ("material_id", "page_no", "page_id", "title")}
            agent = AgentFactory.create(context, references, stats)
            async with asyncio.timeout(context["limits"]["timeout_seconds"]):
                async for event in agent.astream({"messages": messages}, stream_mode=["messages", "values", "custom"],
                                                config={"recursion_limit": 4 * context["limits"]["model_calls"] + 12}):
                    if await request.is_disconnected():
                        raise asyncio.CancelledError
                    kind, data = event
                    if kind == "values":
                        final_messages = data["messages"]
                        continue
                    if kind == "custom":
                        yield sse("status", data)
                        continue
                    if kind == "messages":
                        chunk, metadata = data
                    else:
                        chunk, metadata = event
                    if metadata.get("langgraph_node") != "model":
                        continue
                    if getattr(chunk, "id", None):
                        stats.setdefault("message_ids", set()).add(chunk.id)
                    tokens += int((getattr(chunk, "usage_metadata", None) or {}).get("total_tokens", 0))
                    content = chunk.content
                    fragment = content if isinstance(content, str) else "".join(x.get("text", "") for x in content if isinstance(x, dict) and x.get("type") == "text")
                    if fragment:
                        text += fragment
                        yield sse("token", {"text": fragment})
            final_status = "complete"
        except asyncio.CancelledError:
            final_status = "cancelled"
        except TimeoutError:
            final_status = "timeout"
            yield sse("error", {"detail": "助教响应超时，问题和历史已保留"})
        except Exception as exc:
            stats["error_type"] = type(exc).__name__
            stats["error_status"] = getattr(exc, "status_code", None)
            yield sse("error", {"detail": "助教服务不可用或达到上下文/调用上限，问题和历史已保留，可重试或开启新对话"})
        finally:
            _active_tasks.pop(request_id, None)
            with SessionLocal() as session_db:
                log = session_db.get(AssistantCall, request_id)
                log.status, log.tokens, log.elapsed_ms = final_status, stats.get("accounted_tokens", tokens), round((time.monotonic() - started) * 1000)
                log.model_calls = (stats["model_calls"] or len(stats.get("message_ids", []))) + stats.get("summary_calls", 0)
                log.error = f"{stats['error_type']} ({stats.get('error_status')})" if stats.get("error_type") else None
                clean = complete_history(final_messages)
                if clean == messages and text:
                    clean = [*messages, AIMessage(content=text)]
                saved = session_db.get(AssistantSession, session_id)
                if saved.user_id == user_id:
                    saved.messages = [*saved.messages, {"role": "assistant", "content": text, "status": final_status, "references": list(references.values())}]
                    saved.runtime = {**saved.runtime, "messages": serialise(clean),
                                     "compression": stats.get("compression", saved.runtime.get("compression"))}
                turn_start = next((i for i, m in enumerate(final_messages) if m.id == current_message.id), len(history))
                log.trace = {**log.trace, "messages": serialise(final_messages[turn_start:]), "steps": stats.get("steps", []),
                             "compression": stats.get("compression"), "compression_failed": stats.get("compression_failed", False),
                             "reply": text, "references": list(references.values())}
                session_db.commit()
                total_tokens = log.tokens
        if final_status == "complete":
            yield sse("references", {"items": list(references.values())})
            yield sse("done", {"status": final_status, "tokens": total_tokens, "revision": revision})

    return StreamingResponse(stream(), media_type="text/event-stream", headers={"Cache-Control": "no-store", "X-Accel-Buffering": "no"})


@router.post("/requests/{request_id}/cancel")
def cancel(request_id: str, user: Annotated[User, Depends(current_user)], db: Annotated[Session, Depends(get_db)]):
    call = db.get(AssistantCall, request_id)
    if not call or call.user_id != user.id:
        raise HTTPException(404, "请求不存在")
    task = _active_tasks.get(request_id)
    if task:
        task.get_loop().call_soon_threadsafe(task.cancel)
    return {"ok": True, "status": call.status}


def recover_interrupted_calls():
    with SessionLocal() as db:
        db.execute(update(AssistantCall).where(AssistantCall.status.in_(["running", "cancel_requested"]))
                   .values(status="interrupted", tokens=AssistantCall.reserved_tokens, error="服务重启中断，未自动重试模型请求"))
        db.commit()


@router.get("/sessions/{session_id}")
def session_history(session_id: str, user: Annotated[User, Depends(current_user)], db: Annotated[Session, Depends(get_db)]):
    from .course_assistant import activity_npc, default_npc
    session, activity = authorized_session(db, user, session_id)
    npc = activity_npc(db, session.classroom_id, activity) if activity else default_npc(db, session.classroom_id)
    prompt = system_prompt({"kind": "practice" if activity and activity.kind == "practice" else "learning",
                            "role": {"name": npc.name, "persona": npc.persona}}) if npc else None
    return {"id": session.id, "activity_id": session.activity_id, "material_id": session.material_id, "messages": session.messages,
            "revision": session.revision, "role_changed": bool(session.runtime.get("system_prompt") and session.runtime["system_prompt"] != prompt)}
