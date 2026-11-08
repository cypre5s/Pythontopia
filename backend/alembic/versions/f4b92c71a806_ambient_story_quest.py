"""Allow independent scripted daily dialogue without a quest.

Revision ID: f4b92c71a806
Revises: e682f9a410c7
"""
from alembic import op
import sqlalchemy as sa

revision = "f4b92c71a806"
down_revision = "e682f9a410c7"
branch_labels = None
depends_on = None


def upgrade():
    with op.batch_alter_table("story_definitions") as batch:
        batch.alter_column("quest_id", existing_type=sa.String(100), nullable=True)


def downgrade():
    # A task-free definition cannot be represented by the older schema. Session
    # history has no definition FK and remains available for audit/recovery.
    op.execute(sa.text("DELETE FROM story_definitions WHERE quest_id IS NULL"))
    with op.batch_alter_table("story_definitions") as batch:
        batch.alter_column("quest_id", existing_type=sa.String(100), nullable=False)
