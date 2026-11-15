"""Bounded ZIP transport for synthetic test fixtures only; runtime accepts directories."""
from pathlib import Path
import stat
import zipfile
from app.course_packages.manifest import PackageError, PackageLimits, relative_package_path

LIMITS = PackageLimits()
MAX_ARCHIVE = LIMITS.total_bytes


def unpack_archive(archive, root):
    if archive.stat().st_size > MAX_ARCHIVE:
        raise PackageError("课程压缩包超过上传限制")
    try:
        with zipfile.ZipFile(archive) as source:
            members = source.infolist()
            if len(members) > LIMITS.files:
                raise PackageError("压缩包条目过多")
            seen, total = set(), 0
            for item in members:
                name = relative_package_path(item.orig_filename[:-1] if item.is_dir() else item.orig_filename)
                folded = name.casefold()
                if folded in seen or item.flag_bits & 1:
                    raise PackageError("压缩包包含重复路径或加密文件")
                seen.add(folded)
                mode = item.external_attr >> 16
                if stat.S_ISLNK(mode) or (stat.S_IFMT(mode) and not (stat.S_ISDIR(mode) or stat.S_ISREG(mode))):
                    raise PackageError("压缩包不能包含链接或特殊文件")
                total += item.file_size
                if item.file_size > LIMITS.file_bytes or total > LIMITS.total_bytes or len(Path(name).parts) > LIMITS.depth:
                    raise PackageError("压缩包解压大小或深度超限")
                target = root / name
                if item.is_dir():
                    target.mkdir(parents=True, exist_ok=True)
                    continue
                target.parent.mkdir(parents=True, exist_ok=True)
                size = 0
                with source.open(item) as reader, target.open("xb") as writer:
                    while chunk := reader.read(1024 * 1024):
                        size += len(chunk)
                        if size > item.file_size:
                            raise PackageError("压缩包文件大小不一致")
                        writer.write(chunk)
    except (zipfile.BadZipFile, RuntimeError, OSError, NotImplementedError) as exc:
        raise PackageError("课程ZIP文件无效或不可读取") from exc
