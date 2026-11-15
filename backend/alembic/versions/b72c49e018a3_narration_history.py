"""Preserve individual narration attempts and retain the existing latest record."""
from alembic import op
import sqlalchemy as sa

revision = "b72c49e018a3"
down_revision = "a52e901c7d41"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "narration_runs",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("material_id", sa.Integer(), sa.ForeignKey("materials.id", ondelete="CASCADE"), nullable=False),
        sa.Column("kind", sa.String(24), nullable=False),
        sa.Column("status", sa.String(16), nullable=False),
        sa.Column("requested_by", sa.Integer(), sa.ForeignKey("users.id")),
        sa.Column("started_at", sa.DateTime(timezone=True)),
        sa.Column("heartbeat_at", sa.DateTime(timezone=True)),
        sa.Column("finished_at", sa.DateTime(timezone=True)),
        sa.Column("progress", sa.JSON(), nullable=False),
        sa.Column("message", sa.Text(), nullable=False),
        sa.UniqueConstraint("material_id", "started_at", name="uq_narration_runs_attempt"),
    )
    op.create_index("ix_narration_runs_material_id", "narration_runs", ["material_id"])
    columns = "material_id, kind, status, requested_by, started_at, heartbeat_at, finished_at, progress, message"
    op.execute(sa.text(f"INSERT INTO narration_runs ({columns}) SELECT {columns} FROM narration_jobs WHERE status <> 'idle'"))


def downgrade():
    op.drop_index("ix_narration_runs_material_id", table_name="narration_runs")
    op.drop_table("narration_runs")
