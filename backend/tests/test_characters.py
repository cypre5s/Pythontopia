# ruff: noqa: F811 -- imported pytest fixture
"""Unified character identity, staged private assets and placement protection."""
from copy import deepcopy
from io import BytesIO
import asyncio
from threading import Event
from time import monotonic

import httpx
import pytest
from PIL import Image
from sqlalchemy import func, select

from app.characters import validate_image
from app.course_exchange import import_bundle, load_bundle, write_package
from app.course_instances import content_mapping
from app.course_package import PackageError
from app.integration_models import AssistantCall
from app.models import Classroom, Npc, StorySession
from test_world_authoring import scene, draft, endpoint, flow, save, start  # noqa: F401


def image_bytes(size=(48, 80), format="PNG"):
    output = BytesIO()
    Image.new("RGB", size, "green").save(output, format=format)
    return output.getvalue()


def upload(client, teacher, scene, kind="sprite", content=None):
    return client.post(f"/api/teacher/classrooms/{scene['classroom'].id}/character-assets", headers=teacher["headers"],
                       data={"kind": kind, "owner_npc_id": "resident", "expected_revision": draft(client, teacher, scene)["expected_revision"]},
                       files={"file": ("test.png", content or image_bytes(), "image/png")})


def test_unplaced_character_and_speaker_share_one_identity(client, db, teacher, student, scene):
    body = draft(client, teacher, scene)
    extra = {**deepcopy(body["npcs"][0]), "id": "assistant-only", "name": "独立助教", "persona": "PRIVATE-PERSONA", "placement": None}
    body["npcs"].append(extra)
    body["flows"][0]["content"]["nodes"][0]["speaker_npc_id"] = extra["id"]
    body["configuration"]["assistant"]["default_npc_id"] = extra["id"]
    save(client, teacher, scene, body)
    view = start(client, student, scene).json()
    assert view["node"]["speaker"] == "独立助教"
    assert "PRIVATE-PERSONA" not in str(view)
    assert all(row["stable_id"] != extra["id"] for row in view["world"]["npcs"])
    assert view["world"]["default_role"]["name"] == "独立助教"
    assert view["world"]["default_role"]["sprite"]["id"] == extra["sprite"]
    current = client.get(endpoint(scene), headers=teacher["headers"]).json()
    changed = {"expected_revision": current["revision"], **{key: current[key] for key in ("npcs", "flows", "configuration", "quests")}}
    changed["npcs"][1]["name"] = "新名字"
    changed["npcs"][1]["portrait"] = "builtin:portrait:mayor_py"
    result = save(client, teacher, scene, changed)
    assert result["revision"] > current["revision"]
    assert db.get(StorySession, view["id"]) is not None
    resumed = start(client, student, scene).json()
    assert resumed["node"]["speaker"] == "新名字"
    assert resumed["node"]["portrait"].endswith("mayor_py.png")
    assert db.scalar(select(func.count()).select_from(AssistantCall)) == 0


def test_active_character_cannot_move_and_history_cannot_be_deleted(client, db, teacher, student, scene):
    body = draft(client, teacher, scene, flows=[flow(entry="resident")])
    save(client, teacher, scene, body)
    view = start(client, student, scene, npc_key=scene["npc"].key).json()
    body["expected_revision"] += 1
    body["npcs"][0]["placement"]["tile_x"] += 1
    failed = client.put(endpoint(scene), json=body, headers=teacher["headers"])
    assert failed.status_code == 409 and "进行中" in failed.text
    assert db.get(Npc, scene["npc"].key).tile_x == 22
    assert db.get(StorySession, view["id"]).status == "active"


def test_unplaced_map_entry_and_unknown_speaker_are_located_errors(client, teacher, scene):
    body = draft(client, teacher, scene, flows=[flow(entry="resident")])
    body["npcs"][0]["placement"] = None
    response = client.post(endpoint(scene, "/validate"), json=body, headers=teacher["headers"])
    assert not response.json()["valid"] and "尚未放置" in response.text
    body["npcs"][0]["placement"] = {"map_key": "town_hub", "tile_x": 22, "tile_y": 36, "direction": "up"}
    body["flows"][0]["content"]["nodes"][0]["speaker_npc_id"] = "missing"
    response = client.post(endpoint(scene, "/validate"), json=body, headers=teacher["headers"])
    assert not response.json()["valid"] and response.json()["diagnostics"][0]["target"]


def test_private_upload_publish_and_package_roundtrip(client, db, teacher, student, scene, monkeypatch, tmp_path):
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "storage"))
    first = upload(client, teacher, scene)
    assert first.status_code == 201, first.text
    asset = first.json()
    url = asset["url"]
    assert client.get(url, headers=teacher["headers"]).status_code == 200
    assert client.get(url, headers=student["headers"]).status_code == 404
    assert client.get(url).status_code == 401
    body = draft(client, teacher, scene)
    body["npcs"][0]["sprite"] = asset["id"]
    save(client, teacher, scene, body)
    assert client.get(url, headers=student["headers"]).content == image_bytes()
    from app.characters import presentation
    identity = presentation(db, scene["classroom"].id, db.get(Npc, scene["npc"].key))
    assert identity["sprite"]["url"] == url
    assert identity["sprite"]["frame_width"] == 16
    assert identity["sprite"]["frame_height"] == 20
    # An unused staged upload is excluded from the exported course.
    second = upload(client, teacher, scene, kind="portrait")
    assert second.status_code == 201
    root = tmp_path / "package"
    root.mkdir()
    write_package(db, scene["instance"], root)
    bundle = load_bundle(root)
    assert len(bundle["assets"]) == 1
    assert bundle["manifest"]["schema_version"] == 2
    target = Classroom(name="隔离导入", teacher_id=teacher["id"], join_code="characters-test")
    db.add(target)
    db.flush()
    instance = import_bundle(db, target, None, bundle, root)
    db.commit()
    import_bundle(db, target, instance, bundle, root)
    db.commit()
    mapped = content_mapping(db, instance)
    assert db.get(Npc, mapped["npc"]["resident"]).sprite == asset["id"]
    assert client.get(url.replace(f"/{scene['classroom'].id}/", f"/{target.id}/"), headers=student["headers"]).status_code == 403
    again = tmp_path / "again"
    again.mkdir()
    write_package(db, instance, again)
    reexported = load_bundle(again)
    assert reexported["data"]["npc"] == bundle["data"]["npc"]
    assert reexported["assets"][0]["sha256"] == bundle["assets"][0]["sha256"]
    (root / bundle["assets"][0]["path"]).unlink()
    with pytest.raises(PackageError):
        load_bundle(root)


@pytest.mark.parametrize("size,format,kind", [((47, 80), "PNG", "sprite"), ((48, 80), "JPEG", "sprite"), ((5000, 1), "PNG", "portrait")])
def test_invalid_character_image_rejected(size, format, kind, tmp_path):
    path = tmp_path / "image"
    path.write_bytes(image_bytes(size, format))
    with pytest.raises(ValueError):
        validate_image(path, kind)


def test_failed_upload_keeps_published_reference(client, db, teacher, scene, monkeypatch, tmp_path):
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "storage"))
    before = scene["npc"].sprite
    response = upload(client, teacher, scene, content=b"not a png")
    assert response.status_code == 422
    assert db.get(Npc, scene["npc"].key).sprite == before


def test_replacement_deletes_old_file_only_after_successful_save(client, db, teacher, scene, monkeypatch, tmp_path):
    from app.course_exchange_models import CoursePackageState
    from app.storage import get_storage
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "storage"))
    first = upload(client, teacher, scene).json()
    body = draft(client, teacher, scene)
    body["npcs"][0]["sprite"] = first["id"]
    save(client, teacher, scene, body)
    state = db.get(CoursePackageState, scene["instance"].id)
    old_key = state.configuration["_assets"][0]["storage_key"]
    second = upload(client, teacher, scene).json()
    assert get_storage().size(old_key) > 0
    assert client.get(first["url"], headers=teacher["headers"]).status_code == 200
    current = draft(client, teacher, scene)
    current["npcs"][0]["sprite"] = second["id"]
    stale = {**current, "expected_revision": current["expected_revision"] - 1}
    assert client.put(endpoint(scene), json=stale, headers=teacher["headers"]).status_code == 409
    assert get_storage().size(old_key) > 0
    save(client, teacher, scene, current)
    with pytest.raises(FileNotFoundError):
        get_storage().size(old_key)
    assert client.get(first["url"], headers=teacher["headers"]).status_code == 404
    assert client.get(second["url"], headers=teacher["headers"]).status_code == 200


def test_owned_asset_cannot_be_shared_and_deleted_role_retires_file(client, db, teacher, scene, monkeypatch, tmp_path):
    from app.course_exchange_models import CoursePackageState
    from app.storage import get_storage
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "storage"))
    asset = upload(client, teacher, scene).json()
    body = draft(client, teacher, scene)
    body["npcs"].append({**deepcopy(body["npcs"][0]), "id": "other", "placement": None, "sprite": asset["id"]})
    assert client.put(endpoint(scene), json=body, headers=teacher["headers"]).status_code == 422
    body["npcs"].pop()
    body["npcs"][0]["sprite"] = asset["id"]
    save(client, teacher, scene, body)
    state = db.get(CoursePackageState, scene["instance"].id)
    key = state.configuration["_assets"][0]["storage_key"]
    # The activity reference blocks deletion, retaining the image.
    body = draft(client, teacher, scene)
    body["npcs"] = []
    assert client.put(endpoint(scene), json=body, headers=teacher["headers"]).status_code == 422
    assert get_storage().size(key) > 0
    db.delete(scene["activity"])
    db.commit()
    save(client, teacher, scene, body)
    with pytest.raises(FileNotFoundError):
        get_storage().size(key)


def test_abandoned_upload_expiry_and_failed_file_delete_retry(client, db, teacher, scene, monkeypatch, tmp_path):
    from app.character_assets import retire_unused_assets, drain_character_garbage
    from app.course_exchange_models import CoursePackageState
    from app.storage import LocalStorage
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "storage"))
    asset = upload(client, teacher, scene).json()
    state = db.get(CoursePackageState, scene["instance"].id)
    config = deepcopy(state.configuration)
    config["_assets"][0]["staged_at"] = 0
    state.configuration = config
    retire_unused_assets(state, [], expired_only=True)
    db.commit()
    remove = LocalStorage.delete
    def fail(*args):
        raise OSError("temporary storage failure")
    monkeypatch.setattr(LocalStorage, "delete", fail)
    drain_character_garbage(db, state)
    assert state.configuration["_character_garbage"]
    assert client.get(asset["url"], headers=teacher["headers"]).status_code == 404
    monkeypatch.setattr(LocalStorage, "delete", remove)
    drain_character_garbage(db, state)
    assert state.configuration["_character_garbage"] == []


def test_upload_waiting_for_course_lock_keeps_api_responsive(client, teacher, scene, monkeypatch, tmp_path):
    from app import character_assets
    from app.main import app

    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "storage"))
    entered, release = Event(), Event()
    resolve_instance = character_assets.teacher_instance_id

    def wait_for_course(*args, **kwargs):
        entered.set()
        release.wait(3)  # Bounded stand-in for another request holding the course lock.
        return resolve_instance(*args, **kwargs)

    monkeypatch.setattr(character_assets, "teacher_instance_id", wait_for_course)

    async def exercise():
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as api:
            started = monotonic()
            pending = asyncio.create_task(api.post(
                f"/api/teacher/classrooms/{scene['classroom'].id}/character-assets", headers=teacher["headers"],
                data={"kind": "sprite", "owner_npc_id": "resident", "expected_revision": scene["instance"].revision},
                files={"file": ("sprite.png", image_bytes(), "image/png")},
            ))
            try:
                assert await asyncio.to_thread(entered.wait, 2)
                health = await api.get("/openapi.json")
                elapsed = monotonic() - started
            finally:
                release.set()
                uploaded = await pending
            assert health.status_code == 200
            assert elapsed < 2, "A waiting upload must not block the API event loop"
            assert uploaded.status_code == 201, uploaded.text

    asyncio.run(exercise())


def test_unreferenced_character_delete_preserves_other_appearance(client, teacher, scene):
    body = draft(client, teacher, scene)
    original = deepcopy(body["npcs"][0])
    body["npcs"].append({**original, "id": "temporary", "placement": None, "sprite": "builtin:sprite:chara_10"})
    body["expected_revision"] = save(client, teacher, scene, body)["revision"]
    body["npcs"].reverse()
    body["expected_revision"] = save(client, teacher, scene, body)["revision"]
    body["npcs"] = [original]
    save(client, teacher, scene, body)
    current = client.get(endpoint(scene), headers=teacher["headers"]).json()
    assert current["npcs"] == [original]


def test_spoken_character_history_blocks_deletion_after_references_removed(client, db, teacher, student, scene):
    body = draft(client, teacher, scene)
    extra = {**deepcopy(body["npcs"][0]), "id": "narrating-character", "placement": None}
    body["npcs"].append(extra)
    body["flows"][0]["content"]["nodes"][0]["speaker_npc_id"] = extra["id"]
    body["expected_revision"] = save(client, teacher, scene, body)["revision"]
    view = start(client, student, scene).json()
    db.get(StorySession, view["id"]).status = "ended"
    db.commit()
    body["npcs"].pop()
    body["flows"][0]["content"]["nodes"][0].pop("speaker_npc_id")
    response = client.put(endpoint(scene), headers=teacher["headers"], json=body)
    assert response.status_code == 409 and "互动历史" in response.text
