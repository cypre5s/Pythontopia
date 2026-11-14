"""Student page questions: audit trail and daily cap."""
from alembic import op
import sqlalchemy as sa

revision = "e1f4b23c7d90"
down_revision = "d5a17c93e206"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "narration_questions",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("material_id", sa.Integer(), sa.ForeignKey("materials.id", ondelete="CASCADE"), nullable=False),
        sa.Column("page_no", sa.Integer(), nullable=False),
        sa.Column("question", sa.Text(), nullable=False),
        sa.Column("answer", sa.Text(), nullable=False),
        sa.Column("cites", sa.JSON(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_narration_questions_user_id", "narration_questions", ["user_id"])
    op.create_index("ix_narration_questions_material_id", "narration_questions", ["material_id"])


def downgrade():
    op.drop_index("ix_narration_questions_material_id", table_name="narration_questions")
    op.drop_index("ix_narration_questions_user_id", table_name="narration_questions")
    op.drop_table("narration_questions")
