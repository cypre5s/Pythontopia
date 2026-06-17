"""消费侧工具收敛（MVP Phase D）：把 17 个意图收敛为 9 个高杠杆、参数化工具。

仅用于**有界工具调用循环**的工具表（loop-only）：用 `mode`/`kind` 枚举合并近义工具，
executor 再把枚举展开回既有 17-意图 dispatch。**不改既有意图路由 / 前端 / Android**
（纯加法、非破坏）。

收敛依据（2026 前沿："工具少而清晰 → 选择更准、token 更省"；臃肿/重叠工具是头号失败模式）：

| 收敛工具 | 选择器 | 覆盖的旧意图 |
|---|---|---|
| ask          | mode=qa/explain_code/fix_bug   | ASK / EXPLAIN_CODE / FIX_BUG |
| quiz         | mode=test_me/review            | ASKME / REVIEW |
| make_resource| kind=handout/mindmap/reading/storyboard | HANDOUT / MINDMAP / READING / STORYBOARD |
| make_practice| kind=practice/code_lab         | PRACTICE / CODE_LAB |
| plan_study   | —                              | PLAN |
| my_space     | kind=progress/resources        | PROGRESS / MY_STUDY |
| video        | —                              | VIDEO |
| navigate     | —                              | NAVIGATE |
| browse       | —                              | BROWSE |
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any

from pythontopia_graph.tools.registry import NAVIGATE_TARGETS, Scope

# ── 复用 registry 的参数描述风格（保持文案一致）──────────────────────────────
_P_TOPIC: dict[str, Any] = {
    "type": "string",
    "description": "本次聚焦的学习主题/重点（一句话，如「for 循环遍历列表」）",
}
_P_KC_CODES: dict[str, Any] = {
    "type": "array",
    "items": {"type": "string"},
    "description": "相关知识点 code（取自课程目录，最多 3 个；不确定就留空，禁止编造）",
}
_P_CODE: dict[str, Any] = {
    "type": "string",
    "description": "学生提供的代码或报错文本（mode=explain_code/fix_bug 时填）",
}
_P_TARGET: dict[str, Any] = {
    "type": "string",
    "enum": list(NAVIGATE_TARGETS),
    "description": "跳转目标地标：library/arena/bugmine/world/workshop",
}
_P_GOAL: dict[str, Any] = {
    "type": "string",
    "description": "联网查证目标（自然语言；仅在白名单官方站只读浏览）",
}


@dataclass(frozen=True)
class ConsolidatedTool:
    """一个收敛工具：name + JSON-Schema 参数 + 示例 + scope + 到旧意图的展开规则。"""

    name: str
    description: str
    parameters: dict[str, Any]
    examples: tuple[dict[str, Any], ...] = ()
    scopes: tuple[Scope, ...] = ("default",)
    # 枚举选择器字段名（None = 无选择器，单一旧意图）。
    selector: str | None = None
    # selector 值 → 旧意图。
    legacy_by_value: dict[str, str] = field(default_factory=dict)
    # 无 selector 时的唯一旧意图。
    legacy_intent: str = ""


CONSOLIDATED_TOOLS: tuple[ConsolidatedTool, ...] = (
    ConsolidatedTool(
        name="ask",
        description="答疑/讲代码/修报错：解释概念、逐行讲代码、定位报错根因（附课件证据，不给完整答案）",
        parameters={
            "type": "object",
            "properties": {
                "mode": {
                    "type": "string",
                    "enum": ["qa", "explain_code", "fix_bug"],
                    "description": "qa=概念答疑；explain_code=逐行讲代码；fix_bug=定位报错根因",
                },
                "topic": _P_TOPIC,
                "code": _P_CODE,
                "kc_codes": _P_KC_CODES,
            },
            "required": ["mode"],
        },
        examples=(
            {"mode": "qa", "topic": "为什么 list 不能作字典 key", "kc_codes": ["PY43-KC-012"]},
            {"mode": "fix_bug", "code": "print(1 + '2')  # TypeError"},
        ),
        scopes=("default", "quiz"),
        selector="mode",
        legacy_by_value={"qa": "ASK", "explain_code": "EXPLAIN_CODE", "fix_bug": "FIX_BUG"},
    ),
    ConsolidatedTool(
        name="quiz",
        description=(
            "当学生想被检验、或想温习巩固已学内容时调用：mode=test_me 苏格拉底式口头考核"
            "（检验掌握度）；mode=review 带学生重温/查漏/巩固已学但掌握不牢的内容。"
            "边界：检验或巩固已学内容用本工具，不要用 my_space（那是看报告/找资料）。"
        ),
        parameters={
            "type": "object",
            "properties": {
                "mode": {
                    "type": "string",
                    "enum": ["test_me", "review"],
                    "description": "test_me=进入考核被检验；review=去复盘巩固快遗忘的旧知识点",
                },
                "topic": _P_TOPIC,
                "kc_codes": _P_KC_CODES,
            },
            "required": ["mode"],
        },
        examples=(
            {"mode": "test_me", "topic": "循环", "kc_codes": ["PY43-KC-015"]},
            {"mode": "review", "kc_codes": ["PY43-KC-008"]},
        ),
        scopes=("default", "quiz"),
        selector="mode",
        legacy_by_value={"test_me": "ASKME", "review": "REVIEW"},
    ),
    ConsolidatedTool(
        name="make_resource",
        description=(
            "想要任何学习资料就调用本工具，按 kind 区分四类："
            "讲义(handout)、思维导图(mindmap)、拓展阅读(reading)、动画分镜脚本(storyboard)。"
            "例如「整理一份讲义」「把第三章画成思维导图」「有什么拓展阅读材料」「做成动画脚本」"
            "都用本工具（含课件页引用）。"
        ),
        parameters={
            "type": "object",
            "properties": {
                "kind": {
                    "type": "string",
                    "enum": ["handout", "mindmap", "reading", "storyboard"],
                    "description": (
                        "资料类型：handout=段落讲义；mindmap=知识结构思维导图/脑图；"
                        "reading=拓展/延伸阅读卡片；storyboard=动画分镜脚本"
                    ),
                },
                "topic": _P_TOPIC,
                "kc_codes": _P_KC_CODES,
            },
            "required": ["kind"],
        },
        examples=(
            {"kind": "handout", "topic": "函数定义与参数", "kc_codes": ["PY43-KC-020"]},
            {"kind": "mindmap", "topic": "第三章知识结构"},
            {"kind": "reading", "topic": "列表的拓展阅读"},
        ),
        selector="kind",
        legacy_by_value={
            "handout": "HANDOUT",
            "mindmap": "MINDMAP",
            "reading": "READING",
            "storyboard": "STORYBOARD",
        },
    ),
    ConsolidatedTool(
        name="make_practice",
        description="生成练习：练习题（不含完整答案）或可运行代码案例",
        parameters={
            "type": "object",
            "properties": {
                "kind": {
                    "type": "string",
                    "enum": ["practice", "code_lab"],
                    "description": "practice=练习题；code_lab=可运行代码案例",
                },
                "topic": _P_TOPIC,
                "kc_codes": _P_KC_CODES,
            },
            "required": ["kind"],
        },
        examples=({"kind": "practice", "topic": "while 循环边界", "kc_codes": ["PY43-KC-015"]},),
        selector="kind",
        legacy_by_value={"practice": "PRACTICE", "code_lab": "CODE_LAB"},
    ),
    ConsolidatedTool(
        name="plan_study",
        description=(
            "为学生制定或调整一段时间的个性化学习路径（看→学→练→测，多智能体协同）。"
            "当学生想要一份学习安排/路线、想知道接下来按什么顺序学，而非查看已有进度时调用。"
            "边界：制定/编排学习路径用本工具；查看掌握度报告用 my_space。"
        ),
        parameters={
            "type": "object",
            "properties": {"topic": _P_TOPIC, "kc_codes": _P_KC_CODES},
        },
        examples=(
            {"topic": "两周内打好循环基础", "kc_codes": ["PY43-KC-015"]},
            {"topic": "期末前的复习路线", "kc_codes": ["PY43-KC-020"]},
        ),
        legacy_intent="PLAN",
    ),
    ConsolidatedTool(
        name="my_space",
        description=(
            "只用于查看自己的掌握度/学情报告(progress) 或 找回既往生成过的资料(resources)。"
            "不要用于制定计划(用 plan_study)或复习巩固(用 quiz)。"
        ),
        parameters={
            "type": "object",
            "properties": {
                "kind": {
                    "type": "string",
                    "enum": ["progress", "resources"],
                    "description": "progress=掌握度报告；resources=检索既往生成的资料",
                },
                "topic": _P_TOPIC,
            },
            "required": ["kind"],
        },
        selector="kind",
        legacy_by_value={"progress": "PROGRESS", "resources": "MY_STUDY"},
    ),
    ConsolidatedTool(
        name="video",
        description="按主题检索 B 站精选视频讲解卡片（外部资源，非生成）",
        parameters={"type": "object", "properties": {"topic": _P_TOPIC}},
        legacy_intent="VIDEO",
    ),
    ConsolidatedTool(
        name="navigate",
        description="带学生跳转到目标场所：图书馆/闯关塔/错题矿洞/小镇/工坊",
        parameters={"type": "object", "properties": {"target": _P_TARGET}, "required": ["target"]},
        examples=({"target": "library"},),
        legacy_intent="NAVIGATE",
    ),
    ConsolidatedTool(
        name="browse",
        description="在白名单域内自主浏览官方文档并回带可引用摘要（外部内容，非课件证据）",
        parameters={"type": "object", "properties": {"goal": _P_GOAL}, "required": ["goal"]},
        examples=({"goal": "去 python 官网查 sorted 的稳定性"},),
        legacy_intent="BROWSE",
    ),
)

CONSOLIDATED_BY_NAME: dict[str, ConsolidatedTool] = {t.name: t for t in CONSOLIDATED_TOOLS}
CONSOLIDATED_TOOL_NAMES: frozenset[str] = frozenset(CONSOLIDATED_BY_NAME)


def consolidated_tool_schemas(scope: Scope = "default") -> list[dict[str, Any]]:
    """收敛工具的 OpenAI function-calling schema 列表（示例折进 description；scope 收缩）。"""
    schemas: list[dict[str, Any]] = []
    for tool in CONSOLIDATED_TOOLS:
        if scope not in tool.scopes:
            continue
        description = tool.description
        if tool.examples:
            rendered = "；".join(json.dumps(e, ensure_ascii=False) for e in tool.examples)
            description = f"{description}\n调用示例：{rendered}"
        schemas.append(
            {
                "type": "function",
                "function": {
                    "name": tool.name,
                    "description": description,
                    "parameters": tool.parameters,
                },
            }
        )
    return schemas


def consolidated_for_legacy(intent: str) -> tuple[str, dict[str, str]] | None:
    """旧意图 → (收敛工具名, 选择器参数补丁)；未知意图 → None。

    用于把既有意图路由器选出的旧意图喂进收敛工具循环（混合架构）。是 :func:`expand_tool_call`
    的逆：``HANDOUT → ("make_resource", {"kind": "handout"})``、``PLAN → ("plan_study", {})``。
    """
    for tool in CONSOLIDATED_TOOLS:
        if tool.selector:
            for value, legacy in tool.legacy_by_value.items():
                if legacy == intent:
                    return tool.name, {tool.selector: value}
        elif tool.legacy_intent == intent:
            return tool.name, {}
    return None


def expand_tool_call(name: str, arguments: dict[str, Any]) -> tuple[str | None, dict[str, Any]]:
    """把收敛工具调用展开为 (旧意图, 规整后参数)；未知工具 → (None, {})。

    选择器字段（mode/kind）从参数中弹出并据此选旧意图；非法/缺省选择器值回落到该工具
    第一个枚举值对应的旧意图（fail-soft，不报错）。
    """
    tool = CONSOLIDATED_BY_NAME.get(name)
    if tool is None:
        return None, {}
    args = dict(arguments)
    if tool.selector:
        value = str(args.pop(tool.selector, "")).strip().lower()
        intent = tool.legacy_by_value.get(value)
        if intent is None:
            # 缺省/非法选择器：回落到第一个枚举值的旧意图（保序、确定性）。
            intent = next(iter(tool.legacy_by_value.values()))
        return intent, args
    return tool.legacy_intent, args
