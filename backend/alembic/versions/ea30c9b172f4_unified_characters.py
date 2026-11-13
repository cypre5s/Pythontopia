"""Unified characters. Old course definitions must be explicitly cleared first."""
from alembic import op
import sqlalchemy as sa

revision = "ea30c9b172f4"
down_revision = "c34d851ae60b"
branch_labels = None
depends_on = None


def upgrade():
    if op.get_bind().execute(sa.text("SELECT count(*) FROM npcs")).scalar():
        raise RuntimeError("角色格式已升级，请先备份并显式清理旧测试课程，再导入v2课程包")
    with op.batch_alter_table("npcs") as batch:
        batch.alter_column("map_key", existing_type=sa.String(40), nullable=True)
        batch.alter_column("tile_x", existing_type=sa.Integer(), nullable=True)
        batch.alter_column("tile_y", existing_type=sa.Integer(), nullable=True)
        batch.drop_column("dialogue")
        batch.add_column(sa.Column("direction", sa.String(8), nullable=False))
        batch.add_column(sa.Column("sprite", sa.Text(), nullable=False))
        batch.add_column(sa.Column("persona", sa.Text(), nullable=False))
        batch.add_column(sa.Column("appearance_condition", sa.JSON(), nullable=False))


def downgrade():
    raise RuntimeError("请通过升级前备份恢复旧课程")
