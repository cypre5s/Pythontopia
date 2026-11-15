"""Own-classroom development experience checkpoints."""
from alembic import op
import sqlalchemy as sa

revision = "c38d01fe72b9"
down_revision = "b72c49e018a3"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table("experience_checkpoints",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("classroom_id", sa.Integer(), sa.ForeignKey("classrooms.id", ondelete="CASCADE"), nullable=False),
        sa.Column("name", sa.String(80), nullable=False),
        sa.Column("definition_hash", sa.String(64), nullable=False),
        sa.Column("snapshot", sa.JSON(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False))
    op.create_index("ix_experience_checkpoints_user_id", "experience_checkpoints", ["user_id"])
    op.create_index("ix_experience_checkpoints_classroom_id", "experience_checkpoints", ["classroom_id"])


def downgrade():
    op.drop_table("experience_checkpoints")
