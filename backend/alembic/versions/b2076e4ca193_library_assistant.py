"""Allow material-scoped librarian sessions without synthetic activities."""
from alembic import op
import sqlalchemy as sa

revision = "b2076e4ca193"
down_revision = "a91c7d20e648"
branch_labels = None
depends_on = None


def upgrade():
    if op.get_bind().dialect.name == "sqlite":
        # Batch replaces the table; preserve the calls which reference sessions.
        with op.get_context().autocommit_block():
            op.execute("PRAGMA foreign_keys=OFF")
        with op.batch_alter_table("assistant_sessions") as batch:
            batch.alter_column("activity_id", existing_type=sa.String(100), nullable=True)
        with op.get_context().autocommit_block():
            op.execute("PRAGMA foreign_keys=ON")
        if op.get_bind().exec_driver_sql("PRAGMA foreign_key_check").fetchall():
            raise RuntimeError("Foreign key check failed after assistant session migration")
    else:
        op.alter_column("assistant_sessions", "activity_id", existing_type=sa.String(100), nullable=True)


def downgrade():
    if op.get_bind().execute(sa.text("SELECT count(*) FROM assistant_sessions WHERE activity_id IS NULL")).scalar():
        raise RuntimeError("Material assistant history cannot be discarded; restore a backup instead")
    with op.batch_alter_table("assistant_sessions") as batch:
        batch.alter_column("activity_id", existing_type=sa.String(100), nullable=False)
