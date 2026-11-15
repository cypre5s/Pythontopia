"""Maintain player quest state, pinned versions and completion effects."""

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.characters import presentation, world_character
from app.course_assistant import default_npc
from app.course_content.access import authorize_classroom, chapter_available
from app.course_scope import course_clause
from app.models import Chapter, Classroom, NodeState, Npc, PlayerPosition, PlayerQuest, QuestDefinition, StoryDefinition, User, WorldFlag
from app.teaching.quests.access import quest_visible
from app.teaching.quests.graph import node_statuses
from app.world_config import load_world_config, validate_point


def ensure_quests(db, user, classroom_id, *, commit=True):
    if user.role != "student":
        return

    classroom = db.get(Classroom, classroom_id)
    definitions = db.scalars(
        select(QuestDefinition).where(
            course_clause(db, QuestDefinition, classroom_id),
            (QuestDefinition.owner_id.is_(None)) | (QuestDefinition.owner_id == classroom.teacher_id),
        )
    ).all()
    for definition in definitions:
        if not chapter_available(db, user, classroom_id, definition.chapter_id):
            continue
        existing = db.scalar(
            select(PlayerQuest).where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == classroom_id, PlayerQuest.quest_id == definition.id)
        )
        if not existing:
            db.add(PlayerQuest(user_id=user.id, classroom_id=classroom_id, quest_id=definition.id))
    if not commit:
        db.flush()
        return
    try:
        db.commit()
    except IntegrityError:
        db.rollback()


def world_payload(db, user, classroom_id, initialize_quests=True):
    from app.teaching.world.runtime import active_interactions, building_access, npc_present
    from app.teaching.world.state import unlocked_buildings

    classroom = authorize_classroom(db, user, classroom_id)
    if initialize_quests:
        ensure_quests(db, user, classroom_id)
    position = db.get(PlayerPosition, (user.id, classroom_id))
    player = (
        {"map_key": position.map_key, "tile_x": position.tile_x, "tile_y": position.tile_y, "direction": position.direction}
        if position
        else dict(load_world_config()["default_spawn"])
    )
    npcs = [npc for npc in db.scalars(select(Npc).where(course_clause(db, Npc, classroom_id))) if npc_present(db, user, classroom_id, npc)]
    try:
        validate_point(player["map_key"], player["tile_x"], player["tile_y"])
        for npc in npcs:
            validate_point(npc.map_key, npc.tile_x, npc.tile_y)
    except ValueError as exc:
        raise HTTPException(409, "存档或NPC位置与当前地图配置不一致，请联系管理员") from exc
    quests = []
    for instance in db.scalars(select(PlayerQuest).where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == classroom_id)):
        definition = db.get(QuestDefinition, instance.quest_id)
        if not quest_visible(definition, user, classroom, db):
            continue
        states = {state.node_id: state for state in db.scalars(select(NodeState).where(NodeState.player_quest_id == instance.id))}
        statuses = node_statuses(definition.definition, states)
        quests.append(
            {
                "id": definition.id,
                "title": definition.title,
                "chapter_id": definition.chapter_id,
                "status": instance.status,
                "kind": definition.definition.get("kind", "main"),
                "nodes": [
                    {**node, "status": statuses[node["id"]], "choice": states[node["id"]].choice if node["id"] in states else None}
                    for node in definition.definition["nodes"]
                ],
            }
        )
        story = db.scalar(select(StoryDefinition).where(StoryDefinition.quest_id == definition.id, course_clause(db, StoryDefinition, classroom_id)))
        if story:
            quests[-1]["story_id"] = story.id
    return {
        "story_available": any(
            (story.chapter_id is None or chapter_available(db, user, classroom_id, story.chapter_id))
            and (
                story.quest_id is None
                and story.content.get("mode") in {"ambient", "public"}
                or story.quest_id is not None
                and quest_visible(db.get(QuestDefinition, story.quest_id), user, classroom, db)
            )
            for story in db.scalars(select(StoryDefinition).where(course_clause(db, StoryDefinition, classroom_id)))
        ),
        "player": player,
        "npcs": [world_character(db, classroom_id, npc) for npc in npcs],
        "default_role": presentation(db, classroom_id, default_npc(db, classroom_id)),
        "quests": quests,
        "chapters": [
            {
                "id": chapter.id,
                "title": chapter.title,
                "order_index": chapter.order_index,
                "available": chapter_available(db, user, classroom_id, chapter.id),
            }
            for chapter in db.scalars(select(Chapter).where(course_clause(db, Chapter, classroom_id)).order_by(Chapter.order_index))
            if (chapter.owner_id in {None, classroom.teacher_id} or user.role == "admin")
            and (chapter.owner_id is None or user.role != "student" or chapter_available(db, user, classroom_id, chapter.id))
        ],
        "flags": list(db.scalars(select(WorldFlag.key).where(WorldFlag.user_id == user.id, WorldFlag.classroom_id == classroom_id))),
        "building_unlock": sorted(unlocked_buildings(db, user, classroom_id)),
        "building_access": building_access(db, user, classroom_id),
        "interactions": active_interactions(db, user, classroom_id),
    }


def world(classroom_id: int, db: Session, user: User):
    return world_payload(db, user, classroom_id)
