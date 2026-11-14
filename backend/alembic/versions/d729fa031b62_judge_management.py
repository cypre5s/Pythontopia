"""Private judging diagnostics and durable service controls."""
from alembic import op
import sqlalchemy as sa

revision = "d729fa031b62"
down_revision = "c421b8e091aa"
branch_labels = None
depends_on = None


def upgrade():
    table = op.create_table(
        "judge_service",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("paused", sa.Boolean(), nullable=False),
        sa.Column("health", sa.JSON(), nullable=False),
        sa.Column("events", sa.JSON(), nullable=False),
        sa.Column("checked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("worker_seen_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.bulk_insert(table, [{"id": 1, "paused": False, "health": {}, "events": []}])
    op.create_table(
        "submission_diagnostics",
        sa.Column("submission_id", sa.String(36), sa.ForeignKey("submissions.id", ondelete="CASCADE"), primary_key=True),
        sa.Column("cases", sa.JSON(), nullable=False),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("finished_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("detail", sa.Text(), nullable=False),
    )


def downgrade():
    op.drop_table("submission_diagnostics")
    op.drop_table("judge_service")
