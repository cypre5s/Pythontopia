"""Bounded story predicates over authorized current configuration and player evidence."""

from fastapi import HTTPException
from sqlalchemy import select

from .courses import authorize_activity, authorize_classroom, chapter_available, chapter_completion_requirements
from .models import Activity, ActivityProgress, Chapter, NodeState, PlayerQuest, QuestDefinition
from .quests import node_statuses, quest_visible


class UnavailableCondition(Exception):
    """A missing or unauthorized target must not become true through negation."""


def _short(value):
    return isinstance(value, str) and 0 < len(value) <= 100 and bool(value.strip())


def validate_condition(condition, bindings, quest_definition=None, db=None, depth=0, budget=None):
    budget = [0] if budget is None else budget
    budget[0] += 1
    if depth > 8 or budget[0] > 100 or not isinstance(condition, dict) or len(condition) != 1:
        raise ValueError("演出条件必须是有界的受限条件")
    key, value = next(iter(condition.items()))
    if key in {"all", "any"}:
        if not isinstance(value, list) or not 1 <= len(value) <= 20:
            raise ValueError("组合条件需要1至20个子条件")
        for child in value:
            validate_condition(child, bindings, quest_definition, db, depth + 1, budget)
    elif key == "not":
        validate_condition(value, bindings, quest_definition, db, depth + 1, budget)
    elif key in {"always", "all_quests_complete"}:
        if value is not True:
            raise ValueError("布尔条件只能声明true")
    elif key in {"goal_complete", "goal_ready"}:
        if not _short(value) or (quest_definition and value not in {node["id"] for node in quest_definition["nodes"]}):
            raise ValueError("条件引用了不存在的任务目标")
    elif key == "activity_started":
        if not _short(value) or value not in bindings:
            raise ValueError("条件引用了不存在的活动绑定")
    elif key in {"quest", "goal"}:
        fields = {"id", "status"} if key == "quest" else {"quest_id", "id", "status"}
        statuses = {"active", "complete"} if key == "quest" else {"ready", "complete", "locked", "skipped"}
        if (not isinstance(value, dict) or set(value) != fields or not _short(value.get("id"))
                or not isinstance(value.get("status"), str) or value["status"] not in statuses):
            raise ValueError("无效任务状态条件")
        quest_id = value["id"] if key == "quest" else value["quest_id"]
        if not _short(quest_id):
            raise ValueError("无效任务条件标识")
        definition = quest_definition if quest_definition and quest_definition["id"] == quest_id else None
        if not definition and db:
            item = db.get(QuestDefinition, quest_id)
            if not item:
                raise ValueError("条件引用了不存在的任务")
            definition = item.definition
        if key == "goal" and definition and value["id"] not in {node["id"] for node in definition["nodes"]}:
            raise ValueError("条件引用了不存在的任务目标")
    elif key == "chapter":
        if (not isinstance(value, dict) or set(value) != {"id", "status"} or type(value.get("id")) is not int
                or value["id"] < 1 or not isinstance(value.get("status"), str) or value["status"] not in {"available", "complete"}
                or db and not db.get(Chapter, value["id"])):
            raise ValueError("无效章节状态条件")
    elif key == "assessment_score":
        if (not isinstance(value, dict) or set(value) - {"activity_id", "min", "max"} or not _short(value.get("activity_id"))
                or not {"min", "max"} & set(value)):
            raise ValueError("测验成绩条件需要活动和分数范围")
        if any(type(value.get(field, default)) is not int or not 0 <= value.get(field, default) <= 100 for field, default in (("min", 0), ("max", 100))):
            raise ValueError("测验分数必须为0至100的整数")
        if value.get("min", 0) > value.get("max", 100):
            raise ValueError("测验分数范围倒置")
        if db:
            activity = db.get(Activity, value["activity_id"])
            if not activity or activity.kind != "assessment":
                raise ValueError("分数条件必须引用测验活动")
    else:
        raise ValueError("未知演出条件")


def condition_matches(db, user, classroom_id, story, condition):
    try:
        return _matches(db, user, classroom_id, story, condition)
    except UnavailableCondition:
        return False


def _chapter_available(db, user, classroom, chapter_id):
    chapter = db.get(Chapter, chapter_id)
    if not chapter or chapter.owner_id not in {None, classroom.teacher_id} and user.role != "admin":
        raise UnavailableCondition
    return chapter_available(db, user, classroom.id, chapter_id)


def _matches(db, user, classroom_id, story, condition):
    key, value = next(iter(condition.items()))
    if key == "always":
        return True
    if key == "all":
        return all(_matches(db, user, classroom_id, story, child) for child in value)
    if key == "any":
        return any(_matches(db, user, classroom_id, story, child) for child in value)
    if key == "not":
        return not _matches(db, user, classroom_id, story, value)
    classroom = authorize_classroom(db, user, classroom_id)
    if key == "activity_started":
        activity_id = story.content["bindings"][value]["activity_id"]
        try:
            authorize_activity(db, user, classroom_id, activity_id)
        except HTTPException as exc:
            raise UnavailableCondition from exc
        return bool(db.get(ActivityProgress, (user.id, classroom_id, activity_id)))
    if key == "assessment_score":
        from .assessments import assessment_payload

        try:
            activity = authorize_activity(db, user, classroom_id, value["activity_id"])
        except HTTPException as exc:
            raise UnavailableCondition from exc
        if activity.kind != "assessment":
            raise UnavailableCondition
        score = assessment_payload(db, user, classroom_id, activity)["score"]
        return score is not None and value.get("min", 0) <= score <= value.get("max", 100)
    if key in {"chapter", "all_quests_complete"}:
        if key == "chapter":
            if not _chapter_available(db, user, classroom, value["id"]):
                return False
            if value["status"] == "available":
                return True
            ids = chapter_completion_requirements(db, classroom_id, value["id"]).get(value["id"], set())
        else:
            ids = {item.id for item in db.scalars(select(QuestDefinition)) if quest_visible(item, user, classroom)
                   and chapter_available(db, user, classroom_id, item.chapter_id)}
        completed = set(db.scalars(select(PlayerQuest.quest_id).where(PlayerQuest.user_id == user.id,
            PlayerQuest.classroom_id == classroom_id, PlayerQuest.status == "complete")))
        return bool(ids) and ids.issubset(completed)
    quest_id = value["quest_id"] if key == "goal" else value["id"] if key == "quest" else story.quest_id
    definition = db.get(QuestDefinition, quest_id) if quest_id else None
    if not quest_visible(definition, user, classroom):
        raise UnavailableCondition
    if key != "quest" and (value["id"] if key == "goal" else value) not in {node["id"] for node in definition.definition["nodes"]}:
        raise UnavailableCondition
    if not _chapter_available(db, user, classroom, definition.chapter_id):
        return False
    instance = db.scalar(select(PlayerQuest).where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == classroom_id,
                                                  PlayerQuest.quest_id == quest_id))
    if not instance:
        return False
    if key == "quest":
        return instance.status == value["status"]
    states = {row.node_id: row for row in db.scalars(select(NodeState).where(NodeState.player_quest_id == instance.id))}
    statuses = node_statuses(definition.definition, states)
    goal_id = value["id"] if key == "goal" else value
    expected = value["status"] if key == "goal" else "complete" if key == "goal_complete" else "ready"
    return statuses.get(goal_id) == expected
