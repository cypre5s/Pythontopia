"""Synthetic course-package fixtures; never opens the configured application database."""
import argparse
from contextlib import contextmanager
import json
import os
from pathlib import Path
import tempfile
from uuid import uuid4
import zipfile

from sqlalchemy import create_engine, event
from sqlalchemy.orm import Session

from app.course_packages.reader import load_bundle
from app.course_packages.writer import write_package
from fixture_archive import MAX_ARCHIVE
from fixture_instances import clone_current_course
from app.course_packages.manifest import PackageError
from app.db import Base
from app.models import Chapter, ChapterRelease, Classroom, User
from fixture_seed import seed
from app.materials.storage import get_storage


@contextmanager
def isolated_storage(root):
    settings = {"REBORN_STORAGE": "local", "REBORN_STORAGE_ROOT": str(root)}
    previous = {key: os.environ.get(key) for key in settings}
    os.environ.update(settings)
    try:
        yield
    finally:
        for key, value in previous.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value


def build_fixture_package(source, content, output, *, package_id, name, package_version="1", media_archive=None):
    """Build a bounded synthetic test package, with optional verified test media.

    Intended for an offline CLI process. Sources are read-only, output is exclusive,
    and all database rows/storage live in a temporary directory removed on exit.
    """
    source, content, output = Path(source).resolve(), Path(content).resolve(), Path(output).resolve()
    if not source.is_dir() or not content.is_dir():
        raise PackageError("必须明确提供存在的测试课程数据目录与教学脚本目录")
    if output.exists():
        raise PackageError("输出文件已存在，请使用新的路径")
    if output.suffix.lower() != ".zip":
        raise PackageError("测试交换文件必须是ZIP")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="fixture-package-", dir=output.parent) as temporary:
        root = Path(temporary)
        engine = create_engine(f"sqlite:///{(root / 'isolated.db').as_posix()}")
        @event.listens_for(engine, "connect")
        def foreign_keys(connection, _):
            connection.execute("PRAGMA foreign_keys=ON")
        try:
            Base.metadata.create_all(engine)
            with isolated_storage(root / "media"), Session(engine) as db:
                counts = seed(db, source=source, content_root=content)
                if media_archive is not None:
                    from app.course_media import import_media
                    import_media(db, Path(media_archive).resolve(), source_root=source, storage=get_storage())
                teacher = User(name="测试包临时教师", role="teacher", password_hash="offline-disabled-account")
                db.add(teacher)
                db.flush()
                classroom = Classroom(name=name, teacher_id=teacher.id, join_code=uuid4().hex[:24])
                db.add(classroom)
                db.flush()
                # Exported test curriculum remains unpublished until a teacher explicitly releases it.
                from sqlalchemy import select
                for chapter in db.scalars(select(Chapter)):
                    db.add(ChapterRelease(classroom_id=classroom.id, chapter_id=chapter.id, status="paused"))
                instance = clone_current_course(db, classroom, package_id=package_id, name=name, package_version=package_version)
                db.commit()
                package = root / "package"
                package.mkdir()
                write_package(db, instance, package)
                bundle = load_bundle(package)
                archive = root / "converted.zip"
                with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as writer:
                    for path in sorted(package.rglob("*")):
                        if path.is_file():
                            writer.write(path, path.relative_to(package).as_posix())
                if archive.stat().st_size > MAX_ARCHIVE:
                    raise PackageError("测试交换文件超过大小限制")
                # Both operations publish the complete same-volume file without
                # replacing an operator's concurrent output. Windows rename is
                # exclusive; POSIX hard-link has the same no-overwrite property.
                if os.name == "nt":
                    os.rename(archive, output)
                else:
                    os.link(archive, output)
                return {"output": str(output), "package_id": package_id, "counts": counts,
                        "assets": len(bundle["assets"]), "chapters_released": False,
                        "media": "verified_originals" if media_archive else "existing_extracted_text_only"}
        finally:
            engine.dispose()


def main():
    parser = argparse.ArgumentParser(description="构建合成测试课程包；仅使用临时SQLite，不连接运行数据库")
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--content", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--package-id", required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--package-version", default="1")
    parser.add_argument("--media-archive", type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(build_fixture_package(args.source, args.content, args.output, package_id=args.package_id,
                                        name=args.name, package_version=args.package_version, media_archive=args.media_archive), ensure_ascii=False))
    except (ValueError, OSError) as exc:
        parser.exit(1, f"测试课程包构建失败：{exc}\n")


if __name__ == "__main__":
    main()
