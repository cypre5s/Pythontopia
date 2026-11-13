"""Per-page narration drafts, separate from material JSON so reviews survive re-uploads."""
from alembic import op
import sqlalchemy as sa

revision = "b7d3f1a0c452"
down_revision = "c421b8e091aa"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "page_narrations",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("material_id", sa.Integer(), sa.ForeignKey("materials.id", ondelete="CASCADE"), nullable=False),
        sa.Column("page_no", sa.Integer(), nullable=False),
        sa.Column("text", sa.Text(), nullable=False),
        sa.Column("bridge_in", sa.Text(), nullable=False),
        sa.Column("target_seconds", sa.Integer(), nullable=False),
        sa.Column("status", sa.String(24), nullable=False),
        sa.Column("source", sa.String(24), nullable=False),
        sa.Column("page_hash", sa.String(64), nullable=False),
        sa.Column("input_hash", sa.String(64), nullable=False),
        sa.Column("prompt_version", sa.String(24), nullable=False),
        sa.Column("model", sa.String(80), nullable=False),
        sa.Column("image_used", sa.Boolean(), nullable=False),
        sa.Column("anchors", sa.JSON(), nullable=False),
        sa.Column("reviewed_by", sa.Integer(), sa.ForeignKey("users.id"), nullable=True),
        sa.Column("reviewed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("edited_by_teacher", sa.Boolean(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint("material_id", "page_no", name="uq_page_narrations_material_page"),
    )
    op.create_index("ix_page_narrations_material_id", "page_narrations", ["material_id"])


def downgrade():
    op.drop_index("ix_page_narrations_material_id", table_name="page_narrations")
    op.drop_table("page_narrations")
