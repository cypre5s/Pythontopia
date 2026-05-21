from __future__ import annotations

import ast

from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle
from pythontopia_graph.schemas.resource import (
    CodeLabContent,
    GroundingResult,
    HandoutContent,
    MindMapContent,
    PracticeContent,
    ReadingContent,
    ResourceDraft,
    StoryboardContent,
)


def hallucination_detector(
    draft: ResourceDraft,
    evidence: EvidenceBundle,
    valid_kc_codes: set[str],
) -> GroundingResult:
    """Run rule based checks that complement the evidence-grounded critic."""

    reasons: list[str] = []
    referenced_kcs: list[str] = []
    if isinstance(draft.mindmap, MindMapContent):
        referenced_kcs.extend(draft.mindmap.kc_map.keys())
        for related in draft.mindmap.kc_map.values():
            referenced_kcs.extend(related)
    if isinstance(draft.practice, PracticeContent):
        for problem in draft.practice.problems:
            referenced_kcs.extend(problem.kc_codes)
    if isinstance(draft.reading, ReadingContent):
        for item in draft.reading.items:
            referenced_kcs.extend(item.kc_codes)
    if isinstance(draft.storyboard, StoryboardContent):
        for scene in draft.storyboard.scenes:
            referenced_kcs.extend(scene.kc_codes)
    if isinstance(draft.handout, HandoutContent):
        for section in draft.handout.sections:
            for paragraph in section.paragraphs:
                referenced_kcs.extend(_extract_kc_tokens(paragraph))

    unknown = sorted({code for code in referenced_kcs if code and code not in valid_kc_codes})
    if unknown:
        reasons.append("Unknown KC: " + ",".join(unknown))

    if isinstance(draft.code_lab, CodeLabContent):
        for walkthrough in draft.code_lab.walkthroughs:
            try:
                ast.parse(walkthrough.code)
            except SyntaxError as exc:
                reasons.append(f"CodeLab '{walkthrough.title}' code parse failed: {exc.msg}")
    if isinstance(draft.practice, PracticeContent):
        for problem in draft.practice.problems:
            if not problem.sample_io:
                reasons.append(f"Practice '{problem.title}' missing sample_io")

    return GroundingResult(passed=not reasons, reasons=reasons)


def _extract_kc_tokens(text: str) -> list[str]:
    tokens = []
    for chunk in text.split():
        cleaned = chunk.strip(",.;:()[]{}").strip()
        if cleaned.startswith("PY43-KC-"):
            tokens.append(cleaned)
    return tokens
