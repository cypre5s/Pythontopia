"""Persist student assessment drafts and immutable submitted answer sheets.

Revision ID: e682f9a410c7
Revises: d193b6e408a1
"""
from alembic import op
import sqlalchemy as sa

revision = "e682f9a410c7"
down_revision = "d193b6e408a1"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "assessment_attempts",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("classroom_id", sa.Integer(), sa.ForeignKey("classrooms.id"), nullable=False),
        sa.Column("activity_id", sa.String(100), sa.ForeignKey("activities.id"), nullable=False),
        sa.Column("state", sa.String(16), nullable=False),
        sa.Column("revision", sa.Integer(), nullable=False),
        sa.Column("question_ids", sa.JSON(), nullable=False),
        sa.Column("answers", sa.JSON(), nullable=False),
        sa.Column("submission_ids", sa.JSON(), nullable=False),
        sa.Column("idempotency_key", sa.String(100), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("submitted_at", sa.DateTime(timezone=True), nullable=True),
        sa.UniqueConstraint("user_id", "classroom_id", "activity_id"),
    )
    for field in ("user_id", "classroom_id", "activity_id"):
        op.create_index(f"ix_assessment_attempts_{field}", "assessment_attempts", [field])


def downgrade():
    op.drop_table("assessment_attempts")
