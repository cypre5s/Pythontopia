from conftest import make_material
import pytest
from fastapi import HTTPException
from app.storage import LocalStorage, byte_range, validate_upload, convert_presentation


def test_local_storage_rejects_traversal_and_symlink_escape(tmp_path, monkeypatch):
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "private"))
    adapter = LocalStorage()
    for key in ["../secret", "/etc/passwd", "a/../b", "C:/outside", "a//b"]:
        with pytest.raises(ValueError):
            adapter.path(key)
    source = tmp_path / "source.txt"
    source.write_text("private course text")
    adapter.put("materials/a.txt", source)
    assert b"".join(adapter.chunks("materials/a.txt", 8, 13)) == b"course"


@pytest.mark.parametrize(
    ("header", "expected"),
    [
        (None, (0, 99, False)),
        ("bytes=10-19", (10, 19, True)),
        ("bytes=90-", (90, 99, True)),
        ("bytes=-10", (90, 99, True)),
        ("bytes=95-999", (95, 99, True)),
    ],
)
def test_media_ranges(header, expected):
    assert byte_range(header, 100) == expected


@pytest.mark.parametrize("header", ["bytes=100-", "bytes=10-9", "bytes=-0", "bytes=1-2,3-4", "wat", "bytes=-"])
def test_invalid_ranges_are_416(header):
    with pytest.raises(HTTPException) as exc:
        byte_range(header, 100)
    assert exc.value.status_code == 416


def test_fake_pdf_rejected_and_utf8_text_accepted(tmp_path):
    path = tmp_path / "test.pdf"
    path.write_text("not a PDF")
    with pytest.raises(HTTPException):
        validate_upload(path, ".pdf")
    path = tmp_path / "test.txt"
    path.write_text("Python 语言基础", encoding="utf-8")
    validate_upload(path, ".txt")


def test_missing_ppt_converter_reports_failure(tmp_path, monkeypatch):
    monkeypatch.delenv("REBORN_CONVERTER_IMAGE", raising=False)
    monkeypatch.delenv("REBORN_CONVERTER_URL", raising=False)
    with pytest.raises(ValueError, match="PPT转换器未配置"):
        convert_presentation(tmp_path / "source.pptx", tmp_path)


def test_teacher_upload_processing_range_and_current_material_authorization(client, db, teacher, student, classroom, tmp_path, monkeypatch):
    from sqlalchemy import select
    from sqlalchemy.orm import sessionmaker
    from app import storage
    from app.models import Material, Activity

    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "private"))
    monkeypatch.setattr(storage, "SessionLocal", sessionmaker(db.bind, expire_on_commit=False))
    upload = client.post(
        "/api/teacher/materials/upload",
        data={"chapter_ids": "[179]", "title": "教师补充文本"},
        files={"file": ("lesson.txt", "教师上传的真实文本。Python 学习。".encode(), "text/plain")},
        headers=teacher["headers"],
    )
    assert upload.status_code == 201, upload.text
    material_id = upload.json()["id"]
    row = db.get(Material, material_id)
    storage.process_material(row.id)
    db.expire_all()
    assert db.get(Material, row.id).status == "ready"
    url = f"/api/materials/{material_id}/file?classroom_id={classroom['id']}"
    assert client.get(url, headers=student["headers"]).status_code == 403
    activity = Activity(
        id="teacher-reading",
        chapter_id=179,
        title="补充阅读",
        kind="lesson",
        material_id=material_id,
        npc_key="mayor_py",
        assistant_allowed=True,
        content={"published": True, "owner_id": teacher["id"]},
    )
    db.add(activity)
    db.flush()
    db.commit()
    response = client.get(url, headers={**student["headers"], "Range": "bytes=0-5"})
    assert response.status_code == 206, response.text
    assert response.headers["content-length"] == "6"
    assert "private" in response.headers["cache-control"]
    assert client.get(url).status_code == 401
    update = client.post(
        "/api/teacher/materials/upload",
        data={"chapter_ids": "[179]", "title": "更新", "material_id": material_id},
        files={"file": ("lesson.txt", b"updated content", "text/plain")},
        headers=teacher["headers"],
    )
    assert update.status_code == 201 and update.json()["id"] == material_id
    assert "version" not in update.json()
    assert client.get(url, headers=student["headers"]).status_code == 409
    storage.process_material(material_id)
    db.expire_all()
    response = client.get(url, headers=student["headers"])
    assert response.status_code == 200 and response.content == b"updated content"
    db.expire_all()
    assert db.get(Material, material_id).title == "更新"
    assert len(db.scalars(select(Material).where(Material.id == material_id)).all()) == 1


def test_material_worker_retries_database_and_recovers_queued_job(db, monkeypatch, caplog):
    import asyncio
    from sqlalchemy import select
    from sqlalchemy.exc import OperationalError
    from sqlalchemy.orm import sessionmaker
    from app import storage
    from app.models import Material

    row = db.scalar(select(Material))
    row.status = "processing"
    db.commit()
    factory = sessionmaker(db.bind, expire_on_commit=False)
    attempts, delays, processed = [], [], []

    def flaky_database():
        attempts.append(1)
        if len(attempts) == 1:
            raise OperationalError("connection", {}, RuntimeError("private-connection-value"))
        return factory()

    async def sleep(delay):
        delays.append(delay)

    def stop_after_job(material_id):
        processed.append(material_id)
        raise asyncio.CancelledError

    monkeypatch.setattr(storage, "SessionLocal", flaky_database)
    monkeypatch.setattr(storage.asyncio, "sleep", sleep)
    monkeypatch.setattr(storage, "process_material", stop_after_job)
    with pytest.raises(asyncio.CancelledError):
        asyncio.run(storage.material_worker())
    db.expire_all()
    assert delays == [1] and processed == [row.id]
    assert db.get(Material, row.id).status == "queued"
    assert "private-connection-value" not in caplog.text


def test_private_http_converter_adapter_auth_type_and_pdf_contract(tmp_path, monkeypatch):
    import io
    import httpx
    from pypdf import PdfReader, PdfWriter
    from app import storage

    output = io.BytesIO()
    writer = PdfWriter()
    writer.add_blank_page(width=100, height=100)
    writer.write(output)
    pdf = output.getvalue()
    source = tmp_path / "source.pptx"
    source.write_bytes(b"explicit-test-presentation-payload")
    real_client = httpx.Client

    def handler(request):
        assert str(request.url) == "http://private-converter.test/convert"
        assert request.headers["X-Convert-Token"] == "test-only-private-converter-token"
        assert request.headers["X-Source-Type"] == "pptx"
        assert request.read() == source.read_bytes()
        return httpx.Response(200, content=pdf, headers={"Content-Type": "application/pdf"})

    monkeypatch.setenv("REBORN_CONVERTER_URL", "http://private-converter.test")
    monkeypatch.setenv("REBORN_CONVERTER_TOKEN", "test-only-private-converter-token")
    monkeypatch.setattr(storage.httpx, "Client", lambda **kwargs: real_client(transport=httpx.MockTransport(handler), **kwargs))
    result = storage.convert_presentation(source, tmp_path)
    assert result.read_bytes() == pdf and len(PdfReader(result).pages) == 1
    monkeypatch.setattr(storage, "MAX_UPLOAD", 8)
    with pytest.raises(ValueError, match="过大"):
        storage.convert_presentation(source, tmp_path)


def test_private_http_converter_does_not_follow_redirects(tmp_path, monkeypatch):
    import httpx
    from app import storage

    source = tmp_path / "source.pptx"
    source.write_bytes(b"explicit-test-presentation-payload")
    real_client = httpx.Client
    requests = []

    def handler(request):
        requests.append(str(request.url))
        return httpx.Response(302, headers={"Location": "http://unauthorized-redirect.test/private"})

    monkeypatch.setenv("REBORN_CONVERTER_URL", "http://private-converter.test")
    monkeypatch.setenv("REBORN_CONVERTER_TOKEN", "test-only-private-converter-token")
    monkeypatch.setattr(storage.httpx, "Client", lambda **kwargs: real_client(transport=httpx.MockTransport(handler), **kwargs))
    with pytest.raises(ValueError, match="转换服务"):
        storage.convert_presentation(source, tmp_path)
    assert requests == ["http://private-converter.test/convert"]


def test_image_fallback_keeps_host_read_only_and_applies_isolation(tmp_path, monkeypatch):
    import subprocess
    from app import storage

    source = tmp_path / "self authored.pptx"
    source.write_bytes(b"explicit-test-presentation")
    monkeypatch.delenv("REBORN_CONVERTER_URL", raising=False)
    monkeypatch.setenv("REBORN_CONVERTER_IMAGE", "pythontopia-reborn-converter:test-only")
    pdf = b"%PDF-1.4\nexplicit-test-output\n%%EOF"

    def fake_process(command, **kwargs):
        assert command[:3] == ["docker", "run", "--rm"]
        assert "--network=none" in command and "--cap-drop=ALL" in command
        assert "--security-opt=no-new-privileges:true" in command and "--read-only" in command
        assert "HOME=/tmp" in command and "TMPDIR=/tmp" in command
        mounts = [arg for arg in command if arg.startswith("type=bind,")]
        assert len(mounts) == 1 and mounts[0].endswith("dst=/input,readonly")
        assert "timeout" in command and "55s" in command
        assert kwargs["timeout"] == 70 and kwargs.get("shell") is not True
        assert command[command.index("--name") + 1].startswith("reborn-presentation-")
        return subprocess.CompletedProcess(command, 0, stdout=pdf, stderr=b"")

    monkeypatch.setattr(storage.subprocess, "run", fake_process)
    result = storage.convert_presentation(source, tmp_path)
    assert result.read_bytes() == pdf


def test_image_fallback_timeout_only_removes_its_own_container(tmp_path, monkeypatch):
    import subprocess
    from app import storage

    source = tmp_path / "source.pptx"
    source.write_bytes(b"explicit-test-presentation")
    monkeypatch.delenv("REBORN_CONVERTER_URL", raising=False)
    monkeypatch.setenv("REBORN_CONVERTER_IMAGE", "pythontopia-reborn-converter:test-only")
    calls = []

    def fake_process(command, **kwargs):
        calls.append(command)
        if command[1] == "run":
            raise subprocess.TimeoutExpired(command, 70)
        return subprocess.CompletedProcess(command, 0, stdout=b"", stderr=b"")

    monkeypatch.setattr(storage.subprocess, "run", fake_process)
    with pytest.raises(ValueError, match="超时"):
        storage.convert_presentation(source, tmp_path)
    name = calls[0][calls[0].index("--name") + 1]
    assert calls[1] == ["docker", "rm", "--force", name]
    assert name.startswith("reborn-presentation-") and len(calls) == 2


def test_replaced_material_is_not_overwritten_by_old_processing_job(db, monkeypatch, tmp_path):
    from sqlalchemy.orm import sessionmaker
    from app import storage
    from app.models import Material

    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "private"))
    factory = sessionmaker(db.bind, expire_on_commit=False)
    monkeypatch.setattr(storage, "SessionLocal", factory)
    row = make_material(db, chapter_ids=[179], title="current upload", kind="text", status="queued", storage_key="materials/old.txt", content={"pages": []})
    db.add(row)
    db.commit()
    material_id = row.id
    source = tmp_path / "old.txt"
    source.write_text("old upload", encoding="utf-8")
    storage.get_storage().put(row.storage_key, source)

    def replaced_during_parse(path, tmp):
        with factory() as other:
            current = other.get(Material, material_id)
            current.storage_key = "materials/replacement.txt"
            current.content = {"filename": "replacement.txt", "pages": []}
            current.status = "queued"
            other.commit()
        return {"pages": [{"id": 1, "page_no": 1, "text": "stale output"}]}

    monkeypatch.setattr(storage, "parse_bounded", replaced_during_parse)
    storage.process_material(material_id)
    db.expire_all()
    saved = db.get(Material, material_id)
    assert saved.storage_key == "materials/replacement.txt" and saved.status == "queued"
    assert saved.content == {"filename": "replacement.txt", "pages": []}
