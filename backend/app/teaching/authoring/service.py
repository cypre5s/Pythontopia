"""Prepare, preview and publish a consistent teaching document."""

from copy import deepcopy
from types import SimpleNamespace

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.authoring_diagnostics import INPUT_ERRORS, AuthoringIssue, located, row_target
from app.authoring_protection import publication_plan
from app.course_exchange_models import CoursePackageState
from app.course_instances import content_mapping, remap_references
from app.course_scope import lock_course
from app.models import Classroom, CourseInstance, Membership, Npc, QuestDefinition, StoryDefinition, User
from app.teacher_scope import scoped_key, teacher_instance_id
from app.teaching.authoring.contracts import AuthoringInput, PreviewInput, PublishInput
from app.teaching.authoring.validation import flow_title, name_entry_issue, validate_configuration, validate_object, validate_short_text
from app.teaching.world.runtime import world_condition_matches
from app.world_config import interaction_points, load_world_config


def _instance(db, user, classroom_id, *, exclusive=False):
    lock_course(db, classroom_id, exclusive=exclusive)
    key = teacher_instance_id(db, user, classroom_id)
    return db.scalar(select(CourseInstance).where(CourseInstance.id == key).with_for_update()) if exclusive else db.get(CourseInstance, key)


def _data(db, instance):
    # The exchange serializer is also the editor's portable representation.
    from app.course_content.snapshot import snapshot_content

    data, _, _, config, _ = snapshot_content(db, instance)
    return data, {key: value for key, value in config.items() if key in {"world", "buildings", "assistant", "editor"}}


def _catalog(data):
    config, objects = load_world_config(), interaction_points()
    maps = []
    for key, area in config["maps"].items():
        items = []
        for object_id, positions in objects.items():
            points = [positions] if isinstance(positions[0], str) else positions
            points = [{"map_key": m, "tile_x": x, "tile_y": y} for m, x, y in points if m == key]
            if points:
                building = next((b for b in area["buildings"] if b["id"] == object_id), None)
                label = building["label"] if building else config["interactions"].get(object_id, {}).get("label", object_id)
                items.append({"id": object_id, "label": label, "points": points})
        maps.append(
            {
                "key": key,
                **{field: area[field] for field in ("label", "width", "height")},
                "walk_bounds": area.get("walk_bounds", {"x": 0, "y": 0, "width": area["width"], "height": area["height"]}),
                "objects": items,
            }
        )
    return {
        "maps": maps,
        "buildings": [
            {"id": b["id"], "label": b["label"], "map_key": key, "has_interior": bool(b.get("portal_id"))}
            for key, area in config["maps"].items()
            for b in area["buildings"]
        ],
        "chapters": [{"id": row["id"], "title": row["title"]} for row in data["chapter"]],
        "activities": [{"id": row["id"], "title": row["title"], "kind": row["kind"]} for row in data["activity"]],
        "npcs": [{"id": row["id"], "name": row["name"], "map_key": row["placement"]["map_key"] if row["placement"] else None} for row in data["npc"]],
        "quests": [
            {
                "id": row["id"],
                "title": row["title"],
                "nodes": [{key: node[key] for key in ("id", "title", "type")} for node in row["definition"]["nodes"]],
            }
            for row in data["quest"]
        ],
    }


def get_authoring(classroom_id: int, db: Session, user: User):
    instance = _instance(db, user, classroom_id)
    data, config = _data(db, instance)
    students = db.scalars(
        select(User).join(Membership, Membership.user_id == User.id).where(Membership.classroom_id == classroom_id, User.role == "student")
    )
    return {
        "revision": instance.revision,
        "catalog": _catalog(data),
        "npcs": data["npc"],
        "flows": [{**row, "title": row["content"].get("title", row["id"])} for row in data["story"]],
        "quests": data["quest"],
        "configuration": {key: config.get(key, {}) for key in ("world", "buildings", "assistant", "editor")},
        "students": [{"id": row.id, "name": row.name} for row in students],
    }


def _prepare(db, instance, body):
    from app.teaching.quests.validation import validate_definition
    from app.teaching.story.validation import validate_story

    if instance.revision != body.expected_revision:
        raise HTTPException(409, "课程已被其他编辑修改，请刷新后重试")
    data, _ = _data(db, instance)
    from app.characters import stored_assets, validate_characters

    validate_characters(body.npcs, stored_assets(db, instance.id))
    data["npc"] = deepcopy(body.npcs)
    flows, seen = [], set()
    for index, row in enumerate(body.flows):
        with located("flows", index, target=row_target("flow", row), label=f"流程「{flow_title(row)}」"):
            validate_object(row, {"id", "title", "chapter_id", "quest_id", "content"}, {"id", "chapter_id", "quest_id", "content"})
            if not validate_short_text(row["id"]) or row["id"] in seen:
                raise ValueError("流程ID重复或无效")
            seen.add(row["id"])
            copied = deepcopy(row)
            if "title" in copied:
                copied["content"]["title"] = copied.pop("title")
            flows.append(copied)
    data["story"] = flows
    if body.quests is not None:
        seen = set()
        for index, row in enumerate(body.quests):
            with located("quests", index, target=row_target("quest", row), label=f"任务「{row.get('title', index + 1)}」"):
                validate_object(row, {"id", "title", "chapter_id", "definition"}, {"id", "title", "chapter_id", "definition"})
                if (
                    not validate_short_text(row["id"])
                    or row["id"] in seen
                    or row["definition"].get("id") != row["id"]
                    or row["definition"].get("chapter_id") != row["chapter_id"]
                ):
                    raise ValueError("任务ID或章节引用不一致")
                seen.add(row["id"])
        data["quest"] = deepcopy(body.quests)
    validate_configuration(body.configuration, data)
    maps = content_mapping(db, instance)
    maps["npc"] = {row["id"]: maps["npc"].get(row["id"], scoped_key(instance.id, "npc", row["id"])) for row in data["npc"]}
    for kind in ("story", "quest"):
        for row in data[kind]:
            maps[kind].setdefault(row["id"], scoped_key(instance.id, kind, row["id"]))

    class DraftCatalog:
        def get(self, model, key):
            if model is Npc:
                row = next((row for row in data["npc"] if maps["npc"][row["id"]] == key), None)
                return SimpleNamespace(**row) if row else None
            return db.get(model, key)

    draft_catalog = DraftCatalog()
    quests = {}
    for index, row in enumerate(data["quest"]):
        with located("quests", index, "definition", target=row_target("quest", row, quest_id=row["id"]), label=f"任务「{row['title']}」"):
            actual = remap_references(row, maps)
            actual["definition"]["id"] = maps["quest"][row["id"]]
            objects = set(interaction_points()) | {row["id"] for row in body.configuration.get("world", {}).get("interactions", [])}
            validate_definition(actual["definition"], draft_catalog, objects=objects)
            quests[row["id"]] = actual
    entry_names = {
        **{row["id"]: row["name"] for row in data["npc"]},
        **{f"interaction:{item['id']}": item["label"] for item in body.configuration.get("world", {}).get("interactions", [])},
    }
    stable_npc_ids = {value: key for key, value in maps["npc"].items()}
    for index, row in enumerate(data["story"]):
        with located("flows", index, "content", target=row_target("flow", row, flow_id=row["id"]), label=f"流程「{flow_title(row)}」"):
            actual = remap_references(row, maps)
            quest = quests.get(row["quest_id"])
            try:
                validate_story(actual["content"], draft_catalog, quest["definition"] if quest else None, maps["story"][row["id"]])
            except AuthoringIssue as issue:
                name_entry_issue(issue, entry_names, stable_npc_ids)
                raise
    return data, maps


def _diagnostic(exc):
    issue = exc if isinstance(exc, AuthoringIssue) else AuthoringIssue(str(exc) if isinstance(exc, ValueError) else "字段格式不正确或缺少必填项")
    return issue.diagnostic()


def _error_detail(diagnostics):
    return {"message": diagnostics[0]["message"], "diagnostics": diagnostics}


def validate_authoring(classroom_id: int, body: AuthoringInput, db: Session, user: User):
    instance = _instance(db, user, classroom_id)
    impact = None
    try:
        data, maps = _prepare(db, instance, body)
        diagnostics, plan = publication_plan(db, instance, data, maps, body.configuration)
        if not diagnostics:
            impact = plan.impact(instance, user.id, body.model_dump())
    except INPUT_ERRORS as exc:
        diagnostics = [_diagnostic(exc)]
    return {"valid": not diagnostics, "diagnostics": diagnostics, **({"reset_impact": impact} if impact else {})}


def save_authoring(classroom_id: int, body: PublishInput, db: Session, user: User):
    instance = _instance(db, user, classroom_id, exclusive=True)
    try:
        data, maps = _prepare(db, instance, body)
    except INPUT_ERRORS as exc:
        raise HTTPException(422, _error_detail([_diagnostic(exc)])) from exc
    # Recheck under the exclusive course lock in case a student starts after preflight.
    diagnostics, plan = publication_plan(db, instance, data, maps, body.configuration)
    if diagnostics:
        raise HTTPException(409, _error_detail(diagnostics))
    impact = plan.impact(instance, user.id, body.model_dump(exclude={"confirmation_token"}))
    if impact and impact["token"] != body.confirmation_token:
        raise HTTPException(
            409,
            {
                "message": "受影响的存档已更新，请核对后再次确认。" if body.confirmation_token else "本次修改会清理部分班级存档，请先确认影响范围。",
                "reset_impact": impact,
            },
        )
    plan.apply(db, classroom_id)
    from app.characters import CHARACTER_FIELDS

    incoming_npcs = {row["id"]: row for row in data["npc"]}
    current_npcs = {row.stable_id: row for row in db.scalars(select(Npc).where(Npc.course_instance_id == instance.id))}
    for stable, npc in current_npcs.items():
        if stable not in incoming_npcs:
            db.delete(npc)
    for stable, row in incoming_npcs.items():
        npc = current_npcs.get(stable)
        if npc is None:
            npc = Npc(key=maps["npc"][stable], stable_id=stable, course_instance_id=instance.id)
            db.add(npc)
        for field in CHARACTER_FIELDS:
            setattr(npc, field, deepcopy(row[field]))
    db.flush()
    for model, kind, field in ((QuestDefinition, "quest", "definition"), (StoryDefinition, "story", "content")):
        incoming = {row["id"]: row for row in data[kind]}
        current = {row.stable_id or row.id: row for row in db.scalars(select(model).where(model.course_instance_id == instance.id))}
        for stable, existing in current.items():
            if stable not in incoming:
                db.delete(existing)
        for stable, row in incoming.items():
            actual = remap_references(row, maps)
            fields = {"chapter_id": actual["chapter_id"], field: actual[field]}
            if kind == "quest":
                fields.update(owner_id=db.get(Classroom, classroom_id).teacher_id, title=actual["title"])
                fields["definition"]["id"] = maps[kind][stable]
            else:
                fields["quest_id"] = actual["quest_id"]
            existing = current.get(stable)
            if existing:
                for key, value in fields.items():
                    setattr(existing, key, value)
            else:
                db.add(model(id=maps[kind][stable], stable_id=stable, course_instance_id=instance.id, **fields))
    state = db.get(CoursePackageState, instance.id)
    if not state:
        state = CoursePackageState(course_instance_id=instance.id, configuration={})
        db.add(state)
    state.configuration = {**deepcopy(body.configuration), **{key: value for key, value in state.configuration.items() if key.startswith("_")}}
    instance.revision += 1
    from app.character_assets import retire_unused_assets

    retire_unused_assets(state, body.npcs)
    saved_revision = instance.revision
    db.commit()
    # File failures are retried by the periodic worker; the saved draft stays committed.
    from app.character_assets import drain_character_garbage

    try:
        from app.course_scope import lock_course

        lock_course(db, classroom_id, exclusive=True)
        db.refresh(state)
        drain_character_garbage(db, state)
    except Exception:
        db.rollback()
        import logging

        logging.getLogger(__name__).exception("Character cleanup deferred")
    return {"revision": saved_revision}


def preview_authoring(classroom_id: int, body: PreviewInput, db: Session, user: User):
    from app.teaching.conditions.runtime import condition_matches
    from app.teaching.story.runtime import select_flow

    instance = _instance(db, user, classroom_id)
    student = db.get(User, body.student_id)
    if not student or student.role != "student" or not db.get(Membership, (student.id, classroom_id)):
        raise HTTPException(404, "预览学生不属于本班")
    try:
        data, maps = _prepare(db, instance, body)
    except INPUT_ERRORS as exc:
        raise HTTPException(422, _error_detail([_diagnostic(exc)])) from exc
    entry = body.entry if body.entry.startswith("interaction:") else maps["npc"].get(body.entry)
    definitions = {}
    for row in data["quest"]:
        actual = remap_references(row, maps)
        actual["id"] = maps["quest"][row["id"]]
        actual["definition"]["id"] = actual["id"]
        definitions[actual["id"]] = SimpleNamespace(**actual, course_instance_id=instance.id, owner_id=db.get(Classroom, classroom_id).teacher_id)
    candidates = []
    for row in data["story"]:
        actual = remap_references(row, maps)
        story = SimpleNamespace(**actual, course_instance_id=instance.id, preview_definitions=definitions)
        candidates.append(story)
    targets = {entry}
    if body.entry.startswith("interaction:"):
        interaction_id = body.entry.removeprefix("interaction:")
        item = next((row for row in body.configuration.get("world", {}).get("interactions", []) if row["id"] == interaction_id), None)
        if not item or not world_condition_matches(db, student, classroom_id, item.get("when", {"always": True})):
            return {"selected_flow_id": None, "node_id": None, "trace": [], "stopped_at": "unavailable"}
        targets = {interaction_id}
        if item["target"].get("object_id"):
            targets.add(item["target"]["object_id"])
    else:
        npc = next(row for row in body.npcs if row["id"] == body.entry)
        if npc["placement"] is None or not world_condition_matches(db, student, classroom_id, npc["appearance_condition"]):
            return {"selected_flow_id": None, "node_id": None, "trace": [], "stopped_at": "unavailable"}
    selected = select_flow(db, student, classroom_id, entry, candidates, targets=targets)
    if not selected:
        return {"selected_flow_id": None, "node_id": None, "trace": [], "stopped_at": "unavailable"}
    story = selected[0]
    current = next(route["node"] for route in story.content["entries"][entry] if condition_matches(db, student, classroom_id, story, route["when"]))
    nodes, trace = {node["id"]: node for node in story.content["nodes"]}, []
    for _ in range(len(nodes) + 1):
        node = nodes[current]
        trace.append({"node_id": current, "type": node["type"]})
        if node["type"] != "branch":
            break
        current = next(route["node"] for route in node["routes"] if condition_matches(db, student, classroom_id, story, route["when"]))
    return {"selected_flow_id": story.id, "node_id": current, "trace": trace, "stopped_at": node["type"]}
