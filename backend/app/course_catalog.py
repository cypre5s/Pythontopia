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


def package_counts(path, limits):
    """Read chapter definitions and question indexes without scanning media."""
    counts = {}
    for key, pattern in (("chapter_count", "curriculum/chapters/*/chapter.yaml"),
                         ("question_count", "content/questions/*.jsonl")):
        count = total = 0
        for index, file in enumerate(path.glob(pattern)):
            if index >= limits.files:
                raise PackageError("课程目录条目过多")
            with regular_reader(file, path) as stream:
                raw = stream.read(limits.index_bytes + 1)
            total += len(raw)
            if total > limits.index_bytes:
                raise PackageError("课程目录索引过大")
            count += sum(bool(line.strip()) for line in raw.splitlines()) if key == "question_count" else 1
        counts[key] = count
    return counts


def mounted_catalog(root, limits, max_archive):
    """List root-level package directories. Internal staging is private."""
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
            else:
                continue
            if len(raw) > limits.manifest_bytes:
                raise PackageError("课程包清单过大")
            manifest = _manifest(raw.decode("utf-8-sig"))
            stamp = json.dumps([path.name, info.st_size, info.st_mtime_ns, hashlib.sha256(raw).hexdigest()])
            key = "mounted-" + hashlib.sha256(stamp.encode()).hexdigest()
            items.append({"key": key, "name": manifest["name"], "package_id": manifest["package_id"],
                          "package_version": manifest["package_version"], "source": "mounted", "path": path, "kind": kind,
                          "directory": path.name, "created_at": manifest.get("exported_at", manifest["created_at"]),
                          **package_counts(path, limits)})
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


def copy_directory(source, destination, root, limits):
    """Create a private, bounded copy without compression or following links."""
    source = Path(source)
    if not source.resolve().is_relative_to(root.resolve()) or source.is_symlink():
        raise PackageError("课程包目录越界或使用链接")
    files, _ = _inventory(source, limits)
    destination.mkdir()
    digest = hashlib.sha256()
    for relative, expected in sorted(files.items()):
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        digest.update(relative.encode())
        digest.update(b"\0")
        file_digest = hashlib.sha256()
        with regular_reader(source / relative, root) as reader, target.open("xb") as writer:
            size = 0
            while chunk := reader.read(1024 * 1024):
                size += len(chunk)
                if size > expected:
                    raise PackageError("课程包在读取期间发生变化，请重新预览")
                writer.write(chunk)
                file_digest.update(chunk)
        if size != expected:
            raise PackageError("课程包在读取期间发生变化，请重新预览")
        digest.update(file_digest.digest())
    return digest.hexdigest()


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
