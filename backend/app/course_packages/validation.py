"""Validate package content references and teaching definitions before import."""

from datetime import datetime
from pathlib import Path

from app.course_content.snapshot import FIELDS
from app.course_instances import MODEL_NAMES, remap_references
from app.course_packages.contracts import EXTRAS
from app.course_packages.manifest import PackageError
from app.materials.uploads import validate_upload
from app.models import Activity, Chapter, CourseExample, KnowledgePoint, Material, QuestDefinition


def _object(value, keys, required=None):
    if not isinstance(value, dict) or set(value) - set(keys) or not set(required or []).issubset(value):
        raise PackageError("课程对象字段缺失或包含未知字段")


def _short(value, maximum=100):
    return isinstance(value, str) and bool(value.strip()) and len(value) <= maximum


def _bounded(value, depth=0, budget=None):
    budget = [0] if budget is None else budget
    budget[0] += 1
    if depth > 24 or budget[0] > 200000:
        raise PackageError("课程配置嵌套或对象数量超限")
    if isinstance(value, dict):
        if any(not isinstance(key, str) for key in value):
            raise PackageError("配置字段必须为字符串")
        for item in value.values():
            _bounded(item, depth + 1, budget)
    elif isinstance(value, list):
        for item in value:
            _bounded(item, depth + 1, budget)
    elif value is not None and type(value) not in (str, int, float, bool):
        raise PackageError("配置只支持JSON值")
    elif isinstance(value, float):
        import math

        if not math.isfinite(value):
            raise PackageError("配置不能包含非有限数字")


def validate_bundle(bundle, root):
    """Validate portable references and full graphs before any database/storage writes."""
    from types import SimpleNamespace

    from app.course_content.question_contracts import QuestionInput
    from app.learning_rules import validate_learning
    from app.teaching.conditions.validation import validate_condition
    from app.teaching.quests.validation import validate_definition
    from app.teaching.story.validation import validate_story

    try:
        _bounded(bundle)
        data = bundle["data"]
        indexed, maps = {}, {}
        for model, fields in FIELDS.items():
            kind, items = MODEL_NAMES[model], data[MODEL_NAMES[model]]
            if not isinstance(items, list) or len(items) > 10000:
                raise PackageError("课程领域条目超限")
            indexed[kind] = {}
            for row in items:
                keys = {"id", *fields, *EXTRAS.get(model, set())}
                _object(row, keys, keys)
                if not _short(row["id"]) or row["id"] in indexed[kind]:
                    raise PackageError("课程稳定ID无效或重复")
                if "title" in row and not _short(row["title"], 200):
                    raise PackageError("课程标题无效")
                if any(not isinstance(row[field], dict) for field in ("content", "definition", "source") if field in row):
                    raise PackageError("课程内容必须为对象")
                indexed[kind][row["id"]] = row
            maps[kind] = {key: (i + 1 if model in {Chapter, Material, KnowledgePoint, CourseExample} else key) for i, key in enumerate(indexed[kind])}
        # Typed conversion resolves every declared foreign reference, including conditions.
        converted = {kind: {key: remap_references(row, maps) for key, row in items.items()} for kind, items in indexed.items()}
        for model in (KnowledgePoint, Material, Activity, QuestDefinition):
            for row in indexed[MODEL_NAMES[model]].values():
                if "chapter_id" in row and row["chapter_id"] is None:
                    raise PackageError("此类课程条目必须绑定章节")
        orders = [row["order_index"] for row in data["chapter"]]
        if any(type(order) is not int or not 0 <= order <= 100000 for order in orders) or len(set(orders)) != len(orders):
            raise PackageError("章节顺序无效或重复")
        secret_map = {}
        for row in bundle["secrets"]:
            _object(row, {"id", "answer", "hidden_cases"}, {"id", "answer", "hidden_cases"})
            if row["id"] not in indexed["question"] or row["id"] in secret_map:
                raise PackageError("私有答案引用缺失或重复")
            secret_map[row["id"]] = row
        if set(secret_map) != set(indexed["question"]):
            raise PackageError("每道题必须具有私有答案记录（草稿可为空）")
        assets = {asset["id"]: asset for asset in bundle["assets"]}
        from app.characters import validate_image

        for asset in bundle["assets"]:
            if asset.get("character_kind"):
                metadata = validate_image(root / asset["path"], asset["character_kind"], asset.get("frame_width", 16), asset.get("frame_height", 20))
                if any(asset.get(key) != value for key, value in metadata.items()):
                    raise PackageError("角色素材元信息与实际图片不一致")
        for row in data["material"]:
            if row["kind"] not in {"text", "pdf", "video", "presentation"} or row["status"] not in {"ready", "failed", "missing"}:
                raise PackageError("资料类型或状态无效")
            if row["error"] is not None and not isinstance(row["error"], str):
                raise PackageError("资料错误信息无效")
            if row["asset_id"] is not None and row["asset_id"] not in assets:
                raise PackageError("资料引用缺失资源")
            if "preview_key" in row["content"] or "storage_key" in row["content"]:
                raise PackageError("课程包不能包含服务器存储键")
            if row["preview_asset_id"] is not None:
                preview_asset = assets.get(row["preview_asset_id"])
                if not preview_asset or preview_asset["media_type"] != "application/pdf":
                    raise PackageError("转换预览必须引用实际PDF资源")
                validate_upload(root / preview_asset["path"], ".pdf")
            if row["kind"] == "presentation" and row["status"] == "ready" and not row["preview_asset_id"]:
                raise PackageError("已就绪演示文稿缺少转换预览PDF")
            if row["kind"] != "text" and row["asset_id"] is None:
                raise PackageError("完整课程包不能包含缺失媒体")
            pages = row["content"].get("pages", [])
            if not isinstance(pages, list) or len(pages) > 10000:
                raise PackageError("资料页数据无效")
            ids, numbers = set(), set()
            for page in pages:
                if not isinstance(page, dict) or type(page.get("id")) is not int or type(page.get("page_no")) is not int:
                    raise PackageError("资料页需要原始整数ID与页码")
                if page["id"] in ids or page["page_no"] in numbers or page["id"] < 1 or page["page_no"] < 1:
                    raise PackageError("资料页重复或页码无效")
                ids.add(page["id"])
                numbers.add(page["page_no"])
            if len(row["chapter_ids"]) != len(set(row["chapter_ids"])):
                raise PackageError("资料章节关联重复")
            if row["asset_id"]:
                asset = assets[row["asset_id"]]
                from app.materials.uploads import KINDS

                suffix = Path(asset["path"]).suffix.lower()
                if KINDS.get(suffix) != row["kind"]:
                    raise PackageError("资料类型与资源文件不匹配")
                validate_upload(root / asset["path"], suffix)
        for key, row in converted["question"].items():
            QuestionInput.model_validate({**row, **{field: secret_map[key][field] for field in ("answer", "hidden_cases")}}, strict=True)
            portable = indexed["question"][key]
            for point_id in portable["knowledge_point_ids"]:
                if indexed["knowledge_point"][point_id]["chapter_id"] not in portable["chapter_ids"]:
                    raise PackageError("题目知识点必须属于关联章节")
            source = portable["source"]
            if "page_no" in source and (
                "material_id" not in source
                or source["page_no"] not in {page["page_no"] for page in indexed["material"][source["material_id"]]["content"].get("pages", [])}
            ):
                raise PackageError("题目来源页不存在")
        from app.characters import validate_characters

        validate_characters(data["npc"], bundle["assets"])
        for row in data["activity"]:
            if row["kind"] not in {"lesson", "reading", "practice", "assessment"} or type(row["assistant_allowed"]) is not bool:
                raise PackageError("学习活动类型或助教开关无效")
            if "question_ids" in row["content"]:
                raise PackageError("活动题集只能声明于结构化question_ids关联")
            if row["npc_key"] is not None and row["npc_key"] not in indexed["npc"]:
                raise PackageError("活动NPC不存在")
            material = indexed["material"].get(row["material_id"])
            if material and row["chapter_id"] not in material["chapter_ids"]:
                raise PackageError("活动资料必须属于活动章节")
            validate_learning(row["content"], row["kind"], SimpleNamespace(**material) if material else None, resolve=True)
            if len(set(row["question_ids"])) != len(row["question_ids"]):
                raise PackageError("活动题目重复")
            for question_id in row["question_ids"]:
                question = indexed["question"][question_id]
                if row["chapter_id"] not in question["chapter_ids"] or question["status"] != "ready":
                    raise PackageError("活动题目必须已就绪并属于当前章节")
        for key, row in converted["quest"].items():
            definition = row["definition"]
            if definition.get("id") != key or definition.get("chapter_id") != row["chapter_id"]:
                raise PackageError("任务定义与外层ID/章节不一致")
            from app.world_config import interaction_points

            validate_definition(
                definition,
                objects=set(interaction_points()) | {item["id"] for item in bundle["configuration"].get("world", {}).get("interactions", [])},
            )
            for node in definition["nodes"]:
                if node.get("navigation_target") and node["navigation_target"] not in indexed["npc"]:
                    raise PackageError("任务导航NPC不存在")
                if node["type"] in {"TALK_TO_NPC", "PLAY_DIALOGUE", "REPORT_BACK"} and node.get("target") not in indexed["npc"]:
                    raise PackageError("任务NPC不存在")
                if node.get("activity_id"):
                    activity = converted["activity"][node["activity_id"]]
                    expected = {"STUDY_LESSON": {"lesson", "reading"}, "COMPLETE_PRACTICE": {"practice"}, "PASS_ASSESSMENT": {"assessment"}}
                    if node["type"] in expected and activity["kind"] not in expected[node["type"]]:
                        raise PackageError("任务节点活动类型不匹配")
                    if activity["chapter_id"] != row["chapter_id"]:
                        raise PackageError("任务活动必须属于当前章节")

        class Catalog:
            def get(self, model, key):
                kind = MODEL_NAMES[model]
                row = next((value for stable, value in converted[kind].items() if maps[kind][stable] == key), None)
                return SimpleNamespace(**row) if row else None

        catalog = Catalog()

        def conditions(value, bindings, definition):
            if isinstance(value, dict):
                for key, item in value.items():
                    if key == "when":
                        validate_condition(item, bindings, definition, catalog)
                    else:
                        conditions(item, bindings, definition)
            elif isinstance(value, list):
                for item in value:
                    conditions(item, bindings, definition)

        for key, row in converted["story"].items():
            quest = converted["quest"].get(row["quest_id"])
            definition = quest["definition"] if quest else None
            if quest and quest["chapter_id"] != row["chapter_id"]:
                raise PackageError("剧情任务必须属于同一章节")
            validate_story(row["content"], quest_definition=definition, story_id=key)
            conditions(row["content"], row["content"]["bindings"], definition)
        course = bundle["course"]
        _object(course, {"schema_version", "title", "releases"}, {"schema_version", "title", "releases"})
        if course["schema_version"] != 1 or not _short(course["title"], 200) or not isinstance(course["releases"], list):
            raise PackageError("课程目录格式无效")
        releases = {}
        for row in course["releases"]:
            _object(
                row, {"chapter_id", "status", "opens_at", "prerequisite_chapter_id"}, {"chapter_id", "status", "opens_at", "prerequisite_chapter_id"}
            )
            remap_references(row, maps)
            if row["chapter_id"] in releases or row["status"] not in {"published", "paused"}:
                raise PackageError("章节发布配置重复或无效")
            if row["opens_at"] is not None and datetime.fromisoformat(row["opens_at"]).tzinfo is None:
                raise PackageError("开放时间需要时区")
            releases[row["chapter_id"]] = row
        for key in releases:
            visited, current = set(), key
            while current:
                if current in visited:
                    raise PackageError("章节前置依赖存在循环")
                visited.add(current)
                current = releases.get(current, {}).get("prerequisite_chapter_id")
        from app.teaching.authoring.validation import validate_configuration

        validate_configuration(bundle["configuration"], data)
    except (ValueError, TypeError, KeyError, AttributeError, RecursionError) as exc:
        if isinstance(exc, PackageError):
            raise
        raise PackageError(f"课程语义校验失败：{exc}") from exc
