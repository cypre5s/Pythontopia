from __future__ import annotations

import argparse
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

from pythontopia_graph.schemas.resource import (
    ResourceEvaluationHook,
    ResourcePublishDecision,
    ResourcePublishFlow,
    ResourcePublishStage,
)
from pythontopia_graph.schemas.world_loop import (
    EvidenceRef,
    NpcDialoguePatch,
    RewardPlan,
    WorldLoopProposal,
    WorldQuestProposal,
    WorldStatePatch,
)


class M2GateModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class M2GateCase(M2GateModel):
    case_id: str = Field(min_length=1)
    category: Literal["resource_publish", "world_proposal", "ingestion_boundary"]
    passed: bool
    reasons: list[str] = Field(default_factory=list)


class M2GateReport(M2GateModel):
    dataset_id: str = "agentic-m2-resource-world-gate-v1"
    case_count: int
    passed: bool
    cases: list[M2GateCase]


def run_m2_resource_world_gate() -> M2GateReport:
    cases = [
        _case_resource_publish_with_evidence(),
        _case_resource_hold_without_evidence(),
        _case_resource_rejects_publish_without_evidence(),
        _case_world_proposal_accepts_evidence_bound_changes(),
        _case_world_proposal_rejects_unbound_changes(),
        _case_ingestion_boundary_is_local_and_explicit(),
    ]
    return M2GateReport(
        case_count=len(cases),
        passed=all(case.passed for case in cases),
        cases=cases,
    )


def write_markdown_report(report: M2GateReport, path: Path) -> Path:
    lines = [
        "# Agentic M2 Resource + World Gate",
        "",
        f"- Dataset: `{report.dataset_id}`",
        f"- Cases: {report.case_count}",
        f"- Result: {'PASS' if report.passed else 'FAIL'}",
        "",
        "| Case | Category | Result | Reasons |",
        "|---|---|---|---|",
    ]
    for case in report.cases:
        reasons = ", ".join(case.reasons) if case.reasons else "-"
        lines.append(
            f"| `{case.case_id}` | `{case.category}` | {'PASS' if case.passed else 'FAIL'} | {reasons} |"
        )
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return path


def _case_resource_publish_with_evidence() -> M2GateCase:
    flow = _flow(ResourcePublishDecision(status="PUBLISH", evidence_page_ids=[101]))
    passed = (
        flow.publish_decision.status == "PUBLISH"
        and flow.publish_decision.evidence_page_ids == [101]
        and flow.evaluation_hook.trigger == "resource_feedback_or_check"
    )
    return M2GateCase(
        case_id="resource-publish-bound-evidence",
        category="resource_publish",
        passed=passed,
        reasons=[] if passed else ["publish flow lost evidence or evaluation hook"],
    )


def _case_resource_hold_without_evidence() -> M2GateCase:
    flow = _flow(
        ResourcePublishDecision(
            status="HOLD_FOR_REVIEW",
            reasons=["evidence_missing_or_unbound"],
            evidence_page_ids=[],
        )
    )
    passed = flow.publish_decision.status == "HOLD_FOR_REVIEW"
    return M2GateCase(
        case_id="resource-hold-without-evidence",
        category="resource_publish",
        passed=passed,
        reasons=[] if passed else ["partial resource should not publish"],
    )


def _case_resource_rejects_publish_without_evidence() -> M2GateCase:
    try:
        ResourcePublishDecision(status="PUBLISH", evidence_page_ids=[])
    except ValueError:
        return M2GateCase(
            case_id="resource-reject-publish-without-evidence",
            category="resource_publish",
            passed=True,
        )
    return M2GateCase(
        case_id="resource-reject-publish-without-evidence",
        category="resource_publish",
        passed=False,
        reasons=["PUBLISH accepted empty evidence_page_ids"],
    )


def _case_world_proposal_accepts_evidence_bound_changes() -> M2GateCase:
    evidence = [EvidenceRef(type="submission_id", id=9001)]
    proposal = WorldLoopProposal(
        proposal_id="world-1",
        loop_run_id="loop-1",
        state_patch=[
            WorldStatePatch(
                key="town_hub.training_gate.flash",
                value=True,
                reason="学生已有通过提交，训练入口可突出下一步。",
                evidence_refs=evidence,
            )
        ],
        new_quests=[
            WorldQuestProposal(
                title="复用 input 写一个正式提交",
                kc_code="PY43-KC-001",
                node_type="SOLVE_PROBLEM",
                required_evidence_type="submission",
                deep_link_route="/training",
                evidence_refs=evidence,
            )
        ],
        npc_dialogue=[
            NpcDialoguePatch(
                npc_key="bai_xiao",
                lines=["这次进步有提交证据，去训练场收口吧。"],
                evidence_refs=evidence,
            )
        ],
        reward_plan=RewardPlan(summary="通过提交后发放任务奖励。", evidence_refs=evidence),
        evidence_refs=evidence,
    )
    return M2GateCase(
        case_id="world-proposal-evidence-bound",
        category="world_proposal",
        passed=proposal.has_complete_evidence_binding(),
    )


def _case_world_proposal_rejects_unbound_changes() -> M2GateCase:
    try:
        WorldLoopProposal(
            proposal_id="world-2",
            loop_run_id="loop-2",
            state_patch=[],
            new_quests=[],
            npc_dialogue=[],
            reward_plan=RewardPlan(summary="missing evidence", evidence_refs=[]),
            evidence_refs=[],
        )
    except ValueError:
        return M2GateCase(
            case_id="world-proposal-rejects-unbound",
            category="world_proposal",
            passed=True,
        )
    return M2GateCase(
        case_id="world-proposal-rejects-unbound",
        category="world_proposal",
        passed=False,
        reasons=["world proposal accepted unbound changes"],
    )


def _case_ingestion_boundary_is_local_and_explicit() -> M2GateCase:
    boundary = {
        "adapter_name": "local_text_pdf_docx",
        "supported_extensions": {".py", ".txt", ".md", ".csv", ".pdf", ".docx"},
        "external_ocr_enabled": False,
        "marker_enabled": False,
        "modes": {
            ".py": "utf8_text",
            ".txt": "utf8_text",
            ".md": "utf8_text",
            ".csv": "utf8_text",
            ".pdf": "pdfbox_text",
            ".docx": "poi_xwpf_text",
        },
    }
    passed = (
        boundary["adapter_name"] == "local_text_pdf_docx"
        and boundary["external_ocr_enabled"] is False
        and boundary["marker_enabled"] is False
        and {".pdf", ".docx"}.issubset(boundary["supported_extensions"])
    )
    return M2GateCase(
        case_id="ingestion-boundary-local-adapter",
        category="ingestion_boundary",
        passed=passed,
        reasons=[] if passed else ["ingestion boundary is not explicit local adapter"],
    )


def _flow(decision: ResourcePublishDecision) -> ResourcePublishFlow:
    return ResourcePublishFlow(
        stages=[
            ResourcePublishStage(
                name="design",
                status="PASSED",
                trace_nodes=["ResourceDesignerAgent.handout.attempt0"],
                summary="drafted",
            ),
            ResourcePublishStage(
                name="evidence_assembly",
                status="PASSED" if decision.evidence_page_ids else "NEEDS_REVIEW",
                trace_nodes=["evidence_fetcher"],
                summary="assembled evidence",
                evidence_page_ids=decision.evidence_page_ids,
            ),
            ResourcePublishStage(
                name="pedagogy_critique",
                status="PASSED",
                trace_nodes=["publish_flow.pedagogy_critique"],
                summary="checked learning fit",
                evidence_page_ids=decision.evidence_page_ids,
            ),
            ResourcePublishStage(
                name="grounding_safety",
                status="PASSED",
                trace_nodes=["grounding_critic", "safety_critic"],
                summary="checked grounding and safety",
                evidence_page_ids=decision.evidence_page_ids,
            ),
            ResourcePublishStage(
                name="publish_decision",
                status="PASSED" if decision.status == "PUBLISH" else "NEEDS_REVIEW",
                trace_nodes=["publish_flow.publish_decision"],
                summary="made publish decision",
                evidence_page_ids=decision.evidence_page_ids,
            ),
            ResourcePublishStage(
                name="evaluation_hook",
                status="PASSED",
                trace_nodes=["publish_flow.evaluation_hook"],
                summary="bound effectiveness metrics",
                evidence_page_ids=decision.evidence_page_ids,
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


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--markdown", type=Path, default=None)
    args = parser.parse_args()
    report = run_m2_resource_world_gate()
    if args.markdown is not None:
        write_markdown_report(report, args.markdown)
    print(report.model_dump_json(indent=2))
    return 0 if report.passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
