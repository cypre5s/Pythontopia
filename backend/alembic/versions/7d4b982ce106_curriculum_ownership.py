"""Teacher-owned chapters and knowledge points in the existing single course.

Revision ID: 7d4b982ce106
Revises: 221d0f8caf6e

Purpose: add nullable owner_id to chapters/knowledge_points. Existing course seed
rows stay shared (NULL); no content, grades, or original baseline is rewritten.
Downgrade removes ownership and is refused when private rows exist, to prevent
private curriculum becoming shared by accident.
"""

from alembic import op, context
import sqlalchemy as sa

revision = "7d4b982ce106"
down_revision = "221d0f8caf6e"
branch_labels = None
depends_on = None


def upgrade():
    for table in ("chapters", "knowledge_points"):
        if op.get_bind().dialect.name == "sqlite":
            # Nullable inline REFERENCES is supported with foreign_keys=ON.
            # Do not rebuild/drop parent tables referenced by live learning data.
            op.execute(sa.text(f"ALTER TABLE {table} ADD COLUMN owner_id INTEGER REFERENCES users(id)"))
        else:
            op.add_column(table, sa.Column("owner_id", sa.Integer(), nullable=True))
            op.create_foreign_key(f"fk_{table}_owner_id_users", table, "users", ["owner_id"], ["id"])
        op.create_index(f"ix_{table}_owner_id", table, ["owner_id"])


def downgrade():
    if not context.is_offline_mode():
        connection = op.get_bind()
        for table in ("chapters", "knowledge_points"):
            if connection.scalar(sa.text(f"SELECT count(*) FROM {table} WHERE owner_id IS NOT NULL")):
                raise RuntimeError("Cannot downgrade while private curriculum exists: ownership must not silently become shared")
    for table in ("knowledge_points", "chapters"):
        op.drop_index(f"ix_{table}_owner_id", table_name=table)
        if op.get_bind().dialect.name == "sqlite":
            op.execute(sa.text(f"ALTER TABLE {table} DROP COLUMN owner_id"))
        else:
            op.drop_constraint(f"fk_{table}_owner_id_users", table, type_="foreignkey")
            op.drop_column(table, "owner_id")
