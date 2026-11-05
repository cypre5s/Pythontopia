"""Versioned story sessions and resumable reading participation.

Revision ID: b63e1a4920d7
Revises: 7d4b982ce106
"""

from alembic import op
import sqlalchemy as sa

revision = "b63e1a4920d7"
down_revision = "7d4b982ce106"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "story_definitions",
        sa.Column("id", sa.String(100), primary_key=True),
        sa.Column("version", sa.Integer(), primary_key=True),
        sa.Column("chapter_id", sa.Integer(), sa.ForeignKey("chapters.id"), nullable=False),
        sa.Column("quest_id", sa.String(100), nullable=False),
        sa.Column("quest_version", sa.Integer(), nullable=False),
        sa.Column("content", sa.JSON(), nullable=False),
    )
    op.create_table(
        "story_sessions",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("classroom_id", sa.Integer(), sa.ForeignKey("classrooms.id"), nullable=False),
        sa.Column("story_id", sa.String(100), nullable=False),
        sa.Column("story_version", sa.Integer(), nullable=False),
        sa.Column("npc_key", sa.String(80), sa.ForeignKey("npcs.key"), nullable=False),
        sa.Column("node_id", sa.String(100), nullable=False),
        sa.Column("status", sa.String(16), nullable=False),
        sa.Column("revision", sa.Integer(), nullable=False),
        sa.Column("pending_activity_id", sa.String(100)),
        sa.Column("pending_activity_version", sa.Integer()),
        sa.Column("return_mapping", sa.JSON(), nullable=False),
        sa.Column("receipts", sa.JSON(), nullable=False),
    )
    op.create_index("ix_story_sessions_user_id", "story_sessions", ["user_id"])
    op.create_index("ix_story_sessions_classroom_id", "story_sessions", ["classroom_id"])
    op.create_index(
        "uq_story_session_open", "story_sessions", ["user_id", "classroom_id"], unique=True,
        postgresql_where=sa.text("status IN ('active', 'waiting')"), sqlite_where=sa.text("status IN ('active', 'waiting')"),
    )
    op.create_table(
        "activity_progress",
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id"), primary_key=True),
        sa.Column("classroom_id", sa.Integer(), sa.ForeignKey("classrooms.id"), primary_key=True),
        sa.Column("activity_id", sa.String(100), sa.ForeignKey("activities.id"), primary_key=True),
        sa.Column("version", sa.Integer(), primary_key=True),
        sa.Column("page_no", sa.Integer()),
        sa.Column("visited_pages", sa.JSON(), nullable=False),
        sa.Column("completed", sa.Boolean(), nullable=False),
    )


def downgrade():
    op.drop_table("activity_progress")
    op.drop_index("uq_story_session_open", table_name="story_sessions")
    op.drop_index("ix_story_sessions_classroom_id", table_name="story_sessions")
    op.drop_index("ix_story_sessions_user_id", table_name="story_sessions")
    op.drop_table("story_sessions")
    op.drop_table("story_definitions")
