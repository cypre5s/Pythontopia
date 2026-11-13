"""Narration rows stay outside `Material.content` so teacher review survives re-uploads."""

from datetime import datetime

from sqlalchemy import JSON, Boolean, DateTime, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from ..db import Base
from ..models import now

# draft   : AI-written (or teacher-written) and not visible to students
# accepted: teacher approved; the only status students and exports may use
# rejected: teacher refused this draft
# unavailable: the page has no text and no image input, so nothing can be authored
# stale   : the page text changed after this row was written; review is void
STATUSES = frozenset({"draft", "accepted", "rejected", "unavailable", "stale"})
# Only these may be shown to students or travel in a course package.
VISIBLE_STATUSES = frozenset({"accepted"})


class PageNarration(Base):
    __tablename__ = "page_narrations"
    __table_args__ = (UniqueConstraint("material_id", "page_no", name="uq_page_narrations_material_page"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    material_id: Mapped[int] = mapped_column(ForeignKey("materials.id", ondelete="CASCADE"), index=True)
    page_no: Mapped[int] = mapped_column(Integer)
    text: Mapped[str] = mapped_column(Text, default="")
    bridge_in: Mapped[str] = mapped_column(Text, default="")
    target_seconds: Mapped[int] = mapped_column(Integer, default=90)
    status: Mapped[str] = mapped_column(String(24), default="draft")

    # Provenance and idempotency: what was read, how it was produced.
    source: Mapped[str] = mapped_column(String(24), default="teacher")
    page_hash: Mapped[str] = mapped_column(String(64), default="")
    input_hash: Mapped[str] = mapped_column(String(64), default="")
    prompt_version: Mapped[str] = mapped_column(String(24), default="")
    model: Mapped[str] = mapped_column(String(80), default="")
    image_used: Mapped[bool] = mapped_column(Boolean, default=False)
    anchors: Mapped[list] = mapped_column(JSON, default=list)

    # Review audit: who approved which wording, and when.
    reviewed_by: Mapped[int | None] = mapped_column(ForeignKey("users.id"), nullable=True)
    reviewed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    edited_by_teacher: Mapped[bool] = mapped_column(Boolean, default=False)

    # Audio is derived from the approved wording; the hash makes stale audio unusable.
    audio_key: Mapped[str | None] = mapped_column(Text, nullable=True)
    audio_text_hash: Mapped[str | None] = mapped_column(String(64), nullable=True)
    audio_seconds: Mapped[int | None] = mapped_column(Integer, nullable=True)
    audio_created_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now, onupdate=now)


class NarrationCalibration(Base):
    """The latest whole-deck review of one material: a report, not a rewrite."""
    __tablename__ = "narration_calibrations"

    id: Mapped[int] = mapped_column(primary_key=True)
    material_id: Mapped[int] = mapped_column(ForeignKey("materials.id", ondelete="CASCADE"), index=True)
    report: Mapped[dict] = mapped_column(JSON, default=dict)
    windows: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    applied_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    applied_pages: Mapped[dict | None] = mapped_column(JSON, nullable=True)
