"""Load bounded, data-only chapter sources; import current teaching configuration."""

import argparse
import json
import os
from pathlib import Path

import yaml
from yaml.constructor import ConstructorError
from yaml.tokens import AliasToken, AnchorToken

CONTENT_ROOT = Path(__file__).resolve().parents[2] / "data" / "course" / "legacy" / "content"


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


def load_content(root=None):
    root = Path(root or os.getenv("REBORN_CONTENT_ROOT", str(CONTENT_ROOT)))
    bundles = [load_yaml(path) for path in sorted((root / "chapters").glob("*.yaml"))]
    questions = []
    for path in sorted((root / "questions").glob("*.yaml")):
        bundle = load_yaml(path)
        if set(bundle) != {"schema_version", "questions"} or not isinstance(bundle["questions"], list):
            raise ValueError("题库文件仅允许schema_version与questions列表")
        questions.extend(bundle["questions"])
    if not bundles:
        raise ValueError("未找到章节脚本")
    npcs, activities, quests, stories = [], [], [], []
    for bundle in bundles:
        expected = {"schema_version", "npcs", "activities", "quests"} if "quests" in bundle else {
            "schema_version", "id", "chapter_id", "quest", "story", "activities"
        }
        if set(bundle) - expected:
            raise ValueError("章节包包含未知字段")
        npcs.extend(bundle.get("npcs", []))
        activities.extend(bundle.get("activities", []))
        if "quests" in bundle:
            quests.extend(bundle["quests"])
        else:
            quest = bundle["quest"]
            if quest["chapter_id"] != bundle["chapter_id"]:
                raise ValueError("章节包与任务的章节不一致")
            quests.append(quest)
            stories.append({key: bundle[key] for key in ("id", "chapter_id", "quest", "story")})
    for path in sorted((root / "ambient").glob("*.yaml")):
        bundle = load_yaml(path)
        if (set(bundle) != {"schema_version", "id", "chapter_id", "story"} or not isinstance(bundle["story"], dict)
                or bundle["story"].get("mode") != "ambient"):
            raise ValueError("日常对白包仅允许标识、章节与独立日常演出")
        if (not isinstance(bundle["id"], str) or not bundle["id"].strip() or len(bundle["id"]) > 100
                or type(bundle["chapter_id"]) is not int or bundle["chapter_id"] < 1):
            raise ValueError("无效日常对白标识或章节")
        stories.append({"id": bundle["id"], "chapter_id": bundle["chapter_id"], "quest": None, "story": bundle["story"]})
    for label, rows, keys in (
        ("NPC", npcs, ("key",)), ("活动", activities, ("id",)),
        ("任务", quests, ("id",)), ("演出", stories, ("id",)), ("题目", questions, ("id",)),
    ):
        ids = [tuple(row[key] for key in keys) for row in rows]
        if len(set(ids)) != len(ids):
            raise ValueError(f"{label}配置标识重复")
    return dict(npcs=npcs, activities=sorted(activities, key=lambda a: a["id"]),
                quests=sorted(quests, key=lambda q: q["id"]), stories=stories, questions=questions)


def validate_sources(root=None):
    from app.quests import validate_definition
    from app.story import validate_story
    from app.learning_rules import validate_learning

    content = load_content(root)
    for quest in content["quests"]:
        validate_definition(quest)
    activities = {item["id"] for item in content["activities"]}
    npc_keys = {item["key"] for item in content["npcs"]}
    questions = {item["id"] for item in content["questions"]}
    for item in content["activities"]:
        validate_learning(item["content"], item["kind"])
        ids = item["content"].get("question_ids")
        if not isinstance(ids, list) or any(not isinstance(key, str) for key in ids) or len(set(ids)) != len(ids):
            raise ValueError("每个活动必须显式固定非重复question_ids")
        if len(ids) > (100 if item["kind"] == "assessment" else 5000):
            raise ValueError("章节测验最多100道题，其他活动最多5000道题")
        # Exported course questions are validated against the chosen JSON package
        # by import-course; YAML questions may be reused by multiple activities.
        if any(key not in questions and not (key.startswith("course-") and key[7:].isdigit()) for key in ids):
            raise ValueError("活动题集引用了未配置的题目")
    for story in content["stories"]:
        validate_story(story["story"], quest_definition=story["quest"], story_id=story["id"])
        if set(story["story"]["entries"]) - npc_keys:
            raise ValueError("演出引用了未配置的NPC")
        for binding in story["story"]["bindings"].values():
            if binding["activity_id"] not in activities:
                raise ValueError("演出引用了未配置的活动")
    return {key: len(value) for key, value in content.items()}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Validate chapter YAML sources (no database writes).")
    parser.add_argument("--check", action="store_true", help="Validate chapter sources and print counts")
    parser.add_argument("--content", type=Path, default=None, help="External YAML content directory")
    args = parser.parse_args()
    print(json.dumps(validate_sources(args.content), ensure_ascii=False))
