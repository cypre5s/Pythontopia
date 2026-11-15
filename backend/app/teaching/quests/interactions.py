"""Record verified map interactions as task evidence."""

from sqlalchemy import select

from app.models import Classroom, PlayerQuest, QuestDefinition
from app.teaching.world.runtime import require_interaction


def record_interaction(db, user, classroom_id, interaction_id, session):
    """An actual public interaction can satisfy an explicitly authored task goal."""
    from app.course_content.access import chapter_available
    from app.course_scope import course_clause
    from app.models import NodeState
    from app.teaching.quests.access import quest_visible
    from app.teaching.quests.contracts import EventInput
    from app.teaching.quests.events import apply_quest_event
    from app.teaching.quests.graph import node_statuses
    from app.teaching.quests.state import ensure_quests

    item = require_interaction(db, user, classroom_id, interaction_id)
    targets = {interaction_id, item["target"].get("object_id")}
    ensure_quests(db, user, classroom_id, commit=False)
    classroom = db.get(Classroom, classroom_id)
    for definition in db.scalars(select(QuestDefinition).where(course_clause(db, QuestDefinition, classroom_id))):
        if not quest_visible(definition, user, classroom, db) or not chapter_available(db, user, classroom_id, definition.chapter_id):
            continue
        player = db.scalar(
            select(PlayerQuest).where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == classroom_id, PlayerQuest.quest_id == definition.id)
        )
        if not player or player.status == "complete":
            continue
        states = {state.node_id: state for state in db.scalars(select(NodeState).where(NodeState.player_quest_id == player.id))}
        statuses = node_statuses(definition.definition, states)
        for node in definition.definition["nodes"]:
            if node["type"] in {"INTERACT_OBJECT", "REACH_LOCATION"} and node.get("target") in targets and statuses[node["id"]] == "ready":
                import hashlib

                event_id = hashlib.sha256(f"interaction:{session.id}:{definition.id}:{node['id']}".encode()).hexdigest()
                apply_quest_event(
                    db,
                    user,
                    EventInput(
                        classroom_id=classroom_id,
                        quest_id=definition.id,
                        node_id=node["id"],
                        event_id=event_id,
                        evidence={"object_id": node["target"]} if node["type"] == "INTERACT_OBJECT" else {},
                    ),
                )
