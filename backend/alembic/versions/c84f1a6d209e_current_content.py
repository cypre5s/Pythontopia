"""Collapse teaching content to one current configuration, preserving learner data.

Revision ID: c84f1a6d209e
Revises: b63e1a4920d7

Historical content variants cannot be reconstructed after this migration. Learner
submissions, grades, events, quest states and reading participation are retained.
"""

import re
from datetime import datetime, timezone

from alembic import context, op
import sqlalchemy as sa

revision = "c84f1a6d209e"
down_revision = "b63e1a4920d7"
branch_labels = None
depends_on = None

REMOVED_KEYS = {"version", "activity_version", "material_version", "quest_version", "story_version", "content_version", "pending_activity_version"}


def current_value(value):
    if isinstance(value, dict):
        return {key: current_value(item) for key, item in value.items() if key not in REMOVED_KEYS}
    if isinstance(value, list):
        return [current_value(item) for item in value]
    if isinstance(value, str):
        return re.sub(r"(story:[^:]+):v\d+:", r"\1:", value)
    return value


def upgrade():
    if context.is_offline_mode():
        raise RuntimeError("Current-content migration requires an online connection to preserve and merge learner records")
    connection = op.get_bind()
    metadata = sa.MetaData()
    metadata.reflect(connection)
    tables = metadata.tables

    for column in (
        sa.Column("content", sa.JSON(), nullable=False, server_default="{}"),
        sa.Column("storage_key", sa.Text()),
        sa.Column("status", sa.String(24), nullable=False, server_default="ready"),
        sa.Column("error", sa.Text()),
        # SQLite allows a constant default when adding a column to populated
        # tables, but rejects CURRENT_TIMESTAMP in that operation.
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=datetime.now(timezone.utc).isoformat()),
    ):
        op.add_column("materials", column)
    material = sa.Table("materials", sa.MetaData(), autoload_with=connection)
    versions = tables["material_versions"]
    latest = {}
    for row in connection.execute(sa.select(versions).order_by(versions.c.version)).mappings():
        latest[row["material_id"]] = row
    for material_id, row in latest.items():
        connection.execute(material.update().where(material.c.id == material_id).values(
            **{key: row[key] for key in ("content", "storage_key", "status", "error", "created_at")}
        ))

    # These definitions have no incoming database foreign keys. Keep the newest
    # configuration per stable ID before replacing their composite primary keys.
    for name in ("quest_definitions", "story_definitions"):
        table = tables[name]
        seen = set()
        for row in connection.execute(sa.select(table).order_by(table.c.version.desc())).mappings():
            if row["id"] in seen:
                connection.execute(table.delete().where(table.c.id == row["id"], table.c.version == row["version"]))
            seen.add(row["id"])

    # Preserve participation from every historical activity configuration. The
    # highest configuration supplies the last page; completed history survives.
    progress = tables["activity_progress"]
    merged = {}
    for row in connection.execute(sa.select(progress).order_by(progress.c.version)).mappings():
        key = (row["user_id"], row["classroom_id"], row["activity_id"])
        previous = merged.get(key)
        current = dict(row)
        if previous:
            current["visited_pages"] = sorted(set(previous["visited_pages"]) | set(current["visited_pages"]))
            current["completed"] = previous["completed"] or current["completed"]
            if current["page_no"] is None:
                current["page_no"] = previous["page_no"]
        merged[key] = current
    connection.execute(progress.delete())
    if merged:
        connection.execute(progress.insert(), list(merged.values()))

    # Normalize script references and persisted receipts without discarding them.
    json_fields = {
        "activities": ("content",), "materials": ("content",),
        "quest_definitions": ("definition",), "story_definitions": ("content",),
        "story_sessions": ("return_mapping", "receipts"), "node_states": ("evidence",),
        "learning_events": ("evidence",), "assistant_sessions": ("messages",),
    }
    for name, fields in json_fields.items():
        table = material if name == "materials" else tables[name]
        for row in connection.execute(sa.select(table)).mappings():
            updates = {field: current_value(row[field]) for field in fields}
            connection.execute(table.update().where(*(column == row[column.name] for column in table.primary_key.columns)).values(**updates))
    flags = tables["world_flags"]
    rows = list(connection.execute(sa.select(flags)).mappings())
    existing = {(row["user_id"], row["classroom_id"], row["key"]) for row in rows}
    for row in rows:
        normalized = current_value(row["key"])
        if normalized == row["key"]:
            continue
        predicate = (flags.c.user_id == row["user_id"], flags.c.classroom_id == row["classroom_id"], flags.c.key == row["key"])
        key = (row["user_id"], row["classroom_id"], normalized)
        if key in existing:
            connection.execute(flags.delete().where(*predicate))
        else:
            connection.execute(flags.update().where(*predicate).values(key=normalized))
            existing.add(key)

    op.drop_table("activity_versions")
    op.drop_table("material_versions")
    for name, primary in (("quest_definitions", ["id"]), ("story_definitions", ["id"]),
                          ("activity_progress", ["user_id", "classroom_id", "activity_id"])):
        with op.batch_alter_table(name, naming_convention={"pk": "pk_%(table_name)s"}) as batch:
            batch.drop_constraint(sa.inspect(connection).get_pk_constraint(name)["name"] or f"pk_{name}", type_="primary")
            batch.drop_column("version")
            if name == "story_definitions":
                batch.drop_column("quest_version")
            batch.create_primary_key(f"pk_{name}", primary)

    removed = {
        "activities": ("version",), "chapter_releases": ("version",),
        "player_quests": ("quest_version",), "learning_events": ("content_version",),
        "learning_records": ("version",), "story_sessions": ("story_version", "pending_activity_version"),
        "questions": ("version",), "submissions": ("version",), "assistant_sessions": ("version", "material_version"),
    }
    for name, columns in removed.items():
        for column in columns:
            # Native DROP COLUMN avoids rebuilding referenced SQLite parents.
            op.drop_column(name, column)


def downgrade():
    raise RuntimeError("Cannot reconstruct removed content versions; restore a pre-migration database backup to downgrade")
