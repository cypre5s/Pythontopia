"""Idempotent real course import; no default accounts or classrooms."""

import json
import os
from pathlib import Path
from .config import ROOT
from .db import SessionLocal
from .models import (
    Activity,
    ActivityVersion,
    Chapter,
    CourseExample,
    KnowledgePoint,
    Material,
    MaterialVersion,
    Npc,
    QuestDefinition,
    Question,
    QuestionSecret,
)
from .quests import validate_definition

NPCS = [
    ("mayor_py", "镇长 Py", "town_hub", 22, 34, "welcome", "欢迎来到 Pythontopia！小镇图书馆等待你的计算思维重新点亮。"),
    ("professor", "林教授", "town_hub", 25, 14, "language", "语言连接思想与机器。先读课件，再决定探索路线。"),
    ("arena_guard", "练习场教练", "town_hub", 38, 24, "practice", "正式提交会记录成绩，练习可以重试。"),
    ("librarian_doc", "图书管理员", "library", 9, 5, "books", "书架记录语言的历史。阅读参与和掌握知识是两回事。"),
]


def node(id, type, title, target=None, dependencies=None, **kwargs):
    value = {"id": id, "type": type, "title": title, "dependencies": dependencies or [], **kwargs}
    if target:
        value["target"] = target
    return value


QUESTS = [
    {
        "id": "arrival",
        "version": 1,
        "title": "第一束灯：计算思维的来信",
        "chapter_id": 179,
        "kind": "main",
        "nodes": [
            node("greet", "TALK_TO_NPC", "在南侧广场找到镇长 Py", "mayor_py"),
            node("opening", "PLAY_DIALOGUE", "听完图书馆的来信", "mayor_py", ["greet"], dialogue_id="welcome"),
            node("study", "STUDY_LESSON", "阅读计算工具与计算思维", dependencies=["opening"], activity_id="ch1-lesson", activity_version=1),
            node("reach-practice", "REACH_LOCATION", "前往东侧练习场", "practice_plaza", ["study"]),
            node(
                "practice", "COMPLETE_PRACTICE", "完成计算思维概念题", dependencies=["reach-practice"], activity_id="ch1-practice", activity_version=1
            ),
            node("report", "REPORT_BACK", "向镇长汇报，点亮图书馆", "mayor_py", ["practice"]),
        ],
        "effects": [{"type": "set_world_flag", "key": "library_unlocked"}, {"type": "set_world_flag", "key": "town_light_restored"}],
    },
    {
        "id": "lost-language",
        "version": 1,
        "title": "失落的语言：寻找思想与机器的桥梁",
        "chapter_id": 180,
        "kind": "main",
        "nodes": [
            node("mission", "TALK_TO_NPC", "领取寻找失落知识的委托", "mayor_py"),
            node("professor", "TALK_TO_NPC", "拜访北侧的林教授", "professor", ["mission"]),
            node(
                "learn-language",
                "STUDY_LESSON",
                "学习 Python 与程序设计语言",
                dependencies=["professor"],
                activity_id="ch2-lesson",
                activity_version=1,
            ),
            node(
                "choose-route",
                "PLAY_DIALOGUE",
                "选择概念或编程路线",
                "professor",
                ["learn-language"],
                dialogue_id="language",
                choices=[{"id": "concept", "label": "辨认程序设计语言"}, {"id": "code", "label": "补学输入输出并编程"}],
            ),
            node(
                "concept-practice",
                "COMPLETE_PRACTICE",
                "辨认程序设计语言",
                dependencies=["choose-route"],
                condition={"node": "choose-route", "choice": "concept"},
                activity_id="ch2-concept",
                activity_version=1,
            ),
            node(
                "io-study",
                "STUDY_LESSON",
                "补学输入、输出与圆面积",
                dependencies=["choose-route"],
                condition={"node": "choose-route", "choice": "code"},
                activity_id="ch2-io",
                activity_version=1,
            ),
            node("code-practice", "COMPLETE_PRACTICE", "正式提交圆面积计算", dependencies=["io-study"], activity_id="ch2-code", activity_version=1),
            node("meet-librarian", "TALK_TO_NPC", "进入图书馆拜访馆员", "librarian_doc", ["concept-practice", "code-practice"], aggregation="ANY"),
            node("read-shelf", "INTERACT_OBJECT", "调查西侧书架", "library_shelf", ["concept-practice", "code-practice"], aggregation="ANY"),
            node("report-language", "REPORT_BACK", "完成两个目标后向镇长汇报", "mayor_py", ["meet-librarian", "read-shelf"]),
        ],
        "effects": [{"type": "set_world_flag", "key": "language_archive_restored"}],
    },
]


def seed(db, source=None):
    root = Path(source) if source else Path(os.getenv("REBORN_COURSE_ROOT", str(ROOT / "data/course-pack-43"))) / "json"

    def load(name):
        return json.loads((root / (name + ".json")).read_text(encoding="utf-8-sig"))

    chapters, docs, pages, kcs, examples, problems = [
        load(n) for n in ("course_chapter", "course_document", "course_page", "course_kc", "course_example", "course_problem")
    ]
    counts = dict(
        zip(
            ("documents", "pages", "chapters", "knowledge_points", "examples", "problems"), map(len, (docs, pages, chapters, kcs, examples, problems))
        )
    )
    if list(counts.values()) != [7, 561, 7, 65, 46, 39]:
        raise ValueError(f"Unexpected export: {counts}")
    for c in chapters:
        if not db.get(Chapter, c["id"]):
            db.add(Chapter(id=c["id"], title=c["title"], order_index=c["order_index"]))
    db.flush()
    for k in kcs:
        if not db.get(KnowledgePoint, k["id"]):
            db.add(KnowledgePoint(id=k["id"], chapter_id=k["chapter_id"], title=k["kc_name"], content=k))
    for e in examples:
        if not db.get(CourseExample, e["id"]):
            db.add(CourseExample(id=e["id"], content=e))
    for doc in docs:
        chapter = next(c for c in chapters if c["order_index"] == doc["sort_order"])
        if not db.get(Material, doc["id"]):
            db.add(Material(id=doc["id"], chapter_id=chapter["id"], title=chapter["title"], kind="text"))
            db.flush()
            db.add(
                MaterialVersion(
                    material_id=doc["id"],
                    version=1,
                    status="ready",
                    content={
                        "notice": "原课件真实提取文本。原 PPT/PDF 和视频文件未随仓库提供，不代表完整幻灯片或视频。",
                        "pages": [
                            {"id": p["id"], "page_no": p["page_no"], "title": p["title"], "text": p["content_markdown"]}
                            for p in pages
                            if p["document_id"] == doc["id"]
                        ],
                    },
                )
            )
    db.flush()
    activities = [
        ("ch1-lesson", 179, "计算工具与计算思维", "lesson", 186, "mayor_py"),
        ("ch1-practice", 179, "计算思维概念练习", "practice", None, "arena_guard"),
        ("ch2-lesson", 180, "Python 与程序设计语言入门", "lesson", 187, "professor"),
        ("ch2-concept", 180, "失落的语言：概念练习", "practice", None, "arena_guard"),
        ("ch2-io", 180, "输入输出与圆面积", "lesson", 187, "professor"),
        ("ch2-code", 180, "圆面积计算", "practice", None, "arena_guard"),
    ]
    for id, chapter, title, kind, material, npc in activities:
        if not db.get(Activity, id):
            db.add(
                Activity(
                    id=id,
                    chapter_id=chapter,
                    title=title,
                    kind=kind,
                    material_id=material,
                    npc_key=npc,
                    assistant_allowed=True,
                    version=1,
                    content={"material_version": 1, "published": True},
                )
            )
    db.flush()
    for id, *_ in activities:
        activity = db.get(Activity, id)
        if not db.get(ActivityVersion, (id, 1)):
            db.add(
                ActivityVersion(
                    activity_id=id,
                    version=1,
                    snapshot={key: getattr(activity, key) for key in ("title", "kind", "material_id", "npc_key", "assistant_allowed", "content")},
                )
            )
    db.flush()
    questions = [
        (
            "ch1-abstraction",
            179,
            "ch1-practice",
            "计算思维的核心",
            "single_choice",
            "依据第一章第42页，计算思维的本质包括？",
            ["抽象与自动化", "打字与排版", "装配与焊接"],
            "抽象与自动化",
            [10723],
        ),
        ("ch1-computation", 179, "ch1-practice", "计算的规则", "boolean", "依据第一章第34页，计算是基于规则的符号变换。", [], True, [10715]),
        (
            "ch1-model",
            179,
            "ch1-practice",
            "问题到计算",
            "fill",
            "依据第一章第43页，把现实问题转化成可求解形式的步骤称为____。",
            [],
            ["建模", "建立模型", "模型建立"],
            [10724],
        ),
        (
            "ch2-language",
            180,
            "ch2-concept",
            "语言分类",
            "single_choice",
            "依据第二章第5页，哪组是三类程序设计语言？",
            ["机器语言、汇编语言、高级语言", "汉语、英语、法语", "文本、声音、图像"],
            "机器语言、汇编语言、高级语言",
            [10731],
        ),
        ("ch2-binary", 180, "ch2-concept", "机器语言", "boolean", "依据第二章第6页，机器语言使用二进制形式表示指令。", [], True, [10732]),
        (
            "ch2-assembly",
            180,
            "ch2-concept",
            "汇编语言",
            "fill",
            "依据第二章第8页，汇编语言使用便于记忆的____表示指令。",
            [],
            ["助记符", "符号"],
            [10734],
        ),
    ]
    for id, chapter, activity, title, kind, prompt, options, answer, refs in questions:
        if not db.get(Question, id):
            db.add(
                Question(
                    id=id,
                    chapter_id=chapter,
                    activity_id=activity,
                    title=title,
                    kind=kind,
                    prompt=prompt,
                    options=options,
                    samples=[],
                    source={"kind": "manually_authored_from_course", "page_ids": refs},
                )
            )
            db.flush()
            db.add(QuestionSecret(question_id=id, answer=answer, hidden_cases=[]))
    kc_map = {k["kc_code"]: k["chapter_id"] for k in kcs}
    for p in problems:
        reviewed = p["id"] == 476
        activity_id = "ch2-code" if reviewed else "archive-" + str(p["id"])
        chapter = kc_map.get(p["kc_code"], 180)
        if not reviewed and not db.get(Activity, activity_id):
            db.add(
                Activity(id=activity_id, chapter_id=chapter, title=p["title"], kind="practice", assistant_allowed=False, content={"published": False})
            )
            db.flush()
        id = "course-" + str(p["id"])
        if not db.get(Question, id):
            db.add(
                Question(
                    id=id,
                    chapter_id=chapter,
                    activity_id=activity_id,
                    title=p["title"],
                    kind="python",
                    prompt=p["statement_markdown"],
                    options=[],
                    samples=p["samples"],
                    source={"kind": "course_export", "original_id": p["id"], "page_ids": [10771] if reviewed else [], "reviewed": reviewed},
                )
            )
            db.flush()
            db.add(
                QuestionSecret(
                    question_id=id,
                    answer=None,
                    hidden_cases=[{"input": c["input"], "expected_output": c["expected_output"]} for c in p["test_cases"] if not c.get("is_sample")],
                )
            )
    for activity_id, *_ in activities:
        activity = db.get(Activity, activity_id)
        if "question_ids" not in activity.content:
            question_ids = sorted(q.id for q in db.query(Question).filter(Question.activity_id == activity_id))
            activity.content = {**activity.content, "question_ids": question_ids}
            snapshot = db.get(ActivityVersion, (activity_id, 1))
            snapshot.snapshot = {**snapshot.snapshot, "content": activity.content}
    from .dialogues import validate_dialogue

    for key, name, map_key, x, y, dialogue_id, text in NPCS:
        script = [{"id": dialogue_id, "text": text}]
        if key == "mayor_py":
            script = [
                {"id": "welcome", "text": text, "next": "welcome-story", "speaker": name},
                {
                    "id": "welcome-story",
                    "speaker": name,
                    "text": "先读计算思维课件，再到东侧练习场。完成练习后回来汇报，我们一起点亮图书馆。",
                    "once": "opening-letter",
                },
            ]
        elif key == "professor":
            script = [
                {
                    "id": "language",
                    "speaker": name,
                    "text": text,
                    "choices": [
                        {"id": "concept", "label": "辨认程序设计语言", "next": "concept-path"},
                        {"id": "code", "label": "补学输入输出并编程", "next": "code-path"},
                    ],
                },
                {"id": "concept-path", "speaker": name, "text": "到练习场辨认语言，再去图书馆寻找馆员和西侧书架。"},
                {"id": "code-path", "speaker": name, "text": "先阅读输入输出与圆面积相关页，再正式提交圆面积题目。"},
            ]
        validate_dialogue(script)
        npc = db.get(Npc, key)
        if not npc:
            db.add(
                Npc(key=key, name=name, map_key=map_key, tile_x=x, tile_y=y, portrait=f"/assets/pixel/portraits/galgame/{key}.png", dialogue=script)
            )
        elif npc.dialogue == [{"id": dialogue_id, "text": text}]:
            npc.dialogue = script
    db.flush()
    for q in QUESTS:
        validate_definition(q, db)
        if not db.get(QuestDefinition, (q["id"], q["version"])):
            db.add(QuestDefinition(id=q["id"], version=q["version"], title=q["title"], chapter_id=q["chapter_id"], definition=q))
    db.commit()
    return counts


if __name__ == "__main__":
    with SessionLocal() as db:
        print(seed(db))
