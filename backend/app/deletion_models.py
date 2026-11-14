"""Private, expiring deletion previews and idempotent confirmation receipts."""
from datetime import datetime
from sqlalchemy import DateTime, ForeignKey, Integer, JSON, String
from sqlalchemy.orm import Mapped, mapped_column
from .db import Base
from .models import now, uid


class ContentDeletionPreview(Base):
    __tablename__ = "content_deletion_previews"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    teacher_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), index=True)
    course_instance_id: Mapped[int] = mapped_column(ForeignKey("course_instances.id"))
    revision: Mapped[int] = mapped_column(Integer)
    targets: Mapped[list] = mapped_column(JSON)
    fingerprint: Mapped[str] = mapped_column(String(64))
    confirmation: Mapped[str] = mapped_column(String(200))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    result: Mapped[dict | None] = mapped_column(JSON, nullable=True)
