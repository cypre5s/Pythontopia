"""Read-only manifest/resource preflight. This does not authorize or import a course."""

import argparse
from dataclasses import dataclass
from datetime import datetime
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import stat
import sys

import yaml
from yaml.tokens import AliasToken, AnchorToken


@dataclass(frozen=True)
class PackageLimits:
    manifest_bytes: int = 512 * 1024
    index_bytes: int = 16 * 1024 * 1024
    file_bytes: int = 2 * 1024 * 1024 * 1024
    total_bytes: int = 8 * 1024 * 1024 * 1024
    files: int = 20000
    assets: int = 10000
    depth: int = 32


WORLD_ID = "pythontopia-town"
WORLD_SCHEMA_VERSION = 1
CAPABILITIES = frozenset({"reading", "video", "practice", "assessment", "dialogue", "tasks", "library"})
_IDENTIFIER = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,99}\Z")
_SHA256 = re.compile(r"[0-9a-f]{64}\Z")
_TIMESTAMP = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})\Z")
_MEDIA = {
    ".pdf": "application/pdf",
    ".pptx": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    ".ppt": "application/vnd.ms-powerpoint",
    ".mp4": "video/mp4",
    ".webm": "video/webm",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".webp": "image/webp",
    ".txt": "text/plain",
    ".md": "text/markdown",
    ".vtt": "text/vtt",
    ".json": "application/json",
    ".jsonl": "application/x-ndjson",
}
_RESERVED = re.compile(r"(?:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\..*)?\Z", re.IGNORECASE)


class PackageError(ValueError):
    """Invalid or unsupported package, with no file contents in the error."""


class ManifestLoader(yaml.SafeLoader):
    # Preserve ISO timestamps as strings without changing the global SafeLoader.
    yaml_implicit_resolvers = {
        key: [(tag, pattern) for tag, pattern in values if tag != "tag:yaml.org,2002:timestamp"]
        for key, values in yaml.SafeLoader.yaml_implicit_resolvers.items()
    }


def _unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        if not isinstance(key, str) or key in result:
            raise PackageError("字段名必须为字符串且不能重复")
        result[key] = value
    return result


def _mapping(loader, node):
    return _unique_pairs((loader.construct_object(key), loader.construct_object(value)) for key, value in node.value)


ManifestLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, _mapping)


def _relative(value):
    if not isinstance(value, str) or not value or len(value) > 512 or "\\" in value:
        raise PackageError("文件路径必须为包内POSIX相对路径")
    parts = value.split("/")
    if any(not p or p in {".", ".."} or p.endswith((" ", ".")) or _RESERVED.fullmatch(p)
           or any(ord(c) < 32 or 0xD800 <= ord(c) <= 0xDFFF or c in '<>:"|?*' for c in p) for p in parts):
        raise PackageError("文件路径包含不安全或不可移植的部分")
    if PurePosixPath(value).is_absolute():
        raise PackageError("文件路径不能是绝对路径")
    return value


def _inventory(root, limits):
    files, folded, total = {}, set(), 0
    pending = [(root, 0)]
    entries = 0
    while pending:
        folder, depth = pending.pop()
        if depth > limits.depth:
            raise PackageError("包目录嵌套过深")
        for item in folder.iterdir():
            entries += 1
            if entries > limits.files:
                raise PackageError("包目录条目过多")
            relative = _relative(item.relative_to(root).as_posix())
            canonical = relative.casefold()
            if canonical in folded:
                raise PackageError("包中路径大小写冲突")
            folded.add(canonical)
            info = item.lstat()
            if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400:
                raise PackageError("包中不能包含符号链接或重解析点")
            if stat.S_ISDIR(info.st_mode):
                pending.append((item, depth + 1))
            elif stat.S_ISREG(info.st_mode):
                if info.st_size > limits.file_bytes:
                    raise PackageError("包中文件超过单文件限制")
                total += info.st_size
                if total > limits.total_bytes:
                    raise PackageError("包超过总大小限制")
                files[relative] = info.st_size
            else:
                raise PackageError("包中只能包含普通文件和目录")
    return files, total


def _read_text(root, path, files, maximum):
    if path not in files:
        raise PackageError(f"缺少文件：{path}")
    if files[path] > maximum:
        raise PackageError(f"配置文件过大：{path}")
    with (root / path).open("rb") as stream:
        raw = stream.read(maximum + 1)
    if len(raw) > maximum:
        raise PackageError(f"配置文件过大：{path}")
    try:
        return raw.decode("utf-8-sig")
    except UnicodeDecodeError as exc:
        raise PackageError(f"配置文件必须为UTF-8：{path}") from exc


def _fields(data, required, optional=frozenset()):
    if not isinstance(data, dict) or set(data) - required - optional or required - set(data):
        raise PackageError("配置缺少必填字段或包含未知字段")


def _string(value, label, maximum=200):
    if not isinstance(value, str) or not value.strip() or len(value) > maximum or any(0xD800 <= ord(c) <= 0xDFFF for c in value):
        raise PackageError(f"{label}必须为非空字符串且长度不超过{maximum}")
    return value


def _identifier(value, label):
    if not isinstance(value, str) or not _IDENTIFIER.fullmatch(value):
        raise PackageError(f"{label}必须为最多100位字母、数字、点、下划线或连字符标识")


def _timestamp(value):
    if not isinstance(value, str) or not _TIMESTAMP.fullmatch(value):
        raise PackageError("时间必须为带时区的ISO8601字符串")
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise PackageError("时间值无效") from exc


def _manifest(raw):
    try:
        for index, token in enumerate(yaml.scan(raw)):
            if index > 20000 or isinstance(token, (AliasToken, AnchorToken)):
                raise PackageError("manifest不支持锚点/别名或过多配置项")
        data = yaml.load(raw, Loader=ManifestLoader)
    except PackageError:
        raise
    except (yaml.YAMLError, RecursionError, ValueError) as exc:
        raise PackageError("manifest YAML格式无效") from exc
    _fields(data, {"schema_version", "package_id", "name", "package_version", "created_at", "requires", "entry"},
            {"updated_at", "exported_at"})
    if type(data["schema_version"]) is not int or data["schema_version"] != 2:
        raise PackageError("仅支持v2课程包，请使用新版角色格式")
    _identifier(data["package_id"], "package_id")
    _string(data["name"], "name")
    _string(data["package_version"], "package_version", 100)
    previous = _timestamp(data["created_at"])
    for key in ("updated_at", "exported_at"):
        if key in data:
            current = _timestamp(data[key])
            if current < previous:
                raise PackageError("更新时间/导出时间不能早于之前的时间")
            previous = current
    requires = data["requires"]
    _fields(requires, {"world_id", "world_schema_version", "capabilities"})
    if requires["world_id"] != WORLD_ID or type(requires["world_schema_version"]) is not int or requires["world_schema_version"] != WORLD_SCHEMA_VERSION:
        raise PackageError("不支持的世界资源契约")
    capabilities = requires["capabilities"]
    if not isinstance(capabilities, list) or any(not isinstance(item, str) for item in capabilities):
        raise PackageError("capabilities必须为字符串列表")
    if len(capabilities) != len(set(capabilities)) or set(capabilities) - CAPABILITIES:
        raise PackageError("能力清单重复或包含不支持的能力")
    if _relative(data["entry"]) != "curriculum/course.yaml":
        raise PackageError("格式1的entry必须为curriculum/course.yaml")
    return data


def _assets(root, files, limits):
    raw = _read_text(root, "assets/index.jsonl", files, limits.index_bytes)
    ids, paths = set(), set()
    for line_number, line in enumerate(raw.splitlines(), 1):
        if not line.strip():
            continue
        if len(ids) >= limits.assets:
            raise PackageError("资源条目过多")
        try:
            row = json.loads(line, object_pairs_hook=_unique_pairs)
        except PackageError:
            raise
        except (ValueError, RecursionError) as exc:
            raise PackageError(f"资源清单第{line_number}行JSON无效") from exc
        _fields(row, {"id", "path", "media_type", "size_bytes", "sha256", "source", "license"}, {"character_kind", "frame_width", "frame_height", "width", "height", "original_name"})
        _identifier(row["id"], "资源id")
        path = _relative(row["path"])
        if not path.startswith("assets/") or path == "assets/index.jsonl":
            raise PackageError("资源路径必须指向assets内的媒体文件")
        if row["id"] in ids or path.casefold() in paths:
            raise PackageError("资源ID或路径重复")
        if path not in files:
            raise PackageError(f"资源文件缺失：{path}")
        if type(row["size_bytes"]) is not int or row["size_bytes"] < 0 or row["size_bytes"] != files[path]:
            raise PackageError(f"资源大小不匹配：{path}")
        expected_type = _MEDIA.get(PurePosixPath(path).suffix.lower())
        if expected_type is None or row["media_type"] != expected_type:
            raise PackageError(f"资源类型不支持或与扩展名不符：{path}")
        _string(row["source"], "source", 2000)
        _string(row["license"], "license", 2000)
        if not isinstance(row["sha256"], str) or not _SHA256.fullmatch(row["sha256"]):
            raise PackageError("sha256必须为64位小写十六进制字符串")
        digest, size = hashlib.sha256(), 0
        with (root / path).open("rb") as stream:
            while chunk := stream.read(1024 * 1024):
                size += len(chunk)
                if size > files[path]:
                    raise PackageError(f"校验期间资源大小发生变化：{path}")
                digest.update(chunk)
        if size != files[path] or digest.hexdigest() != row["sha256"]:
            raise PackageError(f"资源哈希或大小不匹配：{path}")
        ids.add(row["id"])
        paths.add(path.casefold())
    actual = {path.casefold() for path in files if path.startswith("assets/") and path != "assets/index.jsonl"}
    if actual != paths:
        raise PackageError("assets中存在未登记文件")
    return len(ids)


def validate_package(directory, *, limits=PackageLimits()):
    """Validate a stationary, unpacked private directory; never import or write it.

    Content schemas, references, flow semantics, media decoding, licensing and
    classroom authorization are deliberately outside this first preflight step.
    """
    root = Path(directory).absolute()
    try:
        for part in (root, *root.parents):
            info = part.lstat()
            if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400:
                raise PackageError("包目录路径不能经过符号链接或重解析点")
        if not root.is_dir():
            raise PackageError("课程包必须为已解包目录")
        files, total = _inventory(root, limits)
        manifest = _manifest(_read_text(root, "manifest.yaml", files, limits.manifest_bytes))
        _read_text(root, manifest["entry"], files, limits.manifest_bytes)
        assets = _assets(root, files, limits)
    except OSError as exc:
        raise PackageError("无法读取课程包文件") from exc
    return {
        "preflight_ok": True,
        "scope": "manifest_and_assets",
        "content_validated": False,
        "importable": False,
        "package_id": manifest["package_id"],
        "package_version": manifest["package_version"],
        "files": len(files),
        "assets": assets,
        "bytes": total,
        "notice": "仅通过清单与资源预检；教学内容、引用、流程和班级权限尚未验证，未导入任何数据。",
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description="Read-only course manifest/resource preflight; no database or import.")
    parser.add_argument("--check", required=True, type=Path, metavar="DIRECTORY")
    args = parser.parse_args(argv)
    try:
        result = validate_package(args.check)
    except PackageError as exc:
        print(json.dumps({"preflight_ok": False, "error": str(exc)}, ensure_ascii=True), file=sys.stderr)
        return 2
    print(json.dumps(result, ensure_ascii=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
