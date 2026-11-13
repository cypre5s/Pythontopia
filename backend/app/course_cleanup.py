"""Bounded cleanup of private course exchange scratch data."""
import asyncio
from contextlib import contextmanager
import logging
import os
from pathlib import Path
import re
import shutil
import stat
import time

from sqlalchemy import or_, select

from .course_exchange_models import CourseImportPreview
from .db import SessionLocal
from .models import now

logger = logging.getLogger(__name__)
INTERVAL_SECONDS = 600
ORPHAN_SECONDS = 24 * 60 * 60
TEMP_NAME = re.compile(r"course-(?:catalog|apply|preview|export)-[a-z0-9_]{8}$")
IMPORT_NAME = re.compile(r"[0-9a-f]{32}(?:\.zip)?$")


def _plain(path):
    info = path.lstat()
    if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400:
        raise OSError("Linked exchange paths cannot be cleaned")
    return info


@contextmanager
def exchange_guard(root, *, blocking=True):
    """Coordinate API writers and cleanup across threads and worker processes."""
    lock = root / ".exchange.lock"
    if lock.exists() or lock.is_symlink():
        _plain(lock)
    with lock.open("a+b") as stream:
        if stream.tell() == 0:
            stream.write(b"0")
            stream.flush()
        stream.seek(0)
        acquired = False
        try:
            while not acquired:
                try:
                    if os.name == "nt":
                        import msvcrt
                        msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
                    else:
                        import fcntl
                        fcntl.flock(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                    acquired = True
                except BlockingIOError:
                    if not blocking:
                        yield False
                        return
                    time.sleep(0.1)
                except OSError as exc:
                    if os.name != "nt" or exc.errno not in (13, 36):
                        raise
                    if not blocking:
                        yield False
                        return
                    time.sleep(0.1)
            yield True
        finally:
            if acquired:
                stream.seek(0)
                if os.name == "nt":
                    import msvcrt
                    msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
                else:
                    import fcntl
                    fcntl.flock(stream.fileno(), fcntl.LOCK_UN)


def remove_scratch(root, path):
    """Delete only resolved descendants, rejecting links including Windows junctions."""
    root = root.resolve()
    if path == root or not path.resolve().is_relative_to(root):
        raise OSError("Exchange cleanup path outside root")
    current = path
    while current != root:
        if current.exists() or current.is_symlink():
            _plain(current)
        current = current.parent
    if not path.exists():
        return
    if path.is_dir():
        for directory, dirs, files in os.walk(path, followlinks=False):
            for name in dirs + files:
                _plain(Path(directory) / name)
        shutil.rmtree(path)
    else:
        path.unlink()


def cleanup_exchange(root, session_factory=SessionLocal):
    removed = 0
    with exchange_guard(root, blocking=False) as acquired:
        if not acquired:
            return 0
        with session_factory() as db:
            previews = db.scalars(select(CourseImportPreview).where(
                CourseImportPreview.storage_key != "",
                or_(CourseImportPreview.applied_request_id.is_not(None),
                    CourseImportPreview.expires_at <= now()),
            ).order_by(CourseImportPreview.expires_at).limit(100)).all()
            for preview in previews:
                key = Path(preview.storage_key)
                if len(key.parts) != 2 or key.parts[0] != "imports" or key.name in (".", ".."):
                    logger.warning("Skipped invalid course preview cleanup key")
                    continue
                try:
                    remove_scratch(root, root / key)
                    preview.storage_key = ""
                    db.commit()
                    removed += 1
                except OSError:
                    db.rollback()
                    logger.exception("Course preview cleanup will retry")
            referenced = set(db.scalars(select(CourseImportPreview.storage_key).where(
                CourseImportPreview.storage_key != "")))
            cutoff = time.time() - ORPHAN_SECONDS
            candidates = list(root.iterdir())
            imports = root / "imports"
            if imports.exists():
                _plain(imports)
                candidates.extend(imports.iterdir())
            for path in candidates:
                relative = path.relative_to(root).as_posix()
                owned = (path.parent == root and TEMP_NAME.fullmatch(path.name)) or (
                    path.parent == imports and IMPORT_NAME.fullmatch(path.name) and relative not in referenced)
                if not owned:
                    continue
                try:
                    if _plain(path).st_mtime > cutoff:
                        continue
                    remove_scratch(root, path)
                    removed += 1
                except OSError:
                    logger.exception("Orphan course scratch cleanup will retry")
    if removed:
        logger.info("Cleaned %s course exchange scratch entries", removed)
    return removed


async def cleanup_worker():
    from .course_exchange import exchange_root
    while True:
        try:
            await asyncio.to_thread(cleanup_exchange, exchange_root())
            from .character_assets import cleanup_character_assets
            await asyncio.to_thread(cleanup_character_assets)
        except Exception:
            logger.exception("Course exchange cleanup failed; retrying next cycle")
        await asyncio.sleep(INTERVAL_SECONDS)
