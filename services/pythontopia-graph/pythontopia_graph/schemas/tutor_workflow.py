"""AI 导学工作流（6 阶段 + 卡片）schema。

移植自 Alethicode tutor-graph 的 contracts/tutor_workflow/cards/*.schema.json，
按 Pythontopia 仓风格收敛为 pydantic 模型（强校验 fail fast，等价 jsonschema 强校验，
违规 → SCHEMA_VIOLATION，不打补丁）。

与源版的对齐 / 适配差异：
- 已补齐被裁动作：knowledge_review（知识点复盘）、execution_trace（执行轨迹讲解）、
  visualize（画一下）、parsons（拼装挑战），与源版 PHASE_ACTIONS / AUXILIARY_EVENTS 对齐。
- visualize：源版回调 Java 出图能力，本仓「graph 零回调 Java」架构改为 LLM 直接产出
  **文本可渲染**教学图（默认 mermaid 源码 + alt_text），不依赖 Java 出图主链路。
- parsons：源版由 Java FSRS 间隔重复子系统出题，本仓改为 LLM 产出一道**独立子技能微练习**
  （明确区别于当前题完整解法，防泄答），不依赖 Java FSRS。
- execution_trace：本仓新增（源版以 visualize 的 for_loop_trace intent 承载），结构化逐步轨迹。
- TransferProblemCard 去掉 reference_solution / test_cases：迁移题不物化成可判题题目
  （防泄答 + 不依赖出题主链路），卡内展示 + 学生自行到训练场练习。
"""

from __future__ import annotations

import re
from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator, model_validator

from pythontopia_graph.schemas.tutor import normalize_image_data

Phase = Literal[
    "READING",
    "IDEATING",
    "CODING",
    "ERROR_FEEDBACK",
    "AC_REVIEW",
    "TRANSFER",
]

WorkflowEvent = Literal[
    "READING",
    "IDEATING",
    "SKELETON",
    "CODING",
    "ERROR_FEEDBACK",
    "AC_REVIEW",
    "TRANSFER",
    "CHAT",
    # 辅助事件（不改变 Phase，与源版 AUXILIARY_EVENTS 对齐）。
    "KNOWLEDGE_REVIEW",
    "EXECUTION_TRACE",
    "VISUALIZE",
    "PARSONS",
]


class WorkflowProblemContext(BaseModel):
    """题目上下文（Java 随请求附带，等价 Alethicode workflow_context）。"""

    problem_id: int = Field(ge=1)
    title: str = ""
    statement: str = ""
    input_description: str = ""
    output_description: str = ""
    samples: list[dict[str, Any]] = Field(default_factory=list)
    kc_codes: list[str] = Field(default_factory=list)
    difficulty: str = ""


class WorkflowDiagnosisContext(BaseModel):
    """判题证据（ERROR_FEEDBACK / AC_REVIEW 必带；Java 从 submission 投影）。"""

    submission_id: int = Field(ge=1)
    verdict: str = ""
    code: str = ""
    failure_reason: str = ""
    passed_cases: int = 0
    total_cases: int = 0
    # 学生近期连错次数（Java 统计），驱动挫败度分级。
    consecutive_errors: int = 0


class TutorStudentState(BaseModel):
    """导学助手的持久学生状态快照。

    这是 manager agent 的输入，不直接代替既有阶段状态机；Java 持久化后每次 run 还原给 graph。
    """

    session_id: str = Field(min_length=1, max_length=80)
    problem_id: int = Field(ge=1)
    user_id: int = Field(ge=1)
    phase: Phase
    attempt_count: int = Field(default=0, ge=0)
    latest_verdict: str = ""
    consecutive_errors: int = Field(default=0, ge=0)
    hint_level: int = Field(default=0, ge=0, le=3)
    frustration_level: Literal["low", "moderate", "severe"] = "low"
    misconception_hypothesis: str = ""
    last_action: str = ""
    last_action_effective: bool | None = None
    last_card_key: str = ""
    next_best_action: str = ""
    mastery_snapshot: dict[str, Any] = Field(default_factory=dict)
    clicked_actions: list[str] = Field(default_factory=list)
    completed_checkpoints: list[str] = Field(default_factory=list)


# ── Episode 规划链（有界自主多步辅导计划；与 GuideCoachDecision 同处一个 schema 文件） ──

TutorEpisodeStepStatus = Literal["PENDING", "ACTIVE", "DONE", "SKIPPED", "REVISED"]

TutorEpisodeExpectedEvidence = Literal[
    "CARD_VIEWED",
    "CHECKPOINT_COMPLETED",
    "ACTION_CLICKED",
    "SUBMISSION_OBSERVED",
    "AC_SUBMISSION",
    "TRANSFER_ATTEMPT",
]

TutorEpisodeStopPolicy = Literal["WAIT_FOR_CLICK", "WAIT_FOR_SUBMISSION", "DONE"]

# 学生可见文本里禁止出现的原始推理痕迹标记（防把 chain-of-thought / system prompt 泄给前端）。
_RAW_REASONING_MARKERS: tuple[str, ...] = (
    "chain-of-thought",
    "chain of thought",
    "hidden reasoning",
    "system prompt",
    "思维链",
)


def _assert_no_raw_reasoning(*texts: str) -> None:
    for text in texts:
        lowered = str(text).lower()
        for marker in _RAW_REASONING_MARKERS:
            if marker in lowered:
                raise ValueError(f"raw reasoning marker not allowed: {marker}")


class TutorEpisodeStep(BaseModel):
    """episode 计划的一步（一张待生成卡 + 推进它所需的证据与停止条件）。"""

    step_id: str = Field(min_length=1, max_length=80)
    event: WorkflowEvent
    tool_name: str = ""
    title: str = Field(min_length=1)
    goal: str = ""
    student_visible_reason: str = ""
    stop_condition: str = ""
    status: TutorEpisodeStepStatus = "PENDING"
    depends_on: list[str] = Field(default_factory=list)
    expected_evidence: TutorEpisodeExpectedEvidence = "CARD_VIEWED"

    @model_validator(mode="after")
    def _no_raw_reasoning(self) -> TutorEpisodeStep:
        _assert_no_raw_reasoning(
            self.title, self.goal, self.student_visible_reason, self.stop_condition
        )
        return self


class TutorEpisodePlan(BaseModel):
    """单题/单会话的有界短程辅导计划（2-4 步，至多一步 ACTIVE，每步明确停止条件）。"""

    plan_id: str = Field(min_length=1, max_length=80)
    session_id: str = Field(min_length=1, max_length=80)
    problem_id: int = Field(ge=1)
    user_id: int = Field(ge=1)
    objective: str = Field(min_length=1)
    trigger_event: WorkflowEvent
    trigger_verdict: str = ""
    revision: int = Field(default=0, ge=0)
    current_step_id: str = Field(min_length=1)
    steps: list[TutorEpisodeStep] = Field(min_length=2, max_length=4)
    stop_policy: TutorEpisodeStopPolicy = "WAIT_FOR_CLICK"
    revision_reason: str = ""
    created_by: Literal["coach", "fallback"] = "coach"
    evidence_summary: str = ""

    @model_validator(mode="after")
    def _validate_plan(self) -> TutorEpisodePlan:
        step_ids = [step.step_id for step in self.steps]
        if len(set(step_ids)) != len(step_ids):
            raise ValueError("episode step ids must be unique")
        if self.current_step_id not in step_ids:
            raise ValueError("current_step_id must reference an existing step")
        active = [step for step in self.steps if step.status == "ACTIVE"]
        if len(active) > 1:
            raise ValueError("at most one episode step may be ACTIVE")
        _assert_no_raw_reasoning(self.objective, self.revision_reason, self.evidence_summary)
        return self


class TutorEpisodePlanPatch(BaseModel):
    """episode 修订增量（revise 后回传，便于审计与最小化传输；不携带完整 steps）。"""

    plan_id: str = Field(min_length=1, max_length=80)
    revision: int = Field(ge=0)
    current_step_id: str | None = None
    revised_step_ids: list[str] = Field(default_factory=list)
    revision_reason: str = ""
    stop_policy: TutorEpisodeStopPolicy | None = None


class WorkflowRunRequest(BaseModel):
    """一次工作流事件 run（同步 SSE 单请求，替代 Alethicode 异步 run + WebSocket）。"""

    session_id: str = Field(min_length=1, max_length=80)
    user_id: int = Field(ge=1)
    event: WorkflowEvent
    problem: WorkflowProblemContext
    diagnosis: WorkflowDiagnosisContext | None = None
    # manager-style education agent；默认开启，graph 尚可通过 coach_mode=False 保持旧工作流。
    coach_mode: bool = True
    student_state: TutorStudentState | None = None
    force_event: WorkflowEvent | None = None
    # CHAT 消息 / IDEATING 学生思路等事件数据。
    event_data: dict[str, Any] = Field(default_factory=dict)
    profile_brief: str = Field(default="", max_length=1000)
    # Java 投影还原的既有 episode 计划（首次触发为 None，由 graph 新建）。
    episode_plan: TutorEpisodePlan | None = None
    episode_patch: TutorEpisodePlanPatch | None = None

    @model_validator(mode="after")
    def validate_chat_images(self) -> "WorkflowRunRequest":
        images = self.event_data.get("images")
        if images is not None and self.event != "CHAT":
            raise ValueError("images are only supported for CHAT")
        if self.event == "CHAT":
            message = self.event_data.get("message", "")
            if not isinstance(message, str):
                raise ValueError("CHAT message must be text")
            if len(message) > 2000:
                raise ValueError("CHAT message must be at most 2000 characters")
            if re.search(r"(?i)data:image/(?:png|jpeg|gif|webp);base64,", message):
                raise ValueError("CHAT message must not contain image data URI")
            if images is None:
                images = []
            if not isinstance(images, list) or not all(isinstance(image, str) for image in images):
                raise ValueError("event_data.images must be a list of image data")
            normalized = normalize_image_data(images)
            if not message.strip() and normalized:
                message = "请结合图片帮我分析。"
            if not message.strip():
                raise ValueError("CHAT requires a message or image")
            self.event_data = {**self.event_data, "message": message, "images": normalized}
        return self


class WorkflowAction(BaseModel):
    """阶段可用动作（驱动前端「下一步」按钮链）。"""

    key: str
    label: str
    event: WorkflowEvent
    recommended: bool = False
    reason: str = ""


class GuideCoachDecision(BaseModel):
    """GuideCoachAgent 的结构化决策。

    reason_summary 是可审计摘要，不是隐藏推理链；前端可以展示，但不能当作 chain-of-thought。
    """

    next_event: WorkflowEvent
    tool_name: str
    hint_level: int = Field(default=0, ge=0, le=3)
    diagnosis_label: str = ""
    student_state_label: str = ""
    action_goal: str
    stop_condition: str
    allowed_followups: list[WorkflowAction] = Field(default_factory=list)
    confidence: float = Field(default=0.0, ge=0, le=1)
    reason_summary: str


# ── 卡片模型（字段对齐 Alethicode contracts/tutor_workflow/cards） ──


class ProblemGuideCard(BaseModel):
    """审题导读卡（READING）。"""

    problem_restatement: str = Field(min_length=1)
    input_output_focus: str = Field(min_length=1)
    key_observation: str = Field(min_length=1)
    starter_questions: list[str] = Field(min_length=1)
    related_kcs: list[str] = Field(default_factory=list)
    teaching_goal: str = ""
    checkpoint_prompt: str = ""
    mentor_role: str = "百晓"
    reflection_prompt: str = ""


class IdeateAnalysisCard(BaseModel):
    """思路分析卡（IDEATING）。"""

    analysis: str = Field(min_length=1)
    steps: list[str] = Field(default_factory=list)
    guiding_questions: list[str] = Field(default_factory=list)
    misconception_alert: str = ""
    teaching_goal: str = ""
    checkpoint_prompt: str = ""
    mentor_role: str = "百晓"
    reflection_prompt: str = ""


class SkeletonCodeCard(BaseModel):
    """骨架代码卡（SKELETON）：挖空骨架，核心逻辑必须留 TODO。"""

    description: str = Field(min_length=1)
    skeleton: str = Field(min_length=1)
    teaching_goal: str = ""
    checkpoint_prompt: str = ""
    mentor_role: str = "百晓"
    reflection_prompt: str = ""


class FailedTestCase(BaseModel):
    """失败样例（判题返回的首个失败用例投影；无详情时不出现）。"""

    input: str = ""
    expected_output: str = ""
    actual_output: str = ""


class ErrorDiagnosisCard(BaseModel):
    """错因诊断卡（ERROR_FEEDBACK）。"""

    root_cause: str = Field(min_length=1)
    what_program_is_doing: str = ""
    expected_behavior: str = ""
    fix_direction: str = Field(min_length=1)
    related_kcs: list[str] = Field(default_factory=list)
    error_pattern: str = ""
    is_recurring: bool = False
    encouragement: str = ""
    # 服务端按 consecutive_errors 推导注入（low / moderate / severe），非 LLM 产出。
    frustration_level: str = "low"
    first_failed_test_case: FailedTestCase | None = None
    teaching_goal: str = ""
    checkpoint_prompt: str = ""
    mentor_role: str = "百晓"
    reflection_prompt: str = ""


class PostAcCard(BaseModel):
    """AC 复盘卡（AC_REVIEW）。"""

    success_summary: str = Field(min_length=1)
    key_action: str = ""
    code_quality_notes: list[str] = Field(default_factory=list)
    knowledge_points: list[str] = Field(default_factory=list)
    next_practice_direction: str = Field(min_length=1)
    teaching_goal: str = ""
    checkpoint_prompt: str = ""
    mentor_role: str = "百晓"
    reflection_prompt: str = ""


class TransferSample(BaseModel):
    input: str = ""
    output: str = ""


class TransferProblemCard(BaseModel):
    """迁移练习卡（TRANSFER）：卡内展示的变式题（不物化判题）。"""

    title: str = Field(min_length=1)
    description: str = Field(min_length=1)
    input_description: str = ""
    output_description: str = ""
    samples: list[TransferSample] = Field(min_length=1)
    hint: str = Field(min_length=1)
    target_kcs: list[str] = Field(default_factory=list)


class AiReplyCard(BaseModel):
    """阶段内导学对话回复（CHAT，不改变 Phase）。"""

    content: str = Field(min_length=1)
    referenced_card_ids: list[str] = Field(default_factory=list)


# ── 辅助卡片（不改变 Phase，补齐源版被裁动作） ──


class CoursewareRef(BaseModel):
    """课件引用（由服务端据 RAG 证据确定性回填，防伪造页码）。"""

    page_no: int = 0
    page_id: int | None = None
    chapter_id: int | None = None
    snippet: str = ""


class KnowledgeReviewCard(BaseModel):
    """知识点复盘卡（KNOWLEDGE_REVIEW，不改变 Phase）。

    review_content 由 LLM 产出（须引用所给课件页、以问句收束）；related_kcs / courseware_refs
    由服务端据题目 KC 与 RAG 证据确定性回填（防幻觉，不让 LLM 自由编造 KC 与页码）。
    """

    review_content: str = Field(min_length=1)
    related_kcs: list[str] = Field(default_factory=list)
    courseware_refs: list[CoursewareRef] = Field(default_factory=list)
    practice_suggestions: list[str] = Field(default_factory=list)
    mastery_snapshot: dict[str, Any] = Field(default_factory=dict)
    # related_kcs 取自何处：current_problem_kcs（当前题挂的 KC）。
    kc_selection_basis: str = ""
    teaching_goal: str = ""
    checkpoint_prompt: str = ""
    mentor_role: str = "百晓"
    reflection_prompt: str = ""


class ExecutionTraceStep(BaseModel):
    """执行轨迹一步（轨迹表一行）。variables 为变量名→值快照（值类型不限，前端按字符串渲染）。"""

    step: int = Field(ge=1)
    line_ref: str = ""
    action: str = Field(min_length=1)
    variables: dict[str, Any] = Field(default_factory=dict)
    note: str = ""


class ExecutionTraceCard(BaseModel):
    """执行轨迹讲解卡（EXECUTION_TRACE，不改变 Phase）。

    对给定代码（或无代码时对题目样例处理过程）做逐步轨迹讲解；若代码有 bug，如实呈现其
    「当前」行为，不替学生改对、不给完整修正代码（防泄答 + 调试导向）。
    """

    summary: str = Field(min_length=1)
    trace_target: str = ""
    input_used: str = ""
    steps: list[ExecutionTraceStep] = Field(min_length=1)
    key_insight: str = ""
    teaching_goal: str = ""
    checkpoint_prompt: str = ""
    mentor_role: str = "百晓"
    reflection_prompt: str = ""


class VisualizeCard(BaseModel):
    """可视化教学图卡（VISUALIZE，不改变 Phase）。

    本仓「graph 零回调 Java」：不调用 Java 出图，改为 LLM 产出文本可渲染图（默认 mermaid 源码）。
    图只画题目结构 / 变量关系 / 控制流 / 检查点，不给完整解题链路、公式或可照抄步骤（防泄答）。
    """

    # flowchart / data_flow / for_loop_trace / data_structure_state 等（与源版 visualize intent 对齐）。
    intent: str = "flowchart"
    title: str = Field(min_length=1)
    # mermaid（默认，前端可直接渲染）/ ascii / svg。
    format: str = "mermaid"
    diagram: str = Field(min_length=1)
    alt_text: str = Field(min_length=1)
    caption: str = ""
    teaching_goal: str = ""
    checkpoint_prompt: str = ""
    mentor_role: str = "百晓"
    reflection_prompt: str = ""

    @field_validator("format", mode="before")
    @classmethod
    def _normalize_format(cls, value: Any) -> str:
        token = str(value or "mermaid").strip().lower()
        return token if token in {"mermaid", "ascii", "svg"} else "mermaid"


class ParsonsBlock(BaseModel):
    """Parsons 代码积木（一行代码 + 缩进层级）。"""

    id: str = Field(min_length=1)
    code: str = Field(min_length=1)
    indent: int = Field(default=0, ge=0, le=6)


class ParsonsCard(BaseModel):
    """拼装挑战卡（PARSONS，不改变 Phase）。

    本仓不依赖 Java FSRS 出题：LLM 产出一道**独立子技能微练习**（明确区别于当前题完整解法，
    防泄答）。correct_order 是该微练习的正确顺序（非当前题答案），供前端本地自检。
    """

    drill_goal: str = Field(min_length=1)
    prompt: str = Field(min_length=1)
    blocks: list[ParsonsBlock] = Field(min_length=2)
    distractors: list[ParsonsBlock] = Field(default_factory=list)
    correct_order: list[str] = Field(min_length=2)
    # 脚手架淡出等级（0=给全部积木；越大干扰/留空越多）。
    fading_level: int = Field(default=0, ge=0, le=3)
    success_check: str = ""
    teaching_goal: str = ""
    checkpoint_prompt: str = ""
    mentor_role: str = "百晓"
    reflection_prompt: str = ""


# 事件 → node_outputs key（与 Alethicode EVENT_OUTPUT_KEY 对齐）。
EVENT_OUTPUT_KEY: dict[str, str] = {
    "READING": "problem_guide",
    "IDEATING": "ideate_analysis",
    "SKELETON": "skeleton_code",
    "ERROR_FEEDBACK": "error_diagnosis",
    "AC_REVIEW": "post_ac",
    "TRANSFER": "transfer_problem",
    "CHAT": "ai_reply",
    "KNOWLEDGE_REVIEW": "knowledge_review",
    "EXECUTION_TRACE": "execution_trace",
    "VISUALIZE": "visualize",
    "PARSONS": "parsons",
}

# 卡片 key → pydantic 模型（等价 Alethicode CARD_KEY_TO_SCHEMA 强校验映射）。
CARD_KEY_TO_MODEL: dict[str, type[BaseModel]] = {
    "problem_guide": ProblemGuideCard,
    "ideate_analysis": IdeateAnalysisCard,
    "skeleton_code": SkeletonCodeCard,
    "error_diagnosis": ErrorDiagnosisCard,
    "post_ac": PostAcCard,
    "transfer_problem": TransferProblemCard,
    "ai_reply": AiReplyCard,
    "knowledge_review": KnowledgeReviewCard,
    "execution_trace": ExecutionTraceCard,
    "visualize": VisualizeCard,
    "parsons": ParsonsCard,
}


class WorkflowRunResult(BaseModel):
    """run 终值（RUN_FINISHED.result）。CODING 等纯状态推进事件无卡片。"""

    session_id: str
    event: WorkflowEvent
    phase: Phase
    card_key: str | None = None
    card: dict[str, Any] | None = None
    available_actions: list[WorkflowAction] = Field(default_factory=list)
    coach_decision: GuideCoachDecision | None = None
    student_state: TutorStudentState | None = None
    agentic: bool = True
    # 有界自主多步辅导计划（coach_mode 才有；纯状态推进事件可为 None）。
    episode_plan: TutorEpisodePlan | None = None
    episode_patch: TutorEpisodePlanPatch | None = None
    # 会话内卡片序号（投影排序用；无卡片事件不递增）。
    seq: int = 0
