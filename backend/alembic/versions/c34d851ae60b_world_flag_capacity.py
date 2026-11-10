"""Preserve complete dialogue evidence keys for authored checkpoints."""

import sqlalchemy as sa
from alembic import op

revision = "c34d851ae60b"
down_revision = "5a3d09d4c2b1"
branch_labels = None
depends_on = None


def upgrade():
    with op.batch_alter_table("world_flags") as batch:
        batch.alter_column("key", existing_type=sa.String(100), type_=sa.String(320))


def downgrade():
    long_key = op.get_bind().scalar(sa.text("SELECT 1 FROM world_flags WHERE length(key) > 100 LIMIT 1"))
    if long_key:
        raise RuntimeError("Cannot shorten world flags while long dialogue evidence exists")
    with op.batch_alter_table("world_flags") as batch:
        batch.alter_column("key", existing_type=sa.String(320), type_=sa.String(100))
