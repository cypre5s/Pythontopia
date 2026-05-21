from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field, model_validator

ResourceType = Literal[
    "HANDOUT",
    "MINDMAP",
    "PRACTICE",
    "CODE_LAB",
    "READING",
    "STORYBOARD",
]

ContentFormat = Literal["MARKDOWN", "MERMAID", "STRUCT_JSON"]


class PageRef(BaseModel):
    page_id: int
    page_no: int
    chapter_id: int | None = None


class HandoutSection(BaseModel):
    heading: str = Field(min_length=1, max_length=200)
    paragraphs: list[str] = Field(default_factory=list)
    code_snippets: list[str] = Field(default_factory=list)
    page_refs: list[PageRef] = Field(default_factory=list)


class HandoutContent(BaseModel):
    sections: list[HandoutSection] = Field(min_length=1)


class MindMapContent(BaseModel):
    mermaid: str = Field(min_length=10)
    kc_map: dict[str, list[str]] = Field(default_factory=dict)


class SampleIo(BaseModel):
    input: str
    output: str


class ProblemSpec(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    statement_md: str = Field(min_length=10)
    hints: list[str] = Field(default_factory=list)
    sample_io: list[SampleIo] = Field(min_length=1)
    kc_codes: list[str] = Field(default_factory=list)
    difficulty: Literal["basic", "medium", "hard"] = "basic"


class PracticeContent(BaseModel):
    problems: list[ProblemSpec] = Field(min_length=1, max_length=3)

    @model_validator(mode="after")
    def _no_hidden_keys(self) -> "PracticeContent":
        forbidden = {"hidden_test_cases", "hidden", "answer_key", "secret_test"}
        for problem in self.problems:
            for forbidden_key in forbidden:
                if forbidden_key in problem.statement_md.lower():
                    raise ValueError(
                        f"PracticeContent.problem statement must not contain '{forbidden_key}'"
                    )
        return self


class Walkthrough(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    narrative: str
    code: str = Field(min_length=1)
    expected_output: str
    common_mistakes: list[str] = Field(default_factory=list)


class CodeLabContent(BaseModel):
    walkthroughs: list[Walkthrough] = Field(min_length=1, max_length=3)


class ReadingItem(BaseModel):
    summary_md: str = Field(min_length=10)
    page_refs: list[PageRef] = Field(default_factory=list)
    kc_codes: list[str] = Field(default_factory=list)


class ReadingContent(BaseModel):
    items: list[ReadingItem] = Field(min_length=2)


class StoryboardScene(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    narration: str
    visual_hint: str
    kc_codes: list[str] = Field(default_factory=list)
    duration_seconds: int = Field(ge=5, le=90)


class StoryboardContent(BaseModel):
    scenes: list[StoryboardScene] = Field(min_length=3, max_length=5)


ResourceContent = (
    HandoutContent
    | MindMapContent
    | PracticeContent
    | CodeLabContent
    | ReadingContent
    | StoryboardContent
)


class ProfileSnapshot(BaseModel):
    major: Literal["CS", "NON_CS_STEM", "NON_CS_HUMANITIES", "OTHER"] = "OTHER"
    python_self_rated_level: int = Field(default=2, ge=1, le=5)
    media_priority: list[str] = Field(default_factory=list)


class ResourceProduceRequest(BaseModel):
    user_id: int = Field(ge=1)
    kc_codes: list[str] = Field(min_length=1, max_length=10)
    type: ResourceType
    focus_hint: str | None = Field(default=None, max_length=512)
    profile_snapshot: ProfileSnapshot = Field(default_factory=ProfileSnapshot)


class GroundingResult(BaseModel):
    passed: bool
    reasons: list[str] = Field(default_factory=list)
    no_evidence_found: bool = False
    retried: bool = False


class SafetyResult(BaseModel):
    passed: bool
    reasons: list[str] = Field(default_factory=list)


class GraphTraceStep(BaseModel):
    node: str
    elapsed_ms: int
    status: Literal["ok", "retry", "fail"]
    note: str | None = None


class ResourceDraft(BaseModel):
    title: str = Field(min_length=1, max_length=256)
    summary: str | None = Field(default=None, max_length=512)
    content_format: ContentFormat
    handout: HandoutContent | None = None
    mindmap: MindMapContent | None = None
    practice: PracticeContent | None = None
    code_lab: CodeLabContent | None = None
    reading: ReadingContent | None = None
    storyboard: StoryboardContent | None = None

    @model_validator(mode="after")
    def _exactly_one_body(self) -> "ResourceDraft":
        bodies = [self.handout, self.mindmap, self.practice, self.code_lab, self.reading, self.storyboard]
        filled = [body for body in bodies if body is not None]
        if len(filled) != 1:
            raise ValueError("ResourceDraft must carry exactly one content body")
        return self

    def content_payload(self) -> dict[str, object]:
        return (
            self.handout
            or self.mindmap
            or self.practice
            or self.code_lab
            or self.reading
            or self.storyboard
        ).model_dump(mode="json")  # type: ignore[union-attr]


class ResourceProduceResponse(BaseModel):
    artifact: ResourceDraft
    type: ResourceType
    evidence_page_ids: list[int] = Field(default_factory=list)
    grounding_result: GroundingResult
    safety_result: SafetyResult
    graph_trace: list[GraphTraceStep] = Field(default_factory=list)
    status: Literal["SUCCESS", "PARTIAL", "FAILED"] = "SUCCESS"
