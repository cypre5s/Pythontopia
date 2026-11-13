"""Move the persisted assistant selection to the course default; activities may inherit."""
from copy import deepcopy

from alembic import op
import sqlalchemy as sa

revision = "f318a72c90de"
down_revision = "ea30c9b172f4"
branch_labels = None
depends_on = None


def upgrade():
    if op.get_bind().dialect.name == "sqlite":
        # SQLite replaces the table; preserve all referencing learning records.
        with op.get_context().autocommit_block():
            op.execute("PRAGMA foreign_keys=OFF")
        try:
            with op.batch_alter_table("activities") as batch:
                batch.alter_column("npc_key", existing_type=sa.String(80), nullable=True)
        finally:
            with op.get_context().autocommit_block():
                op.execute("PRAGMA foreign_keys=ON")
        if op.get_bind().exec_driver_sql("PRAGMA foreign_key_check").fetchall():
            raise RuntimeError("Foreign key check failed after course assistant migration")
    else:
        op.alter_column("activities", "npc_key", existing_type=sa.String(80), nullable=True)
    states = sa.table("course_package_state", sa.column("course_instance_id", sa.Integer), sa.column("configuration", sa.JSON))
    bind = op.get_bind()
    for instance_id, configuration in bind.execute(sa.select(states.c.course_instance_id, states.c.configuration)):
        settings = configuration.get("assistant", {})
        if "library_npc_id" in settings:
            config = deepcopy(configuration)
            config["assistant"]["default_npc_id"] = config["assistant"].pop("library_npc_id")
            bind.execute(states.update().where(states.c.course_instance_id == instance_id).values(configuration=config))


def downgrade():
    raise RuntimeError("请通过升级前备份恢复；不恢复旧助教配置")
