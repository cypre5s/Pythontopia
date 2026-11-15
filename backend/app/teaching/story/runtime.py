"""Settle automatic story transitions and build the current session response."""

import secrets

from fastapi import HTTPException
from sqlalchemy import select

from app.course_content.access import authorize_activity, authorize_classroom, chapter_available
from app.models import ActivityProgress, NodeState, Npc, PlayerQuest, QuestDefinition, StoryDefinition
from app.teaching.conditions.runtime import condition_matches
from app.teaching.quests.access import quest_visible
from app.teaching.quests.graph import node_statuses
from app.teaching.quests.state import world_payload
from app.teaching.story.state import available_activity, quest_states, session_definition, story_nodes


def settle_session(db, user, session, story):
    mapping = story_nodes(story)
    for _ in range(len(mapping) + 1):
        node = mapping[session.node_id]
        if node["type"] == "end":
            session.status = "ended"
            session.pending_activity_id = None
            session.return_mapping = {}
            return
        if node["type"] in {"dialogue", "action"}:
            session.status = "active"
            return
        if node["type"] == "branch":
            session.node_id = next(
                route["node"] for route in node["routes"] if condition_matches(db, user, session.classroom_id, story, route["when"])
            )
            continue
        if node["type"] == "random":
            session.node_id = secrets.choice(node["next"])
            continue
        if node["type"] == "unlock_building":
            from app.teaching.world.state import unlock_building

            if not condition_matches(db, user, session.classroom_id, story, node["when"]):
                raise HTTPException(409, "尚未获得建筑解锁所需的真实完成证据")
            unlock_building(db, user, session.classroom_id, node["building_id"])
            session.node_id = node["next"]
            continue
        binding = story.content["bindings"][node["binding"]]
        _, instance = session_definition(db, user, session)
        statuses = quest_states(db, instance)[1] if instance else None
        if statuses is not None and statuses[binding["goal_id"]] not in {"ready", "complete"}:
            session.node_id = node["returns"]["unavailable"]
            continue
        activity = available_activity(db, user, session, binding)
        if not activity:
            session.node_id = node["returns"]["unavailable"]
            continue
        session.status = "waiting"
        session.pending_activity_id = activity.id
        session.return_mapping = node["returns"]
        return
    raise HTTPException(409, "演出自动推进超过限制")


def _view(db, user, session):
    if session.status == "ended":
        story = db.get(StoryDefinition, session.story_id)
        return {
            "id": session.id,
            "revision": session.revision,
            "status": "ended",
            "mode": story.content.get("mode", "task") if story else "public",
            "npc_key": session.npc_key,
            "interaction_id": session.interaction_id,
            "node": None,
            "activity": None,
        }
    story, _ = session_definition(db, user, session)
    node, activity = None, None
    if session.status == "active":
        source = story_nodes(story)[session.node_id]
        fields = ("text", "speaker") if source["type"] == "dialogue" else ("action", "workspace", "building_id")
        node = {key: source[key] for key in ("id", "type", *fields) if key in source}
        if source["type"] == "dialogue":
            from app.characters import presentation

            speaker_key = source.get("speaker_npc_id", session.npc_key if "speaker" not in source else None)
            speaker = db.get(Npc, speaker_key) if speaker_key else None
            identity = presentation(db, session.classroom_id, speaker)
            if identity:
                node.update(speaker=identity["name"], portrait=identity["portrait"])
            else:
                node["portrait"] = ""
        rounds = source.get("text", "").split("\n\n")
        round_index = session.return_mapping.get("dialogue_round", 0) if source["type"] == "dialogue" else 0
        if source["type"] == "dialogue":
            node["text"] = rounds[min(round_index, len(rounds) - 1)]
        if "choices" in source and round_index >= len(rounds) - 1:
            node["choices"] = [{key: choice[key] for key in ("id", "label")} for choice in available_choices(db, user, session, story, source)]
    elif session.status == "waiting":
        item = authorize_activity(db, user, session.classroom_id, session.pending_activity_id)
        progress = db.get(ActivityProgress, (user.id, session.classroom_id, item.id))
        activity = {"id": item.id, "kind": item.kind, "page_no": progress.page_no if progress else None}
    return {
        "id": session.id,
        "revision": session.revision,
        "status": session.status,
        "mode": story.content.get("mode", "task"),
        "npc_key": session.npc_key,
        "interaction_id": session.interaction_id,
        "node": node,
        "activity": activity,
    }


def available_choices(db, user, session, story, node):
    return [
        choice
        for choice in node.get("choices", [])
        if "when" not in choice or condition_matches(db, user, session.classroom_id, story, choice["when"])
    ]


def session_response(db, user, session):
    result = _view(db, user, session)
    result["world"] = world_payload(db, user, session.classroom_id, initialize_quests=result["mode"] != "ambient")
    return result


def select_flow(db, user, classroom_id, entry, stories, *, targets=None):
    """One deterministic resolver shared by the runtime and read-only preview."""
    classroom = authorize_classroom(db, user, classroom_id)
    targets = {entry} if targets is None else targets
    candidates = []
    for story in stories:
        if entry not in story.content["entries"] or story.chapter_id is not None and not chapter_available(db, user, classroom_id, story.chapter_id):
            continue
        if not condition_matches(db, user, classroom_id, story, story.content.get("when", {"always": True})):
            continue
        instance = None
        if story.quest_id is None:
            if story.content.get("mode") not in {"ambient", "public"}:
                continue
            layer = 1 if story.chapter_id is not None else 2
        else:
            definition = getattr(story, "preview_definitions", {}).get(story.quest_id) or db.get(QuestDefinition, story.quest_id)
            if not quest_visible(definition, user, classroom, db) or definition.chapter_id != story.chapter_id:
                continue
            instance = db.scalar(
                select(PlayerQuest).where(
                    PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == classroom_id, PlayerQuest.quest_id == story.quest_id
                )
            )
            states = {row.node_id: row for row in db.scalars(select(NodeState).where(NodeState.player_quest_id == instance.id))} if instance else {}
            statuses = node_statuses(definition.definition, states)
            ready = (not instance or instance.status != "complete") and any(
                statuses[node["id"]] == "ready" and (node.get("target") in targets or node.get("navigation_target") in targets)
                for node in definition.definition["nodes"]
            )
            layer = 0 if ready else 3 if not instance or instance.status != "complete" else 4
        candidates.append((layer, -story.content.get("priority", 0), story, instance))
    candidates.sort(key=lambda row: row[:2])
    if len(candidates) > 1 and candidates[0][:2] == candidates[1][:2]:
        raise HTTPException(409, "流程入口优先级冲突，请联系教师")
    return candidates[0][2:] if candidates else None
