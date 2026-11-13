"""Classroom course namespaces are enforced even for the same teacher."""

from uuid import uuid4

import pytest
from sqlalchemy import select

from app.content_relations import set_activity_questions, set_material_chapters, set_question_chapters
from app.models import (
    Activity, ActivityQuestion, Chapter, ChapterRelease, Classroom, CourseInstance, Material, Membership,
    Npc, Question, QuestDefinition, StoryDefinition, StorySession,
)


@pytest.fixture
def spaces(db, teacher, student):
    rows = []
    for suffix in ("a", "b"):
        classroom = Classroom(name=f"课程{suffix}", teacher_id=teacher["id"], join_code=uuid4().hex[:24])
        db.add(classroom)
        db.flush()
        db.add(Membership(user_id=student["id"], classroom_id=classroom.id))
        instance = CourseInstance(classroom_id=classroom.id, package_id="same-package", package_version="1", name="课程")
        db.add(instance)
        db.flush()
        common = {"course_instance_id": instance.id, "owner_id": teacher["id"]}
        chapter = Chapter(stable_id="chapter-1", title=f"章节{suffix}", order_index=1, **common)
        db.add(chapter)
        db.flush()
        db.add(ChapterRelease(classroom_id=classroom.id, chapter_id=chapter.id, status="published"))
        material = Material(stable_id="material-1", title=f"资料{suffix}", kind="text", status="ready",
                            content={"library_available": True, "pages": [{"id": 1, "page_no": 1, "title": "页", "text": suffix}]}, **common)
        db.add(material)
        db.flush()
        set_material_chapters(db, material.id, [chapter.id])
        npc = Npc(key=f"npc-{suffix}", stable_id="librarian_doc", course_instance_id=instance.id, name=f"NPC{suffix}",
                  map_key="town_hub", tile_x=22, tile_y=36, portrait="builtin:portrait:professor", sprite="builtin:sprite:chara_2")
        activity = Activity(id=f"activity-{suffix}", stable_id="lesson", chapter_id=chapter.id, title=f"阅读{suffix}",
                            kind="lesson", material_id=material.id, npc_key=npc.key, content={"published": True}, **common)
        question = Question(id=f"question-{suffix}", stable_id="question", title="概念题", kind="single_choice",
                            prompt=suffix, options=[{"id": "a", "text": "A"}], status="ready", source_type="imported", **common)
        definition = {"id": f"quest-{suffix}", "chapter_id": chapter.id, "title": f"任务{suffix}", "kind": "main",
                      "nodes": [{"id": "talk", "title": "交谈", "type": "PLAY_DIALOGUE", "target": npc.key,
                                 "navigation_target": npc.key, "dependencies": []}], "effects": []}
        quest = QuestDefinition(id=definition["id"], stable_id="quest", chapter_id=chapter.id, title=definition["title"],
                                definition=definition, **common)
        story = StoryDefinition(id=f"story-{suffix}", stable_id="common", course_instance_id=instance.id, chapter_id=None,
                                quest_id=None, content={"schema_version": 1, "mode": "ambient", "bindings": {},
                                    "entries": {npc.key: [{"when": {"always": True}, "node": "hello"}]},
                                    "nodes": [{"id": "hello", "type": "dialogue", "speaker": npc.name, "text": suffix, "next": "end"},
                                              {"id": "end", "type": "end"}]})
        db.add_all([npc, activity, question, quest, story])
        db.flush()
        set_question_chapters(db, question.id, [chapter.id])
        set_activity_questions(db, activity.id, [])
        rows.append({"classroom": classroom, "instance": instance, "chapter": chapter, "material": material,
                     "npc": npc, "activity": activity, "question": question, "quest": quest, "story": story})
    db.commit()
    return rows


def test_world_and_library_only_show_selected_instance(client, student, spaces):
    a, b = spaces
    response = client.get(f"/api/world?classroom_id={a['classroom'].id}", headers=student["headers"])
    assert response.status_code == 200, response.text
    world = response.json()
    assert [npc["key"] for npc in world["npcs"]] == [a["npc"].key]
    assert world["npcs"][0]["stable_id"] == "librarian_doc"
    assert [chapter["id"] for chapter in world["chapters"]] == [a["chapter"].id]
    assert [quest["id"] for quest in world["quests"]] == [a["quest"].id]
    response = client.get(f"/api/library?classroom_id={a['classroom'].id}", headers=student["headers"])
    assert [material["id"] for material in response.json()] == [a["material"].id]
    assert b["material"].id not in [material["id"] for material in response.json()]


@pytest.mark.parametrize("role", ["student", "teacher"])
def test_cross_instance_and_template_direct_access_rejected(client, student, teacher, spaces, role):
    a, b = spaces
    headers = student["headers"] if role == "student" else teacher["headers"]
    for path in (f"activities/{b['activity'].id}", f"materials/{b['material'].id}", "activities/ch1-lesson", "materials/186"):
        response = client.get(f"/api/{path}?classroom_id={a['classroom'].id}", headers=headers)
        assert response.status_code == 403, response.text
    assert client.get(f"/api/activities/{a['activity'].id}?classroom_id={a['classroom'].id}", headers=headers).status_code == 200


def test_cross_instance_npc_story_and_quest_rejected(client, db, student, spaces):
    a, b = spaces
    cid = a["classroom"].id
    for npc_key in (b["npc"].key, "mayor_py"):
        assert client.post("/api/story/interactions", headers=student["headers"],
                           json={"classroom_id": cid, "npc_key": npc_key}).status_code == 403
    response = client.post("/api/events", headers=student["headers"], json={"classroom_id": cid, "event_id": str(uuid4()),
                           "quest_id": b["quest"].id, "node_id": "talk", "evidence": {"npc_key": b["npc"].key}})
    assert response.status_code == 403
    # A corrupt/stale saved story cannot expose a different class's dialogue on resume.
    db.add(StorySession(user_id=student["id"], classroom_id=cid, story_id=b["story"].id, npc_key=b["npc"].key,
                        node_id="hello", status="active", revision=0, receipts=[]))
    db.commit()
    assert client.get(f"/api/story/current?classroom_id={cid}", headers=student["headers"]).status_code == 403


def test_cross_instance_question_binding_rejected(client, db, student, spaces):
    a, b = spaces
    a["activity"].kind = "practice"
    # Simulate corrupt legacy data bypassing the now-scoped authoring relation helper.
    db.add(ActivityQuestion(activity_id=a["activity"].id, question_id=b["question"].id, order_index=0))
    db.commit()
    response = client.get(f"/api/activities/{a['activity'].id}?classroom_id={a['classroom'].id}", headers=student["headers"])
    assert response.status_code == 403


def test_public_story_has_no_fake_chapter_and_can_run(client, db, student, spaces):
    a, _ = spaces
    # Remove the task so the public interaction is selected without a lesson priority.
    db.delete(a["quest"])
    db.commit()
    response = client.post("/api/story/interactions", headers=student["headers"],
                           json={"classroom_id": a["classroom"].id, "npc_key": a["npc"].key})
    assert response.status_code == 200, response.text
    assert response.json()["mode"] == "ambient"
    assert response.json()["node"]["text"] == "a"
    assert db.scalar(select(StoryDefinition.chapter_id).where(StoryDefinition.id == a["story"].id)) is None


def test_teacher_quest_authoring_uses_selected_scope(client, teacher, spaces):
    a, b = spaces
    response = client.get(f"/api/teacher/quests?classroom_id={a['classroom'].id}", headers=teacher["headers"])
    assert [row["id"] for row in response.json()] == [a["quest"].id]
    definition = {**a["quest"].definition, "id": "new-quest"}
    response = client.post(f"/api/teacher/quests?classroom_id={a['classroom'].id}", headers=teacher["headers"], json={"definition": definition})
    assert response.status_code == 201, response.text
    definition["nodes"] = [{**definition["nodes"][0], "target": b["npc"].key, "navigation_target": b["npc"].key}]
    assert client.post(f"/api/teacher/quests/validate?classroom_id={a['classroom'].id}", headers=teacher["headers"],
                       json={"definition": definition}).status_code == 404

def test_cloned_arrival_can_begin_and_resume(client, db, classroom, student):
    from app.course_instances import content_mapping
    from fixture_instances import clone_current_course
    instance = clone_current_course(db, classroom["id"])
    db.commit()
    mapping = content_mapping(db, instance)
    response = client.get(f"/api/world?classroom_id={classroom['id']}", headers=student["headers"])
    assert response.status_code == 200, response.text
    assert response.json()["quests"][0]["id"] == mapping["quest"]["arrival"]
    response = client.post("/api/story/interactions", headers=student["headers"],
                           json={"classroom_id": classroom["id"], "npc_key": mapping["npc"]["mayor_py"]})
    assert response.status_code == 200, response.text
    saved = response.json()
    resumed = client.get(f"/api/story/current?classroom_id={classroom['id']}", headers=student["headers"])
    assert resumed.status_code == 200, resumed.text
    assert resumed.json()["id"] == saved["id"]
    assert resumed.json()["node"] == saved["node"]
