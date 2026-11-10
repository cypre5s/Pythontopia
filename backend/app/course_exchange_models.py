"""Private durable package exchanges and editable non-table course configuration."""

from datetime import datetime
from sqlalchemy import DateTime, ForeignKey, Integer, JSON, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column
from .db import Base
from .models import now, uid


class CoursePackageState(Base):
    __tablename__ = "course_package_state"
    course_instance_id: Mapped[int] = mapped_column(ForeignKey("course_instances.id"), primary_key=True)
    configuration: Mapped[dict] = mapped_column(JSON, default=dict)


class CourseImportPreview(Base):
    __tablename__ = "course_import_previews"
    __table_args__ = (UniqueConstraint("classroom_id", "applied_request_id", name="uq_course_import_request"),)
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    teacher_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), index=True)
    expected_revision: Mapped[int] = mapped_column(Integer)
    archive_sha256: Mapped[str] = mapped_column(String(64))
    storage_key: Mapped[str] = mapped_column(Text)
    summary: Mapped[dict] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    applied_request_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    result: Mapped[dict | None] = mapped_column(JSON, nullable=True)
