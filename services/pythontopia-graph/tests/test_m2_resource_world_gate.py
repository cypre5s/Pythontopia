from __future__ import annotations

import pytest

from pythontopia_graph.graph.resource_graph import ResourceGraph, ResourceGraphConfig
from pythontopia_graph.eval.m2_resource_world_gate import run_m2_resource_world_gate
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import RagHit, StaticEvidenceFetcher
from pythontopia_graph.schemas.resource import ResourceProduceRequest
from pythontopia_graph.schemas.world_loop import (
    EvidenceRef,
    NpcDialoguePatch,
    RewardPlan,
    WorldLoopProposal,
    WorldQuestProposal,
    WorldStatePatch,
)


class StubProvider(LlmProvider):
    async def chat(self, messages, **kwargs):  # type: ignore[override]
        return LlmResponse(content_json=None)


HIT = RagHit(
    chunk_id="c1",
    page_id=101,
    page_no=12,
    chapter_id=3,
    kc_codes=["PY43-KC-001"],
    text="第 12 页讲解 input 函数。",
    score=0.81,
)


@pytest.mark.asyncio
async def test_resource_graph_response_exposes_publish_flow_with_eval_hook() -> None:
    graph = ResourceGraph(
        provider=StubProvider(),
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )

    response = await graph.run(
        ResourceProduceRequest(user_id=1, kc_codes=["PY43-KC-001"], type="HANDOUT")
    )

    flow = response.publish_flow
    assert [stage.name for stage in flow.stages] == [
        "design",
        "evidence_assembly",
        "pedagogy_critique",
        "grounding_safety",
        "publish_decision",
        "evaluation_hook",
    ]
    assert flow.publish_decision.status == "PUBLISH"
    assert flow.publish_decision.evidence_page_ids == [101]
    assert flow.evaluation_hook.trigger == "resource_feedback_or_check"
    assert {
        "feedback_adoption_rate",
        "micro_check_pass_rate",
        "mastery_delta_sum",
    }.issubset(set(flow.evaluation_hook.metric_names))


@pytest.mark.asyncio
async def test_resource_publish_flow_holds_partial_without_evidence() -> None:
    graph = ResourceGraph(
        provider=StubProvider(),
        evidence_fetcher=StaticEvidenceFetcher([]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )

    response = await graph.run(
        ResourceProduceRequest(user_id=1, kc_codes=["PY43-KC-001"], type="HANDOUT")
    )

    assert response.status == "PARTIAL"
    assert response.publish_flow.publish_decision.status == "HOLD_FOR_REVIEW"
    assert any("evidence" in reason for reason in response.publish_flow.publish_decision.reasons)


def test_world_loop_proposal_contract_requires_evidence_for_all_changes() -> None:
    evidence = [EvidenceRef(type="kc_mastery_delta", id="PY43-KC-001:+0.05")]

    proposal = WorldLoopProposal(
        proposal_id="world-loop-1",
        loop_run_id="loop-run-7",
        state_patch=[
            WorldStatePatch(
                key="town_hub.notice_board.highlight",
                value="PY43-KC-001",
                reason="学生刚通过 input 微测，公告板突出下一步练习。",
                evidence_refs=evidence,
            )
        ],
        new_quests=[
            WorldQuestProposal(
                title="完成一次 input 训练",
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
                lines=["你刚证明了 input 的关键点，去训练场把它写成一次正式提交。"],
                evidence_refs=evidence,
            )
        ],
        reward_plan=RewardPlan(
            summary="微测通过后只给小额 mastery 奖励，正式提交后再结算任务。",
            mastery_delta=0.05,
            evidence_refs=evidence,
        ),
        evidence_refs=evidence,
    )

    assert proposal.has_complete_evidence_binding() is True

    with pytest.raises(ValueError, match="evidence"):
        WorldLoopProposal(
            proposal_id="world-loop-2",
            loop_run_id="loop-run-8",
            state_patch=[],
            new_quests=[],
            npc_dialogue=[],
            reward_plan=RewardPlan(summary="no evidence", evidence_refs=[]),
            evidence_refs=[],
        )


def test_m2_resource_world_gate_covers_publish_world_and_ingestion() -> None:
    report = run_m2_resource_world_gate()

    assert report.passed is True
    assert report.case_count >= 6
    assert {case.category for case in report.cases} == {
        "resource_publish",
        "world_proposal",
        "ingestion_boundary",
    }
    assert all(case.passed for case in report.cases)
