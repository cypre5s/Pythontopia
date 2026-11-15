"""Fixture generation must not open the configured app database or overwrite artifacts."""
from pathlib import Path
import os

import pytest
from fixture_package import build_fixture_package
from app.course_packages.reader import load_bundle
from fixture_archive import unpack_archive
from app.course_packages.manifest import PackageError


def test_fixture_package_isolated_and_paused(tmp_path, monkeypatch):
    from app import db as app_db
    def forbidden(*args, **kwargs):
        raise AssertionError("configured database must never be opened")
    monkeypatch.setattr(app_db.engine, "connect", forbidden)
    monkeypatch.setenv("REBORN_STORAGE", "s3")
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "untouched-runtime"))
    destination = tmp_path / "fixture.zip"
    source = Path(os.environ["REBORN_COURSE_ROOT"])
    content = Path(os.environ["REBORN_CONTENT_ROOT"])
    result = build_fixture_package(source, content, destination,
                                   package_id="offline-test", name="合成课程包测试")
    assert result["assets"] == 0 and result["media"] == "existing_extracted_text_only"
    assert os.environ["REBORN_STORAGE"] == "s3" and not (tmp_path / "untouched-runtime").exists()
    directory = tmp_path / "package"
    directory.mkdir()
    unpack_archive(destination, directory)
    bundle = load_bundle(directory)
    assert bundle["manifest"]["package_id"] == "offline-test"
    assert all(row["status"] == "paused" for row in bundle["course"]["releases"])
    shelf = next(row for row in bundle["data"]["story"] if row["id"] == "legacy-world-library_shelf")
    assert shelf["content"]["nodes"][0] == {
        "id": "start", "type": "action", "action": "open_workspace", "workspace": "library", "next": "end",
    }
    assert "users" not in bundle and "student" not in bundle
    original = destination.read_bytes()
    with pytest.raises(PackageError):
        build_fixture_package(source, content, destination, package_id="x", name="x")
    assert destination.read_bytes() == original
