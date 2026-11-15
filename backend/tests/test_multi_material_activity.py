"""Mixed-resource activities earn independent evidence and keep optional work optional."""
from datetime import datetime, timedelta, timezone
from uuid import uuid4

import pytest
from sqlalchemy import select

from app import learning_rules
from app.models import Activity, ActivityMaterial, ActivityProgress, LearningRecord
from conftest import make_material


@pytest.fixture
def mixed(client, db, teacher, classroom):
    for key, kind in [(9910, "text"), (9911, "video"), (9912, "text")]:
        make_material(db, id=key, title=str(key), kind=kind, chapter_ids=[179], owner_id=teacher["id"],
                      status="ready", storage_key="test-video.mp4" if kind == "video" else None,
                      content={"duration_seconds": 60, "pages": [] if kind == "video" else [
                          {"id": key * 10 + i, "page_no": i, "title": str(i), "text": "fixture"} for i in (1, 2)]})
    db.commit()
    rows = [
        {"material_id": 9910, "required": True, "learning": {"reading": {"scope": "all"}}},
        {"material_id": 9911, "required": True, "learning": {"video": {"minimum_seconds": 10,
            "segments": [{"start_seconds": 20, "end_seconds": 25, "label": "关键概念"}]}}},
        {"material_id": 9912, "required": False, "learning": {"reading": {"scope": "all"}}},
    ]
    response = client.post("/api/teacher/activities", params={"classroom_id": classroom["id"]}, headers=teacher["headers"],
        json={"id": "mixed", "chapter_id": 179, "title": "多资料学习", "kind": "lesson", "resources": rows})
    assert response.status_code == 201, response.text
    return response.json()["id"], rows


def test_mixed_all_of_video_requirements_optional_progress_and_reopen(client, db, teacher, student, classroom, mixed, monkeypatch):
    key, resources = mixed
    base = f"/api/activities/{key}"
    scope = {"classroom_id": classroom["id"]}
    headers = student["headers"]
    now = [datetime(2026, 11, 15, tzinfo=timezone.utc)]
    monkeypatch.setattr(learning_rules, "utcnow", lambda: now[0])
    assert client.post(base + "/participation", json=scope, headers=headers).status_code == 422
    for material, page in [(9910, 1), (9912, 1), (9910, 2)]:
        reply = client.post(base + "/progress", json={**scope, "material_id": material, "page_no": page}, headers=headers)
        assert reply.status_code == 200, reply.text
    assert reply.json()["completed_count"] == 1
    assert reply.json()["required_count"] == 2
    assert not reply.json()["completed"]
    assert client.post(base + "/progress", json={**scope, "material_id": 186, "page_no": 1}, headers=headers).status_code == 409
    packet = {**scope, "material_id": 9911, "playback_session_id": str(uuid4()), "sequence": 1, "position_seconds": 0., "playing": True}
    def heartbeat(position):
        now[0] += timedelta(seconds=5)
        packet["sequence"] += 1
        packet["position_seconds"] = float(position)
        response = client.post(base + "/video-progress", json=packet, headers=headers)
        assert response.status_code == 200, response.text
        return response.json()
    assert client.post(base + "/video-progress", json=packet, headers=headers).status_code == 200
    heartbeat(5)
    state = heartbeat(10)
    assert state["resources"][1]["watched_seconds"] == 10 and not state["completed"]
    state = heartbeat(55)  # Seeking does not cover the key segment.
    assert state["resources"][1]["completed_segments"] == []
    heartbeat(20)
    state = heartbeat(25)
    assert state["completed"] and state["fraction"] == 1
    assert state["resources"][2]["read_count"] == 1 and not state["resources"][2]["completed"]
    assert client.post(base + "/participation", json=scope, headers=headers).status_code == 200
    reopened = client.get(base, params=scope, headers=headers).json()
    assert [row["material_id"] for row in reopened["resources"]] == [9910, 9911, 9912]
    assert reopened["progress"]["resources"][0]["page_no"] == 2
    assert reopened["progress"]["resources"][2]["page_no"] == 1
    before = len(db.scalars(select(LearningRecord).where(LearningRecord.activity_id == key)).all())
    client.post(base + "/progress", json={**scope, "material_id": 9910, "page_no": 2}, headers=headers)
    assert len(db.scalars(select(LearningRecord).where(LearningRecord.activity_id == key)).all()) == before
    # Reordering and optional flags never erase previous evidence.
    changed = [resources[2], resources[0], resources[1]]
    response = client.put(f"/api/teacher/activities/{key}", params=scope, headers=teacher["headers"], json={"resources": changed})
    assert response.status_code == 200, response.text
    assert db.get(ActivityProgress, (student["id"], classroom["id"], key)).visited_pages == [99101, 99102, 99121]


def test_resource_config_rejects_duplicates_unowned_material_and_invalid_ranges(client, db, teacher, classroom, mixed):
    key, resources = mixed
    url = f"/api/teacher/activities/{key}"
    scope = {"classroom_id": classroom["id"]}
    for rows in [resources + [resources[0]], [{**row, "required": False} for row in resources],
                 [{**resources[0], "learning": {"reading": {"scope": "pages", "page_ids": [99121]}}}],
                 [{**resources[1], "learning": {"video": {"segments": [{"start_seconds": 40, "end_seconds": 70, "label": "越界"}]}}}],
                 [{**resources[0], "material_id": True}]]:
        result = client.put(url, params=scope, headers=teacher["headers"], json={"resources": rows})
        assert result.status_code == 422, result.text
    assert len(db.get(Activity, key).resources) == 3
    assert len(db.scalars(select(ActivityMaterial).where(ActivityMaterial.activity_id == key)).all()) == 3


def test_same_page_ids_in_different_resources_never_share_reading_credit(client, db, student, teacher, classroom, mixed):
    from app.models import Material
    key, resources = mixed
    db.get(Material, 9912).content = db.get(Material, 9910).content
    db.commit()
    scope = {"classroom_id": classroom["id"]}
    for page in (1, 2):
        response = client.post(f"/api/activities/{key}/progress", headers=student["headers"], json={**scope, "material_id": 9910, "page_no": page})
        assert response.status_code == 200, response.text
    assert response.json()["resources"][0]["completed"]
    assert response.json()["resources"][2]["read_count"] == 0


def test_two_videos_cannot_earn_overlapping_time(client, db, student, teacher, classroom, mixed, monkeypatch):
    from app.models import Material
    key, resources = mixed
    extra = db.get(Material, 9912)
    extra.kind, extra.storage_key, extra.content = "video", "other-video.mp4", {"pages": [], "duration_seconds": 60}
    db.commit()
    resources[2]["learning"] = {"video": {"minimum_seconds": 10}}
    scope = {"classroom_id": classroom["id"]}
    assert client.put(f"/api/teacher/activities/{key}", params=scope, headers=teacher["headers"], json={"resources": resources}).status_code == 200
    now = [datetime(2026, 11, 15, tzinfo=timezone.utc)]
    monkeypatch.setattr(learning_rules, "utcnow", lambda: now[0])
    packet = {**scope, "material_id": 9911, "playback_session_id": str(uuid4()), "sequence": 1, "position_seconds": 0., "playing": True}
    endpoint = f"/api/activities/{key}/video-progress"
    assert client.post(endpoint, headers=student["headers"], json=packet).status_code == 200
    other = {**packet, "material_id": 9912, "playback_session_id": str(uuid4())}
    assert client.post(endpoint, headers=student["headers"], json=other).status_code == 409
    now[0] += timedelta(seconds=5)
    packet.update(sequence=2, position_seconds=5., playing=False)
    assert client.post(endpoint, headers=student["headers"], json=packet).status_code == 200
    assert client.post(endpoint, headers=student["headers"], json=other).status_code == 200
    now[0] += timedelta(seconds=5)
    other.update(sequence=2, position_seconds=5., playing=False)
    result = client.post(endpoint, headers=student["headers"], json=other).json()
    assert result["resources"][1]["watched_seconds"] == result["resources"][2]["watched_seconds"] == 5
