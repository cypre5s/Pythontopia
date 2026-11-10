"""Check draft compatibility with saved progress before publishing teaching content."""

from collections import defaultdict

from sqlalchemy import select

from .authoring_diagnostics import AuthoringIssue, node_label
from .course_exchange_models import CoursePackageState
from .course_instances import _json
from .models import PlayerQuest, QuestDefinition, StoryDefinition, StorySession


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


def _story_issues(db, instance, rows, maps, active):
    incoming = {row["id"]: (index, row) for index, row in enumerate(rows)}
    for existing in db.scalars(select(StoryDefinition).where(StoryDefinition.course_instance_id == instance.id)):
        stable = existing.stable_id or existing.id
        label = f"流程「{existing.content.get('title', stable)}」"
        target = {"kind": "flow", "id": stable, "flow_id": stable}
        entry = incoming.get(stable)
        if entry is None:
            if db.scalar(select(StorySession.id).where(StorySession.classroom_id == instance.classroom_id,
                                                       StorySession.story_id == existing.id).limit(1)):
                yield AuthoringIssue("这个流程已产生互动记录，不能删除。", path=("flows",), target=target,
                                     label=label, code="flow_history", suggestion="请保留原流程，或另建一个新流程。")
            continue
        index, row = entry
        if existing.id not in active or not _changed_story(existing, _json(row, maps)):
            continue
        sessions_by_node = defaultdict(list)
        for session in active[existing.id]:
            sessions_by_node[session.node_id].append(session)
        draft_nodes = {node["id"]: index for index, node in enumerate(row["content"]["nodes"])}
        saved_nodes = {node["id"]: node_label(node, index) for index, node in enumerate(existing.content["nodes"])}
        for node_id, sessions in sessions_by_node.items():
            path = ("flows", index, "content")
            node_target = target
            if node_id in draft_nodes:
                path += ("nodes", draft_nodes[node_id])
                node_target = {**target, "kind": "node", "id": node_id}
            where = saved_nodes.get(node_id, "一个已发布节点")
            yield AuthoringIssue(f"{_activity_counts(sessions)}，停在{where}。本次发布会改变他们正在使用的流程。",
                                 path=path, target=node_target, label=label, code="active_story_session",
                                 suggestion=END_INTERACTION)


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


def _quest_issues(db, instance, rows, maps):
    incoming = {row["id"]: (index, row) for index, row in enumerate(rows)}
    for existing in db.scalars(select(QuestDefinition).where(QuestDefinition.course_instance_id == instance.id)):
        stable = existing.stable_id or existing.id
        index, row = incoming.get(stable, (None, None))
        if row:
            actual = _json(row, maps)
            actual["definition"]["id"] = existing.id
            if existing.definition == actual["definition"] and existing.chapter_id == actual["chapter_id"]:
                continue
        if db.scalar(select(PlayerQuest.id).where(PlayerQuest.classroom_id == instance.classroom_id,
                                                  PlayerQuest.quest_id == existing.id).limit(1)):
            path = ("quests",) if index is None else ("quests", index, "definition")
            yield AuthoringIssue("本班已有学生任务进度，不能覆盖目标结构或删除任务。",
                                 path=path, target={"kind": "quest", "id": stable, "quest_id": stable},
                                 code="quest_progress", label=f"任务「{existing.title}」", suggestion="保留现有任务，另建任务承载新的目标结构。")


def publication_diagnostics(db, instance, data, maps, configuration):
    sessions = list(db.scalars(select(StorySession).where(StorySession.classroom_id == instance.classroom_id,
                                                         StorySession.status.in_(["active", "waiting"]))))
    active = defaultdict(list)
    for session in sessions:
        active[session.story_id].append(session)
    state = db.get(CoursePackageState, instance.id)
    old_world = state.configuration.get("world", {}) if state else {}
    npc_labels = {row["id"]: row["name"] for row in data["npc"]}
    issues = [*_story_issues(db, instance, data["story"], maps, active),
              *_quest_issues(db, instance, data["quest"], maps),
              *_world_issues(old_world, configuration.get("world", {}), sessions, maps, npc_labels)]
    return [issue.diagnostic() for issue in issues]
