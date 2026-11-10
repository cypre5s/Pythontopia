"""World authoring uses real scoped definitions, collision and learning evidence."""
from copy import deepcopy
from uuid import uuid4

import pytest
from sqlalchemy import func, select

from app.content_relations import set_material_chapters
from app.models import (Activity, BuildingUnlock, Chapter, ChapterRelease, Classroom, CourseInstance,
                        LearningEvent, Material, Membership, Npc, PlayerQuest, StorySession, WorldFlag)
from app.integration_models import AssistantCall
from app.world_authoring import reachable_cells


@pytest.fixture
def scene(db, teacher, student):
    classroom = Classroom(name="世界编辑", teacher_id=teacher["id"], join_code=uuid4().hex[:24])
    db.add(classroom)
    db.flush()
    instance = CourseInstance(classroom_id=classroom.id, package_id="world-test", name="世界课程", package_version="1")
    db.add(instance)
    db.add(Membership(user_id=student["id"], classroom_id=classroom.id))
    db.flush()
    fields = {"owner_id": teacher["id"], "course_instance_id": instance.id}
    chapter = Chapter(stable_id="ch1", title="第一章", order_index=1, **fields)
    db.add(chapter)
    db.flush()
    material = Material(stable_id="book", title="资料", kind="text", status="ready",
                        content={"library_available": True, "pages": [{"id": 901, "page_no": 1, "title": "页", "text": "真实资料"}]}, **fields)
    db.add(material)
    db.flush()
    set_material_chapters(db, material.id, [chapter.id])
    npc = Npc(key=f"editor-npc-{instance.id}", stable_id="resident", course_instance_id=instance.id, name="居民",
              map_key="town_hub", tile_x=22, tile_y=36, portrait="", dialogue=[])
    db.add(npc)
    db.add(ChapterRelease(classroom_id=classroom.id, chapter_id=chapter.id, status="published"))
    activity = Activity(id=f"editor-lesson-{instance.id}", stable_id="lesson", chapter_id=chapter.id, title="阅读",
                        kind="lesson", material_id=material.id, npc_key=npc.key,
                        content={"published": True, "learning": {"reading": {"scope": "all"}}}, **fields)
    db.add(activity)
    db.commit()
    return {"classroom": classroom, "instance": instance, "chapter": chapter, "npc": npc, "activity": activity}


def endpoint(scene, suffix=""):
    return f"/api/teacher/classrooms/{scene['classroom'].id}/authoring{suffix}"


def interaction(*, object_id="town_square", name="square"):
    return {"id": name, "label": "交互地点", "prompt": "开始交谈", "trigger": "click", "map_key": "town_hub", "target": {"object_id": object_id}}


def flow(*, stable="public", entry="interaction:square", mode="ambient", nodes=None):
    return {"id": stable, "title": "公共流程", "chapter_id": None, "quest_id": None,
            "content": {"schema_version": 1, "mode": mode, "priority": 0, "bindings": {},
                        "entries": {entry: [{"when": {"always": True}, "node": "start"}]},
                        "nodes": nodes or [{"id": "start", "type": "dialogue", "text": "欢迎", "next": "end"}, {"id": "end", "type": "end"}]}}


def draft(client, teacher, scene, *, flows=None, interactions=None):
    response = client.get(endpoint(scene), headers=teacher["headers"])
    assert response.status_code == 200, response.text
    current = response.json()
    return {"expected_revision": current["revision"], "flows": flows if flows is not None else [flow()],
            "configuration": {"world": {"interactions": interactions if interactions is not None else [interaction()], "npcs": {}},
                              "buildings": {}, "assistant": {}, "editor": {"flow_layouts": {"public": {"start": {"x": 10, "y": 20}}}}}}


def save(client, teacher, scene, body):
    response = client.put(endpoint(scene), json=body, headers=teacher["headers"])
    assert response.status_code == 200, response.text
    return response.json()


def start(client, student, scene, interaction_id="square", **target):
    body = {"classroom_id": scene["classroom"].id, **(target or {"interaction_id": interaction_id})}
    return client.post("/api/story/interactions", json=body, headers=student["headers"])


def advance(client, student, scene, view, suffix="advance", event_id=None):
    return client.post(f"/api/story/sessions/{view['id']}/{suffix}", headers=student["headers"], json={
        "classroom_id": scene["classroom"].id, "revision": view["revision"], "event_id": event_id or str(uuid4())})


def test_generic_object_flow_save_validate_resume_preview_and_no_incidental_ai(client, db, teacher, student, scene):
    body = draft(client, teacher, scene)
    preview = client.post(endpoint(scene, "/preview"), json={**body, "student_id": student["id"], "entry": "interaction:square"}, headers=teacher["headers"])
    assert preview.status_code == 200, preview.text
    assert preview.json()["selected_flow_id"] == "public"
    assert db.scalar(select(func.count()).select_from(StorySession)) == 0
    save(client, teacher, scene, body)
    response = start(client, student, scene)
    assert response.status_code == 200, response.text
    view = response.json()
    assert view["npc_key"] is None and view["interaction_id"] == "square"
    assert view["world"]["interactions"][0]["points"]
    resumed = start(client, student, scene).json()
    assert resumed["id"] == view["id"]
    result = advance(client, student, scene, view, event_id="one")
    assert result.status_code == 200 and result.json()["status"] == "ended"
    assert advance(client, student, scene, view, event_id="one").json()["revision"] == 1
    assert db.scalar(select(func.count()).select_from(LearningEvent)) == 0
    assert db.scalar(select(func.count()).select_from(AssistantCall)) == 0
    assert db.scalar(select(func.count()).select_from(BuildingUnlock)) == 0


def test_authoring_revision_scope_and_active_session_fence(client, teacher, student, scene):
    body = draft(client, teacher, scene)
    save(client, teacher, scene, body)
    assert client.put(endpoint(scene), json=body, headers=teacher["headers"]).status_code == 409
    assert client.get(endpoint(scene), headers=student["headers"]).status_code == 403
    view = start(client, student, scene).json()
    body["expected_revision"] += 1
    body["flows"][0]["title"] = "小镇公共流程"
    body["configuration"]["world"]["interactions"][0]["prompt"] = "新的交谈提示"
    assert client.post(endpoint(scene, "/validate"), json=body, headers=teacher["headers"]).json()["valid"] is True
    body["expected_revision"] = save(client, teacher, scene, body)["revision"]
    assert start(client, student, scene).json()["id"] == view["id"]
    body["flows"][0]["content"]["nodes"][0]["text"] = "修改"
    validation = client.post(endpoint(scene, "/validate"), json=body, headers=teacher["headers"]).json()
    assert validation["valid"] is False
    issue, = validation["diagnostics"]
    assert issue["code"] == "active_story_session"
    assert issue["path"] == "/flows/0/content/nodes/0"
    assert issue["target"] == {"kind": "node", "id": "start", "flow_id": "public"}
    assert "小镇公共流程" in issue["message"] and "1 人正在互动" in issue["message"]
    assert "结束本次互动" in issue["suggestion"]
    blocked = client.put(endpoint(scene), json=body, headers=teacher["headers"])
    assert blocked.status_code == 409, blocked.text
    assert blocked.json()["detail"]["diagnostics"] == [issue]
    assert advance(client, student, scene, view, "end").status_code == 200
    save(client, teacher, scene, body)


def test_authoring_entry_issue_uses_portable_npc_id_and_catalog_location(client, teacher, scene):
    item = flow(entry="resident")
    item["content"]["entries"]["resident"][0]["node"] = ""
    body = draft(client, teacher, scene, flows=[item], interactions=[])
    catalog = client.get(endpoint(scene), headers=teacher["headers"]).json()["catalog"]
    assert catalog["npcs"] == [{"id": "resident", "name": "居民", "map_key": "town_hub"}]
    validation = client.post(endpoint(scene, "/validate"), json=body, headers=teacher["headers"]).json()
    assert validation["valid"] is False
    issue, = validation["diagnostics"]
    assert issue["code"] == "missing_link"
    assert issue["path"] == "/flows/0/content/entries/resident/0/node"
    assert issue["target"] == {"kind": "entry", "id": "resident", "flow_id": "public"}
    assert "入口「居民」" in issue["message"]


@pytest.mark.parametrize("mutation", ["priority", "cycle", "unreachable", "bounds", "overlap", "unsafe_unlock", "unknown_ref"])
def test_graph_and_map_validation_rejects_invalid_contract(client, teacher, scene, mutation):
    body = draft(client, teacher, scene)
    content = body["flows"][0]["content"]
    if mutation == "priority":
        body["flows"].append(flow(stable="conflict"))
    elif mutation == "cycle":
        content["nodes"][0]["next"] = "start"
    elif mutation == "unreachable":
        content["nodes"].append({"id": "orphan", "type": "end"})
    elif mutation == "bounds":
        body["configuration"]["world"]["interactions"][0]["target"] = {"region": {"x": 999, "y": 0, "width": 2, "height": 2}}
    elif mutation == "overlap":
        region = {"region": {"x": 22, "y": 34, "width": 2, "height": 2}}
        body["configuration"]["world"]["interactions"] = [{**interaction(name=name), "trigger": "enter", "target": region} for name in ("square", "other")]
    elif mutation == "unsafe_unlock":
        content["mode"] = "public"
        content["nodes"][0] = {"id": "start", "type": "unlock_building", "building_id": "library", "when": {"always": True}, "next": "end"}
    else:
        content["when"] = {"not": {"activity_completed": "foreign"}}
    result = client.post(endpoint(scene, "/validate"), json=body, headers=teacher["headers"])
    assert result.status_code == 200, result.text
    assert result.json()["valid"] is False
    issue, = result.json()["diagnostics"]
    targets = {
        "priority": ("entry", "interaction:square"), "cycle": ("node", "start"),
        "unreachable": ("node", "orphan"), "bounds": ("interaction", "square"),
        "overlap": ("interaction", "other"), "unsafe_unlock": ("node", "start"), "unknown_ref": ("flow", "public"),
    }
    assert (issue["target"]["kind"], issue["target"]["id"]) == targets[mutation]
    rejected = client.put(endpoint(scene), json=body, headers=teacher["headers"])
    assert rejected.status_code == 422, rejected.text
    assert rejected.json()["detail"]["diagnostics"] == [issue]


def test_public_activity_waits_real_completion_before_idempotent_unlock(client, db, teacher, student, scene):
    nodes = [{"id": "start", "type": "activity", "binding": "read", "returns": {"completed": "unlock", "suspended": "pause", "unavailable": "pause"}},
             {"id": "unlock", "type": "unlock_building", "building_id": "library", "when": {"activity_completed": "lesson"}, "next": "end"},
             {"id": "pause", "type": "dialogue", "text": "尚未读完", "next": "end"}, {"id": "end", "type": "end"}]
    item = flow(mode="public", nodes=nodes)
    item["content"]["bindings"] = {"read": {"activity_id": "lesson"}}
    body = draft(client, teacher, scene, flows=[item])
    body["configuration"]["world"]["interactions"][0]["when"] = {"not": {"activity_completed": "lesson"}}
    revision = save(client, teacher, scene, body)["revision"]
    first = start(client, student, scene)
    assert first.status_code == 200, first.text
    assert first.json()["status"] == "waiting"
    changed = deepcopy(body)
    changed["expected_revision"] = revision
    changed["flows"][0]["content"]["nodes"][2]["text"] = "请继续阅读"
    validation = client.post(endpoint(scene, "/validate"), json=changed, headers=teacher["headers"]).json()
    assert validation["valid"] is False
    issue, = validation["diagnostics"]
    assert issue["code"] == "active_story_session"
    assert issue["target"]["id"] == "start" and "1 人在等待学习活动返回" in issue["message"]
    returned = advance(client, student, scene, first.json(), "return").json()
    assert returned["node"]["id"] == "pause" and returned["world"]["building_unlock"] == []
    advance(client, student, scene, returned)
    view = start(client, student, scene).json()
    progress = client.post(f"/api/activities/{scene['activity'].id}/progress", headers=student["headers"],
        json={"classroom_id": scene["classroom"].id, "page_no": 1})
    assert progress.status_code == 200, progress.text
    assert progress.json()["completed"] is True
    done = advance(client, student, scene, view, "return", "unlock-once")
    assert done.status_code == 200, done.text
    assert done.json()["world"]["building_unlock"] == ["library"]
    assert advance(client, student, scene, view, "return", "unlock-once").status_code == 200
    assert db.scalar(select(func.count()).select_from(BuildingUnlock)) == 1


@pytest.mark.parametrize("legacy_text", [False, True])
def test_building_entry_action_cannot_bypass_dialogue_and_exit_stays_safe(client, teacher, student, scene, legacy_text):
    nodes = [{"id": "start", "type": "dialogue", "text": "进入前对白", "next": "enter"},
             {"id": "enter", "type": "action", "action": "enter_building", "building_id": "library",
              **({"text": "进入图书馆"} if legacy_text else {}), "next": "inside"},
             {"id": "inside", "type": "dialogue", "text": "室内对白", "next": "end"}, {"id": "end", "type": "end"}]
    body = draft(client, teacher, scene, flows=[flow(mode="public", nodes=nodes)], interactions=[interaction(object_id="library")])
    body["configuration"]["buildings"] = {"library": {"enabled": True, "default_unlocked": True, "blocked_message": "未开放"}}
    save(client, teacher, scene, body)
    cid = scene["classroom"].id
    assert client.put("/api/world/position", json={"classroom_id": cid, "map_key": "town_hub", "tile_x": 8, "tile_y": 22}, headers=student["headers"]).status_code == 200
    assert client.post("/api/world/transition", json={"classroom_id": cid, "portal_id": "library_door"}, headers=student["headers"]).status_code == 409
    view = start(client, student, scene).json()
    view = advance(client, student, scene, view).json()
    assert view["node"]["action"] == "enter_building"
    assert view["node"]["text"] == ("进入图书馆" if legacy_text else "")
    entered = advance(client, student, scene, view)
    assert entered.status_code == 200, entered.text
    assert entered.json()["world"]["player"]["map_key"] == "library"
    assert advance(client, student, scene, entered.json()).status_code == 200
    body["expected_revision"] += 1
    body["configuration"]["buildings"]["library"]["enabled"] = False
    save(client, teacher, scene, body)
    assert client.post("/api/world/transition", json={"classroom_id": cid, "portal_id": "library_exit"}, headers=student["headers"]).status_code == 200


def test_npc_presence_and_default_buildings_share_conditions(client, db, teacher, student, scene):
    body = draft(client, teacher, scene, flows=[flow(entry="resident")], interactions=[])
    body["configuration"]["world"]["npcs"] = {"resident": {"when": {"building_unlocked": "library"}}}
    save(client, teacher, scene, body)
    cid = scene["classroom"].id
    assert client.get(f"/api/world?classroom_id={cid}", headers=student["headers"]).json()["npcs"] == []
    assert start(client, student, scene, npc_key=scene["npc"].key).status_code == 403
    db.add(WorldFlag(user_id=student["id"], classroom_id=cid, key="library_unlocked"))
    db.commit()
    world = client.get(f"/api/world?classroom_id={cid}", headers=student["headers"]).json()
    assert len(world["npcs"]) == 1 and world["building_unlock"] == ["library"]
    assert start(client, student, scene, npc_key=scene["npc"].key).status_code == 200


def test_reachable_region_server_proximity_and_other_session_conflict(client, teacher, student, scene):
    region = {**interaction(), "trigger": "enter", "target": {"region": {"x": 22, "y": 34, "width": 1, "height": 1}}}
    body = draft(client, teacher, scene, interactions=[region], flows=[flow(), flow(stable="npc", entry="resident")])
    save(client, teacher, scene, body)
    assert (22, 34) in reachable_cells("town_hub")
    assert start(client, student, scene).status_code == 422
    client.put("/api/world/position", json={"classroom_id": scene["classroom"].id, "map_key": "town_hub", "tile_x": 22, "tile_y": 34}, headers=student["headers"])
    assert start(client, student, scene).status_code == 200
    assert start(client, student, scene, npc_key=scene["npc"].key).status_code == 409


def test_chapter_flow_precedes_public_then_ordered_branch_uses_verified_state(client, teacher, student, scene):
    chapter = flow(stable="chapter")
    chapter["chapter_id"] = "ch1"
    chapter["content"]["nodes"][0]["text"] = "章节对白"
    common = flow()
    body = draft(client, teacher, scene, flows=[common, chapter])
    save(client, teacher, scene, body)
    view = start(client, student, scene).json()
    assert view["node"]["text"] == "章节对白"
    advance(client, student, scene, view)
    body["expected_revision"] += 1
    body["flows"][1]["content"]["when"] = {"activity_completed": "lesson"}
    save(client, teacher, scene, body)
    assert start(client, student, scene).json()["node"]["text"] == "欢迎"


def test_task_dag_authoring_accepts_valid_refs_and_rejects_foreign_activity(client, teacher, scene):
    body = draft(client, teacher, scene)
    definition = {"id": "teaching", "chapter_id": "ch1", "title": "阅读目标", "kind": "side", "effects": [],
                  "nodes": [{"id": "read", "title": "阅读", "type": "STUDY_LESSON", "activity_id": "lesson", "dependencies": [], "aggregation": "ALL"}]}
    body["quests"] = [{"id": "teaching", "chapter_id": "ch1", "title": "阅读目标", "definition": definition}]
    save(client, teacher, scene, body)
    got = client.get(endpoint(scene), headers=teacher["headers"]).json()
    assert got["quests"][0]["definition"]["nodes"][0]["activity_id"] == "lesson"
    body["expected_revision"] += 1
    invalid = deepcopy(body)
    invalid["quests"][0]["definition"]["nodes"][0]["activity_id"] = "foreign"
    assert client.put(endpoint(scene), json=invalid, headers=teacher["headers"]).status_code == 422


def test_authored_region_produces_exactly_one_verified_map_goal_and_unlock(client, db, teacher, student, scene):
    body = draft(client, teacher, scene, flows=[flow(mode="public")])
    body["configuration"]["world"]["interactions"][0]["target"] = {"region": {"x": 22, "y": 36, "width": 1, "height": 1}}
    definition = {"id": "map-task", "chapter_id": "ch1", "title": "区域交互", "kind": "side",
                  "effects": [{"type": "unlock_building", "building_id": "library"}],
                  "nodes": [{"id": "inspect", "title": "查看", "type": "INTERACT_OBJECT", "target": "square", "dependencies": []}]}
    body["quests"] = [{"id": "map-task", "title": "区域交互", "chapter_id": "ch1", "definition": definition}]
    save(client, teacher, scene, body)
    first = start(client, student, scene)
    assert first.status_code == 200, first.text
    assert first.json()["world"]["building_unlock"] == ["library"]
    assert start(client, student, scene).json()["id"] == first.json()["id"]
    assert db.scalar(select(func.count()).select_from(LearningEvent)) == 1
    assert db.scalar(select(PlayerQuest.status).where(PlayerQuest.classroom_id == scene["classroom"].id)) == "complete"
    other = Classroom(name="其他课程", teacher_id=teacher["id"], join_code=uuid4().hex[:24])
    db.add(other)
    db.flush()
    db.add(Membership(user_id=student["id"], classroom_id=other.id))
    db.add(CourseInstance(classroom_id=other.id, package_id="other", name="其他", package_version="1"))
    db.commit()
    world = client.get(f"/api/world?classroom_id={other.id}", headers=student["headers"]).json()
    assert world["building_unlock"] == [] and world["building_access"]["library"]["can_enter"] is False


def test_ambient_map_interaction_never_completes_task(client, db, teacher, student, scene):
    body = draft(client, teacher, scene)
    definition = {"id": "map-task", "chapter_id": "ch1", "title": "查看广场", "kind": "side", "effects": [],
                  "nodes": [{"id": "inspect", "title": "查看", "type": "INTERACT_OBJECT", "target": "square", "dependencies": []}]}
    body["quests"] = [{"id": "map-task", "title": "查看广场", "chapter_id": "ch1", "definition": definition}]
    save(client, teacher, scene, body)
    assert start(client, student, scene).status_code == 200
    assert db.scalar(select(func.count()).select_from(LearningEvent)) == 0


def test_draft_paper_freezes_question_set_and_kind_but_allows_identical_order(client, teacher, student, scene):
    cid = scene["classroom"].id
    query = {"classroom_id": cid}
    ids = []
    for key in ("a", "b"):
        created = client.post("/api/teacher/questions", headers=teacher["headers"], params=query, json={
            "id": key, "title": key, "prompt": "Test", "kind": "boolean", "answer": True, "status": "ready", "chapter_ids": [scene["chapter"].id]})
        assert created.status_code == 201, created.text
        ids.append(created.json()["id"])
    result = client.post("/api/teacher/activities", headers=teacher["headers"], params=query, json={"id": "paper", "title": "Paper",
        "kind": "assessment", "chapter_id": scene["chapter"].id, "npc_key": scene["npc"].key})
    assert result.status_code == 201, result.text
    activity_id = result.json()["id"]
    path = f"/api/teacher/activities/{activity_id}/questions"
    assert client.put(path, headers=teacher["headers"], params=query, json={"question_ids": ids}).status_code == 200
    draft = client.put(f"/api/activities/{activity_id}/assessment", headers=student["headers"], json={
        "classroom_id": cid, "revision": 0, "answers": [{"question_id": ids[0], "answer": True}]})
    assert draft.status_code == 200, draft.text
    assert client.put(path, headers=teacher["headers"], params=query, json={"question_ids": list(reversed(ids))}).status_code == 409
    assert client.put(path, headers=teacher["headers"], params=query, json={"question_ids": ids[:1]}).status_code == 409
    assert client.put(path, headers=teacher["headers"], params=query, json={"question_ids": ids}).status_code == 200
    assert client.put(f"/api/teacher/questions/{ids[0]}", headers=teacher["headers"], params=query,
                      json={"kind": "fill", "answer": "yes"}).status_code == 409


def test_actual_submission_freezes_question_kind(client, teacher, student, scene):
    query = {"classroom_id": scene["classroom"].id}
    created = client.post("/api/teacher/questions", headers=teacher["headers"], params=query, json={
        "id": "submitted", "title": "Question", "prompt": "Test", "kind": "boolean", "answer": True,
        "status": "ready", "chapter_ids": [scene["chapter"].id]})
    assert created.status_code == 201
    question_id = created.json()["id"]
    created = client.post("/api/teacher/activities", headers=teacher["headers"], params=query, json={"id": "practice", "title": "Practice",
        "kind": "practice", "chapter_id": scene["chapter"].id, "npc_key": scene["npc"].key})
    assert created.status_code == 201
    activity_id = created.json()["id"]
    assert client.put(f"/api/teacher/activities/{activity_id}/questions", headers=teacher["headers"], params=query,
                      json={"question_ids": [question_id]}).status_code == 200
    submitted = client.post("/api/submissions", headers=student["headers"], json={**query, "activity_id": activity_id,
        "question_id": question_id, "idempotency_key": str(uuid4()), "answer": True})
    assert submitted.status_code == 201, submitted.text
    assert client.put(f"/api/teacher/questions/{question_id}", headers=teacher["headers"], params=query,
                      json={"kind": "fill", "answer": "yes"}).status_code == 409


def test_preview_resolves_unsaved_task_readiness_without_writing_progress(client, db, teacher, student, scene):
    item = flow(stable="task-flow", entry="resident", mode="task")
    item["chapter_id"], item["quest_id"] = "ch1", "draft-task"
    item["content"]["when"] = {"goal_ready": "talk"}
    body = draft(client, teacher, scene, flows=[item], interactions=[])
    body["quests"] = [{"id": "draft-task", "chapter_id": "ch1", "title": "Draft task", "definition": {
        "id": "draft-task", "chapter_id": "ch1", "title": "Draft task", "nodes": [
            {"id": "talk", "title": "Talk", "type": "TALK_TO_NPC", "target": "resident", "dependencies": []}], "effects": []}}]
    response = client.post(endpoint(scene, "/preview"), headers=teacher["headers"], json={**body,
        "student_id": student["id"], "entry": "resident"})
    assert response.status_code == 200, response.text
    assert response.json()["selected_flow_id"] == "task-flow"
    assert response.json()["node_id"] == "start"
    assert db.scalar(select(func.count()).select_from(PlayerQuest)) == 0
    assert db.scalar(select(func.count()).select_from(StorySession)) == 0


def test_explicit_end_can_close_a_now_unavailable_chapter_session(client, db, teacher, student, scene):
    item = flow(entry="resident")
    item["chapter_id"] = "ch1"
    body = draft(client, teacher, scene, flows=[item], interactions=[])
    save(client, teacher, scene, body)
    view = start(client, student, scene, npc_key=scene["npc"].key).json()
    release = db.get(ChapterRelease, (scene["classroom"].id, scene["chapter"].id))
    release.status = "paused"
    db.commit()
    assert client.get(f"/api/story/current?classroom_id={scene['classroom'].id}", headers=student["headers"]).status_code == 403
    ended = advance(client, student, scene, view, "end")
    assert ended.status_code == 200, ended.text
    assert ended.json()["status"] == "ended"
