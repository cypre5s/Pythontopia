"""IntentRouterAgent（百晓工坊意图路由）的结构化输出 schema。

单条消息选定一种 intent：ASK（答疑）、VIDEO（B站检索，非生成）或 6 类资源生成器之一；
KC 建议锚定前端已加载的 course_kcs（@ 菜单同源）。不生成内容，前端再分发到
/api/tutor/ask 或 /api/workshop/resources/stream；KC 为建议值，持久化生成层会二次校验。
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

# ASK = 答疑问答（导师管线）；VIDEO = B站精选讲解视频（外部资源检索，非生成）；
# REVIEW = 记忆矿脉回声复习（引导到错题矿洞按 FSRS 到期 KC 复盘，非生成）；
# HANDOUT…STORYBOARD 6 个对应资源工坊的 6 类资源。
# R-16 新增 7 工具：ASKME = 考考我（苏格拉底考核，复用导师管线 + 考官 directive）；
# EXPLAIN_CODE = 讲代码（逐行讲解）；FIX_BUG = 修报错（调试引导 + 沙箱验证）；
# PLAN = 学习规划（学习总管 orchestrator 管线）；PROGRESS = 学情报告（纯前端学情卡）；
# NAVIGATE = 带路跳转（纯前端，target 给出地标）；MY_STUDY = 找资料（纯前端书房检索）。
# 完整治理元数据见 pythontopia_graph.tools.registry（单一事实源）。
IntentKind = Literal[
    "ASK",
    "VIDEO",
    "REVIEW",
    "HANDOUT",
    "MINDMAP",
    "PRACTICE",
    "CODE_LAB",
    "READING",
    "STORYBOARD",
    "ASKME",
    "EXPLAIN_CODE",
    "FIX_BUG",
    "PLAN",
    "PROGRESS",
    "NAVIGATE",
    "MY_STUDY",
]


class CourseKc(BaseModel):
    """课程 KC 目录项（code + 展示名），供意图锚定建议。"""

    code: str = Field(min_length=1, max_length=64)
    name: str = Field(default="", max_length=200)


class IntentTurn(BaseModel):
    """近期对话轮（指代消解上下文）：role = user|assistant，content 为摘要文本。"""

    role: Literal["user", "assistant"]
    content: str = Field(max_length=600)


class SessionCorrection(BaseModel):
    """会话内意图纠错记忆：学生点了「不是想要这个」后记录（消息 → 改派意图）。"""

    message: str = Field(max_length=600)
    corrected_intent: str = Field(max_length=32)


class IntentRouteRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    # 学生手动 @ 指定的 KC（最高优先级，永远保留在结果里）。
    manual_kc_codes: list[str] = Field(default_factory=list)
    # 前端已加载的课程 KC 目录（code + name），用于把语句里的主题锚定到真实 KC。
    course_kcs: list[CourseKc] = Field(default_factory=list)
    current_chapter: int | None = Field(default=None, ge=1, le=7)
    recent_kc_codes: list[str] = Field(default_factory=list)
    # ── R-16 多轮意图理解 ──
    # 最近对话轮（≤6，时间正序），供「再来几道/换个方式讲」类追问做指代消解。
    recent_turns: list[IntentTurn] = Field(default_factory=list, max_length=6)
    # 上一轮已执行的意图与 KC（最强的指代锚点；为空表示首轮）。
    last_intent: str = Field(default="", max_length=32)
    last_kc_codes: list[str] = Field(default_factory=list, max_length=3)
    # 学生画像摘要（Java 注入；情绪/基础影响 REVIEW vs ASK 等判定），可空。
    profile_brief: str = Field(default="", max_length=1000)
    # 会话内纠错记忆（≤3 条，最近优先），路由时作为强提示。
    session_corrections: list[SessionCorrection] = Field(default_factory=list, max_length=3)
    # 工具作用域：default = 常规；quiz = 考核模式（动态收缩可选工具集）。
    scope: Literal["default", "quiz"] = "default"


class IntentRouteResponse(BaseModel):
    intent: IntentKind
    kc_codes: list[str] = Field(default_factory=list)
    focus_hint: str = ""
    confidence: float = Field(default=0.5, ge=0.0, le=1.0)
    reason: str = ""
    # ── R-16 ──
    # NAVIGATE 专用跳转地标：library|arena|bugmine|world|workshop（其余意图为空串）。
    target: str = ""
    # 组合意图：一句话含两个诉求时的次级意图（如「讲讲循环再出两道题」→ ASK + PRACTICE），
    # 第一个动作完成后前端出「接着…」一键 chip；无则 None。
    secondary_intent: IntentKind | None = None
