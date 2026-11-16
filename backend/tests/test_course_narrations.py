"""Real package files restore narration across courses without local paths or model calls."""

import json
import hashlib
import shutil
import wave
from copy import deepcopy

import pytest
from sqlalchemy import select

from app.content_relations import set_material_chapters
from app.course_content.snapshot import stable_id
from app.course_packages.apply import import_bundle
from app.course_packages.manifest import PackageError
from app.course_packages.reader import load_bundle
from app.course_packages.writer import write_package
from app.materials.storage import get_storage
from app.models import Chapter, CourseInstance, Material, Membership, now
from app.narration import repository
from app.narration.audio import current_audio
from app.narration.models import NarrationJob, PageNarration
from test_course_exchange import apply, endpoint, export, preview
from test_course_exchange import exchange as exchange


@pytest.fixture
def narrated(db, teacher, exchange, tmp_path):
    source, target = exchange
    chapter = db.scalar(select(Chapter).where(Chapter.course_instance_id == source.id))
    material = Material(course_instance_id=source.id, stable_id="portable-slide", owner_id=teacher["id"],
                        title="配音交换测试", kind="pdf", status="ready", storage_key="original/source.pdf",
                        content={"library_available": True, "pages": [{"id": n, "page_no": n, "title": f"第{n}页", "text": f"正文{n}"} for n in range(1, 6)]})
    db.add(material)
    db.flush()
    set_material_chapters(db, material.id, [chapter.id])
    pdf = tmp_path / "original.pdf"
    pdf.write_bytes(b"%PDF-1.7\nsynthetic narration fixture")
    adapter = get_storage()
    adapter.put(material.storage_key, pdf)
    audio = tmp_path / "page.wav"
    with wave.open(str(audio), "wb") as output:
        output.setparams((1, 2, 8000, 8000, "NONE", "not compressed"))
        output.writeframes(b"\x00\x00" * 8000)
    adapter.put("narrations/source/page.wav", audio)
    for page, status in enumerate(("accepted", "draft", "stale", "rejected", "unavailable"), 1):
        row = PageNarration(material_id=material.id, page_no=page, text=f"第{page}页讲稿", bridge_in="承接上一页",
                            target_seconds=60, status=status, source="ai_generated", model="test-model", prompt_version="test",
                            anchors=[f"正文{page}"], image_used=True, edited_by_teacher=False,
                            reviewed_by=teacher["id"] if status == "accepted" else None,
                            reviewed_at=now() if status == "accepted" else None,
                            page_hash="old" if status == "stale" else repository.source_hash(material, page))
        if page in {1, 3}:
            row.audio_key = "narrations/source/page.wav"
            row.audio_text_hash = repository.text_hash(row.text)
            row.audio_seconds = 1
        db.add(row)
    db.commit()
    return source, target, material


def package(db, narrated, tmp_path):
    directory = tmp_path / "exported"
    directory.mkdir()
    write_package(db, narrated[0], directory)
    return directory, load_bundle(directory)


def test_narration_roundtrip_copied_directory_preserves_review_and_audio(client, db, student, narrated, tmp_path, monkeypatch):
    def forbidden(*args, **kwargs):
        pytest.fail("交换课程包不得调用生成服务")
    monkeypatch.setattr("app.narration.tts.synthesize", forbidden)
    monkeypatch.setattr("app.narration.service.model_client.chat", forbidden)
    source, target, original = narrated
    directory, bundle = package(db, narrated, tmp_path)
    pages = bundle["narrations"]
    assert [r["status"] for r in pages] == ["accepted", "draft", "stale", "rejected", "unavailable"]
    assert pages[0]["audio"] and all(r["audio"] is None for r in pages[1:])
    serialized = json.dumps(pages)
    assert "reviewed_by" not in serialized and "original/source" not in serialized and "storage_key" not in serialized
    copied = tmp_path / "other-server" / "renamed"
    shutil.copytree(directory, copied)
    imported = import_bundle(db, target, None, load_bundle(copied), copied)
    db.commit()
    material = db.scalar(select(Material).where(Material.course_instance_id == imported.id, Material.stable_id == stable_id(original)))
    assert material.id != original.id and material.storage_key != original.storage_key
    row = repository.row(db, material.id, 1)
    assert row.text == pages[0]["text"] and row.reviewed_by is None and row.reviewed_at is not None
    assert row.page_hash == repository.source_hash(material, 1)
    assert current_audio(db, material, 1) == (row.audio_key, row.audio_text_hash)
    assert row.audio_key != repository.row(db, original.id, 1).audio_key
    original_bytes = b"".join(get_storage().chunks(row.audio_key, 0, get_storage().size(row.audio_key) - 1))
    assert original_bytes.startswith(b"RIFF")
    # The installed recording survives removal of the exchange copy and a repeated import.
    import_bundle(db, target, imported, bundle, directory)
    db.commit()
    shutil.rmtree(copied)
    assert len(repository.rows(db, material.id)) == 5
    assert get_storage().size(row.audio_key) == len(original_bytes)
    db.add(Membership(classroom_id=target.id, user_id=student["id"]))
    db.commit()
    url = f"/api/materials/{material.id}/narrations"
    visible = client.get(url + f"?classroom_id={target.id}", headers=student["headers"])
    assert visible.status_code == 200, visible.text
    assert [p["page_no"] for p in visible.json()["pages"]] == [1]
    stream = client.get(url + f"/1/audio?classroom_id={target.id}", headers=student["headers"])
    assert stream.status_code == 200 and stream.content == original_bytes
    assert client.get(url + f"/2/audio?classroom_id={target.id}", headers=student["headers"]).status_code == 404
    assert client.get(url + f"/1/audio?classroom_id={source.classroom_id}", headers=student["headers"]).status_code in {403, 404}
    second = tmp_path / "reexport"
    second.mkdir()
    write_package(db, imported, second)
    assert load_bundle(second)["narrations"] == bundle["narrations"]
    # Editing a destination recording cannot delete the source class recording.
    get_storage().delete(row.audio_key)
    assert get_storage().size(repository.row(db, original.id, 1).audio_key) > 0


@pytest.mark.parametrize("change", ["page", "text", "missing", "corrupt", "reviewer", "unknown_material", "duplicate", "path", "duration", "draft_audio"])
def test_invalid_narration_never_imports(db, narrated, tmp_path, change):
    directory, bundle = package(db, narrated, tmp_path)
    path = next(directory.glob("narrations/*/pages.jsonl"))
    rows = deepcopy(bundle["narrations"])
    if change == "page":
        rows[0]["page_hash"] = "0" * 64
    elif change == "text":
        rows[0]["text"] += "被篡改"
    elif change in {"missing", "corrupt"}:
        asset = next(a for a in bundle["assets"] if a["path"].startswith("narrations/"))
        audio = directory / asset["path"]
        audio.unlink() if change == "missing" else audio.write_bytes(b"corrupt")
    elif change == "reviewer":
        rows[0]["reviewed_by"] = 1
    elif change == "unknown_material":
        rows[0]["material_id"] = "unknown"
    elif change == "duplicate":
        rows.append(deepcopy(rows[0]))
    elif change == "path":
        rows[0]["audio"]["asset_id"] = "../../outside"
    elif change == "duration":
        rows[0]["audio"]["seconds"] = True
    else:
        rows[0]["status"] = "draft"
    path.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
    with pytest.raises(PackageError):
        load_bundle(directory)
    assert db.scalar(select(CourseInstance).where(CourseInstance.classroom_id == narrated[1].id)) is None


def test_missing_current_audio_blocks_export_but_stale_audio_does_not(db, narrated, tmp_path):
    source, _, material = narrated
    row = repository.row(db, material.id, 1)
    row.audio_key = "missing/audio.wav"
    db.commit()
    directory = tmp_path / "broken"
    directory.mkdir()
    with pytest.raises(PackageError, match="音频缺失"):
        write_package(db, source, directory)
    row.audio_text_hash = "old"
    db.commit()
    good = tmp_path / "good"
    good.mkdir()
    write_package(db, source, good)
    assert load_bundle(good)["narrations"][0]["audio"] is None


def test_preview_detects_review_changes_and_preserves_target_only_pages(client, db, teacher, narrated):
    source, target, original = narrated
    payload = export(client, teacher, source)
    first = preview(client, teacher, target.id, payload)
    assert len(first["diff"]["narrations"]["added"]) == 5
    result = apply(client, teacher, target.id, first)
    assert result.status_code == 200, result.text
    instance = db.get(CourseInstance, result.json()["instance_id"])
    material = db.scalar(select(Material).where(Material.course_instance_id == instance.id, Material.stable_id == stable_id(original)))
    second = preview(client, teacher, target.id, payload)
    row = repository.row(db, material.id, 2)
    row.text = "刚刚修改的讲稿"
    db.commit()
    assert apply(client, teacher, target.id, second).status_code == 409
    assert repository.row(db, material.id, 2).text == "刚刚修改的讲稿"
    # An ordinary package with no narration is not an instruction to erase local drafts.
    from app.course_packages.paths import exchange_root
    directory = exchange_root() / "no-narration"
    directory.mkdir()
    write_package(db, source, directory)
    bundle = load_bundle(directory)
    bundle["narrations"] = []
    bundle["assets"] = [a for a in bundle["assets"] if not a["path"].startswith("narrations/")]
    import_bundle(db, target, instance, bundle, directory)
    assert repository.row(db, material.id, 2).text == "刚刚修改的讲稿"


def test_running_generation_blocks_package_exchange(client, db, teacher, narrated):
    source, _, material = narrated
    db.add(NarrationJob(material_id=material.id, kind="audio", status="running"))
    db.commit()
    response = client.get(endpoint(source.classroom_id, "export"), headers=teacher["headers"])
    assert response.status_code == 422 and "仍在执行" in response.text


def test_audio_with_recomputed_hash_but_wrong_signature_is_rejected(db, narrated, tmp_path):
    directory, bundle = package(db, narrated, tmp_path)
    asset = next(a for a in bundle["assets"] if a["path"].startswith("narrations/"))
    old = directory / asset["path"]
    content = b"not an audio file"
    digest = hashlib.sha256(content).hexdigest()
    new = old.with_name(digest + "-1.wav")
    old.unlink()
    new.write_bytes(content)
    asset.update(path=new.relative_to(directory).as_posix(), sha256=digest, size_bytes=len(content))
    (directory / "assets/index.jsonl").write_text("".join(json.dumps(a) + "\n" for a in bundle["assets"]), encoding="utf-8")
    with pytest.raises(PackageError, match="签名"):
        load_bundle(directory)


def test_audio_storage_failure_rolls_back_course_and_narration(client, db, teacher, narrated, monkeypatch):
    source, target, material = narrated
    payload = export(client, teacher, source)
    item = preview(client, teacher, target.id, payload)
    storage_type = type(get_storage())
    put = storage_type.put
    def unavailable(self, key, path):
        if key.startswith("course-narrations/"):
            raise OSError("test storage unavailable")
        return put(self, key, path)
    monkeypatch.setattr(storage_type, "put", unavailable)
    result = apply(client, teacher, target.id, item)
    assert result.status_code == 422 and "私有资源写入失败" in result.text
    assert db.scalar(select(CourseInstance).where(CourseInstance.classroom_id == target.id)) is None
    assert len(repository.rows(db, material.id)) == 5
    assert current_audio(db, material, 1) is not None
