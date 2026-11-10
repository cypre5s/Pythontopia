"""Discover operator-provided course packages without publishing filesystem paths."""

from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import stat
import zipfile

from .course_package import PackageError, _inventory, _manifest, _relative


@contextmanager
def regular_reader(path, root):
    """Read a regular file within the private exchange tree, rejecting links."""
    path = Path(path)
    boundary = Path(root).resolve()
    if not path.resolve().is_relative_to(boundary):
        raise PackageError("课程包路径越界")
    current = path
    while current != boundary:
        info = current.lstat()
        if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400:
            raise PackageError("课程包不能使用链接")
        current = current.parent
    before = path.lstat()
    if not stat.S_ISREG(before.st_mode):
        raise PackageError("课程包文件类型无效")
    descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0))
    with os.fdopen(descriptor, "rb") as stream:
        opened = os.fstat(stream.fileno())
        if not stat.S_ISREG(opened.st_mode) or (opened.st_dev, opened.st_ino) != (before.st_dev, before.st_ino):
            raise PackageError("课程包文件已变化，请刷新")
        yield stream


def mounted_catalog(root, limits, max_archive):
    """List only root-level ZIPs/package directories. Internal staging is private."""
    items, rejected = [], 0
    for index, path in enumerate(root.iterdir()):
        if index >= 1000:
            raise PackageError("课程交换目录条目过多，请管理员整理后重试")
        if path.name in {"imports", "exports"} or path.name.startswith((".", "course-preview-", "course-export-", "course-apply-", "course-catalog-")):
            continue
        try:
            _relative(path.name)
            info = path.lstat()
            if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400:
                continue
            if stat.S_ISDIR(info.st_mode):
                if not (path / "manifest.yaml").exists():
                    continue
                with regular_reader(path / "manifest.yaml", root) as stream:
                    raw = stream.read(limits.manifest_bytes + 1)
                kind = "directory"
            elif stat.S_ISREG(info.st_mode) and path.suffix.lower() == ".zip":
                if info.st_size > max_archive:
                    raise PackageError("课程ZIP过大")
                with regular_reader(path, root) as stream, zipfile.ZipFile(stream) as archive:
                    if len(archive.infolist()) > limits.files:
                        raise PackageError("课程ZIP条目过多")
                    members = [m for m in archive.infolist() if m.filename == "manifest.yaml"]
                    if len(members) != 1 or members[0].file_size > limits.manifest_bytes:
                        raise PackageError("课程包清单缺失或过大")
                    with archive.open(members[0]) as manifest_stream:
                        raw = manifest_stream.read(limits.manifest_bytes + 1)
                kind = "zip"
            else:
                continue
            if len(raw) > limits.manifest_bytes:
                raise PackageError("课程包清单过大")
            manifest = _manifest(raw.decode("utf-8-sig"))
            stamp = json.dumps([path.name, info.st_size, info.st_mtime_ns, hashlib.sha256(raw).hexdigest()])
            key = "mounted-" + hashlib.sha256(stamp.encode()).hexdigest()
            items.append({"key": key, "name": manifest["name"], "package_id": manifest["package_id"],
                          "package_version": manifest["package_version"], "source": "mounted", "path": path, "kind": kind})
        except (PackageError, OSError, UnicodeError, ValueError, zipfile.BadZipFile, RuntimeError, NotImplementedError):
            rejected += 1
    if len(items) > 200:
        raise PackageError("可选课程包超过200份，请管理员整理后重试")
    return sorted(items, key=lambda item: (item["name"], item["key"])), rejected


def copy_archive(source, destination, root, maximum):
    size = 0
    with regular_reader(source, root) as reader, destination.open("xb") as writer:
        while chunk := reader.read(1024 * 1024):
            size += len(chunk)
            if size > maximum:
                raise PackageError("课程压缩包超过上传限制")
            writer.write(chunk)


def archive_directory(source, destination, exchange_root, limits, max_archive):
    info = source.lstat()
    if not source.resolve().is_relative_to(exchange_root.resolve()) or not stat.S_ISDIR(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400:
        raise PackageError("课程包目录已变化，请刷新")
    files, _ = _inventory(source, limits)
    with zipfile.ZipFile(destination, "x", zipfile.ZIP_DEFLATED) as archive:
        for relative, expected in sorted(files.items()):
            with regular_reader(source / relative, exchange_root) as reader, archive.open(relative, "w", force_zip64=True) as writer:
                size = 0
                while chunk := reader.read(1024 * 1024):
                    size += len(chunk)
                    if size > expected:
                        raise PackageError("课程包文件已变化，请刷新")
                    writer.write(chunk)
                if size != expected:
                    raise PackageError("课程包文件已变化，请刷新")
            if destination.stat().st_size > max_archive:
                raise PackageError("课程压缩包超过上传限制")
    if destination.stat().st_size > max_archive:
        raise PackageError("课程压缩包超过上传限制")
