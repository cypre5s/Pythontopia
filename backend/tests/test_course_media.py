"""Import self-authored media fixtures without altering real learner evidence."""

from copy import deepcopy
import hashlib
import io
import json
from pathlib import Path
import tarfile
from uuid import uuid4

from botocore.exceptions import ClientError
from pypdf import PdfWriter
import pytest
from sqlalchemy import func, select

from app import course_media
from app.course_media import import_media
from app.models import Activity, ActivityProgress, LearningRecord, Material, NodeState, PlayerQuest, QuestDefinition, Submission
from fixture_seed import seed


class MemoryStorage:
    def __init__(self):
        self.objects = {}
        self.puts = []

    def size(self, key):
        if key not in self.objects:
            raise FileNotFoundError(key)
        return len(self.objects[key])

    def put(self, key, source):
        self.puts.append(key)
        self.objects[key] = Path(source).read_bytes()

    def chunks(self, key, start, end):
        yield self.objects[key][start:end + 1]


def write_archive(path, files, *, extra=None):
    with tarfile.open(path, "w:gz") as archive:
        for name, raw in files.items():
            info = tarfile.TarInfo(name)
            info.size = len(raw)
            archive.addfile(info, io.BytesIO(raw))
        if extra:
            info, raw = extra
            archive.addfile(info, io.BytesIO(raw) if raw is not None else None)


@pytest.fixture
def media_fixture(tmp_path, monkeypatch):
    source = tmp_path / "trusted"
    (source / "json").mkdir(parents=True)
    import os
    export = Path(os.environ["REBORN_COURSE_ROOT"]) / "json"
    documents = json.loads((export / "course_document.json").read_text(encoding="utf-8-sig"))
    pages = json.loads((export / "course_page.json").read_text(encoding="utf-8-sig"))
    documents = [doc for doc in documents if doc["id"] == 186]
    pages = [page for page in pages if page["document_id"] == 186]
    for name, value in (("course_document", documents), ("course_page", pages)):
        (source / f"json/{name}.json").write_text(json.dumps(value), encoding="utf-8")
    writer = PdfWriter()
    for _ in pages:
        writer.add_blank_page(width=320, height=180)
    pdf = io.BytesIO()
    writer.write(pdf)
    manifest, transcripts = [], {"reviewStatus": "machine_generated_unreviewed", "videos": []}
    files = {"course-pack-43/pdfs/186.pdf": pdf.getvalue()}
    for video_id, chapter in ((1, 1), (2, 2)):
        filename = f"{video_id:032x}.mp4"
        raw = b"\x00\x00\x00\x18ftypisom" + bytes([video_id]) * 20
        manifest.append({"id": video_id, "file": filename, "chapter": chapter, "title": f"自制补充视频{video_id}",
                         "durationSec": 12 + video_id, "kcCodes": []})
        transcripts["videos"].append({"videoId": video_id, "sourceFile": filename, "sourceSha256": hashlib.sha256(raw).hexdigest(),
                                       "reviewStatus": "machine_generated_unreviewed", "segments": []})
        files[f"course-pack-43/videos/{filename}"] = raw
    for name, value in (("video_manifest", manifest), ("video_transcripts", transcripts)):
        raw = json.dumps(value, ensure_ascii=False).encode("utf-8")
        (source / f"{name}.json").write_bytes(raw)
        files[f"course-pack-43/{name}.json"] = raw
    archive = tmp_path / "media.tar.gz"
    write_archive(archive, files)
    storage = MemoryStorage()
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "private"))
    return {"source": source, "archive": archive, "files": files, "storage": storage,
            "manifest": manifest, "transcripts": transcripts}


def run_import(db, fixture):
    return import_media(db, fixture["archive"], source_root=fixture["source"], storage=fixture["storage"])


def test_import_preserves_page_identity_and_learner_history_and_is_idempotent(db, student, legacy_classroom, media_fixture):
    classroom = legacy_classroom
    material = db.get(Material, 186)
    pages_before = deepcopy(material.content["pages"])
    pages_before[0]["text"] = "人工维护的原页文本"
    material.content = {**material.content, "pages": pages_before}
    quest = PlayerQuest(user_id=student["id"], classroom_id=classroom["id"], quest_id="arrival", status="complete")
    db.add(quest)
    db.flush()
    state = NodeState(player_quest_id=quest.id, node_id="study", evidence={"record_id": "unchanged"})
    progress = ActivityProgress(user_id=student["id"], classroom_id=classroom["id"], activity_id="ch1-lesson",
                                page_no=43, visited_pages=[10715, 10723, 10724], completed=True)
    record = LearningRecord(user_id=student["id"], classroom_id=classroom["id"], activity_id="ch1-lesson", data={"page_id": 10724})
    submission = Submission(user_id=student["id"], classroom_id=classroom["id"], activity_id="ch1-practice", question_id="ch1-abstraction",
                            mode="submit", status="accepted", answer="preserved", idempotency_key=str(uuid4()))
    db.add_all([state, progress, record, submission])
    db.commit()
    ids = (quest.id, record.id, submission.id)
    activity_before = deepcopy(db.get(Activity, "ch1-lesson").content)
    counts_before = {model: db.scalar(select(func.count()).select_from(model)) for model in (Material, Activity, QuestDefinition)}
    assert run_import(db, media_fixture) == {"pdfs": 1, "pages": 45, "videos": 2}
    db.expire_all()
    imported = db.get(Material, 186)
    assert imported.kind == "pdf" and imported.status == "ready"
    assert imported.content["pages"] == pages_before
    assert "notice" not in imported.content
    assert imported.content["library_available"] is True
    assert imported.content["source"] == {"kind": "course_media_pack", "course_id": 43, "document_id": 186}
    video_ids = [m.id for m in db.scalars(select(Material).where(Material.kind == "video"))]
    assert len(video_ids) == 2
    for video in db.scalars(select(Material).where(Material.kind == "video")):
        assert "supplementary" not in video.content
        assert video.content["library_available"] is True
        assert video.content["duration_seconds"] > 0 and video.storage_key
    assert len(media_fixture["storage"].puts) == 3
    run_import(db, media_fixture)
    assert len(media_fixture["storage"].puts) == 3
    assert [m.id for m in db.scalars(select(Material).where(Material.kind == "video"))] == video_ids
    assert db.scalar(select(func.count()).select_from(Material)) == counts_before[Material] + 2
    assert db.scalar(select(func.count()).select_from(Activity)) == counts_before[Activity]
    assert db.scalar(select(func.count()).select_from(QuestDefinition)) == counts_before[QuestDefinition] == 1
    assert db.get(Activity, "ch1-lesson").content == activity_before
    assert db.get(PlayerQuest, ids[0]).status == "complete"
    assert db.get(NodeState, (ids[0], "study")).evidence == {"record_id": "unchanged"}
    assert db.get(ActivityProgress, (student["id"], classroom["id"], "ch1-lesson")).visited_pages == [10715, 10723, 10724]
    assert db.get(LearningRecord, ids[1]).data == {"page_id": 10724}
    assert db.get(Submission, ids[2]).status == "accepted" and db.get(Submission, ids[2]).answer == "preserved"
    seed(db)
    db.expire_all()
    assert db.get(Material, 186).kind == "pdf"
    assert db.get(Material, 186).content["pages"] == pages_before
    assert db.get(Material, video_ids[0]).content["library_available"] is True


@pytest.mark.parametrize("problem", ["missing", "checksum", "pages", "path", "link", "duplicate", "metadata", "truncated"])
def test_invalid_archive_cannot_update_materials_or_write_storage(db, media_fixture, problem):
    files = dict(media_fixture["files"])
    extra = None
    if problem == "missing":
        files.pop(next(key for key in files if key.endswith(".mp4")))
    elif problem == "checksum":
        key = next(key for key in files if key.endswith(".mp4"))
        files[key] += b"altered"
    elif problem == "pages":
        writer = PdfWriter()
        writer.add_blank_page(width=320, height=180)
        buffer = io.BytesIO()
        writer.write(buffer)
        files["course-pack-43/pdfs/186.pdf"] = buffer.getvalue()
    elif problem == "path":
        extra = (tarfile.TarInfo("../escaped.txt"), b"bad")
        extra[0].size = 3
    elif problem == "link":
        info = tarfile.TarInfo("course-pack-43/videos/link.mp4")
        info.type, info.linkname = tarfile.SYMTYPE, "../../escaped"
        extra = (info, None)
    elif problem == "duplicate":
        name, raw = next(iter(files.items()))
        info = tarfile.TarInfo(name)
        info.size = len(raw)
        extra = (info, raw)
    elif problem == "metadata":
        videos = deepcopy(media_fixture["manifest"])
        videos[0]["title"] = "篡改标题"
        files["course-pack-43/video_manifest.json"] = json.dumps(videos).encode()
    write_archive(media_fixture["archive"], files, extra=extra)
    if problem == "truncated":
        media_fixture["archive"].write_bytes(media_fixture["archive"].read_bytes()[:50])
    before = deepcopy(db.get(Material, 186).content)
    with pytest.raises(ValueError):
        run_import(db, media_fixture)
    db.expire_all()
    assert db.get(Material, 186).kind == "text"
    assert db.get(Material, 186).content == before
    assert not media_fixture["storage"].puts


@pytest.mark.parametrize("problem", ["page_identity", "missing_chapter", "duplicate_video_source"])
def test_database_mapping_validation_precedes_any_storage_write(db, media_fixture, problem):
    if problem == "page_identity":
        material = db.get(Material, 186)
        pages = deepcopy(material.content["pages"])
        pages[0]["id"] = -1
        material.content = {**material.content, "pages": pages}
    elif problem == "missing_chapter":
        media_fixture["manifest"][0]["chapter"] = 99
        raw = json.dumps(media_fixture["manifest"]).encode()
        (media_fixture["source"] / "video_manifest.json").write_bytes(raw)
        media_fixture["files"]["course-pack-43/video_manifest.json"] = raw
        write_archive(media_fixture["archive"], media_fixture["files"])
    else:
        for number in range(2):
            db.add(Material(title=f"duplicate{number}", kind="video",
                            content={"source": {"kind": "course_media_pack", "course_id": 43, "video_id": 1}}))
    db.commit()
    with pytest.raises(ValueError):
        run_import(db, media_fixture)
    assert not media_fixture["storage"].puts
    assert db.get(Material, 186).kind == "text"


@pytest.mark.parametrize("same_size", [False, True])
def test_same_hash_object_corruption_is_not_overwritten(db, media_fixture, same_size):
    run_import(db, media_fixture)
    key = db.get(Material, 186).storage_key
    original = media_fixture["storage"].objects[key]
    broken = b"!" + original[1:] if same_size else b"broken stored object"
    media_fixture["storage"].objects[key] = broken
    before = deepcopy(db.get(Material, 186).content)
    with pytest.raises(ValueError, match="拒绝覆盖"):
        run_import(db, media_fixture)
    assert media_fixture["storage"].objects[key] == broken
    assert len(media_fixture["storage"].puts) == 3
    assert db.get(Material, 186).content == before


@pytest.mark.parametrize("limit", ["MAX_ARCHIVE_BYTES", "MAX_EXPANDED_BYTES", "MAX_FILE_BYTES", "MAX_MEMBERS"])
def test_resource_limits_reject_package_before_publication(db, media_fixture, monkeypatch, limit):
    monkeypatch.setattr(course_media, limit, 1)
    with pytest.raises(ValueError):
        run_import(db, media_fixture)
    assert db.get(Material, 186).kind == "text"
    assert not media_fixture["storage"].puts


def test_unrelated_material_source_metadata_is_not_adopted(db, media_fixture):
    unrelated = Material(title="Unrelated material", kind="video", content={"source": "teacher-provided metadata"})
    db.add(unrelated)
    db.commit()
    unrelated_id = unrelated.id
    run_import(db, media_fixture)
    assert db.get(Material, unrelated_id).content == {"source": "teacher-provided metadata"}
    assert len(list(db.scalars(select(Material).where(Material.kind == "video")))) == 3


def test_storage_failure_rolls_back_material_updates(db, media_fixture, monkeypatch):
    put = media_fixture["storage"].put

    def fail(key, path):
        if media_fixture["storage"].puts:
            raise OSError("self-authored storage failure")
        put(key, path)

    monkeypatch.setattr(media_fixture["storage"], "put", fail)
    before = deepcopy(db.get(Material, 186).content)
    with pytest.raises(OSError):
        run_import(db, media_fixture)
    db.expire_all()
    assert db.get(Material, 186).content == before and db.get(Material, 186).kind == "text"
    assert not list(db.scalars(select(Material).where(Material.kind == "video")))


def test_s3_missing_objects_are_written_but_access_errors_are_not_swallowed(db, media_fixture, monkeypatch):
    def missing(key):
        raise ClientError({"Error": {"Code": "404"}}, "HeadObject")

    monkeypatch.setattr(media_fixture["storage"], "size", missing)
    run_import(db, media_fixture)
    assert len(media_fixture["storage"].puts) == 3

    def denied(key):
        raise ClientError({"Error": {"Code": "AccessDenied"}}, "HeadObject")

    monkeypatch.setattr(media_fixture["storage"], "size", denied)
    with pytest.raises(ClientError):
        run_import(db, media_fixture)
    assert len(media_fixture["storage"].puts) == 3
