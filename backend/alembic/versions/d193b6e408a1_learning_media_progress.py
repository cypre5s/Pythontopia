"""Persist effective video participation without changing existing learning records.

Revision ID: d193b6e408a1
Revises: c84f1a6d209e
"""

from alembic import op
import sqlalchemy as sa

revision = "d193b6e408a1"
down_revision = "c84f1a6d209e"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("activity_progress", sa.Column("media_progress", sa.JSON(), nullable=False, server_default=sa.text("'{}'")))


def downgrade():
    op.drop_column("activity_progress", "media_progress")
