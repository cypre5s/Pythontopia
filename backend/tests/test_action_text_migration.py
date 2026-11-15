"""One-time prompt cleanup preserves graph links, sessions and teaching evidence."""
from copy import deepcopy
from datetime import datetime, timezone
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path

from alembic.migration import MigrationContext
from alembic.operations import Operations
import pytest
import sqlalchemy as sa

from app.teaching.story.validation import validate_story
from test_curriculum_migration import migrate

spec = spec_from_file_location("action_text_migration", Path(__file__).resolve().parents[1] / "alembic/versions/d74e90bc21a6_action_nodes_without_text.py")
migration = module_from_spec(spec)
spec.loader.exec_module(migration)


def content(prompt="请读完资料再回来。\n\n不确定时可以向管理员提问。", workspace="library"):
    return {"schema_version": 1, "mode": "public", "bindings": {},
            "entries": {"interaction:library_shelf": [{"when": {"always": True}, "node": "start"}]},
            "nodes": [{"id": "start", "type": "action", "action": "open_workspace", "workspace": workspace,
                       "text": prompt, "next": "end"}, {"id": "end", "type": "end"}]}


@pytest.mark.parametrize("prompt,workspace", [("打开图书馆", "library"), ("查看学习日志", "journal"), ("", "library")])
def test_redundant_action_labels_are_removed(prompt, workspace):
    original = content(prompt, workspace)
    expected = deepcopy(original)
    del expected["nodes"][0]["text"]
    assert migration._clean_content(original) == expected
    assert "text" in original["nodes"][0]
    validate_story(expected)


def test_substantive_prompt_keeps_original_entry_and_collision_safe_action_id():
    original = content()
    first = migration._clean_content(original)
    # A teacher may already have used the generated ID elsewhere in the graph.
    collision = first["nodes"][1]["id"]
    original["nodes"][1]["id"] = collision
    original["nodes"][0]["next"] = collision
    cleaned = migration._clean_content(original)
    dialogue, action, end = cleaned["nodes"]
    assert dialogue == {"id": "start", "type": "dialogue", "text": original["nodes"][0]["text"], "next": action["id"]}
    assert action["id"] != collision and action["next"] == end["id"] == collision
    assert cleaned["entries"] == original["entries"]
    assert migration._clean_content(cleaned) == cleaned
    validate_story(cleaned)


def test_migration_updates_definitions_once_and_preserves_session_and_receipts(tmp_path):
    url = "sqlite:///" + (tmp_path / "actions.sqlite3").as_posix()
    migrate(url, "upgrade", migration.down_revision)
    engine = sa.create_engine(url)
    metadata = sa.MetaData()
    metadata.reflect(engine)
    stories, instances = metadata.tables["story_definitions"], metadata.tables["course_instances"]
    sessions = metadata.tables["story_sessions"]
    with engine.begin() as connection:
        # Foreign key enforcement is off only in this raw, isolated migration fixture.
        connection.execute(instances.insert(), {"id": 7, "classroom_id": 99, "package_id": "test", "name": "测试", "package_version": "1",
                                               "revision": 4, "created_at": datetime.now(timezone.utc)})
        connection.execute(stories.insert(), [{"id": "prompt", "course_instance_id": 7, "content": content()},
                                             {"id": "label", "course_instance_id": 7, "content": content("打开图书馆")}])
        connection.execute(sessions.insert(), {"id": "resume", "user_id": 1, "classroom_id": 99, "story_id": "prompt", "node_id": "start",
                                               "status": "active", "revision": 3, "return_mapping": {}, "receipts": [{"id": "old", "signature": "evidence"}]})
        session_before = dict(connection.execute(sa.select(sessions)).mappings().one())
    migrate(url, "upgrade", migration.revision)
    migrate(url, "upgrade", migration.revision)
    with engine.begin() as connection:
        assert connection.scalar(sa.select(instances.c.revision)) == 5
        saved = connection.scalar(sa.select(stories.c.content).where(stories.c.id == "prompt"))
        assert saved["nodes"][0]["type"] == "dialogue" and saved["nodes"][0]["id"] == session_before["node_id"]
        assert dict(connection.execute(sa.select(sessions)).mappings().one()) == session_before
        # Re-running the data operation itself is harmless, too.
        with Operations.context(MigrationContext.configure(connection)):
            migration.upgrade()
        assert connection.scalar(sa.select(instances.c.revision)) == 5
    engine.dispose()


def test_oversized_conversion_preserves_original_content():
    original = content()
    original["nodes"].extend({"id": f"end-{i}", "type": "end"} for i in range(198))
    before = deepcopy(original)
    with pytest.raises(RuntimeError, match="200"):
        migration._clean_content(original)
    assert original == before
