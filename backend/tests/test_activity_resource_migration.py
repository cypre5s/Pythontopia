"""Upgrade existing resource requirements and checkpoints without resetting evidence."""
from copy import deepcopy
from datetime import datetime, timezone
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path

import sqlalchemy as sa
from sqlalchemy.orm import Session

from app.experience.state import definition_hash
from test_independent_content_migration import old_content, migrate, engine_for


def test_existing_activity_progress_and_valid_checkpoint_survive(tmp_path):
    path = tmp_path / "resources.db"
    old_content(path)
    migrate(path, "d74e90bc21a6")
    spec = spec_from_file_location("resource_migration", Path(__file__).resolve().parents[1] / "alembic/versions/e51b92fa60c3_activity_resources.py")
    migration = module_from_spec(spec)
    spec.loader.exec_module(migration)
    engine = engine_for(path)
    metadata = sa.MetaData()
    metadata.reflect(engine)
    tables = metadata.tables
    with engine.begin() as db:
        db.execute(tables["course_instances"].insert().values(id=1, classroom_id=1, package_id="migration", name="保留课程", package_version="1", revision=1, created_at=datetime.now(timezone.utc)))
        for table in tables.values():
            if "course_instance_id" in table.c:
                db.execute(table.update().values(course_instance_id=1))
        instance = db.execute(sa.select(tables["course_instances"])).mappings().first()
        activity = db.execute(sa.select(tables["activities"]).where(tables["activities"].c.course_instance_id == instance["id"])).mappings().first()
        material = db.execute(sa.select(tables["materials"]).where(tables["materials"].c.course_instance_id == instance["id"])).mappings().first()
        key, cid = activity["id"], instance["classroom_id"]
        db.execute(tables["activities"].update().where(tables["activities"].c.id == key).values(
            material_id=material["id"], content={"published": True, "completion": {"required_page_ids": [41]}}))
        progress = {"activity_id": key, "page_no": 1, "visited_pages": [41], "completed": True,
                    "media_progress": {"watched_seconds": 12}}
        db.execute(tables["activity_progress"].insert().values(**progress, user_id=2, classroom_id=cid))
        saved = {"activity_progress": [deepcopy(progress)], "client_state": {"draft": "keep"}, "quests": []}
        for name, fingerprint in [("valid", migration._definition_hash(db, instance, False)), ("obsolete", "already-invalid")]:
            db.execute(tables["experience_checkpoints"].insert().values(id=name, user_id=2, classroom_id=cid, name=name,
                snapshot=saved, definition_hash=fingerprint, created_at=datetime.now(timezone.utc)))
        before = db.execute(sa.select(tables["submissions"])).mappings().all()
    migrate(path)
    migrate(path)
    metadata = sa.MetaData()
    metadata.reflect(engine)
    with engine.connect() as db, Session(engine) as session:
        resource = db.execute(sa.select(metadata.tables["activity_materials"])).mappings().one()
        assert resource["learning"] == {"reading": {"scope": "pages", "page_ids": [41]}}
        assert resource["required"] and resource["order_index"] == 0
        current = db.execute(sa.select(metadata.tables["activity_progress"])).mappings().one()
        assert current["visited_pages"] == [41] and current["completed"]
        assert current["media_progress"] == {"watched_seconds": 12, "selected_material_id": material["id"], "reading_positions": {str(material["id"]): 1}, "reading_pages": {str(material["id"]): [41]}}
        checkpoints = {row["id"]: row for row in db.execute(sa.select(metadata.tables["experience_checkpoints"])).mappings()}
        assert checkpoints["valid"]["definition_hash"] == definition_hash(session, cid)
        assert checkpoints["obsolete"]["definition_hash"] == "already-invalid"
        assert checkpoints["valid"]["snapshot"]["activity_progress"][0]["media_progress"] == current["media_progress"]
        assert checkpoints["valid"]["snapshot"]["client_state"] == {"draft": "keep"}
        assert db.execute(sa.select(metadata.tables["submissions"])).mappings().all() == before
        assert not db.exec_driver_sql("PRAGMA foreign_key_check").all()
    engine.dispose()
