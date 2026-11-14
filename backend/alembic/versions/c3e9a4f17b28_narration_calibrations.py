"""Whole-deck calibration reports for narration (report first, rewrite on request)."""
from alembic import op
import sqlalchemy as sa

revision = "c3e9a4f17b28"
down_revision = "b7d3f1a0c452"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "narration_calibrations",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("material_id", sa.Integer(), sa.ForeignKey("materials.id", ondelete="CASCADE"), nullable=False),
        sa.Column("report", sa.JSON(), nullable=False),
        sa.Column("windows", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("applied_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("applied_pages", sa.JSON(), nullable=True),
    )
    op.create_index("ix_narration_calibrations_material_id", "narration_calibrations", ["material_id"])


def downgrade():
    op.drop_index("ix_narration_calibrations_material_id", table_name="narration_calibrations")
    op.drop_table("narration_calibrations")
