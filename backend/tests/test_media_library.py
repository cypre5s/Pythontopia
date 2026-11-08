"""Supplemental library media preserves chapter authorization and learning state."""

from copy import deepcopy

import pytest
from sqlalchemy import func, select

from app.models import (
    Activity, ActivityProgress, ChapterRelease, Classroom, LearningEvent, LearningRecord,
    Material, Membership, NodeState, PlayerQuest, QuestDefinition, Submission, User,
)
from app.seed import seed
from conftest import make_material


def add_media(db, *, chapter_id=180, owner_id=None, kind="video", **metadata):
    material = make_material(db,
        chapter_ids=[chapter_id], owner_id=owner_id, title="自制补充资料", kind=kind,
        status="ready", storage_key="materials/test-video.mp4",
        content={"pages": [], "library_available": True,
                 "duration_seconds": 460, "source": {"kind": "course_media_pack", "course_id": 43}, **metadata},
    )
    db.add(material)
    db.commit()
    return material


def library_ids(client, student, classroom):
    response = client.get(f"/api/library?classroom_id={classroom['id']}", headers=student["headers"])
    assert response.status_code == 200, response.text
    return {item["id"] for item in response.json()}


def test_imported_library_media_needs_no_activity_or_task(client, db, student, classroom):
    media = add_media(db)
    assert db.scalar(select(func.count()).select_from(Activity).where(Activity.material_id == media.id)) == 0
    assert media.id in library_ids(client, student, classroom)
    detail = client.get(f"/api/materials/{media.id}?classroom_id={classroom['id']}", headers=student["headers"])
    assert detail.status_code == 200, detail.text
    assert "supplementary" not in detail.json()
    assert detail.json()["duration_seconds"] == 460
    assert {row.id for row in db.scalars(select(QuestDefinition))} == {"arrival"}


def test_supplemental_file_stream_is_private_and_read_only(client, db, student, classroom, monkeypatch, tmp_path):
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path))
    media = add_media(db)
    path = tmp_path / media.storage_key
    path.parent.mkdir()
    path.write_bytes(b"0000ftyp" + b"self-authored test media bytes")
    models = (Activity, ActivityProgress, PlayerQuest, NodeState, LearningEvent, LearningRecord, Submission)
    before = {model: db.scalar(select(func.count()).select_from(model)) for model in models}
    url = f"/api/materials/{media.id}/file?classroom_id={classroom['id']}"
    assert client.get(url).status_code == 401
    response = client.get(url, headers={**student["headers"], "Range": "bytes=0-7"})
    assert response.status_code == 206
    assert response.content == b"0000ftyp"
    assert response.headers["cache-control"] == "private, no-store"
    library_ids(client, student, classroom)
    client.get(f"/api/materials/{media.id}?classroom_id={classroom['id']}", headers=student["headers"])
    assert {model: db.scalar(select(func.count()).select_from(model)) for model in models} == before


@pytest.mark.parametrize("status,scheduled", [("paused", False), ("published", True)])
def test_library_media_obeys_chapter_schedule(client, db, student, classroom, status, scheduled):
    from datetime import datetime, timedelta, timezone

    media = add_media(db)
    release = db.get(ChapterRelease, (classroom["id"], 180))
    release.status = status
    release.opens_at = datetime.now(timezone.utc) + timedelta(days=1) if scheduled else None
    db.commit()
    assert media.id not in library_ids(client, student, classroom)
    assert client.get(f"/api/materials/{media.id}?classroom_id={classroom['id']}", headers=student["headers"]).status_code == 403
    assert client.get(f"/api/materials/{media.id}/file?classroom_id={classroom['id']}", headers=student["headers"]).status_code == 403


def test_unreleased_chapter_is_not_published_by_library_flag(client, db, student, classroom):
    media = add_media(db, chapter_id=181)
    assert media.id not in library_ids(client, student, classroom)
    assert client.get(f"/api/materials/{media.id}?classroom_id={classroom['id']}", headers=student["headers"]).status_code == 403


def test_ordinary_unbound_teacher_upload_is_not_implicitly_published(client, db, student, teacher, classroom):
    media = add_media(db, owner_id=teacher["id"], library_available=False, source={"kind": "upload"})
    assert media.id not in library_ids(client, student, classroom)
    assert client.get(f"/api/materials/{media.id}?classroom_id={classroom['id']}", headers=student["headers"]).status_code == 403


def test_library_publication_does_not_bypass_class_membership_or_owner(client, db, student, teacher, classroom):
    other = User(student_id="83530536446524", name="其他教师", role="teacher", password_hash="not-used")
    db.add(other)
    db.flush()
    other_class = Classroom(name="其他班级", teacher_id=other.id, join_code="other-media-class")
    db.add(other_class)
    db.flush()
    db.add(ChapterRelease(classroom_id=other_class.id, chapter_id=180, status="published"))
    db.commit()
    shared = add_media(db)
    private = add_media(db, owner_id=other.id)
    assert private.id not in library_ids(client, student, classroom)
    assert client.get(f"/api/materials/{private.id}?classroom_id={classroom['id']}", headers=student["headers"]).status_code == 403
    assert client.get(f"/api/materials/{shared.id}?classroom_id={other_class.id}", headers=student["headers"]).status_code == 403
    assert client.get(f"/api/library?classroom_id={other_class.id}", headers=student["headers"]).status_code == 403
    db.add(Membership(user_id=student["id"], classroom_id=other_class.id))
    db.commit()
    assert client.get(f"/api/materials/{shared.id}?classroom_id={other_class.id}", headers=student["headers"]).status_code == 200
    # The same teacher may explicitly publish their own materials without an activity.
    own = add_media(db, owner_id=teacher["id"], source={})
    assert own.id in library_ids(client, student, classroom)


@pytest.mark.parametrize("flag", [False, "true", 1])
def test_unbound_material_requires_explicit_boolean_publication(client, db, student, classroom, flag):
    media = add_media(db, library_available=flag)
    assert media.id not in library_ids(client, student, classroom)
    assert client.get(f"/api/materials/{media.id}?classroom_id={classroom['id']}", headers=student["headers"]).status_code == 403


@pytest.mark.parametrize("source", [{}, "external import", {"kind": "upload"}])
def test_explicit_library_publication_does_not_depend_on_source_labels(client, db, student, classroom, source):
    media = add_media(db, source=source)
    assert media.id in library_ids(client, student, classroom)
    assert client.get(f"/api/materials/{media.id}?classroom_id={classroom['id']}", headers=student["headers"]).status_code == 200


@pytest.mark.parametrize("duration", [True, "460", -1, 0, None, float("nan"), float("inf")])
def test_video_metadata_is_typed(client, db, student, classroom, duration):
    media = add_media(db, duration_seconds=duration, supplementary="true")
    response = client.get(f"/api/materials/{media.id}?classroom_id={classroom['id']}", headers=student["headers"])
    assert response.status_code == 200
    assert response.json()["duration_seconds"] is None
    assert "supplementary" not in response.json()


def test_seed_preserves_current_pdf_pages_and_library_publication(db):
    material = db.get(Material, 186)
    original_pages = deepcopy(material.content["pages"])
    material.kind, material.storage_key, material.status = "pdf", "course-media/current.pdf", "ready"
    material.content = {"pages": original_pages, "library_available": True,
                        "source": {"kind": "course_media_pack", "course_id": 43, "document_id": 186}}
    db.commit()
    seed(db)
    seed(db)
    db.expire_all()
    current = db.get(Material, 186)
    assert current.kind == "pdf"
    assert current.storage_key == "course-media/current.pdf"
    assert current.content["pages"] == original_pages
    assert current.content["library_available"] is True
    assert {row.id for row in db.scalars(select(QuestDefinition))} == {"arrival"}
