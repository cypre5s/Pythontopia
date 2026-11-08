"""Explicit, bounded import of the real course-43 media into current materials.

This operation never seeds tasks or rewrites learner records. Course JSON remains
the authority for page identity; archive metadata must match the trusted export.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import re
import tarfile
import tempfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath

from botocore.exceptions import ClientError
from pypdf import PdfReader
from pypdf.errors import PdfReadError
from sqlalchemy import select, text

from .config import ROOT
from .content_relations import material_in_chapter, set_material_chapters
from .db import SessionLocal
from .models import Chapter, Material
from .storage import get_storage, storage_root

MAX_ARCHIVE_BYTES = 1024 * 1024 * 1024
MAX_EXPANDED_BYTES = 1024 * 1024 * 1024
MAX_FILE_BYTES = 128 * 1024 * 1024
MAX_METADATA_BYTES = 2 * 1024 * 1024
MAX_MEMBERS = 96
SOURCE_KIND = "course_media_pack"


@dataclass(frozen=True)
class StagedFile:
    path: Path
    size: int
    sha256: str

    @property
    def key(self):
        return f"course-pack-43/{self.sha256}{self.path.suffix}"


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("媒体元数据包含重复字段")
        result[key] = value
    return result


def _json(raw):
    try:
        return json.loads(raw, object_pairs_hook=_unique_object,
                          parse_constant=lambda value: (_ for _ in ()).throw(ValueError("无效JSON数字")))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise ValueError("媒体元数据不是有效UTF-8 JSON") from exc


def _sources(source_root):
    root = Path(source_root) if source_root else Path(os.getenv("REBORN_COURSE_ROOT", str(ROOT / "data/course-pack-43")))
    docs = _json((root / "json/course_document.json").read_text(encoding="utf-8-sig"))
    pages = _json((root / "json/course_page.json").read_text(encoding="utf-8-sig"))
    manifest = _json((root / "video_manifest.json").read_text(encoding="utf-8-sig"))
    transcripts = _json((root / "video_transcripts.json").read_text(encoding="utf-8-sig"))
    if not isinstance(docs, list) or not docs or not isinstance(pages, list) or not isinstance(manifest, list) or not manifest:
        raise ValueError("课程来源清单无效")
    by_doc = {}
    page_ids = set()
    for doc in docs:
        if type(doc.get("id")) is not int or doc["id"] < 1 or doc["id"] in by_doc or type(doc.get("sort_order")) is not int:
            raise ValueError("课程文档ID或章节顺序无效")
        selected = [page for page in pages if page.get("document_id") == doc["id"]]
        if not selected or sorted(page.get("page_no", 0) for page in selected) != list(range(1, len(selected) + 1)):
            raise ValueError("课程来源页面不连续")
        for page in selected:
            if type(page.get("id")) is not int or page["id"] < 1 or page["id"] in page_ids:
                raise ValueError("课程来源页面ID无效或重复")
            page_ids.add(page["id"])
        by_doc[doc["id"]] = (doc, selected)
    if len(page_ids) != len(pages):
        raise ValueError("来源页面包含未知文档")
    videos = {}
    files = set()
    for video in manifest:
        duration = video.get("durationSec")
        if (type(video.get("id")) is not int or video["id"] < 1 or video["id"] in videos
                or type(video.get("chapter")) is not int or video["chapter"] < 1
                or not isinstance(video.get("title"), str) or not 1 <= len(video["title"]) <= 200
                or not isinstance(video.get("file"), str) or not re.fullmatch(r"[a-f0-9]{32}\.mp4", video["file"])
                or video["file"] in files or type(duration) not in {int, float} or not math.isfinite(duration)
                or not 0 < duration <= 86400):
            raise ValueError("视频来源标识、章节、时长或文件名无效")
        videos[video["id"]] = video
        files.add(video["file"])
    checksums = {}
    if not isinstance(transcripts, dict) or not isinstance(transcripts.get("videos"), list):
        raise ValueError("视频字幕来源清单无效")
    for transcript in transcripts["videos"]:
        video_id = transcript.get("videoId")
        video = videos.get(video_id)
        checksum = transcript.get("sourceSha256")
        if (not video or video_id in checksums or transcript.get("sourceFile") != video["file"]
                or not isinstance(checksum, str) or not re.fullmatch(r"[a-f0-9]{64}", checksum)):
            raise ValueError("视频校验和与来源清单不一致")
        checksums[video_id] = checksum
    if checksums.keys() != videos.keys():
        raise ValueError("字幕清单缺少视频校验和")
    return by_doc, videos, checksums, manifest, transcripts


def _stage_archive(archive, directory, documents, videos, checksums, manifest, transcripts):
    archive = Path(archive)
    if not archive.is_file() or archive.stat().st_size > MAX_ARCHIVE_BYTES:
        raise ValueError("媒体包不存在或超过1GiB限制")
    expected = {f"course-pack-43/pdfs/{key}.pdf" for key in documents}
    expected.update(f"course-pack-43/videos/{video['file']}" for video in videos.values())
    metadata = {"course-pack-43/video_manifest.json": manifest, "course-pack-43/video_transcripts.json": transcripts}
    expected.update(metadata)
    staged, seen = {}, set()
    total, members = 0, 0
    try:
        with tarfile.open(archive, mode="r|gz") as stream:
            for member in stream:
                members += 1
                path = PurePosixPath(member.name)
                name = str(path)
                if (members > MAX_MEMBERS or path.is_absolute() or ".." in path.parts or "\\" in member.name
                        or re.match(r"^[A-Za-z]:", member.name) or not path.parts or path.parts[0] != "course-pack-43"):
                    raise ValueError("媒体包包含非法路径或过多成员")
                if name in seen:
                    raise ValueError("媒体包包含重复路径")
                seen.add(name)
                if member.isdir():
                    if name not in {"course-pack-43", "course-pack-43/pdfs", "course-pack-43/videos"}:
                        raise ValueError("媒体包包含未知目录")
                    continue
                if not member.isfile() or member.issym() or member.islnk() or name not in expected:
                    raise ValueError("媒体包包含链接、特殊文件或未知文件")
                limit = MAX_METADATA_BYTES if name in metadata else MAX_FILE_BYTES
                total += member.size
                if not 0 < member.size <= limit or total > MAX_EXPANDED_BYTES:
                    raise ValueError("媒体包文件或解压总量超过限制")
                destination = directory / f"{len(staged)}{path.suffix}"
                digest = hashlib.sha256()
                size = 0
                with stream.extractfile(member) as source, destination.open("wb") as target:
                    while chunk := source.read(1024 * 1024):
                        size += len(chunk)
                        if size > member.size:
                            raise ValueError("媒体文件声明大小无效")
                        digest.update(chunk)
                        target.write(chunk)
                if size != member.size:
                    raise ValueError("媒体文件不完整")
                staged[name] = StagedFile(destination, size, digest.hexdigest())
                if name in metadata and _json(destination.read_text(encoding="utf-8-sig")) != metadata[name]:
                    raise ValueError("包内媒体清单与可信课程来源不一致")
    except (tarfile.TarError, EOFError, OSError) as exc:
        raise ValueError("媒体压缩包损坏或无法完整读取") from exc
    if staged.keys() != expected:
        raise ValueError("媒体包文件不齐全")
    for doc_id, (_, pages) in documents.items():
        item = staged[f"course-pack-43/pdfs/{doc_id}.pdf"]
        try:
            with item.path.open("rb") as source:
                if not source.read(5) == b"%PDF-":
                    raise ValueError("PDF文件签名无效")
            reader = PdfReader(item.path)
            if reader.is_encrypted or len(reader.pages) != len(pages):
                raise ValueError("PDF页数与原课程页面不一致或文件已加密")
        except (ValueError, OSError, PdfReadError) as exc:
            raise ValueError(f"资料{doc_id}不是对应课程的完整PDF") from exc
    for video_id, video in videos.items():
        item = staged[f"course-pack-43/videos/{video['file']}"]
        with item.path.open("rb") as source:
            header = source.read(16)
        if header[4:8] != b"ftyp" or item.sha256 != checksums[video_id]:
            raise ValueError(f"视频{video_id}文件签名或SHA256校验失败")
    return staged


def _store_file(storage, item):
    try:
        size = storage.size(item.key)
    except FileNotFoundError:
        size = None
    except ClientError as exc:
        if str(exc.response.get("Error", {}).get("Code")) not in {"404", "NoSuchKey", "NotFound"}:
            raise
        size = None
    if size == item.size:
        digest = hashlib.sha256()
        for chunk in storage.chunks(item.key, 0, size - 1):
            digest.update(chunk)
        if digest.hexdigest() == item.sha256:
            return
    if size is not None:
        raise ValueError("私有存储中已有同键文件校验失败；拒绝覆盖，请先修复存储对象")
    storage.put(item.key, item.path)


def _lock_materials(db):
    if db.get_bind().dialect.name == "postgresql":
        # Explicit course IDs do not advance PostgreSQL's sequence. Serialize
        # imports and normal inserts before allocating IDs for supplemental files.
        db.execute(text("LOCK TABLE materials IN SHARE ROW EXCLUSIVE MODE"))
        db.execute(text("""
            SELECT setval(pg_get_serial_sequence('materials', 'id'),
                GREATEST(COALESCE((SELECT MAX(id) FROM materials), 1),
                         nextval(pg_get_serial_sequence('materials', 'id'))), true)
        """))


def import_media(db, archive, *, source_root=None, storage=None):
    """Validate the complete package, then update only current material rows.

    Failed imports roll back all database changes. Uploaded hash-addressed files
    are inert until referenced by a committed Material; no history is overwritten.
    """
    storage = storage or get_storage()
    documents, videos, checksums, manifest, transcripts = _sources(source_root)
    with tempfile.TemporaryDirectory(prefix="course-media-", dir=storage_root()) as tmp:
        staged = _stage_archive(archive, Path(tmp), documents, videos, checksums, manifest, transcripts)
        try:
            _lock_materials(db)
            chapters = {c.order_index: c for c in db.scalars(select(Chapter).where(Chapter.course_id == 43, Chapter.owner_id.is_(None)))}
            materials = list(db.scalars(select(Material)))
            by_id = {m.id: m for m in materials}
            pdf_targets = {}
            for doc_id, (doc, pages) in documents.items():
                material = by_id.get(doc_id)
                chapter = chapters.get(doc["sort_order"])
                if (not material or not chapter or not material_in_chapter(db, material.id, chapter.id) or material.owner_id is not None
                        or material.kind not in {"text", "pdf", "presentation"}):
                    raise ValueError(f"资料{doc_id}未正确导入课程或已被其他所有者占用")
                existing_pages = material.content.get("pages", [])
                expected_pages = {(p["id"], p["page_no"]) for p in pages}
                if (not isinstance(existing_pages, list) or any(not isinstance(p, dict) or type(p.get("id")) is not int
                        or type(p.get("page_no")) is not int for p in existing_pages)):
                    raise ValueError(f"资料{doc_id}原页面记录无效")
                actual_pages = [(p.get("id"), p.get("page_no")) for p in existing_pages]
                if len(actual_pages) != len(expected_pages) or set(actual_pages) != expected_pages:
                    raise ValueError(f"资料{doc_id}原页面ID不匹配，拒绝覆盖学习依据")
                pdf_targets[doc_id] = material
            video_targets = {}
            for video_id, video in videos.items():
                if video["chapter"] not in chapters:
                    raise ValueError(f"视频{video_id}对应章节不存在")
                matches = [m for m in materials if isinstance(m.content.get("source"), dict)
                           and m.content["source"].get("kind") == SOURCE_KIND
                           and m.content["source"].get("course_id") == 43 and m.content["source"].get("video_id") == video_id]
                if len(matches) > 1 or any(m.owner_id is not None or m.kind != "video" for m in matches):
                    raise ValueError(f"视频{video_id}导入标识重复或被占用")
                video_targets[video_id] = matches[0] if matches else None
            # Only validated files enter private storage. Hash keys make retries
            # idempotent and prevent a partial import replacing a previous file.
            for name, item in staged.items():
                if name.endswith((".pdf", ".mp4")):
                    _store_file(storage, item)
            for doc_id, material in pdf_targets.items():
                item = staged[f"course-pack-43/pdfs/{doc_id}.pdf"]
                content = {**material.content, "filename": f"{doc_id}.pdf", "size": item.size, "sha256": item.sha256,
                           "library_available": True, "source": {"kind": SOURCE_KIND, "course_id": 43, "document_id": doc_id}}
                content.pop("notice", None)
                content.pop("preview_key", None)
                material.kind, material.storage_key, material.status, material.error = "pdf", item.key, "ready", None
                material.content = content
            for video_id, video in videos.items():
                item = staged[f"course-pack-43/videos/{video['file']}"]
                material = video_targets[video_id]
                if material is None:
                    material = Material()
                    db.add(material)
                material.title = video["title"]
                material.kind, material.owner_id, material.status, material.error = "video", None, "ready", None
                material.storage_key = item.key
                material.content = {"filename": video["file"], "size": item.size, "sha256": item.sha256, "pages": [],
                                    "library_available": True, "duration_seconds": video["durationSec"],
                                    "source": {"kind": SOURCE_KIND, "course_id": 43, "video_id": video_id}}
                db.flush()
                set_material_chapters(db, material.id, [chapters[video["chapter"]].id])
            db.commit()
        except Exception:
            db.rollback()
            raise
    return {"pdfs": len(documents), "pages": sum(len(pages) for _, pages in documents.values()),
            "videos": len(videos)}


def main():
    parser = argparse.ArgumentParser(description="Import verified course-43 PDF/video files without changing learner data")
    parser.add_argument("archive", type=Path)
    parser.add_argument("--source-root", type=Path, help="Trusted external export and media-manifest directory")
    args = parser.parse_args()
    with SessionLocal() as db:
        try:
            print(json.dumps(import_media(db, args.archive, source_root=args.source_root), ensure_ascii=False))
        except (ValueError, OSError) as exc:
            parser.exit(1, f"媒体导入失败：{exc}\n")


if __name__ == "__main__":
    main()
