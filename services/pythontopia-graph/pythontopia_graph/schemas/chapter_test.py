"""章末大测试（chapter test）生成端的结构化契约。

生成端 = graph（本服务）；判分端 = backend。题面 JSON 严格对齐
``docs/plans/r15-mainline-rebuild-plan.md`` §3 的 ``questions`` 契约：
CHOICE / JUDGE / FILL 为客观题，必须携带 RAG 校验过的 ``evidence_page_ids``；
CODING 由 KC 接地 + 沙箱自测背书，不带证据页。

后端落库映射（§3 表 chapter_test）：
- ``ChapterTestGenerateResponse.questions``        → chapter_test.questions（含答案+证据，绝不下发学生）
- ``ChapterTestGenerateResponse.profile_snapshot`` → chapter_test.profile_snapshot
- ``ChapterTestGenerateResponse.generator_meta``   → chapter_test.generator_meta（防幻觉审计）

所有字段 snake_case；客观题答案/解析/CODING 隐藏用例由后端在下发前剥离。
"""

from __future__ import annotations

from typing import Annotated, Any, Literal, Union

from pydantic import BaseModel, ConfigDict, Field, model_validator

from pythontopia_graph.schemas.resource import GraphTraceStep

_OPTION_LETTERS = "ABCDEFGH"


# --------------------------------------------------------------------------- #
# 题面 JSON 契约（§3）
# --------------------------------------------------------------------------- #
class ChoiceItem(BaseModel):
    """单选题。``answer`` 为选项字母（A..），与 ``options`` 下标对应。"""

    qid: str = Field(min_length=1, max_length=32)
    type: Literal["CHOICE"] = "CHOICE"
    stem: str = Field(min_length=1)
    options: list[str] = Field(min_length=2, max_length=len(_OPTION_LETTERS))
    answer: str = Field(min_length=1, max_length=1)
    explain: str = ""
    kc_code: str = Field(min_length=1)
    evidence_page_ids: list[int] = Field(default_factory=list)
    score: int = Field(default=8, ge=1, le=100)

    @model_validator(mode="after")
    def _answer_in_range(self) -> "ChoiceItem":
        allowed = set(_OPTION_LETTERS[: len(self.options)])
        if self.answer.strip().upper() not in allowed:
            raise ValueError(
                f"CHOICE.answer {self.answer!r} must be one of {sorted(allowed)}"
            )
        object.__setattr__(self, "answer", self.answer.strip().upper())
        return self


class JudgeItem(BaseModel):
    """判断题。``answer`` 为布尔。"""

    qid: str = Field(min_length=1, max_length=32)
    type: Literal["JUDGE"] = "JUDGE"
    stem: str = Field(min_length=1)
    answer: bool
    explain: str = ""
    kc_code: str = Field(min_length=1)
    evidence_page_ids: list[int] = Field(default_factory=list)
    score: int = Field(default=5, ge=1, le=100)


class FillItem(BaseModel):
    """填空题。``answers`` 为可接受答案候选集；``match`` 指定判分归一策略。"""

    qid: str = Field(min_length=1, max_length=32)
    type: Literal["FILL"] = "FILL"
    stem: str = Field(min_length=1)
    answers: list[str] = Field(min_length=1)
    match: Literal["normalized", "exact"] = "normalized"
    explain: str = ""
    kc_code: str = Field(min_length=1)
    evidence_page_ids: list[int] = Field(default_factory=list)
    score: int = Field(default=5, ge=1, le=100)

    @model_validator(mode="after")
    def _answers_nonblank(self) -> "FillItem":
        cleaned = [a for a in (s.strip() for s in self.answers) if a]
        if not cleaned:
            raise ValueError("FILL.answers must contain at least one non-blank answer")
        object.__setattr__(self, "answers", cleaned)
        return self


class CodingSample(BaseModel):
    input: str
    output: str


class CodingItem(BaseModel):
    """编程题。``reference_solution`` 须在沙箱内通过 samples + hidden_test_cases。"""

    qid: str = Field(min_length=1, max_length=32)
    type: Literal["CODING"] = "CODING"
    title: str = Field(min_length=1, max_length=200)
    statement_markdown: str = Field(min_length=1)
    samples: list[CodingSample] = Field(min_length=1)
    hidden_test_cases: list[CodingSample] = Field(min_length=1)
    reference_solution: str = Field(min_length=1)
    kc_code: str = Field(min_length=1)
    score: int = Field(default=30, ge=1, le=100)


PaperItem = Annotated[
    Union[ChoiceItem, JudgeItem, FillItem, CodingItem],
    Field(discriminator="type"),
]

OBJECTIVE_TYPES = ("CHOICE", "JUDGE", "FILL")


class QuestionPaper(BaseModel):
    """§3 ``questions`` 容器：``{"items":[...]}``。"""

    items: list[PaperItem] = Field(min_length=1)

    def type_counts(self) -> dict[str, int]:
        counts = {"CHOICE": 0, "JUDGE": 0, "FILL": 0, "CODING": 0}
        for item in self.items:
            counts[item.type] = counts.get(item.type, 0) + 1
        return counts

    def total_score(self) -> int:
        return sum(item.score for item in self.items)

    def objective_items(self) -> list[ChoiceItem | JudgeItem | FillItem]:
        return [
            item
            for item in self.items
            if isinstance(item, (ChoiceItem, JudgeItem, FillItem))
        ]

    def coding_items(self) -> list[CodingItem]:
        return [item for item in self.items if isinstance(item, CodingItem)]


# --------------------------------------------------------------------------- #
# 请求契约（§10 输入）
# --------------------------------------------------------------------------- #
class ProfileBrief(BaseModel):
    """8 维画像摘要（后端注入）。字段全部可选；未知维度键透传以便审计。"""

    model_config = ConfigDict(extra="allow")

    # 知识基础 1..5，驱动 CODING 复杂度（弱→简单，强→进阶），覆盖相同 KC。
    knowledge_level: int = Field(default=3, ge=1, le=5)
    # 专业背景（CS / NON_CS_STEM / NON_CS_HUMANITIES / OTHER 或自由文本）。
    major: str = ""
    learning_goal: str = ""
    cognitive_style: str = ""        # visual / logical / trial
    learning_preference: str = ""
    # 易错模式 → 针对性出题。
    error_patterns: list[str] = Field(default_factory=list)
    practice_ability: str = ""
    persistence: str = ""
    summary: str = ""                # 自由文本兜底摘要


class ErrorbookBrief(BaseModel):
    """错题本高频错因摘要（后端注入）。"""

    model_config = ConfigDict(extra="allow")

    frequent_error_kcs: list[str] = Field(default_factory=list)
    top_error_tags: list[str] = Field(default_factory=list)
    summary: str = ""


class PaperCounts(BaseModel):
    """题量与分值。默认 5×8 + 3×5 + 3×5 + 1×30 = 100（§3）。"""

    choice: int = Field(default=5, ge=0, le=50)
    judge: int = Field(default=3, ge=0, le=50)
    fill: int = Field(default=3, ge=0, le=50)
    coding: int = Field(default=1, ge=0, le=10)
    choice_score: int = Field(default=8, ge=1, le=100)
    judge_score: int = Field(default=5, ge=1, le=100)
    fill_score: int = Field(default=5, ge=1, le=100)
    coding_score: int = Field(default=30, ge=1, le=100)

    @model_validator(mode="after")
    def _at_least_one_item(self) -> "PaperCounts":
        if self.choice + self.judge + self.fill + self.coding <= 0:
            raise ValueError("PaperCounts must request at least one item")
        return self

    def as_dict(self) -> dict[str, int]:
        return {
            "choice": self.choice,
            "judge": self.judge,
            "fill": self.fill,
            "coding": self.coding,
        }

    def planned_total_score(self) -> int:
        return (
            self.choice * self.choice_score
            + self.judge * self.judge_score
            + self.fill * self.fill_score
            + self.coding * self.coding_score
        )


class ChapterTestGenerateRequest(BaseModel):
    user_id: int = Field(ge=1)
    chapter: int = Field(ge=1, le=7)
    kc_codes: list[str] = Field(min_length=1, max_length=40)
    profile_brief: ProfileBrief = Field(default_factory=ProfileBrief)
    errorbook_brief: ErrorbookBrief = Field(default_factory=ErrorbookBrief)
    counts: PaperCounts = Field(default_factory=PaperCounts)
    focus_hint: str | None = Field(default=None, max_length=512)


# --------------------------------------------------------------------------- #
# 审查报告 + 生成元数据（§10 审计 → chapter_test.generator_meta）
# --------------------------------------------------------------------------- #
class GroundingReport(BaseModel):
    passed: bool
    reasons: list[str] = Field(default_factory=list)
    no_evidence_found: bool = False


class SafetyReport(BaseModel):
    passed: bool
    reasons: list[str] = Field(default_factory=list)


class SelfTestReport(BaseModel):
    passed: bool
    coding_items: int = 0
    cases_total: int = 0
    cases_passed: int = 0
    reasons: list[str] = Field(default_factory=list)


class GeneratorMeta(BaseModel):
    """资源生成审计（AGENTS.md 多智能体规则）：run_id/agent/输入摘要/产物/证据/三审结果。"""

    run_id: str
    agent: str
    chapter: int
    input_summary: str
    output_summary: str
    counts: dict[str, int]
    requested_counts: dict[str, int]
    total_score: int
    evidence_page_ids: list[int] = Field(default_factory=list)
    grounding: GroundingReport
    safety: SafetyReport
    self_test: SelfTestReport
    graph_trace: list[GraphTraceStep] = Field(default_factory=list)
    fallback: Literal["none", "golden"] = "none"
    created_at: str


class ChapterTestGenerateResponse(BaseModel):
    status: Literal["SUCCESS"] = "SUCCESS"
    questions: QuestionPaper
    profile_snapshot: dict[str, Any] = Field(default_factory=dict)
    generator_meta: GeneratorMeta


class ChapterTestErrorDetail(BaseModel):
    """节点失败时的结构化 502 detail（backend 落 FAILED → 重试/兜底）。"""

    code: str = "CHAPTER_TEST_GRAPH_FAILED"
    stage: str
    message: str
    run_id: str
    chapter: int
    evidence_page_ids: list[int] = Field(default_factory=list)
    grounding: GroundingReport | None = None
    safety: SafetyReport | None = None
    self_test: SelfTestReport | None = None
    graph_trace: list[GraphTraceStep] = Field(default_factory=list)
