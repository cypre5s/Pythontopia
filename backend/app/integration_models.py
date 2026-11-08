"""Private integration state, included in the same Alembic metadata."""

from datetime import datetime
from sqlalchemy import DateTime, ForeignKey, Integer, JSON, String, Text
from sqlalchemy.orm import Mapped, mapped_column
from .db import Base
from .models import now, uid


class SubmissionPayload(Base):
    __tablename__ = "submission_payloads"
    submission_id: Mapped[str] = mapped_column(ForeignKey("submissions.id", ondelete="CASCADE"), primary_key=True)
    cases: Mapped[list] = mapped_column(JSON, default=list)
    public_count: Mapped[int] = mapped_column(Integer, default=0)


class AssistantSession(Base):
    __tablename__ = "assistant_sessions"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), index=True)
    activity_id: Mapped[str | None] = mapped_column(ForeignKey("activities.id"), index=True, nullable=True)
    material_id: Mapped[int | None] = mapped_column(ForeignKey("materials.id"), nullable=True)
    messages: Mapped[list] = mapped_column(JSON, default=list)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class AssistantCall(Base):
    __tablename__ = "assistant_calls"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), index=True)
    session_id: Mapped[str] = mapped_column(ForeignKey("assistant_sessions.id"), index=True)
    status: Mapped[str] = mapped_column(String(24), default="running")
    model_calls: Mapped[int] = mapped_column(Integer, default=0)
    tokens: Mapped[int] = mapped_column(Integer, default=0)
    elapsed_ms: Mapped[int] = mapped_column(Integer, default=0)
    error: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
