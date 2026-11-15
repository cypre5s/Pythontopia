"""Validate declarative conditions without executing configured code."""

from app.models import Activity, Chapter, QuestDefinition


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
    elif key == "activity_completed":
        if not _short(value) or db and not db.get(Activity, value):
            raise ValueError("条件引用了不存在的活动")
    elif key == "building_unlocked":
        from app.world_config import load_world_config

        if not _short(value) or value not in {b["id"] for area in load_world_config()["maps"].values() for b in area["buildings"]}:
            raise ValueError("条件引用了不存在的建筑")
    elif key in {"quest", "goal"}:
        fields = {"id", "status"} if key == "quest" else {"quest_id", "id", "status"}
        statuses = {"active", "complete"} if key == "quest" else {"ready", "complete", "locked", "skipped"}
        if (
            not isinstance(value, dict)
            or set(value) != fields
            or not _short(value.get("id"))
            or not isinstance(value.get("status"), str)
            or value["status"] not in statuses
        ):
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
        if (
            not isinstance(value, dict)
            or set(value) != {"id", "status"}
            or type(value.get("id")) is not int
            or value["id"] < 1
            or not isinstance(value.get("status"), str)
            or value["status"] not in {"available", "complete"}
            or db
            and not db.get(Chapter, value["id"])
        ):
            raise ValueError("无效章节状态条件")
    elif key == "assessment_score":
        if (
            not isinstance(value, dict)
            or set(value) - {"activity_id", "min", "max"}
            or not _short(value.get("activity_id"))
            or not {"min", "max"} & set(value)
        ):
            raise ValueError("测验成绩条件需要活动和分数范围")
        if any(
            type(value.get(field, default)) is not int or not 0 <= value.get(field, default) <= 100 for field, default in (("min", 0), ("max", 100))
        ):
            raise ValueError("测验分数必须为0至100的整数")
        if value.get("min", 0) > value.get("max", 100):
            raise ValueError("测验分数范围倒置")
        if db:
            activity = db.get(Activity, value["activity_id"])
            if not activity or activity.kind != "assessment":
                raise ValueError("分数条件必须引用测验活动")
    else:
        raise ValueError("未知演出条件")
