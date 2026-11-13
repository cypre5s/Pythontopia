"""The v2 upgrade changes structure and never silently converts old characters."""
from sqlalchemy import inspect, text
from test_curriculum_migration import migrate, sqlite_engine


def test_new_schema_and_repeated_upgrade(tmp_path):
    path = tmp_path / "characters.sqlite"
    url = "sqlite:///" + path.as_posix()
    migrate(url, "upgrade", "head")
    migrate(url, "upgrade", "head")
    migrate(url, "check")
    engine = sqlite_engine(path)
    columns = {row["name"]: row for row in inspect(engine).get_columns("npcs")}
    assert "dialogue" not in columns
    assert {"portrait", "sprite", "persona", "appearance_condition", "direction"} <= columns.keys()
    assert all(columns[field]["nullable"] for field in ("map_key", "tile_x", "tile_y"))
    engine.dispose()


def test_populated_old_characters_require_explicit_cleanup(tmp_path):
    path = tmp_path / "before.sqlite"
    url = "sqlite:///" + path.as_posix()
    migrate(url, "upgrade", "c34d851ae60b")
    engine = sqlite_engine(path)
    with engine.begin() as connection:
        connection.execute(text("INSERT INTO npcs(key,name,map_key,tile_x,tile_y,portrait,dialogue) VALUES('old','Old','town_hub',22,36,'','[]')"))
    refused = migrate(url, "upgrade", "head", check=False)
    assert refused.returncode != 0 and "显式清理旧测试课程" in refused.stderr + refused.stdout
    with engine.connect() as connection:
        assert connection.execute(text("SELECT count(*) FROM npcs")).scalar() == 1
        assert connection.execute(text("SELECT version_num FROM alembic_version")).scalar() == "c34d851ae60b"
    assert "dialogue" in {row["name"] for row in inspect(engine).get_columns("npcs")}
    engine.dispose()
