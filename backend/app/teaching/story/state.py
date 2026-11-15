"""Load and authorize story sessions and their pinned quest definitions."""

from fastapi import HTTPException
from sqlalchemy import select

from app.course_content.access import authorize_activity, authorize_chapter, authorize_classroom, authorize_material
from app.course_scope import require_course_content
from app.materials.presentation import material_payload
from app.models import NodeState, Npc, PlayerQuest, QuestDefinition, StoryDefinition, StorySession, User
from app.teaching.quests.access import authorize_quest
from app.teaching.quests.graph import node_statuses
from app.teaching.world.proximity import check_near


def lock_student(db, user, classroom_id):
    authorize_classroom(db, user, classroom_id)
    if user.role != "student":
        raise HTTPException(403, "演出存档仅供学生使用")
    db.execute(select(User.id).where(User.id == user.id).with_for_update())


def open_session(db, user, classroom_id):
    return db.scalar(
        select(StorySession)
        .where(StorySession.user_id == user.id, StorySession.classroom_id == classroom_id, StorySession.status.in_(["active", "waiting"]))
        .with_for_update()
    )


def session_definition(db, user, session):
    story = db.get(StoryDefinition, session.story_id)
    if not story:
        raise HTTPException(409, "演出不存在")
    require_course_content(db, story, session.classroom_id)
    if story.chapter_id is not None:
        authorize_chapter(db, user, session.classroom_id, story.chapter_id)
    if story.quest_id is None:
        if story.content.get("mode") not in {"ambient", "public"}:
            raise HTTPException(409, "日常对白配置无效")
        return story, None
    definition = authorize_quest(db, user, session.classroom_id, story.quest_id)
    if definition.chapter_id != story.chapter_id:
        raise HTTPException(409, "演出与当前任务章节不一致")
    instance = db.scalar(
        select(PlayerQuest).where(
            PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == session.classroom_id, PlayerQuest.quest_id == story.quest_id
        )
    )
    if not instance:
        raise HTTPException(403, "演出不属于当前任务")
    return story, instance


def quest_states(db, instance):
    definition = db.get(QuestDefinition, instance.quest_id)
    states = {state.node_id: state for state in db.scalars(select(NodeState).where(NodeState.player_quest_id == instance.id))}
    return definition.definition, node_statuses(definition.definition, states)


def require_session_proximity(db, user, session):
    from app.teaching.world.runtime import npc_present, require_interaction

    if session.anchor:
        check_near(
            db,
            user,
            session.classroom_id,
            tuple(session.anchor[key] for key in ("map_key", "tile_x", "tile_y")),
            radius=session.anchor.get("radius", 3),
        )
        return
    if session.interaction_id:
        require_interaction(db, user, session.classroom_id, session.interaction_id)
        return
    npc = db.get(Npc, session.npc_key)
    require_course_content(db, npc, session.classroom_id)
    if not npc_present(db, user, session.classroom_id, npc):
        raise HTTPException(403, "当前NPC尚未出现")
    check_near(db, user, session.classroom_id, (npc.map_key, npc.tile_x, npc.tile_y))


def story_nodes(story):
    return {node["id"]: node for node in story.content["nodes"]}


def available_activity(db, user, session, binding):
    try:
        activity = authorize_activity(db, user, session.classroom_id, binding["activity_id"])
        for resource in activity.resources:
            if not resource.required:
                continue
            material = authorize_material(db, user, session.classroom_id, resource.material_id)
            if material_payload(db, material)["status"] != "ready":
                return None
        return activity
    except HTTPException as exc:
        if exc.status_code not in {404, 409, 503}:
            raise
        return None
