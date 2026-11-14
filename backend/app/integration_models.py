"""Private integration state, included in the same Alembic metadata."""

from datetime import datetime
from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, JSON, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column
from .db import Base
from .models import now, uid


class SubmissionPayload(Base):
    __tablename__ = "submission_payloads"
    submission_id: Mapped[str] = mapped_column(ForeignKey("submissions.id", ondelete="CASCADE"), primary_key=True)
    cases: Mapped[list] = mapped_column(JSON, default=list)
    public_count: Mapped[int] = mapped_column(Integer, default=0)


class JudgeService(Base):
    __tablename__ = "judge_service"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    paused: Mapped[bool] = mapped_column(Boolean, default=False)
    health: Mapped[dict] = mapped_column(JSON, default=dict)
    events: Mapped[list] = mapped_column(JSON, default=list)
    checked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    worker_seen_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class SubmissionDiagnostic(Base):
    __tablename__ = "submission_diagnostics"
    submission_id: Mapped[str] = mapped_column(ForeignKey("submissions.id", ondelete="CASCADE"), primary_key=True)
    cases: Mapped[list] = mapped_column(JSON, default=list)
    started_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    detail: Mapped[str] = mapped_column(Text, default="")


class AssistantSession(Base):
    __tablename__ = "assistant_sessions"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), index=True)
    activity_id: Mapped[str | None] = mapped_column(ForeignKey("activities.id"), index=True, nullable=True)
    material_id: Mapped[int | None] = mapped_column(ForeignKey("materials.id"), nullable=True)
    messages: Mapped[list] = mapped_column(JSON, default=list)
    revision: Mapped[int] = mapped_column(Integer, default=0)
    runtime: Mapped[dict] = mapped_column(JSON, default=dict)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class AssistantCall(Base):
    __tablename__ = "assistant_calls"
    __table_args__ = (UniqueConstraint("session_id", "client_request_id", name="uq_assistant_request"),)
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), index=True)
    session_id: Mapped[str] = mapped_column(ForeignKey("assistant_sessions.id"), index=True)
    status: Mapped[str] = mapped_column(String(24), default="running")
    model_calls: Mapped[int] = mapped_column(Integer, default=0)
    tokens: Mapped[int] = mapped_column(Integer, default=0)
    elapsed_ms: Mapped[int] = mapped_column(Integer, default=0)
    error: Mapped[str | None] = mapped_column(Text, nullable=True)
    client_request_id: Mapped[str | None] = mapped_column(String(100), nullable=True)
    request_hash: Mapped[str | None] = mapped_column(String(64), nullable=True)
    reserved_tokens: Mapped[int] = mapped_column(Integer, default=0)
    trace: Mapped[dict] = mapped_column(JSON, default=dict)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class AssistantModelSettings(Base):
    __tablename__ = "assistant_model_settings"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    revision: Mapped[int] = mapped_column(Integer, default=0)
    configuration: Mapped[dict] = mapped_column(JSON, default=dict)
    encrypted_key: Mapped[str] = mapped_column(Text, default="")
