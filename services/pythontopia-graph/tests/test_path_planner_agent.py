"""Tests for the deterministic PathPlannerAgent."""

from __future__ import annotations

from fastapi.testclient import TestClient

from pythontopia_graph.agents.path_planner_agent import PathPlannerAgent
from pythontopia_graph.graph.path_graph import PathGraph
from pythontopia_graph.main import app
from pythontopia_graph.schemas.path import (
    PathPlannerRequest,
    PlannerClassroomQuest,
    PlannerKcSnapshot,
    PlannerProblemSubmission,
    PlannerProfileSnapshot,
    PlannerResourceUsage,
)


def _profile() -> PlannerProfileSnapshot:
    return PlannerProfileSnapshot(
        has_profile=True,
        major="CS",
        python_self_rated_level=2,
        days_since_last_profile_update=8,
    )


def _kcs(prefix: str = "PY43-KC", count: int = 8) -> list[PlannerKcSnapshot]:
    snapshots: list[PlannerKcSnapshot] = []
    for i in range(1, count + 1):
        snapshots.append(
            PlannerKcSnapshot(
                kc_code=f"{prefix}-{i:03d}",
                mastery_score=0.10 + (i % 3) * 0.05,
                confidence=0.20,
                unlocked=True,
            )
        )
    snapshots.append(
        PlannerKcSnapshot(
            kc_code=f"{prefix}-LOCK",
            mastery_score=0.0,
            confidence=0.0,
            unlocked=False,
        )
    )
    return snapshots


def _request(**overrides):  # type: ignore[no-untyped-def]
    base = {
        "user_id": 42,
        "pack_id": 43,
        "profile": _profile(),
        "kcs": _kcs(),
        "recent_resources": [],
        "recent_submissions": [],
        "classroom_quests": [],
    }
    base.update(overrides)
    return PathPlannerRequest(**base)


def test_plan_returns_seven_day_layout_with_focus_kcs() -> None:
    response = PathPlannerAgent().plan(_request())
    assert response.status == "SUCCESS"
    days = {node.day_index for node in response.nodes}
    assert days.issubset(set(range(1, 8)))
    assert any(node.node_type == "PROFILE_UPDATE" for node in response.nodes)
    assert any(node.node_type == "READ_RESOURCE" for node in response.nodes)
    assert any(node.node_type == "SOLVE_PROBLEM" for node in response.nodes)
    assert any(node.node_type == "PLANT_KC" for node in response.nodes)
    grouped: dict[int, int] = {}
    for node in response.nodes:
        grouped[node.day_index] = grouped.get(node.day_index, 0) + 1
    assert all(count <= 4 for count in grouped.values())


def test_plan_without_profile_returns_failed() -> None:
    request = _request(profile=PlannerProfileSnapshot(has_profile=False))
    response = PathPlannerAgent().plan(request)
    assert response.status == "FAILED"
    assert not response.grounding_result.passed
    assert response.nodes == []


def test_classroom_quest_kcs_are_prioritised_on_day_one() -> None:
    quest = PlannerClassroomQuest(
        classroom_quest_id=99,
        classroom_id=11,
        kc_codes=["PY43-KC-005"],
        node_type="SOLVE_PROBLEM",
        title="第二章 for 循环考核",
        due_date="2026-06-09",
    )
    response = PathPlannerAgent().plan(_request(classroom_quests=[quest]))
    day_one = [n for n in response.nodes if n.day_index == 1]
    assert any(
        node.source_classroom_quest_id == 99 and node.kc_code == "PY43-KC-005"
        for node in day_one
    )


def test_failed_submission_triggers_fix_bug_node() -> None:
    submissions = [
        PlannerProblemSubmission(problem_id=7, kc_code="PY43-KC-001", passed=False)
    ]
    response = PathPlannerAgent().plan(_request(recent_submissions=submissions))
    assert any(
        node.kc_code == "PY43-KC-001" and node.node_type == "FIX_BUG"
        for node in response.nodes
    )


def test_recent_resource_skips_read_node() -> None:
    resource_usage = [
        PlannerResourceUsage(
            kc_code="PY43-KC-001",
            type="HANDOUT",
            created_at="2026-05-20T08:00:00Z",
        )
    ]
    response = PathPlannerAgent().plan(_request(recent_resources=resource_usage))
    kc1_reads = [
        n
        for n in response.nodes
        if n.kc_code == "PY43-KC-001" and n.node_type == "READ_RESOURCE"
    ]
    assert kc1_reads == []


def test_signature_is_deterministic() -> None:
    response_a = PathPlannerAgent().plan(_request())
    response_b = PathPlannerAgent().plan(_request())
    assert response_a.deterministic_signature == response_b.deterministic_signature


def test_path_graph_blocks_safety_leaks() -> None:
    response = PathPlannerAgent().plan(_request())
    response.nodes[0].reason_summary = "提示：直接给完整答案就好"
    after_safety = PathGraph(agent=_StubAgent(response)).run(_request())
    assert not after_safety.safety_result.passed
    assert after_safety.status == "FAILED"


def test_http_route_returns_400_for_missing_profile() -> None:
    client = TestClient(app)
    payload = {
        "user_id": 1,
        "pack_id": 43,
        "profile": {
            "has_profile": False,
            "major": "OTHER",
            "python_self_rated_level": 2,
            "days_since_last_profile_update": 30,
        },
        "kcs": [],
    }
    response = client.post("/agents/path/plan", json=payload)
    assert response.status_code == 400
    assert response.json()["detail"]["code"] == "PROFILE_REQUIRED"


def test_http_route_returns_plan_for_valid_input() -> None:
    client = TestClient(app)
    payload = {
        "user_id": 1,
        "pack_id": 43,
        "profile": {
            "has_profile": True,
            "major": "CS",
            "python_self_rated_level": 3,
            "days_since_last_profile_update": 10,
        },
        "kcs": [
            {
                "kc_code": "PY43-KC-001",
                "mastery_score": 0.10,
                "confidence": 0.20,
                "unlocked": True,
                "prerequisite_codes": [],
            },
            {
                "kc_code": "PY43-KC-002",
                "mastery_score": 0.10,
                "confidence": 0.10,
                "unlocked": True,
                "prerequisite_codes": [],
            },
        ],
        "recent_resources": [],
        "recent_submissions": [],
        "classroom_quests": [],
    }
    response = client.post("/agents/path/plan", json=payload)
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "SUCCESS"
    assert len(body["nodes"]) >= 2


class _StubAgent:
    def __init__(self, response):
        self._response = response

    def plan(self, _request):  # noqa: ARG002
        return self._response
