from dataclasses import replace
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

import pytest
import yaml

from app.course_package import PackageError, PackageLimits, main, validate_package


@pytest.fixture
def package(tmp_path):
    root = tmp_path / "course"
    (root / "curriculum").mkdir(parents=True)
    (root / "curriculum/course.yaml").write_text("name: 测试课程\n", encoding="utf-8")
    (root / "assets/texts").mkdir(parents=True)
    media = b"test-only lesson content\n"
    (root / "assets/texts/lesson.txt").write_bytes(media)
    manifest = {
        "schema_version": 2, "package_id": "test-course", "name": "测试课程", "package_version": "1",
        "created_at": "2026-11-10T10:00:00+08:00", "entry": "curriculum/course.yaml",
        "requires": {"world_id": "pythontopia-town", "world_schema_version": 1, "capabilities": ["reading", "practice"]},
    }
    resource = {"id": "lesson", "path": "assets/texts/lesson.txt", "media_type": "text/plain",
                "size_bytes": len(media), "sha256": hashlib.sha256(media).hexdigest(), "source": "test fixture", "license": "test-only"}
    write_manifest(root, manifest)
    write_resources(root, [resource])
    return root, manifest, resource


def write_manifest(root, manifest):
    (root / "manifest.yaml").write_text(yaml.safe_dump(manifest, allow_unicode=True), encoding="utf-8")


def write_resources(root, rows):
    (root / "assets/index.jsonl").write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")


def test_preflight_is_read_only_and_explicitly_not_import(package):
    root, _, _ = package
    before = {p.relative_to(root).as_posix(): p.read_bytes() for p in root.rglob("*") if p.is_file()}
    first = validate_package(root)
    assert first == validate_package(root)
    assert first["preflight_ok"] is True
    assert first["content_validated"] is False
    assert first["importable"] is False
    assert first["scope"] == "manifest_and_assets"
    assert first["assets"] == 1
    assert before == {p.relative_to(root).as_posix(): p.read_bytes() for p in root.rglob("*") if p.is_file()}


@pytest.mark.parametrize("field,value", [
    ("schema_version", 1), ("schema_version", True), ("package_id", "../evil"), ("name", " "),
    ("package_version", 1), ("created_at", "2026-11-10T10:00:00"), ("created_at", "2026-99-10T10:00:00Z"),
    ("entry", "../secrets.yaml"), ("entry", "C:/file"), ("entry", "curriculum\\course.yaml"),
    ("entry", "/etc/passwd"), ("entry", "curriculum/./course.yaml"), ("entry", "curriculum/other.yaml"),
    ("unknown", "value"),
])
def test_invalid_manifest_fields(package, field, value):
    root, manifest, _ = package
    manifest[field] = value
    write_manifest(root, manifest)
    with pytest.raises(PackageError):
        validate_package(root)


@pytest.mark.parametrize("requires", [
    {"world_id": "other", "world_schema_version": 1, "capabilities": []},
    {"world_id": "pythontopia-town", "world_schema_version": True, "capabilities": []},
    {"world_id": "pythontopia-town", "world_schema_version": 2, "capabilities": []},
    {"world_id": "pythontopia-town", "world_schema_version": 1, "capabilities": ["java"]},
    {"world_id": "pythontopia-town", "world_schema_version": 1, "capabilities": ["reading", "reading"]},
    {"world_id": "pythontopia-town", "world_schema_version": 1, "capabilities": [1]},
    {},
])
def test_world_and_capability_contract(package, requires):
    root, manifest, _ = package
    manifest["requires"] = requires
    write_manifest(root, manifest)
    with pytest.raises(PackageError):
        validate_package(root)


def test_timestamp_handling_and_order(package):
    root, manifest, _ = package
    manifest.update(updated_at="2026-11-10T02:00:00Z", exported_at="2026-11-10T03:00:00Z")
    write_manifest(root, manifest)
    path = root / "manifest.yaml"
    path.write_text(path.read_text(encoding="utf-8").replace("'2026-11-10T10:00:00+08:00'", "2026-11-10T10:00:00+08:00"), encoding="utf-8")
    assert validate_package(root)["preflight_ok"]
    manifest["updated_at"] = "2026-11-09T02:00:00Z"
    write_manifest(root, manifest)
    with pytest.raises(PackageError, match="早于"):
        validate_package(root)


@pytest.mark.parametrize("raw", [
    "schema_version: 1\nschema_version: 1\n",
    "data: &value [1]\ncopy: *value\n",
    "!!python/object/apply:os.system ['echo forbidden']",
    "[unclosed",
    "? [invalid, key]\n: value\n",
])
def test_rejects_unsafe_or_ambiguous_yaml(package, raw):
    root, _, _ = package
    (root / "manifest.yaml").write_text(raw, encoding="utf-8")
    with pytest.raises(PackageError):
        validate_package(root)


@pytest.mark.parametrize("field,value", [
    ("path", "../secret.txt"), ("path", "assets/../secret.txt"), ("path", "assets/texts/lesson.txt:secret"),
    ("path", "assets/texts/con.txt"), ("path", "assets/texts/lesson.txt."), ("path", "assets//texts/lesson.txt"),
    ("path", "assets/index.jsonl"), ("path", "assets/missing.txt"), ("id", ""),
    ("size_bytes", True), ("size_bytes", -1), ("size_bytes", 0), ("sha256", "0" * 64),
    ("sha256", "G" * 64), ("media_type", "application/pdf"), ("source", ""), ("license", None),
])
def test_invalid_resource_metadata(package, field, value):
    root, _, resource = package
    resource[field] = value
    write_resources(root, [resource])
    with pytest.raises(PackageError):
        validate_package(root)


@pytest.mark.parametrize("duplicate", ["id", "path"])
def test_duplicate_resource_identity(package, duplicate):
    root, _, resource = package
    second = resource.copy()
    if duplicate == "path":
        second["id"] = "another"
    write_resources(root, [resource, second])
    with pytest.raises(PackageError, match="重复"):
        validate_package(root)


def test_same_size_tampering_detected(package):
    root, _, _ = package
    path = root / "assets/texts/lesson.txt"
    path.write_bytes(b"x" * path.stat().st_size)
    with pytest.raises(PackageError, match="哈希"):
        validate_package(root)


def test_missing_entry_and_unlisted_asset(package):
    root, _, _ = package
    entry = root / "curriculum/course.yaml"
    entry.unlink()
    with pytest.raises(PackageError, match="缺少文件"):
        validate_package(root)
    entry.write_text("name: test", encoding="utf-8")
    (root / "assets/texts/unlisted.txt").write_text("x", encoding="utf-8")
    with pytest.raises(PackageError, match="未登记"):
        validate_package(root)


@pytest.mark.parametrize("raw", ["not-json\n", '{"id":"one","id":"two"}\n', "[]\n"])
def test_invalid_resource_json(package, raw):
    root, _, _ = package
    (root / "assets/index.jsonl").write_text(raw, encoding="utf-8")
    with pytest.raises(PackageError):
        validate_package(root)


@pytest.mark.parametrize("limit,value", [
    ("manifest_bytes", 10), ("index_bytes", 10), ("file_bytes", 10), ("total_bytes", 10),
    ("files", 2), ("assets", 0), ("depth", 0),
])
def test_limits_apply_before_large_processing(package, limit, value):
    with pytest.raises(PackageError):
        validate_package(package[0], limits=replace(PackageLimits(), **{limit: value}))


def test_symlink_is_rejected(package, tmp_path):
    root, _, _ = package
    outside = tmp_path / "outside.txt"
    outside.write_text("private", encoding="utf-8")
    link = root / "assets/texts/link.txt"
    try:
        link.symlink_to(outside)
    except OSError:
        pytest.skip("Host cannot create symbolic links")
    with pytest.raises(PackageError, match="链接|重解析"):
        validate_package(root)


def test_cli_does_not_import_database_or_touch_configuration(package):
    root, _, _ = package
    env = {**os.environ, "REBORN_DATABASE_URL": "invalid://must-not-be-read", "PYTHONIOENCODING": "utf-8"}
    result = subprocess.run([sys.executable, "-m", "app.course_package", "--check", str(root)],
                            cwd=Path(__file__).resolve().parents[1], env=env, capture_output=True, text=True, encoding="utf-8", check=False)
    assert result.returncode == 0, result.stderr
    assert json.loads(result.stdout)["importable"] is False


def test_cli_failure_is_structured_without_traceback(package, capsys):
    root, _, _ = package
    (root / "manifest.yaml").write_text("secret-field: private-data", encoding="utf-8")
    assert main(["--check", str(root)]) == 2
    captured = capsys.readouterr()
    assert not captured.out
    assert json.loads(captured.err)["preflight_ok"] is False
    assert "private-data" not in captured.err
    assert "Traceback" not in captured.err


def test_reparse_point_is_rejected_without_following_it(package, monkeypatch):
    from types import SimpleNamespace

    root, _, _ = package
    target = root / "assets/texts/lesson.txt"
    original = Path.lstat

    def marked_lstat(path):
        info = original(path)
        if path == target:
            return SimpleNamespace(st_mode=info.st_mode, st_size=info.st_size, st_file_attributes=0x400)
        return info

    monkeypatch.setattr(Path, "lstat", marked_lstat)
    with pytest.raises(PackageError, match="重解析"):
        validate_package(root)

@pytest.mark.parametrize("filename,raw", [
    ("manifest.yaml", "schema_version: " + "1" * 5000),
    ("assets/index.jsonl", '{"size_bytes":' + "1" * 5000 + '}'),
])
def test_oversized_integer_is_a_structured_failure(package, filename, raw, capsys):
    root, _, _ = package
    (root / filename).write_text(raw, encoding="utf-8")
    assert main(["--check", str(root)]) == 2
    captured = capsys.readouterr()
    assert not captured.out
    assert json.loads(captured.err)["preflight_ok"] is False
    assert "Traceback" not in captured.err


def test_surrogate_version_is_rejected_without_cli_encoding_failure(package, capsys):
    root, manifest, _ = package
    manifest["package_version"] = "\ud800"
    write_manifest(root, manifest)
    assert main(["--check", str(root)]) == 2
    assert json.loads(capsys.readouterr().err)["preflight_ok"] is False
