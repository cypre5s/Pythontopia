"""Generic interaction sessions and class/course scoped building unlocks."""
from alembic import op
import sqlalchemy as sa

revision = "a31d5f9b7c20"
down_revision = "f21b9e7c6a40"
branch_labels = None
depends_on = None


def upgrade():
    with op.batch_alter_table("story_sessions") as batch:
        batch.alter_column("npc_key", existing_type=sa.String(80), nullable=True)
        batch.add_column(sa.Column("interaction_id", sa.String(100), nullable=True))
        batch.add_column(sa.Column("anchor", sa.JSON(), nullable=True))
    op.create_table("building_unlocks",
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id"), primary_key=True),
        sa.Column("classroom_id", sa.Integer(), sa.ForeignKey("classrooms.id"), primary_key=True),
        sa.Column("course_instance_id", sa.Integer(), sa.ForeignKey("course_instances.id"), primary_key=True),
        sa.Column("building_id", sa.String(100), primary_key=True))
    op.execute(sa.text("""INSERT INTO building_unlocks (user_id, classroom_id, course_instance_id, building_id)
        SELECT f.user_id, f.classroom_id, c.id, 'library' FROM world_flags f
        JOIN course_instances c ON c.classroom_id=f.classroom_id WHERE f.key='library_unlocked'"""))


def downgrade():
    raise RuntimeError("Generic interaction sessions and building unlock evidence require restoring an isolated backup")
