from __future__ import annotations

from fastapi.testclient import TestClient

from pythontopia_graph.main import app
from pythontopia_graph.runtime.world_state_loop import (
    LearnerWorldSnapshot,
    LearningActionEvidence,
    WorldQuestHistory,
    WorldStateLoop,
    WorldStateLoopRequest,
)
from pythontopia_graph.schemas.loop_contract import LoopResult
from pythontopia_graph.schemas.world_loop import EvidenceRef


def ev(type_: str = "submission_id", id_: int | str = 301) -> EvidenceRef:
    return EvidenceRef(type=type_, id=id_, label=f"{type_}:{id_}")


def learner(*, mastery: float = 0.72) -> LearnerWorldSnapshot:
    return LearnerWorldSnapshot(
        user_id=7,
        map_key="town_hub",
        current_stage_id="chapter-2-stage-3",
        focus_kcs=["PY43-KC-016"],
        mastery_by_kc={"PY43-KC-016": mastery},
        active_quest_ids=["quest-input-formal-submit"],
    )


def history() -> WorldQuestHistory:
    return WorldQuestHistory(
        active_quest_ids=["quest-input-formal-submit"],
        completed_quest_ids=["quest-input-read"],
        last_completed_quest_id="quest-input-read",
    )


def action(
    *,
    event_type: str = "submission.passed",
    evidence_refs: list[EvidenceRef] | None = None,
    payload: dict[str, object] | None = None,
) -> LearningActionEvidence:
    return LearningActionEvidence(
        event_type=event_type,
        kc_code="PY43-KC-016",
        route="/training",
        evidence_refs=evidence_refs
        if evidence_refs is not None
        else [
            ev("submission_id", 301),
            ev("kc_mastery_delta", "PY43-KC-016:+0.08"),
        ],
        payload=payload or {"problem_id": 301, "quest_id": "quest-input-formal-submit"},
    )


def request(
    *,
    learning_action: LearningActionEvidence | None = None,
    reward_preferences: list[str] | None = None,
    previous_world_state: dict[str, object] | None = None,
) -> WorldStateLoopRequest:
    return WorldStateLoopRequest(
        learner=learner(),
        quest_history=history(),
        action=learning_action or action(),
        previous_world_state=previous_world_state
        if previous_world_state is not None
        else {
            "town_hub.training_gate.flash": "PY43-KC-015",
            "town_hub.notice_board.highlight": "PY43-KC-015",
        },
        reward_preferences=reward_preferences or ["badge", "mentor_trust"],
        policy_version="agentic_loop_v1",
        trace_id="trace-phase6",
    )


def test_formal_pass_runs_state_to_new_quest_world_loop() -> None:
    result = WorldStateLoop().run(request())

    assert [step.state for step in result.steps] == [
        "state",
        "quest",
        "action",
        "reward",
        "world_update",
        "new_quest",
    ]
    assert result.status == "SUCCESS"
    assert result.world_state.map_key == "town_hub"
    assert result.action_result.action_kind == "formal_submission_passed"
    assert result.action_result.completed_required_evidence is True
    assert result.narrative_reward.reward_type in {"badge", "mentor_trust"}
    assert result.world_proposal is not None
    assert result.world_proposal.has_complete_evidence_binding() is True
    assert result.world_proposal.reward_plan.mastery_delta > 0
    assert result.world_proposal.new_quests[0].node_type == "SOLVE_PROBLEM"
    assert result.world_proposal.new_quests[0].required_evidence_type == "submission"
    assert all(
        patch.key.startswith("town_hub.") for patch in result.world_proposal.state_patch
    )
    assert "coin" not in result.world_proposal.model_dump_json().lower()
    assert "shop" not in result.world_proposal.model_dump_json().lower()
    assert result.loop_result.loop_type == "WORLD"
    assert isinstance(LoopResult.model_validate(result.loop_result.model_dump()), LoopResult)


def test_missing_evidence_rejects_without_fabricating_world_patch() -> None:
    result = WorldStateLoop().run(request(learning_action=action(evidence_refs=[])))

    assert result.status == "REJECTED"
    assert result.stop_reason == "missing_evidence"
    assert result.world_proposal is None
    assert result.rollback_patch == []
    assert result.next_actions == ["hold:world-patch-review"]
    assert result.loop_result.status == "REJECTED"


def test_forbidden_economy_reward_is_rejected() -> None:
    result = WorldStateLoop().run(request(reward_preferences=["gold", "badge"]))

    assert result.status == "REJECTED"
    assert result.stop_reason == "forbidden_economy_reward"
    assert result.world_proposal is None
    assert result.next_actions == ["hold:world-patch-review"]
    assert "gold" in result.loop_result.result["forbidden_terms"]


def test_rollback_patch_restores_previous_world_values() -> None:
    result = WorldStateLoop().run(
        request(
            previous_world_state={
                "town_hub.training_gate.flash": "PY43-KC-010",
                "town_hub.notice_board.highlight": "PY43-KC-011",
            }
        )
    )

    assert result.rollback_patch
    restored = {patch.key: patch.previous_value for patch in result.rollback_patch}
    assert restored["town_hub.training_gate.flash"] == "PY43-KC-010"
    assert restored["town_hub.notice_board.highlight"] == "PY43-KC-011"
    assert all(patch.evidence_refs for patch in result.rollback_patch)


def test_page_read_action_creates_submission_followup_without_mastery_reward() -> None:
    page_read = LearningActionEvidence(
        event_type="page.read",
        kc_code="PY43-KC-016",
        route="/library",
        evidence_refs=[ev("page_read_id", "read-118")],
        payload={"page_id": 118, "seconds": 90},
    )

    result = WorldStateLoop().run(request(learning_action=page_read))

    assert result.status == "SUCCESS"
    assert result.action_result.action_kind == "page_read_confirmed"
    assert result.world_proposal is not None
    assert result.world_proposal.reward_plan.mastery_delta == 0
    assert result.world_proposal.new_quests[0].node_type == "SOLVE_PROBLEM"
    assert result.world_proposal.new_quests[0].deep_link_route == "/training"


def test_route_returns_api_visible_world_loop_state(monkeypatch) -> None:
    monkeypatch.setenv("PYT_INTERNAL_AUTH_OPTIONAL", "1")
    client = TestClient(app)

    response = client.post(
        "/runtime/world-state-runs",
        json=request().model_dump(mode="json"),
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "SUCCESS"
    assert body["world_state"]["map_key"] == "town_hub"
    assert body["world_proposal"]["new_quests"][0]["kc_code"] == "PY43-KC-016"
    assert body["loop_result"]["trace_id"] == "trace-phase6"
