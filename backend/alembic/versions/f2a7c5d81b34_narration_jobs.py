"""narration jobs: one running task per material"""
from alembic import op
import sqlalchemy as sa

revision = "f2a7c5d81b34"
down_revision = "e1f4b23c7d90"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "narration_jobs",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("material_id", sa.Integer(), sa.ForeignKey("materials.id", ondelete="CASCADE"), nullable=False),
        sa.Column("kind", sa.String(24), nullable=False, server_default=""),
        sa.Column("status", sa.String(16), nullable=False, server_default="idle"),
        sa.Column("requested_by", sa.Integer(), sa.ForeignKey("users.id"), nullable=True),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("heartbeat_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("finished_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("progress", sa.JSON(), nullable=False),
        sa.Column("message", sa.Text(), nullable=False, server_default=""),
    )
    op.create_index("ix_narration_jobs_material_id", "narration_jobs", ["material_id"], unique=True)


def downgrade():
    op.drop_index("ix_narration_jobs_material_id", table_name="narration_jobs")
    op.drop_table("narration_jobs")
