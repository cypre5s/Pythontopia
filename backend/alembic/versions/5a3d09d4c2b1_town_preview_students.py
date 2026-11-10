"""Persistent classroom-scoped town preview students for teachers."""

from alembic import op
import sqlalchemy as sa

revision = "5a3d09d4c2b1"
down_revision = "a31d5f9b7c20"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("auth_sessions", sa.Column("preview_teacher_token_hash", sa.String(64)))
    op.create_table(
        "town_preview_students",
        sa.Column("student_user_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), primary_key=True),
        sa.Column("teacher_id", sa.Integer(), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("classroom_id", sa.Integer(), sa.ForeignKey("classrooms.id"), nullable=False),
        sa.UniqueConstraint("teacher_id", "classroom_id", name="uq_town_preview_teacher_classroom"),
    )
    op.create_index("ix_town_preview_students_classroom_id", "town_preview_students", ["classroom_id"])


def downgrade():
    raise RuntimeError("Downgrading teacher town saves requires restoring an isolated backup")
