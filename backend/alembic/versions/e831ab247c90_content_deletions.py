"""Retire teaching definitions while retaining learner history."""
from alembic import op
import sqlalchemy as sa

revision = "e831ab247c90"
down_revision = "d729fa031b62"
branch_labels = None
depends_on = None
TABLES = ("chapters", "knowledge_points", "materials", "questions", "activities", "quest_definitions", "story_definitions")


def _upgrade():
    for table in TABLES:
        op.add_column(table, sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True))
    with op.batch_alter_table("chapters") as batch:
        batch.drop_constraint("uq_chapters_instance_order", type_="unique")
        batch.create_index("uq_chapters_instance_order", ["course_instance_id", "order_index"], unique=True,
                           postgresql_where=sa.text("deleted_at IS NULL"), sqlite_where=sa.text("deleted_at IS NULL"))
    op.create_table(
        "content_deletion_previews",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("teacher_id", sa.Integer(), sa.ForeignKey("users.id"), nullable=False, index=True),
        sa.Column("classroom_id", sa.Integer(), sa.ForeignKey("classrooms.id"), nullable=False, index=True),
        sa.Column("course_instance_id", sa.Integer(), sa.ForeignKey("course_instances.id"), nullable=False),
        sa.Column("revision", sa.Integer(), nullable=False),
        sa.Column("targets", sa.JSON(), nullable=False),
        sa.Column("fingerprint", sa.String(64), nullable=False),
        sa.Column("confirmation", sa.String(200), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("result", sa.JSON(), nullable=True),
    )


def upgrade():
    if op.get_bind().dialect.name != "sqlite":
        _upgrade()
        return
    # SQLite rebuilds the parent chapter table when replacing the unique constraint.
    # Keep all incoming associations and history intact, then check their integrity.
    with op.get_context().autocommit_block():
        op.execute("PRAGMA foreign_keys=OFF")
    try:
        _upgrade()
    finally:
        with op.get_context().autocommit_block():
            op.execute("PRAGMA foreign_keys=ON")
    if op.get_bind().exec_driver_sql("PRAGMA foreign_key_check").fetchall():
        raise RuntimeError("Foreign key check failed after content retirement migration")


def downgrade():
    raise RuntimeError("Retired content cannot be made current losslessly; restore an isolated backup instead")
