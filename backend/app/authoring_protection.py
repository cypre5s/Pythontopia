"""Check draft compatibility with saved progress before publishing teaching content."""

from collections import Counter, defaultdict
from dataclasses import dataclass
from hashlib import sha256
import json

from sqlalchemy import delete, or_, select, tuple_

from .authoring_diagnostics import AuthoringIssue
from .course_exchange_models import CoursePackageState
from .course_instances import _json
from .models import LearningEvent, NodeState, PlayerQuest, QuestDefinition, StoryDefinition, StorySession, WorldFlag


END_INTERACTION = (
    "让相应学生在小镇继续完成互动，或点「结束本次互动」。如果是你的体验存档，"
    "从当前班级点「进入小镇」，返回对白后结束。暂时离开或关闭网页仍会保留会话。"
)


def _activity_counts(sessions):
    waiting = sum(session.status == "waiting" for session in sessions)
    counts = []
    if len(sessions) > waiting:
        counts.append(f"{len(sessions) - waiting} 人正在互动")
    if waiting:
        counts.append(f"{waiting} 人在等待学习活动返回")
    return "，".join(counts)


def _changed_story(existing, incoming):
    if existing.chapter_id != incoming["chapter_id"] or existing.quest_id != incoming["quest_id"]:
        return True
    return {key: value for key, value in existing.content.items() if key != "title"} != {
        key: value for key, value in incoming["content"].items() if key != "title"
    }


def changed_quest_structure(existing, incoming):
    def without_labels(definition):
        return {**{key: value for key, value in definition.items() if key not in {"title", "nodes"}},
                "nodes": {node["id"]: {key: value for key, value in node.items() if key != "title"}
                          for node in definition["nodes"]}}

    return without_labels(existing) != without_labels(incoming)


def _world_issues(old, new, sessions, maps, npc_labels):
    old_interactions = {item["id"]: item for item in old.get("interactions", [])}
    new_interactions = {item["id"]: (index, item) for index, item in enumerate(new.get("interactions", []))}
    by_interaction, by_npc = defaultdict(list), defaultdict(list)
    npc_ids = {runtime_id: stable_id for stable_id, runtime_id in maps["npc"].items()}
    for session in sessions:
        if session.interaction_id:
            by_interaction[session.interaction_id].append(session)
        elif session.npc_key in npc_ids:
            by_npc[npc_ids[session.npc_key]].append(session)
    for stable, affected in by_interaction.items():
        prior = old_interactions.get(stable)
        index, draft = new_interactions.get(stable, (None, None))
        # The display text is independent of the saved location and conditions.
        if {k: v for k, v in (prior or {}).items() if k not in {"label", "prompt"}} == {
            k: v for k, v in (draft or {}).items() if k not in {"label", "prompt"}
        }:
            continue
        path = ("configuration", "world", "interactions") + ((index,) if index is not None else ())
        yield AuthoringIssue(f"{_activity_counts(affected)}，请等他们结束后再移动或修改交互规则。",
                             path=path, target={"kind": "interaction", "id": stable}, code="active_world_interaction",
                             label=f"地图交互「{(prior or draft or {}).get('label', stable)}」", suggestion=END_INTERACTION)
    for stable, affected in by_npc.items():
        if old.get("npcs", {}).get(stable) != new.get("npcs", {}).get(stable):
            yield AuthoringIssue(f"{_activity_counts(affected)}，请等他们结束后再修改出现规则。",
                                 path=("configuration", "world", "npcs", stable), target={"kind": "npc", "id": stable},
                                 code="active_npc_rule", label=f"角色「{npc_labels.get(stable, stable)}」", suggestion=END_INTERACTION)


OPEN_STATUSES = {"active", "waiting"}


@dataclass
class PublicationReset:
    sessions: list
    progress: list
    events: list
    flags: list
    flow_impact: list
    quest_impact: list

    def impact(self, instance, user_id, draft):
        if not (self.sessions or self.progress or self.flags):
            return None
        students = {row.user_id for row in self.sessions} | {row.user_id for row in self.progress} | {student for student, _ in self.flags}
        confirmed = {
            "classroom": instance.classroom_id, "instance": instance.id, "teacher": user_id, "draft": draft,
            "sessions": [(row.id, row.revision, row.status, row.node_id) for row in self.sessions],
            "progress": [(row.id, row.user_id, row.quest_id, row.status, row.completed_at) for row in self.progress],
            "events": [(row.id, row.event_id) for row in self.events], "flags": self.flags,
        }
        fingerprint = sha256(json.dumps(confirmed, sort_keys=True, default=str).encode()).hexdigest()
        return {"token": fingerprint, "flows": self.flow_impact, "quests": self.quest_impact, "students": len(students),
                "checkpoint_flags": len(self.flags)}

    def apply(self, db, classroom_id):
        _remove(db, LearningEvent, LearningEvent.id, [row.id for row in self.events], LearningEvent.classroom_id == classroom_id)
        quest_ids = [row.id for row in self.progress]
        _remove(db, NodeState, NodeState.player_quest_id, quest_ids)
        _remove(db, PlayerQuest, PlayerQuest.id, quest_ids, PlayerQuest.classroom_id == classroom_id)
        _remove(db, StorySession, StorySession.id, [row.id for row in self.sessions], StorySession.classroom_id == classroom_id)
        _remove(db, WorldFlag, tuple_(WorldFlag.user_id, WorldFlag.key), self.flags, WorldFlag.classroom_id == classroom_id)


def _remove(db, model, field, values, *scope):
    for offset in range(0, len(values), 500):
        db.execute(delete(model).where(*scope, field.in_(values[offset:offset + 500])).execution_options(synchronize_session=False))


def _scope_changes(existing, incoming, maps, changed):
    changes = {}
    by_stable = {row["id"]: row for row in incoming}
    for row in existing.values():
        draft = by_stable.get(row.stable_id or row.id)
        if draft is None:
            changes[row.id] = "delete"
        elif changed(row, _json(draft, maps)):
            changes[row.id] = "edit"
    return changes


def _changed_task(existing, incoming):
    incoming["definition"]["id"] = existing.id
    return existing.chapter_id != incoming["chapter_id"] or changed_quest_structure(existing.definition, incoming["definition"])


def _checkpoint_flags(stories, quests, flow_changes, progress_by_quest):
    """Use exact marker keys, since similar prefixes and generic flags can be shared."""
    deleted = {}
    per_student = set()
    for story in stories.values():
        quest = quests.get(story.quest_id)
        if not quest:
            continue
        delete_flow = flow_changes.get(story.id) == "delete"
        if not delete_flow and quest.id not in progress_by_quest:
            continue
        checkpoints = {node.get("checkpoint") for node in story.content["nodes"]}
        for goal in quest.definition["nodes"]:
            marker = f"story:{story.id}:{goal['id']}"
            if not goal.get("target") or (goal["id"] not in checkpoints and goal.get("dialogue_id") != marker):
                continue
            key = f"dialogue:completed:{goal['target']}:{marker}"
            if delete_flow:
                deleted[key] = story.id
            else:
                per_student.update((row.user_id, key) for row in progress_by_quest[quest.id])
    return deleted, per_student


def _story_impacts(stories, changes, reset_sessions, removed_flags):
    for story_id in sorted(reset_sessions.keys() | removed_flags.keys()):
        story = stories[story_id]
        stable = story.stable_id or story.id
        rows = reset_sessions.get(story_id, [])
        counts = Counter(row.status for row in rows)
        yield {"id": stable, "title": story.content.get("title", stable), "reason": changes.get(story_id, "task_reset"),
               "sessions": len(rows), "active_sessions": counts["active"], "waiting_sessions": counts["waiting"],
               "students": len({row.user_id for row in rows} | removed_flags.get(story_id, set()))}


def publication_plan(db, instance, data, maps, configuration):
    classroom_id = instance.classroom_id
    stories = {row.id: row for row in db.scalars(select(StoryDefinition).where(StoryDefinition.course_instance_id == instance.id))}
    quests = {row.id: row for row in db.scalars(select(QuestDefinition).where(QuestDefinition.course_instance_id == instance.id))}
    flow_changes = _scope_changes(stories, data["story"], maps, _changed_story)
    task_changes = _scope_changes(quests, data["quest"], maps, _changed_task)
    affected_stories = {key for key, reason in flow_changes.items() if reason == "delete"}
    affected_stories.update(row.id for row in stories.values() if row.quest_id in task_changes)
    sessions = db.scalars(select(StorySession).where(StorySession.classroom_id == classroom_id,
        or_(StorySession.story_id.in_(affected_stories), StorySession.status.in_(OPEN_STATUSES))).order_by(StorySession.id))
    reset_sessions, retained = defaultdict(list), []
    for session in sessions:
        if session.story_id in affected_stories or session.story_id in flow_changes:
            reset_sessions[session.story_id].append(session)
        else:
            retained.append(session)

    progress_by_quest, event_counts = defaultdict(list), Counter()
    progress, events = [], []
    if task_changes:
        scope = (PlayerQuest.classroom_id == classroom_id, PlayerQuest.quest_id.in_(task_changes))
        progress = list(db.scalars(select(PlayerQuest).where(*scope).order_by(PlayerQuest.id)))
        for row in progress:
            progress_by_quest[row.quest_id].append(row)
        events = list(db.scalars(select(LearningEvent).join(PlayerQuest, LearningEvent.player_quest_id == PlayerQuest.id)
                                 .where(LearningEvent.classroom_id == classroom_id, *scope).order_by(LearningEvent.id)))
        event_counts.update(row.player_quest_id for row in events)
    deleted_keys, owned_flags = _checkpoint_flags(stories, quests, flow_changes, progress_by_quest)
    flags = []
    if deleted_keys or owned_flags:
        keys = sorted(deleted_keys.keys() | {key for _, key in owned_flags})
        for offset in range(0, len(keys), 500):
            candidates = db.execute(select(WorldFlag.user_id, WorldFlag.key).where(WorldFlag.classroom_id == classroom_id,
                                    WorldFlag.key.in_(keys[offset:offset + 500])))
            flags.extend((user_id, key) for user_id, key in candidates if key in deleted_keys or (user_id, key) in owned_flags)
        flags.sort()
    removed_flags = defaultdict(set)
    for user_id, key in flags:
        if key in deleted_keys:
            removed_flags[deleted_keys[key]].add(user_id)
    flow_impact = list(_story_impacts(stories, flow_changes, reset_sessions, removed_flags))
    quest_impact = []
    for quest_id, rows in progress_by_quest.items():
        quest = quests[quest_id]
        quest_impact.append({"id": quest.stable_id or quest.id, "title": quest.title, "reason": task_changes[quest_id],
                             "progress": len(rows), "students": len({row.user_id for row in rows}),
                             "events": sum(event_counts[row.id] for row in rows)})
    plan = PublicationReset([row for rows in reset_sessions.values() for row in rows], progress, events, flags, flow_impact, quest_impact)
    state = db.get(CoursePackageState, instance.id)
    old_world = state.configuration.get("world", {}) if state else {}
    npc_labels = {row["id"]: row["name"] for row in data["npc"]}
    issues = _world_issues(old_world, configuration.get("world", {}), retained, maps, npc_labels)
    return [issue.diagnostic() for issue in issues], plan
