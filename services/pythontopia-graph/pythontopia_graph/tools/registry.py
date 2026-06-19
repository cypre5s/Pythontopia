"""百晓工坊工具注册表：17 个工具的单一事实源。

治理原则（2026 harness 工程共识）：
- 注册表是治理首要控制点：类型化元数据 + 风险分级 + 版本，而非散落在 prompt 里的手写清单；
- 意图路由 prompt 的工具目录由 :func:`intent_catalog_lines` 自动生成（按作用域收缩——
  「减工具常比加工具更提效」，考核模式下只暴露允许的工具）；
- ``GET /agents/intent/tools`` 暴露目录，前端欢迎屏「能力一览」与测试对账同源。

exec_mode 说明（前端按此分发到既有管线，不新建 SSE 通道）：
- ``tutor_sse``        → POST /api/tutor/ask（导师流；ASKME/EXPLAIN_CODE/FIX_BUG 以 directive 复用）
- ``workshop_sse``     → POST /api/workshop/resources/stream（6 类资源生成流）
- ``orchestrator_sse`` → POST /api/orchestrator/turn/stream（学习总管协同流）
- ``frontend``         → 纯前端工具（B站卡片 / 跳转 / 学情卡 / 书房检索），零 LLM 成本
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any, Literal

RiskClass = Literal["read_only", "generative", "navigational"]
CostClass = Literal["zero", "low", "medium", "high"]
ExecMode = Literal["tutor_sse", "workshop_sse", "orchestrator_sse", "frontend"]

# 工具作用域：default = 常规聊天；quiz = 「考考我」考核模式进行中（动态收缩工具集，
# 防止学生在被考核时跑去生成讲义逃避作答；退出考核由前端模式条控制）。
Scope = Literal["default", "quiz"]

CapabilityNamespace = Literal["ask", "quiz", "make", "practice", "plan", "find"]
Renderer = Literal[
    "text",
    "resource",
    "practice",
    "code_lab",
    "plan",
    "progress",
    "navigation",
    "video",
    "study_space",
    "browse",
]
Visibility = Literal["default_hidden", "default_visible", "debug_only"]


@dataclass(frozen=True)
class ToolSpec:
    """单个工具的治理元数据（版本化；意图名即工具名）。"""

    name: str
    zh_label: str
    desc: str
    examples: tuple[str, ...]
    exec_mode: ExecMode
    risk_class: RiskClass
    cost_class: CostClass
    # 执行治理参数：harness 据此包装 LLM/外部子调用；frontend 工具为 0（不经 graph 执行）。
    timeout_s: float
    max_retries: int
    # 失败时的降级工具名（None = 无降级，向上抛结构化错误）。
    fallback: str | None
    scopes: tuple[Scope, ...] = ("default",)
    version: int = 1
    namespace: CapabilityNamespace = "ask"
    capability_label: str = "问一问"
    renderer: Renderer = "text"
    read_only: bool = False
    destructive: bool = False
    open_world: bool = False
    idempotent: bool = True
    visibility: Visibility = "default_hidden"
    invocation_label: str = "处理中"
    completion_label: str = "已完成"


def _spec(  # noqa: PLR0913 - 声明式注册表，参数即文档
    name: str,
    zh_label: str,
    desc: str,
    examples: tuple[str, ...],
    exec_mode: ExecMode,
    risk_class: RiskClass,
    cost_class: CostClass,
    timeout_s: float = 0.0,
    max_retries: int = 0,
    fallback: str | None = None,
    scopes: tuple[Scope, ...] = ("default",),
    namespace: CapabilityNamespace | None = None,
    capability_label: str | None = None,
    renderer: Renderer | None = None,
    read_only: bool | None = None,
    destructive: bool = False,
    open_world: bool = False,
    idempotent: bool | None = None,
    visibility: Visibility = "default_hidden",
    invocation_label: str | None = None,
    completion_label: str | None = None,
) -> ToolSpec:
    resolved_namespace, resolved_label = CAPABILITY_BY_TOOL[name]
    return ToolSpec(
        name=name,
        zh_label=zh_label,
        desc=desc,
        examples=examples,
        exec_mode=exec_mode,
        risk_class=risk_class,
        cost_class=cost_class,
        timeout_s=timeout_s,
        max_retries=max_retries,
        fallback=fallback,
        scopes=scopes,
        namespace=namespace or resolved_namespace,
        capability_label=capability_label or resolved_label,
        renderer=renderer or RENDERER_BY_TOOL.get(name, "text"),
        read_only=(exec_mode == "frontend" and risk_class == "read_only")
        if read_only is None
        else read_only,
        destructive=destructive,
        open_world=open_world,
        idempotent=(exec_mode != "workshop_sse") if idempotent is None else idempotent,
        visibility=visibility,
        invocation_label=invocation_label or f"正在处理：{zh_label}",
        completion_label=completion_label or f"{zh_label}已完成",
    )


CAPABILITY_BY_TOOL: dict[str, tuple[CapabilityNamespace, str]] = {
    "ASK": ("ask", "问一问"),
    "EXPLAIN_CODE": ("ask", "问一问"),
    "FIX_BUG": ("ask", "问一问"),
    "ASKME": ("quiz", "考考我"),
    "REVIEW": ("quiz", "考考我"),
    "HANDOUT": ("make", "生成资料"),
    "MINDMAP": ("make", "生成资料"),
    "READING": ("make", "生成资料"),
    "STORYBOARD": ("make", "生成资料"),
    "PRACTICE": ("practice", "练一练"),
    "CODE_LAB": ("practice", "练一练"),
    "PLAN": ("plan", "规划学习"),
    "PROGRESS": ("plan", "规划学习"),
    "MY_STUDY": ("plan", "规划学习"),
    "VIDEO": ("find", "找内容/带路"),
    "NAVIGATE": ("find", "找内容/带路"),
    "BROWSE": ("find", "找内容/带路"),
}

RENDERER_BY_TOOL: dict[str, Renderer] = {
    "VIDEO": "video",
    "HANDOUT": "resource",
    "MINDMAP": "resource",
    "READING": "resource",
    "STORYBOARD": "resource",
    "PRACTICE": "practice",
    "CODE_LAB": "code_lab",
    "PLAN": "plan",
    "PROGRESS": "progress",
    "NAVIGATE": "navigation",
    "MY_STUDY": "study_space",
    "BROWSE": "browse",
}


# ── 17 工具注册表（9 既有 + 7 新增 + 1 浏览查证）。顺序即意图路由 prompt 中的呈现顺序。──
TOOLS: dict[str, ToolSpec] = {
    spec.name: spec
    for spec in (
        # ── 既有 9 ──
        _spec(
            "ASK",
            "答疑",
            "直接回答提问/解释概念，附课件证据引用",
            ("为什么 list 不能当字典 key？", "解释一下切片"),
            "tutor_sse",
            "generative",
            "medium",
            timeout_s=90.0,
            max_retries=1,
            scopes=("default", "quiz"),
        ),
        _spec(
            "VIDEO",
            "B站视频",
            "想看视频讲解时按主题检索B站精选视频卡片（外部资源，非生成）",
            ("找个讲循环的视频", "b站有没有函数教程"),
            "frontend",
            "read_only",
            "zero",
        ),
        _spec(
            "REVIEW",
            "复习回顾",
            "想复习/复盘快遗忘的旧知识点时，引导去错题矿洞按到期知识点复盘（非生成）",
            ("帮我复习一下", "感觉前面学的快忘了"),
            "frontend",
            "navigational",
            "zero",
            scopes=("default", "quiz"),
        ),
        _spec(
            "HANDOUT",
            "讲义",
            "生成段落讲解讲义（含课件页引用）",
            ("给我整理一份函数的讲义",),
            "workshop_sse",
            "generative",
            "high",
            timeout_s=120.0,
            max_retries=2,
            fallback="ASK",
        ),
        _spec(
            "MINDMAP",
            "思维导图",
            "生成知识结构思维导图",
            ("把第三章画成脑图",),
            "workshop_sse",
            "generative",
            "high",
            timeout_s=120.0,
            max_retries=2,
            fallback="ASK",
        ),
        _spec(
            "PRACTICE",
            "练习题",
            "生成 1-3 道练习题（不含完整答案）",
            ("出几道循环的题",),
            "workshop_sse",
            "generative",
            "high",
            timeout_s=120.0,
            max_retries=2,
            fallback="ASK",
        ),
        _spec(
            "CODE_LAB",
            "代码案例",
            "生成可运行代码案例",
            ("来个能跑的字典示例",),
            "workshop_sse",
            "generative",
            "high",
            timeout_s=120.0,
            max_retries=2,
            fallback="ASK",
        ),
        _spec(
            "READING",
            "拓展阅读",
            "生成延伸阅读卡片",
            ("有什么拓展材料",),
            "workshop_sse",
            "generative",
            "high",
            timeout_s=120.0,
            max_retries=2,
            fallback="ASK",
        ),
        _spec(
            "STORYBOARD",
            "动画脚本",
            "生成动画分镜脚本（不渲染视频）",
            ("把递归做成动画脚本",),
            "workshop_sse",
            "generative",
            "high",
            timeout_s=120.0,
            max_retries=2,
            fallback="ASK",
        ),
        # ── 新增 7 ──
        _spec(
            "ASKME",
            "考考我",
            "学生想被检验时进入苏格拉底考核：按知识点出口头小题→点评→追问，3 轮后小结",
            ("考考我循环", "检验一下我学得怎么样"),
            "tutor_sse",
            "generative",
            "medium",
            timeout_s=90.0,
            max_retries=1,
            fallback="ASK",
        ),
        _spec(
            "EXPLAIN_CODE",
            "讲代码",
            "学生贴代码想看懂时逐行讲解 + 陷阱预警（不重写完整答案）",
            ("这段代码什么意思```python\nx=[i for i in range(3)]\n```",),
            "tutor_sse",
            "generative",
            "medium",
            timeout_s=90.0,
            max_retries=1,
            fallback="ASK",
        ),
        _spec(
            "FIX_BUG",
            "修报错",
            "学生贴报错/异常时定位根因并引导修复（沙箱实跑验证，不给完整修正代码）",
            ("报错了 TypeError 怎么办```python\nprint(1+'2')\n```",),
            "tutor_sse",
            "generative",
            "medium",
            timeout_s=90.0,
            max_retries=1,
            fallback="ASK",
        ),
        _spec(
            "PLAN",
            "学习规划",
            "制定个性化「看→学→练→测」学习计划（学习总管多智能体协同）",
            ("帮我规划这周怎么学函数", "给我做个学习计划"),
            "orchestrator_sse",
            "generative",
            "high",
            timeout_s=150.0,
            max_retries=1,
            fallback="ASK",
        ),
        _spec(
            "PROGRESS",
            "学情报告",
            "查看自己的掌握度报告：强项/弱项/建议下一步（本地学情快照，非生成）",
            ("我学得怎么样", "我哪里薄弱"),
            "frontend",
            "read_only",
            "zero",
        ),
        _spec(
            "NAVIGATE",
            "带路",
            "带学生跳转到目标场所：图书馆/无尽远征/错题矿洞/小镇（非生成）",
            ("带我去闯关塔", "我想去图书馆看课件"),
            "frontend",
            "navigational",
            "zero",
        ),
        _spec(
            "MY_STUDY",
            "找资料",
            "检索学生书房里既往生成的学习资源（非生成）",
            ("我之前那份讲义呢", "找找我生成过的练习"),
            "frontend",
            "read_only",
            "zero",
        ),
        # ── 新增 1（Agentic Loop 融合 · B1）：白名单内联网查证（browser-use 窄工具化）──
        # 外部内容（非课件证据）；只读导航、有界步数、域 allowlist fail-fast、DOM 去敏。
        # 复用学习总管 SSE，不新建通道；考核 scope 不暴露（防逃避作答）；失败回退 VIDEO。
        _spec(
            "BROWSE",
            "联网查证",
            "在白名单域内自主浏览官方文档/判题站并回带可引用摘要（外部内容，非课件证据）",
            ("去 python 官网查 sorted 的稳定性", "看看这道题在官方文档怎么说"),
            "orchestrator_sse",  # 复用学习总管流，不新建 SSE 通道
            "navigational",
            "high",
            timeout_s=60.0,
            max_retries=0,  # 浏览器步骤不重试（副作用 + 慢），失败回退 VIDEO
            fallback="VIDEO",
            scopes=("default",),  # 考核 scope 不暴露
            open_world=True,
            destructive=False,
            idempotent=False,
            renderer="browse",  # F 新增 renderer；前端映射 BrowseTimeline.vue
            visibility="default_hidden",
        ),
    )
}

ALL_TOOL_NAMES: tuple[str, ...] = tuple(TOOLS.keys())

# NAVIGATE 允许的目标地标（响应 target 字段取值；前端映射到路由跳转）。
NAVIGATE_TARGETS: tuple[str, ...] = ("library", "arena", "bugmine", "world", "workshop")


def tools_for_scope(scope: Scope = "default") -> list[ToolSpec]:
    """按作用域收缩工具集（考核模式只许答疑/复习，防逃避作答）。"""
    return [spec for spec in TOOLS.values() if scope in spec.scopes]


def intent_catalog_lines(scope: Scope = "default") -> list[str]:
    """为意图路由 prompt 自动生成工具目录行（注册表即事实源，不再手写散落）。"""
    lines: list[str] = []
    for spec in tools_for_scope(scope):
        example = f"（例：{spec.examples[0]}）" if spec.examples else ""
        lines.append(f"{spec.name}={spec.zh_label}：{spec.desc}{example}")
    return lines


# ── 原生工具调用 schema（Phase B：function-calling 入参契约 + Tool Use Examples）──────
# 设计：tools 的入参不再走 IntentRouteResponse 旁路，而是作为 function 的 typed 参数，
# 让模型在有界循环里直接以结构化 arguments 调用。参数 schema 用集中映射显式声明
# （沿用 RENDERER_BY_TOOL / CAPABILITY_BY_TOOL 风格，不改 17 个 _spec 调用，纯加法）。
# OpenAI 兼容（DeepSeek）function schema 无 input_examples 槽位，故把示例折进 description
# ——即 Anthropic「Tool Use Examples」原则在 OpenAI-compat 上的等价落地。

_PARAM_TOPIC: dict[str, Any] = {
    "type": "string",
    "description": "本次聚焦的学习主题/重点（一句话，如「for 循环遍历列表」）",
}
_PARAM_KC_CODES: dict[str, Any] = {
    "type": "array",
    "items": {"type": "string"},
    "description": "相关知识点 code（取自课程目录，最多 3 个；不确定就留空数组，禁止编造）",
}
_PARAM_CODE: dict[str, Any] = {
    "type": "string",
    "description": "学生提供的代码或报错文本（可含 ``` 围栏）",
}
_PARAM_TARGET: dict[str, Any] = {
    "type": "string",
    "enum": list(NAVIGATE_TARGETS),
    "description": "跳转目标地标：library/arena/bugmine/world/workshop",
}
_PARAM_GOAL: dict[str, Any] = {
    "type": "string",
    "description": "联网查证目标（自然语言；仅在白名单官方站只读浏览，回带可引用摘要）",
}

_COMMON_PARAMS: dict[str, Any] = {"topic": _PARAM_TOPIC, "kc_codes": _PARAM_KC_CODES}

# 每工具入参 properties（缺省 = 通用 topic+kc_codes）。
PARAMS_BY_TOOL: dict[str, dict[str, Any]] = {
    "ASK": _COMMON_PARAMS,
    "ASKME": _COMMON_PARAMS,
    "REVIEW": {"kc_codes": _PARAM_KC_CODES},
    "HANDOUT": _COMMON_PARAMS,
    "MINDMAP": _COMMON_PARAMS,
    "PRACTICE": _COMMON_PARAMS,
    "CODE_LAB": _COMMON_PARAMS,
    "READING": _COMMON_PARAMS,
    "STORYBOARD": _COMMON_PARAMS,
    "EXPLAIN_CODE": {"code": _PARAM_CODE, "kc_codes": _PARAM_KC_CODES},
    "FIX_BUG": {"code": _PARAM_CODE, "kc_codes": _PARAM_KC_CODES},
    "PLAN": _COMMON_PARAMS,
    "PROGRESS": {},
    "MY_STUDY": {"topic": _PARAM_TOPIC},
    "VIDEO": {"topic": _PARAM_TOPIC},
    "NAVIGATE": {"target": _PARAM_TARGET},
    "BROWSE": {"goal": _PARAM_GOAL},
}

# 必填参数（其余可选——dispatch 端能在缺省下工作）。
REQUIRED_BY_TOOL: dict[str, tuple[str, ...]] = {
    "EXPLAIN_CODE": ("code",),
    "FIX_BUG": ("code",),
    "NAVIGATE": ("target",),
    "BROWSE": ("goal",),
}

# Tool Use Examples：示例调用（argument 对象）；聚焦"易歧义/需约定"的工具，每个 1-2 个。
INPUT_EXAMPLES_BY_TOOL: dict[str, tuple[dict[str, Any], ...]] = {
    "ASK": ({"topic": "为什么 list 不能作字典 key", "kc_codes": ["PY43-KC-012"]},),
    "HANDOUT": ({"topic": "函数定义与参数传递", "kc_codes": ["PY43-KC-020"]},),
    "PRACTICE": ({"topic": "while 循环边界条件", "kc_codes": ["PY43-KC-015"]},),
    "EXPLAIN_CODE": ({"code": "x = [i for i in range(3)]", "kc_codes": []},),
    "FIX_BUG": ({"code": "print(1 + '2')  # TypeError", "kc_codes": []},),
    "NAVIGATE": ({"target": "library"},),
    "BROWSE": ({"goal": "去 python 官网查 sorted 的稳定性"},),
}


def tool_parameters(name: str) -> dict[str, Any]:
    """某工具的 JSON-Schema 入参对象（object + properties + 可选 required）。"""
    properties = PARAMS_BY_TOOL.get(name, _COMMON_PARAMS)
    schema: dict[str, Any] = {"type": "object", "properties": properties}
    required = REQUIRED_BY_TOOL.get(name)
    if required:
        schema["required"] = list(required)
    return schema


def to_tool_schema(spec: ToolSpec) -> dict[str, Any]:
    """单个 ToolSpec → OpenAI 兼容 function-calling schema（示例折进 description）。"""
    description = spec.desc
    examples = INPUT_EXAMPLES_BY_TOOL.get(spec.name)
    if examples:
        rendered = "；".join(json.dumps(example, ensure_ascii=False) for example in examples)
        description = f"{description}\n调用示例：{rendered}"
    return {
        "type": "function",
        "function": {
            "name": spec.name,
            "description": description,
            "parameters": tool_parameters(spec.name),
        },
    }


def tool_schemas_for_scope(scope: Scope = "default") -> list[dict[str, Any]]:
    """按作用域产出 function-calling schema 列表（考核 scope 自动收缩，与意图路由同源）。"""
    return [to_tool_schema(spec) for spec in tools_for_scope(scope)]


def tool_catalog_payload() -> list[dict[str, object]]:
    """``GET /agents/intent/tools`` 的响应载荷：前端能力一览 / 测试对账同源。"""
    return [
        {
            "name": spec.name,
            "zhLabel": spec.zh_label,
            "desc": spec.desc,
            "examples": list(spec.examples),
            "execMode": spec.exec_mode,
            "riskClass": spec.risk_class,
            "costClass": spec.cost_class,
            "scopes": list(spec.scopes),
            "version": spec.version,
            "namespace": spec.namespace,
            "capabilityLabel": spec.capability_label,
            "renderer": spec.renderer,
            "readOnly": spec.read_only,
            "destructive": spec.destructive,
            "openWorld": spec.open_world,
            "idempotent": spec.idempotent,
            "visibility": spec.visibility,
            "invocationLabel": spec.invocation_label,
            "completionLabel": spec.completion_label,
        }
        for spec in TOOLS.values()
    ]
