from __future__ import annotations

from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle
from pythontopia_graph.schemas.resource import (
    GroundingResult,
    HandoutContent,
    PageRef,
    ReadingContent,
    ResourceDraft,
)


def _collect_refs(draft: ResourceDraft) -> list[PageRef]:
    refs: list[PageRef] = []
    if isinstance(draft.handout, HandoutContent):
        for section in draft.handout.sections:
            refs.extend(section.page_refs)
    if isinstance(draft.reading, ReadingContent):
        for item in draft.reading.items:
            refs.extend(item.page_refs)
    return refs


def grounding_critic(draft: ResourceDraft, evidence: EvidenceBundle) -> GroundingResult:
    """Verify that every PageRef in the draft sits inside the RAG hit set."""

    if evidence.no_evidence_found:
        return GroundingResult(passed=True, no_evidence_found=True)
    page_ids = {hit.page_id for hit in evidence.chunks}
    page_nos = evidence.page_nos
    if not page_ids:
        return GroundingResult(passed=True, no_evidence_found=True)
    refs = _collect_refs(draft)
    if not refs:
        return GroundingResult(passed=True, no_evidence_found=False)
    invalid: list[str] = []
    for ref in refs:
        if ref.page_id not in page_ids and ref.page_no not in page_nos:
            invalid.append(f"page_id={ref.page_id},page_no={ref.page_no}")
    if invalid:
        return GroundingResult(
            passed=False,
            reasons=[f"Unknown evidence ref: {item}" for item in invalid],
        )
    return GroundingResult(passed=True)
