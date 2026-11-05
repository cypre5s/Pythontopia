"""Load bounded, data-only chapter sources; publish immutable JSON snapshots."""

import argparse
import json
from pathlib import Path

import yaml
from yaml.constructor import ConstructorError
from yaml.tokens import AliasToken, AnchorToken

CONTENT_ROOT = Path(__file__).resolve().parents[1] / "content"


class ChapterLoader(yaml.SafeLoader):
    """SafeLoader with duplicate keys rejected, rather than silently overwritten."""


def unique_mapping(loader, node, deep=False):
    result = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if not isinstance(key, str):
            raise ConstructorError(None, None, "配置字段名必须为字符串", key_node.start_mark)
        if key in result:
            raise ConstructorError(None, None, f"重复字段：{key}", key_node.start_mark)
        result[key] = loader.construct_object(value_node, deep=deep)
    return result


ChapterLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, unique_mapping)


def load_yaml(path: Path):
    if path.stat().st_size > 512 * 1024:
        raise ValueError(f"{path.name}: 章节文件超过512KiB")
    text = path.read_text(encoding="utf-8-sig")
    try:
        for token in yaml.scan(text):
            if isinstance(token, (AliasToken, AnchorToken)):
                raise ValueError("章节文件不支持YAML锚点或别名")
        data = yaml.load(text, Loader=ChapterLoader)
        budget = [0]

        def check(value, depth=0):
            budget[0] += 1
            if depth > 30 or budget[0] > 20000:
                raise ValueError("章节数据嵌套过深或条目过多")
            if isinstance(value, dict):
                for child in value.values():
                    check(child, depth + 1)
            elif isinstance(value, list):
                for child in value:
                    check(child, depth + 1)
            elif type(value) not in (str, int, bool, type(None)):
                raise ValueError("章节内容只允许字符串、整数、布尔值、列表和对象")

        check(data)
        if not isinstance(data, dict) or data.get("schema_version") != 1:
            raise ValueError("未知章节文件格式，schema_version必须为1")
        return data
    except (yaml.YAMLError, ValueError, RecursionError) as exc:
        raise ValueError(f"{path.name}: {exc}") from exc


def load_content(root=CONTENT_ROOT):
    bundles = [load_yaml(path) for path in sorted((root / "chapters").glob("*.yaml"))]
    questions = load_yaml(root / "questions/concepts.private.yaml")["questions"]
    if not bundles:
        raise ValueError("未找到章节脚本")
    npcs, activities, quests, stories = [], [], [], []
    for bundle in bundles:
        expected = {"schema_version", "npcs", "activities", "quests"} if "quests" in bundle else {
            "schema_version", "id", "version", "chapter_id", "quest", "story", "activities"
        }
        if set(bundle) - expected:
            raise ValueError("章节包包含未知字段")
        npcs.extend(bundle.get("npcs", []))
        activities.extend(bundle.get("activities", []))
        if "quests" in bundle:
            quests.extend(bundle["quests"])
        else:
            quest = bundle["quest"]
            if quest["chapter_id"] != bundle["chapter_id"] or quest["version"] != bundle["version"]:
                raise ValueError("章节包与任务的章节/版本不一致")
            quests.append(quest)
            stories.append({key: bundle[key] for key in ("id", "version", "chapter_id", "quest", "story")})
    for label, rows, keys in (
        ("NPC", npcs, ("key",)), ("活动", activities, ("id", "version")),
        ("任务", quests, ("id", "version")), ("演出", stories, ("id", "version")), ("题目", questions, ("id",)),
    ):
        ids = [tuple(row[key] for key in keys) for row in rows]
        if len(set(ids)) != len(ids):
            raise ValueError(f"{label}配置标识重复")
    return dict(npcs=npcs, activities=sorted(activities, key=lambda a: (a["version"], a["id"])),
                quests=sorted(quests, key=lambda q: (q["version"], q["id"])), stories=stories, questions=questions)


def validate_sources():
    from .dialogues import validate_dialogue
    from .quests import validate_definition
    from .story import validate_story

    content = load_content()
    for npc in content["npcs"]:
        validate_dialogue(npc["dialogue"])
    for quest in content["quests"]:
        validate_definition(quest)
    activities = {(item["id"], item["version"]) for item in content["activities"]}
    npc_keys = {item["key"] for item in content["npcs"]}
    questions = {item["id"]: item["activity_id"] for item in content["questions"]}
    for item in content["activities"]:
        ids = item["content"].get("question_ids")
        if not isinstance(ids, list) or any(not isinstance(key, str) for key in ids) or len(set(ids)) != len(ids):
            raise ValueError("每个活动版本必须显式固定非重复question_ids")
        allowed = {key for key, owner in questions.items() if owner == item["id"]}
        if item.get("knowledge_point_mode") == "problem":
            # Reviewed original course problem is imported from the real course export.
            allowed.add("course-476")
        if set(ids) - allowed:
            raise ValueError("活动题集引用了未配置或不属于它的题目")
    for story in content["stories"]:
        validate_story(story["story"], quest_definition=story["quest"], story_id=story["id"], story_version=story["version"])
        if set(story["story"]["entries"]) - npc_keys:
            raise ValueError("演出引用了未配置的NPC")
        for binding in story["story"]["bindings"].values():
            if (binding["activity_id"], binding["version"]) not in activities:
                raise ValueError("演出引用了未配置的活动版本")
    return {key: len(value) for key, value in content.items()}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Validate versioned chapter YAML sources (no database writes).")
    parser.add_argument("--check", action="store_true", help="Validate chapter sources and print counts")
    parser.parse_args()
    print(json.dumps(validate_sources(), ensure_ascii=False))
