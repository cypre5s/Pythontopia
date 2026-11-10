"""Introduce classroom-owned current course namespaces without altering learner records."""

from alembic import op
import sqlalchemy as sa

revision = "e90a7c4d152b"
down_revision = "d7213a8f9c06"
branch_labels = None
depends_on = None

TABLES = ("chapters", "knowledge_points", "materials", "questions", "activities",
          "quest_definitions", "story_definitions", "npcs", "course_examples")
NAMES = {"fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
         "uq": "uq_%(table_name)s_%(column_0_name)s"}


def _upgrade():
    op.create_table("course_instances",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("classroom_id", sa.Integer(), sa.ForeignKey("classrooms.id"), nullable=False, unique=True),
        sa.Column("package_id", sa.String(100), nullable=False),
        sa.Column("name", sa.String(200), nullable=False),
        sa.Column("package_version", sa.String(100), nullable=False),
        sa.Column("revision", sa.Integer(), nullable=False, server_default="1"),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False))
    for table in TABLES:
        with op.batch_alter_table(table, naming_convention=NAMES) as batch:
            batch.add_column(sa.Column("course_instance_id", sa.Integer(), nullable=True))
            batch.add_column(sa.Column("stable_id", sa.String(100), nullable=True))
            batch.create_foreign_key(f"fk_{table}_course_instance_id_course_instances", "course_instances", ["course_instance_id"], ["id"])
            batch.create_index(f"ix_{table}_course_instance_id", ["course_instance_id"])
            batch.create_unique_constraint(f"uq_{table}_instance_stable", ["course_instance_id", "stable_id"])
            if table == "chapters":
                unique = next(item for item in sa.inspect(op.get_bind()).get_unique_constraints(table)
                              if item["column_names"] == ["order_index"])
                batch.drop_constraint(unique["name"] or "uq_chapters_order_index", type_="unique")
                batch.create_unique_constraint("uq_chapters_instance_order", ["course_instance_id", "order_index"])
                batch.create_index("uq_chapters_template_order", ["order_index"], unique=True,
                    sqlite_where=sa.text("course_instance_id IS NULL"), postgresql_where=sa.text("course_instance_id IS NULL"))
            if table == "story_definitions":
                batch.alter_column("chapter_id", existing_type=sa.Integer(), nullable=True)
    # Existing global rows remain intact as private migration/import sources.
    # Explicit class cloning and evidence remapping is a separately verified transaction.


def upgrade():
    if op.get_bind().dialect.name != "sqlite":
        _upgrade()
        return
    with op.get_context().autocommit_block():
        op.execute("PRAGMA foreign_keys=OFF")
    try:
        _upgrade()
    finally:
        with op.get_context().autocommit_block():
            op.execute("PRAGMA foreign_keys=ON")
    if op.get_bind().exec_driver_sql("PRAGMA foreign_key_check").fetchall():
        raise RuntimeError("Foreign key check failed after course namespace migration")


def downgrade():
    raise RuntimeError("Class-owned definitions cannot be merged losslessly; restore an isolated backup instead")
