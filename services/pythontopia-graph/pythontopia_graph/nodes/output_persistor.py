from __future__ import annotations

from pythontopia_graph.schemas.resource import (
    GroundingResult,
    ResourceDraft,
    ResourceProduceResponse,
    ResourceType,
    SafetyResult,
)


def build_response(
    *,
    artifact: ResourceDraft,
    resource_type: ResourceType,
    evidence_page_ids: list[int],
    grounding: GroundingResult,
    safety: SafetyResult,
    graph_trace: list,
    status: str,
) -> ResourceProduceResponse:
    return ResourceProduceResponse(
        artifact=artifact,
        type=resource_type,
        evidence_page_ids=evidence_page_ids,
        grounding_result=grounding,
        safety_result=safety,
        graph_trace=graph_trace,
        status=status,  # type: ignore[arg-type]
    )
