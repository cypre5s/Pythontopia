"""Offline conversion must not open the configured app database or overwrite artifacts."""
from pathlib import Path
import os

import pytest
from app.config import ROOT
from app.convert_legacy_course import convert_legacy
from app.course_exchange import load_bundle, unpack_archive
from app.course_package import PackageError


def test_offline_conversion_isolated_and_paused(tmp_path, monkeypatch):
    from app import db as app_db
    def forbidden(*args, **kwargs):
        raise AssertionError("configured database must never be opened")
    monkeypatch.setattr(app_db.engine, "connect", forbidden)
    monkeypatch.setenv("REBORN_STORAGE", "s3")
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "untouched-runtime"))
    destination = tmp_path / "converted.zip"
    source = Path(os.getenv("REBORN_COURSE_ROOT", str(ROOT / "data/course/legacy/course-pack-43")))
    content = Path(os.getenv("REBORN_CONTENT_ROOT", str(ROOT / "data/course/legacy/content")))
    result = convert_legacy(source, content, destination,
                            package_id="offline-test", name="离线转换测试")
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
        convert_legacy(source, content, destination, package_id="x", name="x")
    assert destination.read_bytes() == original
