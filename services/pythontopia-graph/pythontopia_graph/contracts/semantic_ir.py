from __future__ import annotations

import ast
import re
from typing import Literal, cast

from pydantic import BaseModel, ConfigDict, Field, model_validator

from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle
from pythontopia_graph.schemas.reflection import Insight, ReflectionOutput
from pythontopia_graph.schemas.resource import (
    HandoutContent,
    HandoutSection,
    CodeLabContent,
    MindMapContent,
    PageRef,
    PracticeContent,
    ProblemSpec,
    ReadingContent,
    ReadingItem,
    ResourceProduceRequest,
    SampleIo,
    StoryboardContent,
    StoryboardScene,
    Walkthrough,
)
from pythontopia_graph.schemas.tutor_workflow import (
    GuideCoachDecision,
    WorkflowAction,
    WorkflowEvent,
)


class BoundEvidence(BaseModel):
    """Evidence exposed to a model through an opaque, auditable identifier."""

    evidence_id: str
    page_id: int
    page_no: int
    chapter_id: int | None = None
    kc_codes: list[str] = Field(default_factory=list)
    excerpt: str


class EvidenceContext(BaseModel):
    items: list[BoundEvidence] = Field(default_factory=list)

    @property
    def allowed_ids(self) -> list[str]:
        return [item.evidence_id for item in self.items]

    @property
    def by_id(self) -> dict[str, BoundEvidence]:
        return {item.evidence_id: item for item in self.items}


class EvidenceBinder:
    """Creates the sole evidence namespace a model may cite."""

    @staticmethod
    def bind(bundle: EvidenceBundle, *, excerpt_chars: int = 800) -> EvidenceContext:
        items = [
            BoundEvidence(
                evidence_id=f"page-{hit.page_id}",
                page_id=hit.page_id,
                page_no=hit.page_no,
                chapter_id=hit.chapter_id,
                kc_codes=list(hit.kc_codes),
                excerpt=hit.text[:excerpt_chars],
            )
            for hit in bundle.chunks
        ]
        return EvidenceContext(items=items)


ReflectionDimensionIntent = Literal[
    "ERROR_PATTERN",
]


class ReflectionObservationIR(BaseModel):
    """Model-owned learning observation; event IDs and product keys stay hidden."""

    model_config = ConfigDict(extra="forbid")

    claim: str = Field(min_length=2, max_length=200)
    dimension_intent: ReflectionDimensionIntent
    tags: list[str] = Field(default_factory=list, max_length=5)
    evidence_refs: list[str] = Field(min_length=1, max_length=5)
    confidence: float = Field(ge=0, le=1)


class ReflectionSemanticIR(BaseModel):
    model_config = ConfigDict(extra="forbid")

    observations: list[ReflectionObservationIR] = Field(max_length=3)

    @model_validator(mode="after")
    def _no_answer_leak_markers(self) -> "ReflectionSemanticIR":
        forbidden = ("hidden_test", "answer_key", "完整答案", "标准答案", "直接提交")
        text = self.model_dump_json().lower()
        if any(token in text for token in forbidden):
            raise ValueError("reflection semantic IR contains forbidden answer material")
        return self


class GuideCoachStateDelta(BaseModel):
    hint_level: int = Field(ge=0, le=3)
    diagnosis_label: str = ""
    student_state_label: str = ""


class GuideCoachSemanticIR(BaseModel):
    """Small model-owned decision; product mechanics are deliberately absent."""

    # Small teaching-action ontology; product workflow constants stay compiler-owned.
    event_intent: Literal[
        "READ", "REVIEW", "TRACE", "SCAFFOLD", "PRACTICE", "ASK", "CHECK"
    ]
    action_goal: str = Field(min_length=1, max_length=300)
    stop_condition: str = Field(min_length=1, max_length=300)
    state_delta: GuideCoachStateDelta
    evidence_refs: list[str] = Field(default_factory=list)
    confidence: float = Field(ge=0, le=1)
    reason_summary: str = Field(min_length=1, max_length=240)


class PracticeProblemIR(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    statement: str = Field(min_length=10)
    hints: list[str] = Field(default_factory=list, max_length=4)
    sample_input: str
    sample_output: str


class PracticeSemanticIR(BaseModel):
    problems: list[PracticeProblemIR] = Field(min_length=1, max_length=3)
    evidence_refs: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def _no_answer_leak_markers(self) -> "PracticeSemanticIR":
        forbidden = ("hidden_test", "answer_key", "完整答案", "标准答案")
        text = self.model_dump_json().lower()
        if any(token in text for token in forbidden):
            raise ValueError("practice semantic IR contains forbidden answer material")
        return self


class HandoutSectionIR(BaseModel):
    heading: str = Field(min_length=1, max_length=200)
    paragraphs: list[str] = Field(min_length=1, max_length=6)
    code_snippets: list[str] = Field(default_factory=list, max_length=3)
    evidence_refs: list[str] = Field(default_factory=list)


class HandoutSemanticIR(BaseModel):
    title: str = Field(min_length=1, max_length=256)
    summary: str = Field(default="", max_length=512)
    sections: list[HandoutSectionIR] = Field(min_length=1, max_length=6)


class ReadingItemIR(BaseModel):
    summary_md: str = Field(min_length=10, max_length=800)
    evidence_refs: list[str] = Field(default_factory=list)


class ReadingSemanticIR(BaseModel):
    items: list[ReadingItemIR] = Field(min_length=2, max_length=4)


class CodeWalkthroughIR(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    narrative: str = Field(min_length=10, max_length=800)
    code: str = Field(min_length=1, max_length=3000)
    expected_output: str = Field(max_length=1000)
    common_mistakes: list[str] = Field(default_factory=list, max_length=5)
    evidence_refs: list[str] = Field(default_factory=list)


class CodeLabSemanticIR(BaseModel):
    """Frozen v2 free-code contract retained for historical experiment replay."""

    walkthroughs: list[CodeWalkthroughIR] = Field(min_length=1, max_length=3)


CodeExamplePattern = Literal[
    "assignment_trace",
    "conditional_branch",
    "while_control",
    "for_accumulate",
    "list_iteration",
    "function_scope",
    "string_cleanup",
    "exception_guard",
]


class CodePlanWalkthroughIR(BaseModel):
    """Model-owned pedagogical choice; executable syntax stays compiler-owned."""

    model_config = ConfigDict(extra="forbid")

    title: str = Field(min_length=1, max_length=200)
    narrative: str = Field(min_length=10, max_length=800)
    example_pattern: CodeExamplePattern
    observation_goal: str = Field(min_length=5, max_length=240)
    common_mistakes: list[str] = Field(min_length=1, max_length=5)
    evidence_refs: list[str] = Field(default_factory=list)


class CodeLabPlanSemanticIR(BaseModel):
    model_config = ConfigDict(extra="forbid")

    walkthroughs: list[CodePlanWalkthroughIR] = Field(min_length=1, max_length=3)


class StoryboardSceneIR(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    narration: str = Field(min_length=10, max_length=800)
    visual_intent: str = Field(min_length=2, max_length=240)
    pacing: Literal["brief", "normal", "deep"] = "normal"
    evidence_refs: list[str] = Field(default_factory=list, max_length=1)


class StoryboardSemanticIR(BaseModel):
    scenes: list[StoryboardSceneIR] = Field(min_length=3, max_length=5)


class IntentRouterSemanticIR(BaseModel):
    """Only the uncertain semantic decision; route governance stays deterministic."""

    intent: str
    kc_codes: list[str] = Field(default_factory=list, max_length=3)
    focus_hint: str = Field(default="", max_length=120)
    confidence: float = 0.5
    reason: str = Field(default="", max_length=240)
    target: str = Field(default="", max_length=32)
    secondary_intent: str | None = None
    needs_clarification: bool = False
    missing_context: str = Field(default="", max_length=160)
    clarification_prompt: str = Field(default="", max_length=240)

    @model_validator(mode="after")
    def _normalize_route(self) -> "IntentRouterSemanticIR":
        self.intent = self.intent.strip().upper()
        if self.secondary_intent:
            self.secondary_intent = self.secondary_intent.strip().upper()
        self.confidence = min(max(self.confidence, 0.0), 1.0)
        return self


class MindMapNodeIR(BaseModel):
    node_id: str = Field(pattern=r"^[A-Za-z][A-Za-z0-9_]{0,31}$")
    label: str = Field(min_length=1, max_length=80)
    kc_code: str | None = Field(default=None, max_length=64)


class MindMapEdgeIR(BaseModel):
    source: str
    target: str
    relation: str = Field(default="关联", max_length=40)


class MindMapSemanticIR(BaseModel):
    nodes: list[MindMapNodeIR] = Field(min_length=2, max_length=12)
    edges: list[MindMapEdgeIR] = Field(min_length=1, max_length=16)
    evidence_refs: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def _valid_graph(self) -> "MindMapSemanticIR":
        node_ids = [node.node_id for node in self.nodes]
        if len(node_ids) != len(set(node_ids)):
            raise ValueError("mind-map node ids must be unique")
        unknown = {
            endpoint
            for edge in self.edges
            for endpoint in (edge.source, edge.target)
            if endpoint not in node_ids
        }
        if unknown:
            raise ValueError(f"mind-map edge references unknown nodes: {sorted(unknown)}")
        return self


class ContractCompiler:
    """Deterministically compiles semantic IR into stable product contracts."""

    @staticmethod
    def compile_reflection(
        ir: ReflectionSemanticIR, *, allowed_event_ids: list[int]
    ) -> ReflectionOutput:
        event_by_ref = {f"event-{event_id}": event_id for event_id in allowed_event_ids}
        dimension_keys = {
            "ERROR_PATTERN": "error_pattern",
        }
        insights: list[Insight] = []
        for observation in ir.observations:
            unknown = sorted(set(observation.evidence_refs) - set(event_by_ref))
            if unknown:
                raise ValueError(
                    "reflection evidence refs outside the live allowlist: "
                    + ",".join(unknown)
                )
            insights.append(
                Insight(
                    text=observation.claim,
                    dimension_key=dimension_keys[observation.dimension_intent],
                    tags=observation.tags,
                    evidence_event_ids=[
                        event_by_ref[ref] for ref in observation.evidence_refs
                    ],
                    confidence=observation.confidence,
                )
            )
        return ReflectionOutput(insights=insights)

    @staticmethod
    def compile_guide_coach(
        ir: GuideCoachSemanticIR,
        *,
        allowed_events: list[WorkflowEvent],
        tool_by_event: dict[str, str],
        followups: list[WorkflowAction],
        allowed_evidence_ids: list[str] | None = None,
    ) -> GuideCoachDecision:
        resolved_event = ContractCompiler.resolve_guide_event(
            ir.event_intent, allowed_events
        )
        ContractCompiler._validate_evidence_refs(
            ir.evidence_refs, allowed_evidence_ids or []
        )
        return GuideCoachDecision(
            next_event=resolved_event,
            tool_name=tool_by_event.get(resolved_event, ""),
            hint_level=ir.state_delta.hint_level,
            diagnosis_label=ir.state_delta.diagnosis_label,
            student_state_label=ir.state_delta.student_state_label,
            action_goal=ir.action_goal,
            stop_condition=ir.stop_condition,
            allowed_followups=followups,
            confidence=ir.confidence,
            reason_summary=ir.reason_summary,
        )

    @staticmethod
    def resolve_guide_event(
        intent: str, allowed_events: list[WorkflowEvent]
    ) -> WorkflowEvent:
        normalized = intent.strip().upper()
        if normalized in allowed_events:
            return cast(WorkflowEvent, normalized)
        intents: dict[str, WorkflowEvent] = {
            "READ": "READING",
            "REVIEW": "KNOWLEDGE_REVIEW",
            "TRACE": "EXECUTION_TRACE",
            "SCAFFOLD": "SKELETON",
            "PRACTICE": "CODING",
            "ASK": "CHAT",
            "CHECK": "AC_REVIEW",
        }
        resolved = intents.get(normalized)
        if resolved is None:
            raise ValueError(f"unrecognized guide event intent: {intent}")
        if resolved not in allowed_events:
            raise ValueError(
                f"resolved guide event outside the live allowlist: {resolved}"
            )
        return resolved

    @staticmethod
    def compile_practice(
        ir: PracticeSemanticIR,
        *,
        request: ResourceProduceRequest,
        difficulty: Literal["basic", "medium", "hard"],
        allowed_evidence_ids: list[str],
    ) -> PracticeContent:
        ContractCompiler._validate_evidence_refs(ir.evidence_refs, allowed_evidence_ids)
        return PracticeContent(
            problems=[
                ProblemSpec(
                    title=problem.title,
                    statement_md=problem.statement,
                    hints=problem.hints,
                    sample_io=[
                        SampleIo(input=problem.sample_input, output=problem.sample_output)
                    ],
                    kc_codes=list(request.kc_codes),
                    difficulty=difficulty,
                )
                for problem in ir.problems
            ]
        )

    @staticmethod
    def compile_handout(
        ir: HandoutSemanticIR, *, evidence: EvidenceContext
    ) -> HandoutContent:
        return HandoutContent(
            sections=[
                HandoutSection(
                    heading=section.heading,
                    paragraphs=section.paragraphs,
                    code_snippets=section.code_snippets,
                    page_refs=ContractCompiler._page_refs(
                        section.evidence_refs, evidence
                    ),
                )
                for section in ir.sections
            ]
        )

    @staticmethod
    def compile_reading(
        ir: ReadingSemanticIR,
        *,
        evidence: EvidenceContext,
        kc_codes: list[str],
    ) -> ReadingContent:
        return ReadingContent(
            items=[
                ReadingItem(
                    summary_md=item.summary_md,
                    page_refs=ContractCompiler._page_refs(
                        item.evidence_refs, evidence
                    ),
                    kc_codes=list(kc_codes),
                )
                for item in ir.items
            ]
        )

    @staticmethod
    def compile_code_lab(
        ir: CodeLabSemanticIR,
        *,
        evidence: EvidenceContext,
        max_lines: int = 50,
    ) -> CodeLabContent:
        walkthroughs: list[Walkthrough] = []
        for item in ir.walkthroughs:
            ContractCompiler._validate_evidence_refs(
                item.evidence_refs, evidence.allowed_ids
            )
            ContractCompiler._validate_beginner_code(item.code, max_lines=max_lines)
            walkthroughs.append(
                Walkthrough(
                    title=item.title,
                    narrative=item.narrative,
                    code=item.code,
                    expected_output=item.expected_output,
                    common_mistakes=item.common_mistakes,
                )
            )
        return CodeLabContent(walkthroughs=walkthroughs)

    @staticmethod
    def compile_code_lab_plan(
        ir: CodeLabPlanSemanticIR,
        *,
        evidence: EvidenceContext,
    ) -> CodeLabContent:
        walkthroughs: list[Walkthrough] = []
        for item in ir.walkthroughs:
            ContractCompiler._validate_evidence_refs(
                item.evidence_refs, evidence.allowed_ids
            )
            code, expected_output = ContractCompiler._compiled_code_example(
                item.example_pattern
            )
            ContractCompiler._validate_beginner_code(code, max_lines=50)
            walkthroughs.append(
                Walkthrough(
                    title=item.title,
                    narrative=f"{item.narrative}\n观察目标：{item.observation_goal}",
                    code=code,
                    expected_output=expected_output,
                    common_mistakes=item.common_mistakes,
                )
            )
        return CodeLabContent(walkthroughs=walkthroughs)

    @staticmethod
    def _compiled_code_example(pattern: CodeExamplePattern) -> tuple[str, str]:
        examples: dict[str, tuple[str, str]] = {
            "assignment_trace": (
                "name = 'Ada'\nscore = 1\nscore = score + 1\nprint(name, score)",
                "Ada 2",
            ),
            "conditional_branch": (
                "score = 68\nif score >= 60:\n    status = 'pass'\nelse:\n    status = 'retry'\nprint(status)",
                "pass",
            ),
            "while_control": (
                "index = 0\nwhile index < 5:\n    index += 1\n    if index == 3:\n        continue\n    print(index)",
                "1\n2\n4\n5",
            ),
            "for_accumulate": (
                "total = 0\nfor value in range(1, 4):\n    total += value\nprint(total)",
                "6",
            ),
            "list_iteration": (
                "values = [2, 4, 6]\nfor value in values:\n    print(value // 2)",
                "1\n2\n3",
            ),
            "function_scope": (
                "def add_bonus(score, bonus):\n    result = score + bonus\n    return result\n\nprint(add_bonus(70, 5))",
                "75",
            ),
            "string_cleanup": (
                "raw_line = 'Ada\\n'\nclean_line = raw_line.rstrip('\\n')\nprint(repr(clean_line))",
                "'Ada'",
            ),
            "exception_guard": (
                "text = '12'\ntry:\n    number = int(text)\n    print(number)\nexcept ValueError:\n    print('invalid')",
                "12",
            ),
        }
        return examples[str(pattern)]

    @staticmethod
    def compile_storyboard(
        ir: StoryboardSemanticIR,
        *,
        evidence: EvidenceContext,
        kc_codes: list[str],
    ) -> StoryboardContent:
        duration_by_pacing = {"brief": 20, "normal": 45, "deep": 75}
        banned_visual_tokens = ("真实人物", "真人头像", "知名品牌", "二维码")
        scenes: list[StoryboardScene] = []
        for item in ir.scenes:
            ContractCompiler._validate_evidence_refs(
                item.evidence_refs, evidence.allowed_ids
            )
            if any(token in item.visual_intent for token in banned_visual_tokens):
                raise ValueError("storyboard visual intent crossed a safety boundary")
            evidence_page_id = None
            if item.evidence_refs:
                evidence_page_id = evidence.by_id[item.evidence_refs[0]].page_id
            scenes.append(
                StoryboardScene(
                    title=item.title,
                    narration=item.narration,
                    visual_hint=item.visual_intent,
                    kc_codes=list(kc_codes),
                    duration_seconds=duration_by_pacing[item.pacing],
                    evidence_page_id=evidence_page_id,
                    narration_tts_voice="x4_yezi",
                    image_prompt=(
                        "像素教学插画，"
                        f"{item.visual_intent}，暖米黄色调，无真实人物、品牌、二维码或文字水印"
                    ),
                )
            )
        return StoryboardContent(scenes=scenes)

    @staticmethod
    def compile_mind_map(
        ir: MindMapSemanticIR,
        *,
        allowed_kc_codes: list[str],
        allowed_evidence_ids: list[str],
    ) -> MindMapContent:
        ContractCompiler._validate_evidence_refs(ir.evidence_refs, allowed_evidence_ids)
        allowed_kcs = set(allowed_kc_codes)
        nodes = {node.node_id: node for node in ir.nodes}
        lines = ["graph TD"]
        for node in ir.nodes:
            label = re.sub(r"[\[\]{}()]", "", node.label).strip() or "概念"
            lines.append(f"  {node.node_id}[{label}]")
        for edge in ir.edges:
            relation = re.sub(r"[|<>\[\]{}()]", "", edge.relation).strip()
            arrow = f"-->|{relation}|" if relation else "-->"
            lines.append(f"  {edge.source} {arrow} {edge.target}")
        kc_map: dict[str, list[str]] = {}
        for edge in ir.edges:
            source_kc = nodes[edge.source].kc_code
            target_kc = nodes[edge.target].kc_code
            if source_kc in allowed_kcs:
                kc_map.setdefault(source_kc, [])
                if target_kc in allowed_kcs and target_kc != source_kc:
                    kc_map[source_kc].append(target_kc)
        root = allowed_kc_codes[0]
        kc_map.setdefault(root, [])
        return MindMapContent(mermaid="\n".join(lines), kc_map=kc_map)

    @staticmethod
    def _validate_evidence_refs(refs: list[str], allowed: list[str]) -> None:
        unknown = sorted(set(refs) - set(allowed))
        if unknown:
            raise ValueError(f"semantic IR cited unknown evidence ids: {unknown}")

    @staticmethod
    def _page_refs(refs: list[str], evidence: EvidenceContext) -> list[PageRef]:
        ContractCompiler._validate_evidence_refs(refs, evidence.allowed_ids)
        by_id = evidence.by_id
        return [
            PageRef(
                page_id=by_id[ref].page_id,
                page_no=by_id[ref].page_no,
                chapter_id=by_id[ref].chapter_id,
            )
            for ref in dict.fromkeys(refs)
        ]

    @staticmethod
    def _validate_beginner_code(code: str, *, max_lines: int) -> None:
        if code.count("\n") + 1 > max_lines:
            raise ValueError(f"code exceeds {max_lines} lines")
        try:
            tree = ast.parse(code)
        except SyntaxError as exc:
            raise ValueError("code is not valid Python") from exc
        forbidden_names = {
            "__import__",
            "compile",
            "eval",
            "exec",
            "open",
            "os",
            "pathlib",
            "shutil",
            "socket",
            "subprocess",
            "sys",
        }
        forbidden_attributes = {
            "popen",
            "remove",
            "request",
            "rmtree",
            "system",
            "unlink",
            "urlopen",
        }
        for node in ast.walk(tree):
            if isinstance(node, (ast.Import, ast.ImportFrom)):
                raise ValueError("imports are not allowed in beginner code labs")
            if isinstance(node, ast.Name) and node.id in forbidden_names:
                raise ValueError(f"unsafe code name: {node.id}")
            if isinstance(node, ast.Attribute) and (
                node.attr.startswith("__") or node.attr in forbidden_attributes
            ):
                raise ValueError(f"unsafe code attribute: {node.attr}")
