from conftest import make_material
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace
from uuid import uuid4

import pytest
from app import learning_rules
from app.learning_rules import record_video, validate_learning
from app.models import Activity, ActivityProgress, Material


def test_learning_config_validates_actual_pages_and_video_boundaries():
    handout = SimpleNamespace(kind="pdf", content={"pages": [{"id": 101, "page_no": 1}, {"id": 102, "page_no": 2}]})
    video = SimpleNamespace(kind="video", content={"duration_seconds": 120})
    valid = {"reading": {"scope": "range", "start_page": 1, "end_page": 2},
             "highlights": {"pages": [{"start_page": 1, "end_page": 1, "label": "重点"}]}}
    assert validate_learning({"learning": valid}, "lesson", handout, resolve=True) == valid
    assert validate_learning({"learning": {"video": {"minimum_seconds": 180}}}, "lesson", video, resolve=True)
    assert validate_learning({"completion": {"required_page_ids": [101]}, "learning": {"highlights": valid["highlights"]}}, "lesson", handout, resolve=True)
    invalid = [
        {"reading": {"scope": "all", "start_page": 1}},
        {"reading": {"scope": "range", "start_page": 2, "end_page": 1}},
        {"reading": {"scope": "range", "start_page": True, "end_page": 2}},
        {"reading": {"scope": "all"}, "video": {"minimum_seconds": 5}},
        {"video": {"minimum_seconds": True}},
        {"video": {"minimum_seconds": 0}},
        {"unknown": True},
        {"highlights": {"pages": [{"start_page": 1, "end_page": 9, "label": "不存在"}]}},
        {"highlights": {"pages": [{"start_page": 1, "end_page": 2, "label": " "}]}},
    ]
    for learning in invalid:
        with pytest.raises(ValueError):
            validate_learning({"learning": learning}, "lesson", handout, resolve=True)
    for learning in ({"video": {"minimum_seconds": 5}}, {"highlights": {"video": [{"start_seconds": 5, "end_seconds": 130, "label": "越界"}]}}):
        with pytest.raises(ValueError):
            validate_learning({"learning": learning}, "lesson", handout if "video" in learning else video, resolve=True)
    with pytest.raises(ValueError, match="不能与required_page_ids"):
        validate_learning({"completion": {"required_page_ids": [101]}, "learning": {"reading": {"scope": "all"}}}, "lesson", handout, resolve=True)
    with pytest.raises(ValueError, match="只有阅读活动"):
        validate_learning({"learning": {"reading": {"scope": "all"}}}, "practice")


@pytest.fixture
def clock(monkeypatch):
    value = [datetime(2026, 11, 7, tzinfo=timezone.utc)]
    monkeypatch.setattr(learning_rules, "utcnow", lambda: value[0])

    def advance(seconds):
        value[0] += timedelta(seconds=seconds)
    return advance


def test_video_clock_blocks_seek_replay_parallel_sessions_and_paused_time(clock):
    session = str(uuid4())
    state, changed = record_video({}, session, 1, 0, True, 120)
    assert changed and state["watched_seconds"] == 0
    clock(5)
    state, _ = record_video(state, session, 2, 5, True, 120)
    assert state["watched_seconds"] == 5
    clock(5)
    duplicate, changed = record_video(state, session, 2, 5, True, 120)
    assert duplicate == state and not changed
    with pytest.raises(ValueError, match="另一条"):
        record_video(state, session, 2, 6, True, 120)
    with pytest.raises(ValueError, match="不连续"):
        record_video(state, session, 4, 10, True, 120)
    with pytest.raises(ValueError, match="另一个播放会话"):
        record_video(state, str(uuid4()), 1, 5, True, 120)
    state, _ = record_video(state, session, 3, 100, True, 120)
    assert state["watched_seconds"] == 5  # Seek is a new baseline, never playback evidence.
    clock(5)
    state, _ = record_video(state, session, 4, 105, True, 120)
    assert state["watched_seconds"] == 10
    clock(2)
    state, _ = record_video(state, session, 5, 107, False, 120)
    assert state["watched_seconds"] == 12
    clock(40)
    state, _ = record_video(state, session, 6, 108, False, 120)
    assert state["watched_seconds"] == 12
    new_session = str(uuid4())
    state, _ = record_video(state, new_session, 1, 0, True, 120)
    clock(5)
    state, _ = record_video(state, new_session, 2, 5, True, 120)
    assert state["watched_seconds"] == 17  # Rewatch counts actual playback time.
    clock(30)
    state, _ = record_video(state, new_session, 3, 35, True, 120)
    assert state["watched_seconds"] == 17  # Long offline gaps never earn time.


def test_video_playback_rates_count_actual_wall_time_without_fast_forward_credit(clock):
    for rate in (.5, 1.5, 2, 4):
        session = str(uuid4())
        state, _ = record_video({}, session, 1, 0, True, 120, rate)
        clock(5)
        state, _ = record_video(state, session, 2, 5 * rate, True, 120, rate)
        assert state["watched_seconds"] == 5
        with pytest.raises(ValueError, match="另一条"):
            record_video(state, session, 2, 5 * rate, True, 120, 1)
    session = str(uuid4())
    state, _ = record_video({}, session, 1, 0, True, 120, 2)
    clock(5)
    state, _ = record_video(state, session, 2, 50, True, 120, 2)
    assert state["watched_seconds"] == 0  # Reported rate is bounded; a seek still earns nothing.


@pytest.fixture
def video_activity(db, teacher):
    material = make_material(db, id=9901, chapter_ids=[179], title="心跳测试视频", kind="video", owner_id=teacher["id"],
                        status="ready", storage_key="test-video.mp4", content={"pages": [], "duration_seconds": 120})
    activity = Activity(id="test-video-goal", chapter_id=179, title="观看视频", kind="lesson", material_id=9901,
                        npc_key="librarian_doc", owner_id=teacher["id"], assistant_allowed=False,
                        content={"published": True, "question_ids": [], "learning": {"video": {"minimum_seconds": 10},
                                 "highlights": {"video": [{"start_seconds": 5, "end_seconds": 20, "label": "重点"}]}}})
    db.add_all([material, activity])
    db.commit()
    return activity


def test_video_api_requires_real_heartbeats_and_keeps_teacher_and_classroom_isolated(client, db, teacher, student, classroom, video_activity, clock):
    activity = video_activity
    path = f"/api/activities/{activity.id}"
    query = {"classroom_id": classroom["id"]}
    view = client.get(path, params=query, headers=student["headers"])
    assert view.status_code == 200
    assert view.json()["learning"]["video"]["minimum_seconds"] == 10
    assert view.json()["progress"]["watched_seconds"] == 0
    packet = {**query, "material_id": 9901, "playback_session_id": str(uuid4()), "sequence": 1, "position_seconds": 0, "playing": True}
    for rate in (.1, 5, True):
        assert client.post(path + "/video-progress", json={**packet, "playback_rate": rate}, headers=student["headers"]).status_code == 422
    assert client.post(path + "/participation", json={**query, "video_time": 120}, headers=student["headers"]).status_code == 422
    assert client.post(path + "/video-progress", json=packet, headers=teacher["headers"]).status_code == 403
    assert client.post(path + "/video-progress", json={**packet, "user_id": teacher["id"]}, headers=student["headers"]).status_code == 422
    first = client.post(path + "/video-progress", json=packet, headers=student["headers"])
    assert first.status_code == 200, first.text
    assert not first.json()["completed"]
    clock(5)
    packet.update(sequence=2, position_seconds=5)
    second = client.post(path + "/video-progress", json=packet, headers=student["headers"])
    assert second.json()["watched_seconds"] == 5
    assert client.post(path + "/video-progress", json=packet, headers=student["headers"]).json()["watched_seconds"] == 5
    clock(5)
    packet.update(sequence=3, position_seconds=10, playing=False)
    complete = client.post(path + "/video-progress", json=packet, headers=student["headers"])
    assert complete.status_code == 200, complete.text
    assert complete.json()["completed"] and complete.json()["watched_seconds"] == 10
    assert client.post(path + "/participation", json={**query, "video_time": 120}, headers=student["headers"]).status_code == 200
    second_class = client.post("/api/classrooms", json={"name": "另一班级"}, headers=teacher["headers"]).json()
    assert client.put(f"/api/teacher/classrooms/{second_class['id']}/releases/179", json={"status": "published"}, headers=teacher["headers"]).status_code == 200
    assert client.get(path, params={"classroom_id": second_class["id"]}, headers=student["headers"]).status_code == 403
    assert client.post("/api/classrooms/join", json={"join_code": second_class["join_code"]}, headers=student["headers"]).status_code == 200
    other = client.get(path + "/progress", params={"classroom_id": second_class["id"]}, headers=student["headers"])
    assert other.json()["watched_seconds"] == 0 and not other.json()["completed"]
    saved = db.get(ActivityProgress, (student["id"], classroom["id"], activity.id))
    assert saved.media_progress["watched_seconds"] == 10
    assert saved.visited_pages == []
    # Material changes preserve each material's participation, never transfer its evidence.
    db.add(make_material(db, id=9902, chapter_ids=[179], title="另一视频", kind="video", owner_id=teacher["id"], status="ready",
                    storage_key="other-test-video.mp4", content={"pages": [], "duration_seconds": 120}))
    db.commit()
    assert client.put(f"/api/teacher/activities/{activity.id}", json={"material_id": 9902}, headers=teacher["headers"]).status_code == 200
    current = client.get(path + "/progress", params=query, headers=student["headers"]).json()
    assert current["watched_seconds"] == 0 and not current["completed"]
    assert client.post(path + "/video-progress", json=packet, headers=student["headers"]).status_code == 409
    replacement_packet = {**query, "material_id": 9902, "playback_session_id": str(uuid4()), "sequence": 1, "position_seconds": 0, "playing": True}
    assert client.post(path + "/video-progress", json=replacement_packet, headers=student["headers"]).status_code == 200
    clock(5)
    replacement_packet.update(sequence=2, position_seconds=5, playing=False)
    assert client.post(path + "/video-progress", json=replacement_packet, headers=student["headers"]).json()["watched_seconds"] == 5
    assert client.put(f"/api/teacher/activities/{activity.id}", json={"material_id": 9901}, headers=teacher["headers"]).status_code == 200
    restored = client.get(path + "/progress", params=query, headers=student["headers"]).json()
    assert restored["watched_seconds"] == 10 and restored["completed"]
    restored_packet = {**query, "material_id": 9901, "playback_session_id": str(uuid4()), "sequence": 1, "position_seconds": 10, "playing": False}
    assert client.post(path + "/video-progress", json=restored_packet, headers=student["headers"]).json()["watched_seconds"] == 10
    assert db.get(ActivityProgress, (student["id"], classroom["id"], activity.id)).media_progress["material_seconds"]["9902"] == 5


def test_teacher_learning_api_validates_material_replacements_and_reading_progress(client, db, teacher, student, classroom):
    material = db.get(Material, 186)
    pages = material.content["pages"]
    selected = [page for page in pages if 34 <= page["page_no"] <= 35]
    response = client.post("/api/teacher/activities", headers=teacher["headers"], json={
        "id": "test-range", "chapter_id": 179, "title": "范围阅读", "kind": "lesson", "material_id": 186,
        "npc_key": "librarian_doc", "learning": {"reading": {"scope": "range", "start_page": 34, "end_page": 35},
        "highlights": {"pages": [{"start_page": 34, "end_page": 34, "label": "重点"}]}}})
    assert response.status_code == 201, response.text
    endpoint = "/api/activities/test-range/progress"
    db.add(ActivityProgress(user_id=student["id"], classroom_id=classroom["id"], activity_id="test-range", page_no=1,
                            visited_pages=[pages[0]["id"]], completed=False))
    db.commit()
    query = {"classroom_id": classroom["id"]}
    before = client.get(endpoint, params=query, headers=student["headers"]).json()
    assert before["required_page_ids"] == [page["id"] for page in selected]
    assert before["read_count"] == 0 and before["read_total"] == 2
    for index, page in enumerate(selected):
        result = client.post(endpoint, json={**query, "page_no": page["page_no"]}, headers=student["headers"])
        assert result.status_code == 200, result.text
        assert result.json()["read_count"] == index + 1
        assert result.json()["completed"] == (index == 1)
        assert pages[0]["id"] in result.json()["visited_pages"]
    db.add(make_material(db, id=9902, chapter_ids=[179], title="其他课件", kind="text", owner_id=teacher["id"], status="ready",
                    content={"pages": [{"id": 99021, "page_no": 1, "title": "新页", "text": "正文"}]}))
    db.commit()
    invalid = client.put("/api/teacher/activities/test-range", headers=teacher["headers"], json={"material_id": 9902})
    assert invalid.status_code == 422
    invalid = client.put("/api/teacher/activities/test-range", headers=teacher["headers"], json={"learning": {"reading": {"scope": "range", "start_page": 35, "end_page": 34}}})
    assert invalid.status_code == 422
    assert db.get(Activity, "test-range").material_id == 186
    changed = client.put("/api/teacher/activities/test-range", headers=teacher["headers"], json={"learning": {"reading": {"scope": "all"}}})
    assert changed.status_code == 200, changed.text
    after = client.get(endpoint, params=query, headers=student["headers"]).json()
    assert after["read_total"] == len(pages) and after["read_count"] == 3 and not after["completed"]
    assert after["visited_pages"] == result.json()["visited_pages"]
