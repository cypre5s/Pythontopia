"""Validate task graphs, dependency modes and evidence definitions."""

from sqlalchemy.orm import Session

from app.authoring_diagnostics import AuthoringIssue, located, node_label, row_target
from app.models import Activity, Chapter, Npc
from app.world_config import interaction_points

NODE_TYPES = {
    "TALK_TO_NPC",
    "PLAY_DIALOGUE",
    "REACH_LOCATION",
    "INTERACT_OBJECT",
    "STUDY_LESSON",
    "COMPLETE_PRACTICE",
    "PASS_ASSESSMENT",
    "REPORT_BACK",
}


OBJECTS = interaction_points()


DIALOGUE_EVIDENCE_ID_MAX_LENGTH = 207


def validate_definition(definition: dict, db: Session | None = None, *, objects=None) -> dict:
    if not isinstance(definition, dict):
        raise ValueError("任务定义必须为对象")
    if set(definition) - {"id", "title", "chapter_id", "nodes", "effects", "kind"}:
        raise ValueError("未知任务字段")
    if not isinstance(definition.get("id"), str) or not definition["id"].strip() or len(definition["id"]) > 100:
        raise ValueError("任务id必须为非空短字符串")
    if not isinstance(definition.get("title"), str) or not definition["title"].strip() or len(definition["title"]) > 200:
        raise ValueError("任务标题必须为非空短字符串")
    if type(definition.get("chapter_id")) is not int or definition["chapter_id"] < 1:
        raise ValueError("章节id必须为正整数")
    if definition.get("kind", "main") not in ("main", "side"):
        raise ValueError("任务kind只能为main或side")
    nodes = definition.get("nodes", [])
    if not isinstance(nodes, list) or not 1 <= len(nodes) <= 100 or any(not isinstance(node, dict) for node in nodes):
        raise ValueError("任务需要1至100个对象节点")
    ids = [node.get("id") for node in nodes]
    if any(not isinstance(id, str) or not id.strip() or len(id) > 100 for id in ids):
        raise ValueError("节点id必须为非空短字符串")
    if len(set(ids)) != len(ids):
        raise ValueError("节点id重复")
    mapping = {node["id"]: node for node in nodes}
    for index, node in enumerate(nodes):
        with located("nodes", index, target=row_target("goal", node), label=node_label(node, index)):
            if set(node) - {
                "id",
                "type",
                "title",
                "target",
                "dependencies",
                "aggregation",
                "optional",
                "activity_id",
                "condition",
                "choices",
                "dialogue_id",
                "navigation_target",
            }:
                raise ValueError("未知节点字段")
            if (
                not isinstance(node.get("type"), str)
                or node["type"] not in NODE_TYPES
                or not isinstance(node.get("title"), str)
                or not node["title"].strip()
            ):
                raise ValueError("节点类型或标题无效")
            if "optional" in node and type(node["optional"]) is not bool:
                raise ValueError("optional必须为布尔值")
            for field in ("target", "activity_id", "dialogue_id", "navigation_target"):
                limit = DIALOGUE_EVIDENCE_ID_MAX_LENGTH if field == "dialogue_id" else 100
                if field in node and (not isinstance(node[field], str) or not node[field].strip() or len(node[field]) > limit):
                    raise ValueError(f"{field}必须为非空短字符串")
            if node.get("navigation_target") and db and not db.get(Npc, node["navigation_target"]):
                raise ValueError("导航NPC不存在")
            dependencies = node.get("dependencies", [])
            if not isinstance(dependencies, list) or any(not isinstance(dep, str) or not dep for dep in dependencies):
                raise ValueError("dependencies必须为节点id字符串列表")
            if len(set(dependencies)) != len(dependencies) or any(dep not in mapping for dep in dependencies):
                raise ValueError("悬空或重复依赖")
            if node.get("aggregation", "ALL") not in ("ALL", "ANY"):
                raise ValueError("聚合必须为ALL或ANY")
            choices = node.get("choices", [])
            if not isinstance(choices, list) or any(not isinstance(choice, dict) or set(choice) != {"id", "label"} for choice in choices):
                raise ValueError("choices必须为id/label对象列表")
            if any(
                not isinstance(choice["id"], str)
                or not choice["id"].strip()
                or len(choice["id"]) > 80
                or not isinstance(choice["label"], str)
                or not choice["label"].strip()
                or len(choice["label"]) > 200
                for choice in choices
            ):
                raise ValueError("选项id和label必须为非空短字符串")
            if len({choice["id"] for choice in choices}) != len(choices):
                raise ValueError("选择项id重复")
            condition = node.get("condition")
            if condition is not None:
                if (
                    not isinstance(condition, dict)
                    or set(condition) != {"node", "choice"}
                    or any(not isinstance(condition[key], str) or not condition[key] for key in ("node", "choice"))
                ):
                    raise ValueError("condition必须包含node和choice字符串")
                if condition["node"] not in mapping or condition["node"] not in dependencies:
                    raise ValueError("分支条件必须引用直接前置节点")
                parent_choices = mapping[condition["node"]].get("choices", [])
                if not isinstance(parent_choices, list) or not any(
                    isinstance(choice, dict) and choice.get("id") == condition["choice"] for choice in parent_choices
                ):
                    raise ValueError("分支选项不存在")
            if node["type"] in {"STUDY_LESSON", "COMPLETE_PRACTICE", "PASS_ASSESSMENT"}:
                if not node.get("activity_id"):
                    raise ValueError("学习节点必须绑定活动")
                if db:
                    activity = db.get(Activity, node["activity_id"])
                    if not activity:
                        raise ValueError("活动不存在")
                    if activity.content.get("published") is False:
                        raise ValueError("活动尚未发布")
                    expected = {"STUDY_LESSON": {"lesson", "reading"}, "COMPLETE_PRACTICE": {"practice"}, "PASS_ASSESSMENT": {"assessment"}}[
                        node["type"]
                    ]
                    if activity.kind not in expected:
                        raise ValueError("任务节点与教学活动类型不匹配")
            if node["type"] in {"TALK_TO_NPC", "PLAY_DIALOGUE", "REPORT_BACK"}:
                if not node.get("target") or (db and not db.get(Npc, node["target"])):
                    raise ValueError("NPC不存在")
            if node["type"] in {"REACH_LOCATION", "INTERACT_OBJECT"} and node.get("target") not in (OBJECTS if objects is None else objects):
                raise ValueError("目标对象不存在")
    node_indices = {node["id"]: index for index, node in enumerate(nodes)}
    visiting, done = set(), set()

    def visit(node_id):
        if node_id in visiting:
            index = node_indices[node_id]
            raise AuthoringIssue(
                "任务节点存在循环",
                path=("nodes", index),
                target=row_target("goal", mapping[node_id]),
                label=node_label(mapping[node_id], index),
                code="quest_cycle",
                suggestion="移除返回上游目标的依赖连线。",
            )
        if node_id in done:
            return
        visiting.add(node_id)
        for dependency in mapping[node_id].get("dependencies", []):
            visit(dependency)
        visiting.remove(node_id)
        done.add(node_id)

    for node_id in mapping:
        visit(node_id)
    if db and not db.get(Chapter, definition["chapter_id"]):
        raise ValueError("章节不存在")
    effects = definition.get("effects", [])
    if not isinstance(effects, list):
        raise ValueError("effects必须为列表")
    for effect in effects:
        if isinstance(effect, dict) and effect.get("type") == "unlock_building":
            from app.teaching.world.rules import interior_buildings

            if set(effect) != {"type", "building_id"} or effect["building_id"] not in interior_buildings():
                raise ValueError("建筑解锁效果必须引用可用室内")
            continue
        if (
            not isinstance(effect, dict)
            or set(effect) != {"type", "key"}
            or effect.get("type") != "set_world_flag"
            or not isinstance(effect.get("key"), str)
            or not effect["key"].strip()
            or len(effect["key"]) > 100
        ):
            raise ValueError("只允许明确的世界标记效果")
    return definition
