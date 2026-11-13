# ruff: noqa: F811
"""Course defaults, explicit overrides and revision/authorization boundaries."""
from copy import deepcopy

from app.course_assistant import activity_npc, default_npc
from app.course_exchange import import_bundle, write_package
from app.course_exchange_models import CoursePackageState
from app.models import Activity, Npc
from test_world_authoring import scene  # noqa: F401


def test_settings_default_override_conflict_and_authorization(client, db, teacher, student, scene):
    cid = scene["classroom"].id
    url = f"/api/teacher/classrooms/{cid}/course/settings"
    initial = client.get(url, headers=teacher["headers"]).json()
    assert client.get(url, headers=student["headers"]).status_code == 403
    state = CoursePackageState(course_instance_id=scene["instance"].id, configuration={"_sentinel": True})
    db.add(state)
    db.commit()
    body = {"expected_revision": initial["revision"], "assistant": {"enabled": True, "default_npc_id": "resident", "limits": {"model_calls": 1}}}
    assert client.put(url, json=body, headers=teacher["headers"]).status_code == 200
    assert client.put(url, json=body, headers=teacher["headers"]).status_code == 409
    assert db.get(CoursePackageState, scene["instance"].id).configuration["_sentinel"] is True
    activity = scene["activity"]
    activity.npc_key = None
    db.commit()
    assert activity_npc(db, cid, activity).key == default_npc(db, cid).key == scene["npc"].key
    other = Npc(key="override", stable_id="override", course_instance_id=scene["instance"].id, name="独立角色",
                portrait="builtin:portrait:professor", sprite="builtin:sprite:chara_2")
    db.add(other)
    activity.npc_key = other.key
    db.commit()
    assert activity_npc(db, cid, activity).key == "override"
    assert default_npc(db, cid).key == scene["npc"].key
    new = client.get(url, headers=teacher["headers"]).json()
    for settings in ({"library_npc_id": "resident"}, {"default_npc_id": "outside"}, {"default_npc_id": []}, {"limits": {"model_calls": 100}}):
        result = client.put(url, headers=teacher["headers"], json={"expected_revision": new["revision"], "assistant": settings})
        assert result.status_code == 422
    authoring = f"/api/teacher/classrooms/{cid}/authoring"
    draft = client.get(authoring, headers=teacher["headers"]).json()
    payload = {key: draft[key] for key in ("npcs", "flows", "quests", "configuration")}
    payload.update(expected_revision=draft["revision"], npcs=[row for row in draft["npcs"] if row["id"] != "resident"])
    result = client.put(authoring, headers=teacher["headers"], json=payload)
    assert result.status_code in (409, 422) and "默认答疑角色不存在" in result.text


def test_activity_can_select_and_clear_override(client, teacher, scene):
    path = f"/api/teacher/activities/{scene['activity'].id}?classroom_id={scene['classroom'].id}"
    for npc in (None, scene["npc"].key, None):
        result = client.put(path, headers=teacher["headers"], json={"npc_key": npc})
        assert result.status_code == 200, result.text


def test_inherited_activity_roundtrip(db, teacher, scene, tmp_path):
    state = db.get(CoursePackageState, scene["instance"].id)
    if state is None:
        state = CoursePackageState(course_instance_id=scene["instance"].id, configuration={})
        db.add(state)
    state.configuration = {"world": {}, "buildings": {}, "editor": {}, "assistant": {"default_npc_id": "resident"}}
    scene["activity"].npc_key = None
    db.commit()
    package = tmp_path / "package"
    package.mkdir()
    write_package(db, scene["instance"], package)
    from app.course_exchange import load_bundle
    bundle = load_bundle(package)
    assert bundle["data"]["activity"][0]["npc_key"] is None
    before = deepcopy(state.configuration["assistant"])
    import_bundle(db, scene["classroom"], scene["instance"], bundle, package)
    db.commit()
    assert db.get(Activity, scene["activity"].id).npc_key is None
    assert db.get(CoursePackageState, scene["instance"].id).configuration["assistant"] == before
