"""Position the authorized learner at a defined task stage."""

from types import SimpleNamespace

from fastapi import HTTPException, Request
from sqlalchemy import delete, select

from app.course_content.access import authorize_chapter
from app.course_scope import course_clause, instance_id
from app.experience.access import guard
from app.experience.contracts import Actor, Db, StageInput
from app.experience.state import own_rows, state_token
from app.models import (
    ActivityProgress,
    BuildingUnlock,
    NodeState,
    PlayerPosition,
    PlayerQuest,
    QuestDefinition,
    StoryDefinition,
    StorySession,
    WorldFlag,
)
from app.teaching.quests.graph import node_statuses
from app.teaching.quests.validation import validate_definition
from app.world_config import load_world_config, validate_point


def stage_plan(definition, target_id, choices, any_dependencies):
    try:
        validate_definition(definition)
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    nodes = {node["id"]: node for node in definition["nodes"]}
    if target_id not in nodes:
        raise HTTPException(404, "任务阶段不存在")
    for key, value in choices.items():
        if key not in nodes or value not in [choice["id"] for choice in nodes[key].get("choices", [])]:
            raise HTTPException(422, "分支选择无效")
    for key, value in any_dependencies.items():
        if key not in nodes or nodes[key].get("aggregation") != "ANY" or value not in nodes[key].get("dependencies", []):
            raise HTTPException(422, "ANY 前置选择无效")
    selected, states, visiting = dict(choices), {}, set()

    def visit(key, complete=True):
        node = nodes[key]
        if key in visiting:
            raise HTTPException(422, "任务依赖存在循环")
        condition = node.get("condition")
        if condition:
            parent, choice = condition["node"], condition["choice"]
            if parent in selected and selected[parent] != choice:
                if key == target_id:
                    raise HTTPException(422, "选择的分支无法到达目标阶段")
                return
            selected[parent] = choice
            visit(parent)
        if key in states:
            return
        visiting.add(key)
        deps = node.get("dependencies", [])
        if node.get("aggregation", "ALL") == "ANY" and deps:
            chosen = any_dependencies.get(key)
            if len(deps) > 1 and not chosen:
                raise HTTPException(422, f"请选择“{node['title']}”采用的 ANY 前置")
            deps = [chosen or deps[0]]
        for dep in deps:
            visit(dep)
        visiting.remove(key)
        if complete:
            if node.get("choices") and key not in selected:
                raise HTTPException(422, f"请选择“{node['title']}”的分支")
            states[key] = SimpleNamespace(choice=selected.get(key))

    visit(target_id, complete=False)
    statuses = node_statuses(definition, states)
    if statuses[target_id] != "ready":
        raise HTTPException(422, "当前前置与分支无法定位到这个阶段")
    return [{"node_id": key, "title": nodes[key]["title"], "choice": state.choice} for key, state in states.items()]


def prepare_stage(body, db, user):
    definition = db.scalar(select(QuestDefinition).where(QuestDefinition.id == body.quest_id, course_clause(db, QuestDefinition, body.classroom_id)))
    if not definition:
        raise HTTPException(404, "本班任务不存在")
    authorize_chapter(db, user, body.classroom_id, definition.chapter_id)
    return definition, stage_plan(definition.definition, body.node_id, body.choices, body.any_dependencies)


def preview_stage(body: StageInput, request: Request, db: Db, user: Actor):
    guard(db, user, body.classroom_id, request)
    definition, plan = prepare_stage(body, db, user)
    return {
        "expected": state_token(db, user, body.classroom_id),
        "completed": plan,
        "target": next(node["title"] for node in definition.definition["nodes"] if node["id"] == body.node_id),
    }


def end_stories(db, user, classroom_id):
    for row in own_rows(db, StorySession, user, classroom_id):
        if row.status in {"active", "waiting"}:
            row.status = "ended"
            row.revision += 1
    db.flush()


def sync_stage_effects(db, user, classroom_id, definition, completed):
    """Rebuild this task's world effects, retaining effects required by other tasks."""
    touched, desired = set(), set()

    def effect_key(effect):
        if effect["type"] == "unlock_building" or effect.get("key") == "library_unlocked":
            return ("building", effect.get("building_id", "library"))
        return ("flag", effect["key"])

    for other in db.scalars(select(QuestDefinition).where(course_clause(db, QuestDefinition, classroom_id))):
        instance = db.scalar(
            select(PlayerQuest).where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == classroom_id, PlayerQuest.quest_id == other.id)
        )
        done = set(db.scalars(select(NodeState.node_id).where(NodeState.player_quest_id == instance.id))) if instance else set()
        for effect in other.definition.get("effects", []):
            key = effect_key(effect)
            if other.id == definition.id:
                touched.add(key)
            elif instance and instance.status == "complete":
                desired.add(key)
        for story in db.scalars(
            select(StoryDefinition).where(StoryDefinition.quest_id == other.id, course_clause(db, StoryDefinition, classroom_id))
        ):
            for node_id, effects in story.content.get("checkpoint_effects", {}).items():
                for effect in effects:
                    key = effect_key(effect)
                    if other.id == definition.id:
                        touched.add(key)
                    if node_id in (completed if other.id == definition.id else done):
                        desired.add(key)
    for kind, key in touched:
        model = BuildingUnlock if kind == "building" else WorldFlag
        pk = (user.id, classroom_id, instance_id(db, classroom_id), key) if kind == "building" else (user.id, classroom_id, key)
        row = db.get(model, pk)
        if (kind, key) not in desired and row:
            db.delete(row)
        elif (kind, key) in desired and not row:
            if kind == "building":
                from app.teaching.world.state import unlock_building

                unlock_building(db, user, classroom_id, key)
            else:
                db.add(WorldFlag(user_id=user.id, classroom_id=classroom_id, key=key))
    for node in definition.definition["nodes"]:
        if node["id"] in completed:
            continue
        if node.get("dialogue_id"):
            row = db.get(WorldFlag, (user.id, classroom_id, f"dialogue:completed:{node['target']}:{node['dialogue_id']}"))
            if row:
                db.delete(row)
        if node.get("activity_id"):
            row = db.get(ActivityProgress, (user.id, classroom_id, node["activity_id"]))
            if row:
                db.delete(row)


def locate_stage(body: StageInput, request: Request, db: Db, user: Actor):
    guard(db, user, body.classroom_id, request, mutate=True)
    if body.expected != state_token(db, user, body.classroom_id):
        raise HTTPException(409, "体验状态或课程已变化，请重新预览")
    definition, plan = prepare_stage(body, db, user)
    quest = db.scalar(
        select(PlayerQuest).where(
            PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == body.classroom_id, PlayerQuest.quest_id == definition.id
        )
    )
    if not quest:
        quest = PlayerQuest(user_id=user.id, classroom_id=body.classroom_id, quest_id=definition.id)
        db.add(quest)
        db.flush()
    db.execute(delete(NodeState).where(NodeState.player_quest_id == quest.id))
    quest.status, quest.completed_at = "active", None
    for item in plan:
        db.add(NodeState(player_quest_id=quest.id, node_id=item["node_id"], choice=item["choice"], evidence={"source": "development_positioning"}))
    sync_stage_effects(db, user, body.classroom_id, definition, {item["node_id"] for item in plan})
    end_stories(db, user, body.classroom_id)
    # Do not leave the actor inside a building which the earlier stage has locked.
    spawn = load_world_config()["default_spawn"]
    validate_point(spawn["map_key"], spawn["tile_x"], spawn["tile_y"])
    db.execute(delete(PlayerPosition).where(PlayerPosition.user_id == user.id, PlayerPosition.classroom_id == body.classroom_id))
    db.add(PlayerPosition(user_id=user.id, classroom_id=body.classroom_id, **spawn))
    db.commit()
    return {"ok": True, "user_id": user.id, "classroom_id": body.classroom_id}
