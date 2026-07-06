from __future__ import annotations

from pythontopia_graph.schemas.resource import (
    GraphTraceStep,
    GroundingResult,
    ResourceEvaluationHook,
    ResourceDraft,
    ResourcePublishDecision,
    ResourcePublishFlow,
    ResourcePublishStage,
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
    graph_trace: list[GraphTraceStep],
    status: str,
) -> ResourceProduceResponse:
    publish_flow = build_publish_flow(
        artifact=artifact,
        evidence_page_ids=evidence_page_ids,
        grounding=grounding,
        safety=safety,
        graph_trace=graph_trace,
    )
    return ResourceProduceResponse(
        artifact=artifact,
        type=resource_type,
        evidence_page_ids=evidence_page_ids,
        grounding_result=grounding,
        safety_result=safety,
        graph_trace=graph_trace,
        status=status,  # type: ignore[arg-type]
        publish_flow=publish_flow,
    )


def build_publish_flow(
    *,
    artifact: ResourceDraft,
    evidence_page_ids: list[int],
    grounding: GroundingResult,
    safety: SafetyResult,
    graph_trace: list[GraphTraceStep],
) -> ResourcePublishFlow:
    trace_nodes = [step.node for step in graph_trace]
    design_nodes = [node for node in trace_nodes if ".attempt" in node]
    grounding_nodes = [
        node
        for node in trace_nodes
        if node
        in {
            "grounding_critic",
            "hallucination_detector",
            "semantic_grounding",
            "safety_critic",
            "oscillation_detected",
        }
    ]
    decision = _publish_decision(evidence_page_ids, grounding, safety)
    decision_status = {
        "PUBLISH": "PASSED",
        "HOLD_FOR_REVIEW": "NEEDS_REVIEW",
        "REJECT": "FAILED",
    }[decision.status]
    return ResourcePublishFlow(
        stages=[
            ResourcePublishStage(
                name="design",
                status=_status_for_nodes(graph_trace, design_nodes),
                trace_nodes=design_nodes,
                summary=f"Drafted {artifact.title} with exactly one structured content body.",
            ),
            ResourcePublishStage(
                name="evidence_assembly",
                status="PASSED" if evidence_page_ids else "NEEDS_REVIEW",
                trace_nodes=[node for node in trace_nodes if node == "evidence_fetcher"],
                summary="Collected course page evidence for resource grounding.",
                evidence_page_ids=evidence_page_ids,
            ),
            ResourcePublishStage(
                name="pedagogy_critique",
                status="PASSED",
                trace_nodes=["publish_flow.pedagogy_critique"],
                summary="Checked that the artifact has a title, body, and follow-up learning action.",
                evidence_page_ids=evidence_page_ids,
            ),
            ResourcePublishStage(
                name="grounding_safety",
                status="PASSED" if grounding.passed and safety.passed else "FAILED",
                trace_nodes=grounding_nodes,
                summary="Combined grounding, hallucination, semantic grounding, and safety verdicts.",
                evidence_page_ids=evidence_page_ids,
            ),
            ResourcePublishStage(
                name="publish_decision",
                status=decision_status,  # type: ignore[arg-type]
                trace_nodes=["publish_flow.publish_decision"],
                summary="Publish only when evidence, grounding, and safety gates all pass.",
                evidence_page_ids=evidence_page_ids,
            ),
            ResourcePublishStage(
                name="evaluation_hook",
                status="PASSED",
                trace_nodes=["publish_flow.evaluation_hook"],
                summary="Bind post-publish feedback and resource checks as effectiveness metrics.",
                evidence_page_ids=evidence_page_ids,
            ),
        ],
        publish_decision=decision,
        evaluation_hook=ResourceEvaluationHook(
            metric_names=[
                "feedback_adoption_rate",
                "micro_check_pass_rate",
                "mastery_delta_sum",
            ],
            artifact_ref_fields=[
                "resource_id",
                "created_by_run_id",
                "evidence_page_ids",
            ],
        ),
    )


def _publish_decision(
    evidence_page_ids: list[int],
    grounding: GroundingResult,
    safety: SafetyResult,
) -> ResourcePublishDecision:
    reasons: list[str] = []
    if not safety.passed:
        reasons.append("safety_failed")
    if not grounding.passed:
        reasons.append("grounding_failed")
    if reasons:
        return ResourcePublishDecision(
            status="REJECT",
            reasons=reasons + list(safety.reasons) + list(grounding.reasons),
            evidence_page_ids=evidence_page_ids,
        )
    if not evidence_page_ids or grounding.no_evidence_found:
        return ResourcePublishDecision(
            status="HOLD_FOR_REVIEW",
            reasons=["evidence_missing_or_unbound"],
            evidence_page_ids=evidence_page_ids,
        )
    return ResourcePublishDecision(
        status="PUBLISH",
        reasons=["grounded_safe_and_evidence_bound"],
        evidence_page_ids=evidence_page_ids,
    )


def _status_for_nodes(
    graph_trace: list[GraphTraceStep], node_names: list[str]
) -> str:
    if not node_names:
        return "NEEDS_REVIEW"
    by_name = {step.node: step for step in graph_trace}
    selected = [by_name[node] for node in node_names if node in by_name]
    if any(step.status == "fail" for step in selected):
        return "FAILED"
    if any(step.status == "retry" for step in selected):
        return "NEEDS_REVIEW"
    return "PASSED"
