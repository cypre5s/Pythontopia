"""Separate reusable materials and questions from chapter and activity ownership."""

from alembic import op
import sqlalchemy as sa

revision = "d7213a8f9c06"
down_revision = "c3187f5db204"
branch_labels = None
depends_on = None

FK_NAMES = {"fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s"}


def _drop_relationship_columns(table, columns, *, question=False):
    inspector = sa.inspect(op.get_bind())
    foreign_keys = inspector.get_foreign_keys(table)
    indexes = inspector.get_indexes(table)
    with op.batch_alter_table(table, naming_convention=FK_NAMES) as batch:
        for index in indexes:
            if set(index["column_names"]) & set(columns):
                batch.drop_index(index["name"])
        for foreign_key in foreign_keys:
            if set(foreign_key["constrained_columns"]) & set(columns):
                name = foreign_key["name"] or f"fk_{table}_{foreign_key['constrained_columns'][0]}_{foreign_key['referred_table']}"
                batch.drop_constraint(name, type_="foreignkey")
        for column in columns:
            batch.drop_column(column)
        if question:
            batch.add_column(sa.Column("owner_id", sa.Integer(), nullable=True))
            batch.add_column(sa.Column("source_type", sa.String(24), nullable=False, server_default="imported"))
            batch.add_column(sa.Column("status", sa.String(16), nullable=False, server_default="draft"))
            batch.create_foreign_key("fk_questions_owner_id_users", "users", ["owner_id"], ["id"])
            batch.create_index("ix_questions_owner_id", ["owner_id"])
            batch.create_check_constraint("ck_questions_source_type", "source_type IN ('ai_generated', 'imported')")
            batch.create_check_constraint("ck_questions_status", "status IN ('draft', 'ready', 'disabled')")


def _upgrade():
    connection = op.get_bind()
    metadata = sa.MetaData()
    materials = sa.Table("materials", metadata, autoload_with=connection)
    questions = sa.Table("questions", metadata, autoload_with=connection)
    activities = sa.Table("activities", metadata, autoload_with=connection)
    material_rows = list(connection.execute(sa.select(materials.c.id, materials.c.chapter_id)).mappings())
    question_rows = list(connection.execute(sa.select(questions)).mappings())
    activity_rows = list(connection.execute(sa.select(activities)).mappings())
    by_question = {row["id"]: row for row in question_rows}
    by_activity = {row["id"]: row for row in activity_rows}

    activity_links, activity_contents = [], []
    for activity in activity_rows:
        content = dict(activity["content"] or {})
        configured = content.pop("question_ids", None)
        if configured is None:
            configured = sorted(row["id"] for row in question_rows if row["activity_id"] == activity["id"])
        if not isinstance(configured, list) or any(not isinstance(key, str) for key in configured) or len(set(configured)) != len(configured):
            raise RuntimeError(f"Activity {activity['id']} has invalid question references; repair before migrating")
        if any(key not in by_question or by_question[key]["activity_id"] != activity["id"] for key in configured):
            raise RuntimeError(f"Activity {activity['id']} has missing or mismatched questions; repair before migrating")
        activity_links.extend({"activity_id": activity["id"], "question_id": key, "order_index": index}
                              for index, key in enumerate(configured))
        activity_contents.append((activity["id"], content))

    op.create_table("material_chapters",
                    sa.Column("material_id", sa.Integer(), sa.ForeignKey("materials.id", ondelete="CASCADE"), primary_key=True),
                    sa.Column("chapter_id", sa.Integer(), sa.ForeignKey("chapters.id", ondelete="CASCADE"), primary_key=True))
    op.create_index("ix_material_chapters_chapter_id", "material_chapters", ["chapter_id"])
    op.create_table("question_chapters",
                    sa.Column("question_id", sa.String(100), sa.ForeignKey("questions.id", ondelete="CASCADE"), primary_key=True),
                    sa.Column("chapter_id", sa.Integer(), sa.ForeignKey("chapters.id", ondelete="CASCADE"), primary_key=True))
    op.create_index("ix_question_chapters_chapter_id", "question_chapters", ["chapter_id"])
    op.create_table("question_knowledge_points",
                    sa.Column("question_id", sa.String(100), sa.ForeignKey("questions.id", ondelete="CASCADE"), primary_key=True),
                    sa.Column("knowledge_point_id", sa.Integer(), sa.ForeignKey("knowledge_points.id", ondelete="CASCADE"), primary_key=True))
    op.create_index("ix_question_knowledge_points_knowledge_point_id", "question_knowledge_points", ["knowledge_point_id"])
    op.create_table("activity_questions",
                    sa.Column("activity_id", sa.String(100), sa.ForeignKey("activities.id", ondelete="CASCADE"), primary_key=True),
                    sa.Column("question_id", sa.String(100), sa.ForeignKey("questions.id"), primary_key=True),
                    sa.Column("order_index", sa.Integer(), nullable=False),
                    sa.CheckConstraint("order_index >= 0", name="ck_activity_questions_order_index"))
    op.create_index("ix_activity_questions_question_id", "activity_questions", ["question_id"])

    links = sa.Table("material_chapters", sa.MetaData(), autoload_with=connection)
    if material_rows:
        connection.execute(links.insert(), [{"material_id": row["id"], "chapter_id": row["chapter_id"]} for row in material_rows])
    links = sa.Table("question_chapters", sa.MetaData(), autoload_with=connection)
    if question_rows:
        connection.execute(links.insert(), [{"question_id": row["id"], "chapter_id": row["chapter_id"]} for row in question_rows])
    links = sa.Table("activity_questions", sa.MetaData(), autoload_with=connection)
    if activity_links:
        connection.execute(links.insert(), activity_links)
    for activity_id, content in activity_contents:
        connection.execute(activities.update().where(activities.c.id == activity_id).values(content=content))

    _drop_relationship_columns("materials", ["chapter_id"])
    _drop_relationship_columns("questions", ["chapter_id", "activity_id"], question=True)
    current_questions = sa.Table("questions", sa.MetaData(), autoload_with=connection)
    for question in question_rows:
        source = question["source"] if isinstance(question["source"], dict) else {}
        source_type = "ai_generated" if source.get("kind") in {"ai", "ai_generated"} else "imported"
        connection.execute(current_questions.update().where(current_questions.c.id == question["id"]).values(
            owner_id=by_activity[question["activity_id"]]["owner_id"], source_type=source_type,
            status="draft" if source.get("reviewed") is False else "ready"))


def upgrade():
    if op.get_bind().dialect.name != "sqlite":
        _upgrade()
        return
    # SQLite's table rebuild must retain incoming submission/session foreign keys.
    with op.get_context().autocommit_block():
        op.execute("PRAGMA foreign_keys=OFF")
    try:
        _upgrade()
    finally:
        with op.get_context().autocommit_block():
            op.execute("PRAGMA foreign_keys=ON")
    if op.get_bind().exec_driver_sql("PRAGMA foreign_key_check").fetchall():
        raise RuntimeError("Foreign key check failed after independent content migration")


def downgrade():
    raise RuntimeError("Cannot reconstruct single-owner content relationships; restore a backup instead")
