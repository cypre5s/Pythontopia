"""Move single material activities into ordered, independently configured resources."""
from copy import deepcopy
import hashlib
import json

from alembic import op
import sqlalchemy as sa

revision = "e51b92fa60c3"
down_revision = "d74e90bc21a6"
branch_labels = None
depends_on = None


def _definition_hash(db, instance, current):
    # Freeze the checkpoint hash transition here; no runtime compatibility path.
    from app.world_config import load_world_config
    tables = sa.MetaData()
    def table(name):
        return sa.Table(name, tables, autoload_with=db)
    result = {"world": load_world_config()}
    for name in ("chapters", "materials", "activities", "questions", "quest_definitions", "story_definitions", "npcs"):
        source = table(name)
        result[name] = sorted([dict(row) for row in db.execute(sa.select(source).where(source.c.course_instance_id == instance["id"])).mappings()],
                             key=lambda row: str(row.get("id", row.get("key"))))
    releases = table("chapter_releases")
    result["releases"] = [dict(row) for row in db.execute(sa.select(releases).where(releases.c.classroom_id == instance["classroom_id"]).order_by(releases.c.chapter_id)).mappings()]
    result["revision"] = instance["revision"]
    activities = tables.tables["activities"]
    for name, fields in [("activity_questions", ("activity_id", "question_id", "order_index")),
                         *(([("activity_materials", ("activity_id", "material_id", "order_index", "required", "learning"))]) if current else [])]:
        source = table(name)
        result[name] = [tuple(row) for row in db.execute(sa.select(*(source.c[field] for field in fields)).join(activities, source.c.activity_id == activities.c.id)
                        .where(activities.c.course_instance_id == instance["id"]).order_by(source.c.activity_id, source.c.order_index))]
    return hashlib.sha256(json.dumps(result, sort_keys=True, ensure_ascii=False, default=str).encode()).hexdigest()


def _progress_state(row, bindings):
    state = deepcopy(row.get("media_progress") or {})
    material_id = bindings.get(row["activity_id"])
    if material_id:
        state["selected_material_id"] = material_id
        state["reading_pages"] = {str(material_id): list(row.get("visited_pages") or [])}
        if row.get("page_no"):
            state["reading_positions"] = {**state.get("reading_positions", {}), str(material_id): row["page_no"]}
    return state


def upgrade():
    db = op.get_bind()
    metadata = sa.MetaData()
    checkpoints = sa.Table("experience_checkpoints", metadata, autoload_with=db)
    instances = sa.Table("course_instances", metadata, autoload_with=db)
    saved = list(db.execute(sa.select(checkpoints)).mappings())
    courses = {row["classroom_id"]: row for row in db.execute(sa.select(instances)).mappings() if any(item["classroom_id"] == row["classroom_id"] for item in saved)}
    old_hashes = {key: _definition_hash(db, row, False) for key, row in courses.items()}
    resources = op.create_table(
        "activity_materials",
        sa.Column("activity_id", sa.String(100), sa.ForeignKey("activities.id", ondelete="CASCADE"), primary_key=True),
        sa.Column("material_id", sa.Integer, sa.ForeignKey("materials.id"), primary_key=True),
        sa.Column("order_index", sa.Integer, nullable=False),
        sa.Column("required", sa.Boolean, nullable=False),
        sa.Column("learning", sa.JSON, nullable=False),
    )
    op.create_index("ix_activity_materials_material_id", "activity_materials", ["material_id"])
    activities = sa.table("activities", sa.column("id", sa.String), sa.column("material_id", sa.Integer), sa.column("content", sa.JSON))
    bindings = {}
    for row in db.execute(sa.select(activities)).mappings():
        content = deepcopy(row["content"])
        learning = content.pop("learning", {})
        completion = content.pop("completion", None)
        if completion:
            learning["reading"] = {"scope": "pages", "page_ids": completion["required_page_ids"]}
        if row["material_id"] is not None:
            bindings[row["id"]] = row["material_id"]
            db.execute(resources.insert().values(activity_id=row["id"], material_id=row["material_id"], order_index=0, required=True, learning=learning))
        db.execute(activities.update().where(activities.c.id == row["id"]).values(content=content))
    progress = sa.table("activity_progress", sa.column("user_id", sa.Integer), sa.column("classroom_id", sa.Integer),
                        sa.column("activity_id", sa.String), sa.column("page_no", sa.Integer), sa.column("visited_pages", sa.JSON), sa.column("media_progress", sa.JSON))
    for row in db.execute(sa.select(progress)).mappings():
        material_id = bindings.get(row["activity_id"])
        if material_id:
            state = _progress_state(row, bindings)
            db.execute(progress.update().where(progress.c.user_id == row["user_id"], progress.c.classroom_id == row["classroom_id"],
                progress.c.activity_id == row["activity_id"]).values(media_progress=state))
    if db.dialect.name == "sqlite":
        with op.get_context().autocommit_block():
            op.execute("PRAGMA foreign_keys=OFF")
        try:
            with op.batch_alter_table("activities") as batch:
                batch.drop_column("material_id")
        finally:
            with op.get_context().autocommit_block():
                op.execute("PRAGMA foreign_keys=ON")
        if db.exec_driver_sql("PRAGMA foreign_key_check").fetchall():
            raise RuntimeError("活动资料迁移后外键校验失败")
    else:
        op.drop_column("activities", "material_id")
    new_hashes = {key: _definition_hash(db, row, True) for key, row in courses.items()}
    for row in saved:
        snapshot = deepcopy(row["snapshot"])
        for item in snapshot.get("activity_progress", []):
            item["media_progress"] = _progress_state(item, bindings)
        values = {"snapshot": snapshot}
        if row["definition_hash"] == old_hashes.get(row["classroom_id"]):
            values["definition_hash"] = new_hashes[row["classroom_id"]]
        db.execute(checkpoints.update().where(checkpoints.c.id == row["id"]).values(**values))


def downgrade():
    raise RuntimeError("多资料活动不能无损还原成单资料，请恢复升级前备份")
