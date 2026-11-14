"""Private current-material local/S3 storage and bounded material processing jobs."""

from __future__ import annotations

import asyncio
import hashlib
import json
import logging
import mimetypes
import multiprocessing
import os
import re
import shutil
import subprocess
import tempfile
import time
import httpx
import zipfile
from pathlib import Path
from typing import Annotated, Protocol
from uuid import uuid4

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, ConfigDict, field_validator
from sqlalchemy import select, update
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.orm import Session
from .auth import current_user, require_teacher
from .db import SessionLocal, get_db
from .teacher_scope import teacher_instance_id, require_scope, new_stable_id, commit_content
from .models import Activity, Chapter, CourseInstance, Material, User
from .content_relations import material_chapter_ids, set_material_chapters

router = APIRouter(tags=["materials"])
MAX_UPLOAD = int(os.getenv("REBORN_MAX_UPLOAD_BYTES", str(128 * 1024 * 1024)))
KINDS = {".txt": "text", ".md": "text", ".pdf": "pdf", ".ppt": "presentation", ".pptx": "presentation", ".mp4": "video", ".webm": "video"}


def storage_root() -> Path:
    path = Path(os.getenv("REBORN_STORAGE_ROOT", "./reborn-storage")).resolve()
    path.mkdir(parents=True, exist_ok=True)
    return path


def safe_key(key: str) -> str:
    if not re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9/_.-]{0,250}", key) or any(x in {"", ".", ".."} for x in key.split("/")):
        raise ValueError("非法存储键")
    return key


class StorageAdapter(Protocol):
    def delete(self, key: str) -> None: ...
    def put(self, key: str, source: Path) -> None: ...
    def copy_to(self, key: str, destination: Path) -> None: ...
    def size(self, key: str) -> int: ...
    def chunks(self, key: str, start: int, end: int): ...


class LocalStorage:
    def delete(self, key: str):
        self.path(key).unlink(missing_ok=True)

    def path(self, key: str) -> Path:
        root = storage_root()
        target = (root / safe_key(key)).resolve()
        if not target.is_relative_to(root):
            raise ValueError("存储路径越界")
        return target

    def put(self, key: str, source: Path):
        target = self.path(key)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)

    def copy_to(self, key: str, destination: Path):
        shutil.copyfile(self.path(key), destination)

    def size(self, key: str):
        return self.path(key).stat().st_size

    def chunks(self, key: str, start: int, end: int):
        with self.path(key).open("rb") as stream:
            stream.seek(start)
            remaining = end - start + 1
            while remaining > 0:
                chunk = stream.read(min(65536, remaining))
                if not chunk:
                    break
                remaining -= len(chunk)
                yield chunk


class S3Storage:
    def delete(self, key):
        self.client.delete_object(Bucket=self.bucket, Key=self.key(key))

    def __init__(self):
        import boto3

        self.client = boto3.client("s3", endpoint_url=os.getenv("REBORN_S3_ENDPOINT") or None)
        self.bucket = os.environ["REBORN_S3_BUCKET"]
        self.prefix = os.getenv("REBORN_S3_PREFIX", "reborn/").strip("/") + "/"
        if not self.prefix.startswith("reborn/"):
            raise RuntimeError("S3 prefix must remain inside reborn/")

    def key(self, key):
        return self.prefix + safe_key(key)

    def put(self, key, source):
        self.client.upload_file(str(source), self.bucket, self.key(key))

    def copy_to(self, key, destination):
        self.client.download_file(self.bucket, self.key(key), str(destination))

    def size(self, key):
        return self.client.head_object(Bucket=self.bucket, Key=self.key(key))["ContentLength"]

    def chunks(self, key, start, end):
        stream = self.client.get_object(Bucket=self.bucket, Key=self.key(key), Range=f"bytes={start}-{end}")["Body"]
        try:
            yield from stream.iter_chunks(chunk_size=65536)
        finally:
            stream.close()


def get_storage() -> StorageAdapter:
    mode = os.getenv("REBORN_STORAGE", "local")
    if mode == "s3":
        return S3Storage()
    if mode != "local":
        raise RuntimeError("REBORN_STORAGE must be local or s3")
    return LocalStorage()


def validate_upload(path: Path, suffix: str):
    with path.open("rb") as stream:
        header = stream.read(16)
    if suffix not in KINDS:
        raise HTTPException(415, "仅支持 PDF、PPT/PPTX、MP4/WebM 和 UTF-8 文本")
    if suffix == ".pdf" and not header.startswith(b"%PDF-"):
        raise HTTPException(415, "PDF 文件签名无效")
    if suffix == ".ppt" and not header.startswith(bytes.fromhex("d0cf11e0a1b11ae1")):
        raise HTTPException(415, "PPT 文件签名无效")
    if suffix == ".pptx":
        try:
            with zipfile.ZipFile(path) as archive:
                items = archive.infolist()
                if len(items) > 10000 or sum(x.file_size for x in items) > 256 * 1024 * 1024 or "ppt/presentation.xml" not in archive.namelist():
                    raise ValueError("无效或过大的 PPTX")
        except (ValueError, zipfile.BadZipFile) as exc:
            raise HTTPException(415, "PPTX 文件无效或解压大小过大") from exc
    if suffix == ".mp4" and header[4:8] != b"ftyp":
        raise HTTPException(415, "MP4 文件签名无效")
    if suffix == ".webm" and not header.startswith(bytes.fromhex("1a45dfa3")):
        raise HTTPException(415, "WebM 文件签名无效")
    if suffix in {".txt", ".md"}:
        try:
            with path.open(encoding="utf-8-sig") as stream:
                if "\x00" in stream.read(500000):
                    raise ValueError("binary")
        except (UnicodeError, ValueError) as exc:
            raise HTTPException(415, "文本必须使用 UTF-8 编码") from exc


@router.post("/teacher/materials/upload", status_code=201)
async def upload_material(
    user: Annotated[User, Depends(require_teacher)],
    db: Annotated[Session, Depends(get_db)],
    title: Annotated[str, Form()],
    file: Annotated[UploadFile, File()],
    material_id: Annotated[int | None, Form()] = None,
    chapter_ids: Annotated[str | None, Form()] = None,
    classroom_id: int | None = None,
):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    name = file.filename or ""
    if not name or "/" in name or "\\" in name or ":" in name or len(name) > 200 or not title.strip() or len(title) > 200:
        raise HTTPException(422, "文件名或标题无效")
    suffix = Path(name).suffix.lower()
    if suffix not in KINDS:
        raise HTTPException(415, "不支持此文件类型")
    from .curriculum import authoring_chapter

    selected_chapters = None
    if chapter_ids is not None:
        from .curriculum import validate_id_list
        try:
            selected_chapters = validate_id_list(json.loads(chapter_ids))
        except (ValueError, TypeError):
            raise HTTPException(422, "chapter_ids 必须是有效的章节ID JSON数组")
        for chapter_id in selected_chapters:
            authoring_chapter(db, user, chapter_id, instance_id)
    material = db.scalar(select(Material).where(Material.id == material_id).with_for_update()) if material_id else None
    if material_id and (not material or (material.owner_id != user.id and user.role != "admin")):
        raise HTTPException(403, "只能更新自己上传的资料")
    if material:
        require_scope(material, instance_id)
    if material and material.kind != KINDS[suffix]:
        raise HTTPException(422, "替换文件必须保留材料类型")
    if material and selected_chapters is not None:
        validate_material_links(db, material.id, selected_chapters)
    with tempfile.TemporaryDirectory(prefix="upload-", dir=storage_root()) as tmp:
        path = Path(tmp) / ("source" + suffix)
        size, digest = 0, hashlib.sha256()
        with path.open("wb") as output:
            while chunk := await file.read(65536):
                size += len(chunk)
                if size > MAX_UPLOAD:
                    raise HTTPException(413, "文件超过上传大小限制")
                digest.update(chunk)
                output.write(chunk)
        if size == 0:
            raise HTTPException(422, "文件为空")
        await asyncio.to_thread(validate_upload, path, suffix)
        key = f"materials/{uuid4().hex}{suffix}"
        await asyncio.to_thread(get_storage().put, key, path)
    if not material:
        material = Material(title=title.strip(), kind=KINDS[suffix], owner_id=user.id, course_instance_id=instance_id, stable_id=new_stable_id())
        db.add(material)
        db.flush()
    if selected_chapters is not None:
        set_material_chapters(db, material.id, selected_chapters)
    material.title = title.strip()
    material.status = "queued"
    material.error = None
    material.storage_key = key
    material.content = {"filename": name, "size": size, "sha256": digest.hexdigest(), "pages": []}
    try:
        commit_content(db, instance_id)
    except IntegrityError:
        db.rollback()
        raise HTTPException(409, "资料同时更新，请刷新后重试")
    return {"id": material.id, "status": material.status, "title": material.title}


def teacher_material(db, user, material_id, *, edit=False, instance_id=None):
    query = select(Material).where(Material.id == material_id)
    material = db.scalar(query.with_for_update() if edit else query)
    if not material:
        raise HTTPException(404, "资料不存在")
    require_scope(material, instance_id)
    if user.role != "admin" and material.owner_id not in ({user.id} if edit else {None, user.id}):
        raise HTTPException(403, "无权访问或编辑此资料")
    return material


def teacher_material_chapters(db, material, user):
    chapter_ids = material_chapter_ids(db, material.id)
    if user.role == "admin":
        return chapter_ids
    return list(db.scalars(select(Chapter.id).where(Chapter.id.in_(chapter_ids), Chapter.owner_id.is_(None) | (Chapter.owner_id == user.id))))


def teacher_material_payload(db, material, user):
    activities = select(Activity).where(Activity.material_id == material.id)
    if user.role != "admin":
        activities = activities.where(Activity.owner_id.is_(None) | (Activity.owner_id == user.id))
    return {
        "id": material.id, "title": material.title, "chapter_ids": teacher_material_chapters(db, material, user),
        "kind": material.kind, "status": material.status, "error": material.error,
        "owner_id": material.owner_id, "pages": len(material.content.get("pages", [])),
        "activities": [{"id": a.id, "title": a.title, "chapter_id": a.chapter_id}
                       for a in db.scalars(activities)],
    }


def validate_material_links(db, material_id, chapter_ids):
    if db.scalar(select(Activity).where(Activity.material_id == material_id, Activity.chapter_id.not_in(chapter_ids)).limit(1)):
        raise HTTPException(409, "资料仍被活动引用，不能移除所用章节")


@router.get("/teacher/materials")
def teacher_materials(user: Annotated[User, Depends(require_teacher)], db: Annotated[Session, Depends(get_db)], classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id)
    query = select(Material).where(Material.course_instance_id == instance_id)
    if user.role != "admin":
        query = query.where(Material.owner_id.is_(None) | (Material.owner_id == user.id))
    return [teacher_material_payload(db, material, user) for material in db.scalars(query.order_by(Material.id))]


@router.get("/teacher/materials/{material_id}")
def teacher_material_preview(material_id: int, user: Annotated[User, Depends(require_teacher)], db: Annotated[Session, Depends(get_db)], classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id)
    from .courses import material_payload

    material = teacher_material(db, user, material_id, instance_id=instance_id)
    result = material_payload(db, material, chapter_ids=teacher_material_chapters(db, material, user))
    result["owner_id"] = material.owner_id
    scope_query = f"?classroom_id={classroom_id}" if classroom_id is not None else ""
    result["media_url"] = f"/api/teacher/materials/{material.id}/file{scope_query}" if material.storage_key and material.status == "ready" else None
    return result


class MaterialUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: str | None = None
    chapter_ids: list[int] | None = None

    @field_validator("title")
    @classmethod
    def valid_title(cls, value):
        if value is None or not value.strip() or len(value) > 200:
            raise ValueError("资料标题必须为1至200个字符")
        return value.strip()

    @field_validator("chapter_ids", mode="before")
    @classmethod
    def valid_chapters(cls, value):
        from .curriculum import validate_id_list
        return validate_id_list(value)


@router.put("/teacher/materials/{material_id}")
def update_material(material_id: int, body: MaterialUpdate, user: Annotated[User, Depends(require_teacher)], db: Annotated[Session, Depends(get_db)], classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    from .curriculum import authoring_chapter

    material = teacher_material(db, user, material_id, edit=True, instance_id=instance_id)
    if "chapter_ids" in body.model_fields_set:
        for chapter_id in body.chapter_ids:
            authoring_chapter(db, user, chapter_id, instance_id)
        validate_material_links(db, material.id, body.chapter_ids)
        set_material_chapters(db, material.id, body.chapter_ids)
    if "title" in body.model_fields_set:
        material.title = body.title
    commit_content(db, instance_id)
    return teacher_material_payload(db, material, user)


@router.delete("/teacher/materials/{material_id}")
def delete_material(material_id: int, user: Annotated[User, Depends(require_teacher)], db: Annotated[Session, Depends(get_db)], classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    teacher_material(db, user, material_id, edit=True, instance_id=instance_id)
    raise HTTPException(409, "请先预览删除影响并完成二次确认")


def byte_range(value: str | None, size: int) -> tuple[int, int, bool]:
    if not value:
        return 0, max(0, size - 1), False
    match = re.fullmatch(r"bytes=(\d*)-(\d*)", value)
    if not match or not any(match.groups()) or size <= 0:
        raise HTTPException(416, "Range 无效", headers={"Content-Range": f"bytes */{size}"})
    left, right = match.groups()
    if not left:
        length = int(right)
        if length <= 0:
            raise HTTPException(416, "Range 无效", headers={"Content-Range": f"bytes */{size}"})
        start, end = max(0, size - length), size - 1
    else:
        start, end = int(left), min(int(right), size - 1) if right else size - 1
    if start > end or start >= size:
        raise HTTPException(416, "Range 越界", headers={"Content-Range": f"bytes */{size}"})
    return start, end, True


@router.get("/materials/{material_id}/file")
def material_file(
    material_id: int,
    classroom_id: int,
    request: Request,
    user: Annotated[User, Depends(current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    from .courses import authorize_material

    item = authorize_material(db, user, classroom_id, material_id)
    return stream_material(item, request)


@router.get("/teacher/materials/{material_id}/file")
def teacher_material_file(material_id: int, request: Request, user: Annotated[User, Depends(require_teacher)], db: Annotated[Session, Depends(get_db)], classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id)
    return stream_material(teacher_material(db, user, material_id, instance_id=instance_id), request)


def stream_material(item, request):
    if not item or not item.storage_key:
        raise HTTPException(404, "原媒体文件缺失")
    if item.status != "ready":
        raise HTTPException(409, "资料尚未就绪")
    key = item.content.get("preview_key", item.storage_key)
    adapter = get_storage()
    try:
        size = adapter.size(key)
    except (FileNotFoundError, OSError):
        raise HTTPException(404, "媒体文件缺失")
    start, end, partial = byte_range(request.headers.get("range"), size)
    headers = {
        "Accept-Ranges": "bytes",
        "Content-Length": str(end - start + 1),
        "Cache-Control": "private, no-store",
        "X-Content-Type-Options": "nosniff",
        "Content-Disposition": 'inline; filename="material' + Path(key).suffix + '"',
    }
    if partial:
        headers["Content-Range"] = f"bytes {start}-{end}/{size}"
    return StreamingResponse(
        adapter.chunks(key, start, end),
        status_code=206 if partial else 200,
        media_type=mimetypes.guess_type(key)[0] or "application/octet-stream",
        headers=headers,
    )


def _parse_in_process(source: str, output: str):
    # Parsing untrusted PDF/text is separate from request processing and killable.
    try:
        if os.name != "nt":
            import resource

            resource.setrlimit(resource.RLIMIT_AS, (512 * 1024 * 1024,) * 2)
            resource.setrlimit(resource.RLIMIT_CPU, (15, 15))
        path = Path(source)
        if path.suffix == ".pdf":
            from pypdf import PdfReader

            reader = PdfReader(path)
            if len(reader.pages) > 600:
                raise ValueError("PDF 超过600页处理限制")
            pages = [
                {"id": i + 1, "page_no": i + 1, "title": f"第{i + 1}页", "text": (page.extract_text() or "")[:40000]}
                for i, page in enumerate(reader.pages)
            ]
        else:
            text = path.read_text(encoding="utf-8-sig")
            if len(text) > 2_000_000:
                raise ValueError("文本超过200万字符限制")
            pages = [
                {"id": i + 1, "page_no": i + 1, "title": f"第{i + 1}页", "text": text[i * 6000 : (i + 1) * 6000]}
                for i in range(max(1, (len(text) + 5999) // 6000))
            ]
        Path(output).write_text(json.dumps({"pages": pages}, ensure_ascii=False), encoding="utf-8")
    except Exception:
        # Do not propagate raw parser exceptions or filesystem paths into UI.
        Path(output).write_text(json.dumps({"error": "资料解析失败：文件损坏、受保护或超过处理限制"}), encoding="utf-8")


def parse_bounded(path: Path, tmp: Path):
    output = tmp / "parsed.json"
    process = multiprocessing.get_context("spawn").Process(target=_parse_in_process, args=(str(path), str(output)))
    process.start()
    process.join(25)
    if process.is_alive():
        process.terminate()
        process.join(3)
        raise ValueError("资料解析超时")
    if not output.exists() or output.stat().st_size > 32 * 1024 * 1024:
        raise ValueError("资料解析进程失败或输出超过限制")
    content = json.loads(output.read_text(encoding="utf-8"))
    if content.get("error"):
        raise ValueError(content["error"])
    return content


def convert_presentation(source: Path, tmp: Path) -> Path:
    converter_url = os.getenv("REBORN_CONVERTER_URL", "").rstrip("/")
    if converter_url:
        token = os.getenv("REBORN_CONVERTER_TOKEN", "")
        if len(token) < 24:
            raise ValueError("私有转换服务token未配置")
        destination = tmp / "converted.pdf"
        started = time.monotonic()
        try:
            with source.open("rb") as data, httpx.Client(timeout=httpx.Timeout(70, connect=5), follow_redirects=False) as client:
                with client.stream(
                    "POST",
                    converter_url + "/convert",
                    headers={
                        "X-Convert-Token": token,
                        "X-Source-Type": source.suffix.lstrip("."),
                        "Content-Type": "application/octet-stream",
                        "Content-Length": str(source.stat().st_size),
                    },
                    content=data,
                ) as response:
                    response.raise_for_status()
                    size = 0
                    with destination.open("wb") as output:
                        for chunk in response.iter_bytes(65536):
                            size += len(chunk)
                            if size > MAX_UPLOAD or time.monotonic() - started > 75:
                                raise ValueError("转换输出过大或服务超时")
                            output.write(chunk)
            validate_upload(destination, ".pdf")
            return destination
        except (httpx.HTTPError, OSError, HTTPException) as exc:
            raise ValueError("隔离PPT转换服务失败、繁忙或超时，请检查处理服务") from exc
    image = os.getenv("REBORN_CONVERTER_IMAGE", "")
    if not image:
        raise ValueError("PPT转换器未配置：请配置经过审计的LibreOffice容器镜像")
    output = tmp / "converted"
    output.mkdir()
    container_name = "reborn-presentation-" + uuid4().hex
    macro_policy = (
        '<?xml version="1.0" encoding="UTF-8"?><oor:items xmlns:oor="http://openoffice.org/2001/registry">'
        '<item oor:path="/org.openoffice.Office.Common/Security/Scripting">'
        '<prop oor:name="MacroSecurityLevel" oor:op="fuse"><value>3</value></prop></item></oor:items>'
    )
    command = [
        "docker",
        "run",
        "--rm",
        "--name",
        container_name,
        "--label",
        "com.pythontopia.project=reborn",
        "--network=none",
        "--memory=512m",
        "--cpus=1",
        "--pids-limit=64",
        "--cap-drop=ALL",
        "--security-opt=no-new-privileges:true",
        "--read-only",
        "--env",
        "HOME=/tmp",
        "--env",
        "TMPDIR=/tmp",
        "--tmpfs",
        "/tmp:rw,noexec,nosuid,size=256m",
        "--mount",
        f"type=bind,src={source.parent},dst=/input,readonly",
        image,
        "timeout",
        "--signal=TERM",
        "--kill-after=5",
        "55s",
        "python3",
        "-c",
        "import os,subprocess,sys;from pathlib import Path;"
        "os.umask(0o022);source=Path(sys.argv[1]);output=Path(sys.argv[2]);output.mkdir();"
        "profile=Path('/tmp/lo/user');profile.mkdir(parents=True);(profile/'registrymodifications.xcu').write_text(sys.argv[4]);"
        "subprocess.run(['libreoffice','-env:UserInstallation=file:///tmp/lo','--headless','--convert-to','pdf','--outdir',str(output),str(source)],check=True,timeout=50,stdout=sys.stderr);"
        "pdf=output/(source.stem+'.pdf');assert 0<pdf.stat().st_size<=int(sys.argv[3]);sys.stdout.buffer.write(pdf.read_bytes())",
        "/input/" + source.name,
        "/tmp/output",
        str(MAX_UPLOAD),
        macro_policy,
    ]
    try:
        completed = subprocess.run(command, check=True, timeout=70, capture_output=True)
    except subprocess.TimeoutExpired as exc:
        # Killing the Docker client does not guarantee that its container stops.
        # Scope cleanup to the generated name of this one conversion job.
        try:
            subprocess.run(["docker", "rm", "--force", container_name], timeout=10, capture_output=True)
        except (OSError, subprocess.SubprocessError):
            pass
        raise ValueError("隔离PPT转换超时") from exc
    except (OSError, subprocess.SubprocessError) as exc:
        raise ValueError("隔离PPT转换失败；请检查转换服务") from exc
    result = output / (source.stem + ".pdf")
    if not 0 < len(completed.stdout) <= MAX_UPLOAD:
        raise ValueError("PPT转换输出为空或超过大小上限")
    result.write_bytes(completed.stdout)
    if not result.is_file() or not 0 < result.stat().st_size <= MAX_UPLOAD:
        raise ValueError("PPT转换未产生有效PDF")
    validate_upload(result, ".pdf")
    return result


def _material_course_lock(db, material_id):
    from .course_scope import lock_course

    instance = db.scalar(select(CourseInstance).join(Material, Material.course_instance_id == CourseInstance.id)
                         .where(Material.id == material_id))
    if instance:
        lock_course(db, instance.classroom_id)
    return instance.id if instance else None


def process_material(material_id: int):
    with SessionLocal() as db:
        instance_id = _material_course_lock(db, material_id)
        claimed = db.execute(
            update(Material).where(Material.id == material_id, Material.status == "queued")
            .values(status="processing").returning(Material.storage_key, Material.content)
        ).first()
        commit_content(db, instance_id if claimed else None)
        if not claimed:
            return
        key, content = claimed
        metadata = dict(content)
    try:
        with tempfile.TemporaryDirectory(prefix="parse-", dir=storage_root()) as tmp_name:
            tmp = Path(tmp_name)
            source = tmp / ("source" + Path(key).suffix)
            adapter = get_storage()
            adapter.copy_to(key, source)
            if source.suffix in {".mp4", ".webm"}:
                result = {"pages": [], "notice": "教师上传的视频；观看参与记录不等同于掌握"}
            else:
                if source.suffix in {".ppt", ".pptx"}:
                    source = convert_presentation(source, tmp)
                    preview_key = f"previews/{uuid4().hex}.pdf"
                    adapter.put(preview_key, source)
                    metadata["preview_key"] = preview_key
                result = parse_bounded(source, tmp)
        status, error = "ready", None
        metadata.update(result)
    except Exception as exc:
        status = "failed"
        error = str(exc) if isinstance(exc, ValueError) else "资料处理失败，请检查存储或处理依赖"
    with SessionLocal() as db:
        instance_id = _material_course_lock(db, material_id)
        # A replacement upload may have arrived while this file was processing.
        # The unique storage key identifies the claimed file, without retaining history.
        result = db.execute(update(Material).where(
            Material.id == material_id, Material.storage_key == key, Material.status == "processing"
        ).values(status=status, error=error, content=metadata))
        commit_content(db, instance_id if result.rowcount else None)


def _recover_material_jobs():
    with SessionLocal() as db:
        from .course_scope import lock_course

        instances = list(db.scalars(select(CourseInstance).join(Material, Material.course_instance_id == CourseInstance.id)
                                   .where(Material.status == "processing").distinct().order_by(CourseInstance.classroom_id)))
        for instance in instances:
            lock_course(db, instance.classroom_id)
        changed = list(db.scalars(update(Material).where(Material.status == "processing").values(status="queued")
                                 .returning(Material.course_instance_id)))
        for key in set(changed) - {None}:
            db.execute(update(CourseInstance).where(CourseInstance.id == key).values(revision=CourseInstance.revision + 1))
        db.commit()


def _material_job_ids():
    with SessionLocal() as db:
        return list(db.scalars(select(Material.id).where(Material.status == "queued").limit(5)))


async def material_worker():
    recovered, delay = False, 1
    while True:
        try:
            if not recovered:
                await asyncio.to_thread(_recover_material_jobs)
                recovered = True
            ids = await asyncio.to_thread(_material_job_ids)
            for material_id in ids:
                await asyncio.to_thread(process_material, material_id)
            delay = 1
        except SQLAlchemyError:
            logging.getLogger(__name__).warning("Material worker database unavailable; retrying in %ss", delay)
            recovered = False
            await asyncio.sleep(delay)
            delay = min(delay * 2, 30)
            continue
        await asyncio.sleep(2)
