from datetime import datetime, timedelta, timezone
from uuid import uuid4
from app import courses
from app.auth import as_utc
from app.classrooms import ReleaseInput
from app.models import ChapterRelease, LearningEvent, LearningRecord, Submission
from sqlalchemy import select


def test_shanghai_and_utc_release_inputs_store_the_same_instant_and_preserve_boundary(client, db, teacher, student, classroom, monkeypatch, branching_course):
    instant = datetime(2026, 11, 3, 12, 0, 0, tzinfo=timezone.utc)

    class ControlledClock(datetime):
        current = instant - timedelta(seconds=1)

        @classmethod
        def now(cls, tz=None):
            return cls.current.astimezone(tz) if tz is not None else cls.current.replace(tzinfo=None)

    monkeypatch.setattr(courses, "datetime", ControlledClock)
    path = f"/api/teacher/classrooms/{classroom['id']}/releases/180"
    for representation in ("2026-11-03T20:00:00+08:00", "2026-11-03T12:00:00Z"):
        model = ReleaseInput(opens_at=representation)
        assert model.opens_at == instant and model.opens_at.utcoffset() == timedelta(0)
        written = client.put(path, json={"status": "published", "opens_at": representation}, headers=teacher["headers"])
        assert written.status_code == 200, written.text
        assert written.json()["opens_at"] == "2026-11-03T12:00:00+00:00"
        db.expire_all()
        stored = db.get(ChapterRelease, (classroom["id"], 180))
        assert stored.opens_at.tzinfo is None  # SQLite strips timezone but must retain the UTC wall clock.
        assert stored.opens_at == datetime(2026, 11, 3, 12, 0, 0)
        listing = client.get(f"/api/teacher/classrooms/{classroom['id']}/releases", headers=teacher["headers"]).json()
        item = next(row for row in listing if row["chapter_id"] == 180)
        assert item["opens_at"] == "2026-11-03T12:00:00+00:00"
        ControlledClock.current = instant - timedelta(seconds=1)
        assert client.get(f"/api/activities/ch2-lesson?classroom_id={classroom['id']}", headers=student["headers"]).status_code == 403
        ControlledClock.current = instant
        assert client.get(f"/api/activities/ch2-lesson?classroom_id={classroom['id']}", headers=student["headers"]).status_code == 200
        ControlledClock.current = instant + timedelta(seconds=1)
        assert client.get(f"/api/activities/ch2-lesson?classroom_id={classroom['id']}", headers=student["headers"]).status_code == 200
    assert client.put(path, json={"status": "published", "opens_at": "2026-11-03T20:00:00"}, headers=teacher["headers"]).status_code == 422
    cleared = client.put(path, json={"status": "published", "opens_at": None}, headers=teacher["headers"])
    assert cleared.status_code == 200 and cleared.json()["opens_at"] is None
    db.expire_all()
    listing = client.get(f"/api/teacher/classrooms/{classroom['id']}/releases", headers=teacher["headers"]).json()
    assert next(row for row in listing if row["chapter_id"] == 180)["opens_at"] is None


def test_all_learning_timestamps_remain_explicit_utc_after_sqlite_requery(client, db, teacher, student, classroom):
    cid = classroom["id"]
    headers = student["headers"]
    position = client.put(
        "/api/world/position", json={"classroom_id": cid, "map_key": "town_hub", "tile_x": 22, "tile_y": 34, "direction": "up"}, headers=headers
    )
    assert position.status_code == 200
    from app.models import QuestDefinition
    definition = {"id": "timestamp-talk", "title": "Timestamp", "chapter_id": 179,
        "nodes": [{"id": "talk", "type": "REACH_LOCATION", "title": "Talk", "target": "town_square", "dependencies": []}], "effects": []}
    db.add(QuestDefinition(id=definition["id"], chapter_id=179, title="Timestamp", definition=definition))
    db.commit()
    event = client.post("/api/events", json={"classroom_id": cid, "event_id": str(uuid4()), "quest_id": definition["id"], "node_id": "talk", "evidence": {}}, headers=headers)
    assert event.status_code == 200, event.text
    record = client.post("/api/activities/ch1-lesson/participation", json={"classroom_id": cid, "page_no": 42}, headers=headers)
    assert record.status_code == 200, record.text
    submission = client.post(
        "/api/submissions",
        json={
            "classroom_id": cid,
            "activity_id": "ch1-practice",
            "question_id": "ch1-computation",
            "idempotency_key": str(uuid4()),
            "mode": "submit",
            "answer": True},
        headers=headers,
    )
    assert submission.status_code == 201, submission.text
    submission_id = submission.json()["id"]
    db.expire_all()
    event_row = db.scalar(select(LearningEvent))
    record_row = db.get(LearningRecord, record.json()["record_id"])
    submission_row = db.get(Submission, submission_id)
    assert event_row.created_at.tzinfo is None and record_row.created_at.tzinfo is None and submission_row.created_at.tzinfo is None
    progress = client.get(f"/api/teacher/classrooms/{cid}/progress", headers=teacher["headers"]).json()
    detail = client.get(f"/api/teacher/classrooms/{cid}/students/{student['id']}", headers=teacher["headers"]).json()
    reread = client.get(f"/api/submissions/{submission_id}?classroom_id={cid}", headers=headers)
    assert reread.status_code == 200, reread.text
    expected = [
        (progress["events"][0]["created_at"], event_row.created_at),
        (progress["submissions"][0]["created_at"], submission_row.created_at),
        (detail["events"][0]["created_at"], event_row.created_at),
        (detail["participation"][0]["created_at"], record_row.created_at),
        (detail["submissions"][0]["created_at"], submission_row.created_at),
        (reread.json()["created_at"], submission_row.created_at),
        (submission.json()["created_at"], submission_row.created_at),
    ]
    for encoded, stored in expected:
        assert isinstance(encoded, str) and encoded.endswith("+00:00")
        parsed = datetime.fromisoformat(encoded)
        assert parsed.utcoffset() == timedelta(0) and parsed == as_utc(stored)
