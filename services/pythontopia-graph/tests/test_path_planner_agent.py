"""PathPlannerAgent 确定性规划单元测试。"""

from __future__ import annotations

from fastapi.testclient import TestClient

from pythontopia_graph.agents.path_planner_agent import PathPlannerAgent, _CandidateKc
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
    assert all(node.node_type != "PLANT_KC" for node in response.nodes)
    grouped: dict[int, int] = {}
    for node in response.nodes:
        grouped[node.day_index] = grouped.get(node.day_index, 0) + 1
    assert all(count <= 4 for count in grouped.values())


def test_plan_without_profile_uses_cold_start_defaults() -> None:
    request = _request(profile=PlannerProfileSnapshot(has_profile=False))
    response = PathPlannerAgent().plan(request)
    assert response.status == "SUCCESS"
    assert response.grounding_result.passed
    assert response.nodes
    assert any(node.node_type == "READ_RESOURCE" for node in response.nodes)
    assert any(node.node_type == "SOLVE_PROBLEM" for node in response.nodes)
    assert "缺少画像" not in response.goal_summary


def test_plan_without_unlocked_kcs_points_to_the_mainline() -> None:
    locked_kcs = [
        PlannerKcSnapshot(
            kc_code="PY43-KC-001",
            mastery_score=0.0,
            confidence=0.0,
            unlocked=False,
        )
    ]

    response = PathPlannerAgent().plan(_request(kcs=locked_kcs))

    assert response.status == "FAILED"
    assert "主线地图" in response.goal_summary
    assert "知识田" not in response.goal_summary


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


def test_topological_order_places_prerequisites_first() -> None:
    agent = PathPlannerAgent()
    dependent_a = _CandidateKc("PY43-KC-003", 0.1, 1, ("PY43-KC-001",))
    dependent_b = _CandidateKc("PY43-KC-002", 0.1, 1, ("PY43-KC-001",))
    prereq = _CandidateKc("PY43-KC-001", 0.1, 1, ())
    ordered = agent._topological_order([dependent_a, dependent_b, prereq])
    codes = [c.kc_code for c in ordered]
    assert codes.index("PY43-KC-001") < codes.index("PY43-KC-002")
    assert codes.index("PY43-KC-001") < codes.index("PY43-KC-003")


def test_topological_order_no_prerequisites_preserves_input() -> None:
    agent = PathPlannerAgent()
    items = [
        _CandidateKc("PY43-KC-005", 0.1, 1, ()),
        _CandidateKc("PY43-KC-002", 0.1, 1, ()),
        _CandidateKc("PY43-KC-009", 0.1, 1, ()),
    ]
    ordered = agent._topological_order(items)
    assert [c.kc_code for c in ordered] == ["PY43-KC-005", "PY43-KC-002", "PY43-KC-009"]


def test_prerequisite_chain_schedules_prereq_no_later_than_dependent() -> None:
    # KC-001 是 KC-002/003 的先修；三者都低掌握度会被选入聚焦集合，先修应排到不晚于依赖的天。
    kcs = [
        PlannerKcSnapshot(kc_code="PY43-KC-001", mastery_score=0.1, unlocked=True),
        PlannerKcSnapshot(
            kc_code="PY43-KC-002", mastery_score=0.1, unlocked=True,
            prerequisite_codes=["PY43-KC-001"],
        ),
        PlannerKcSnapshot(
            kc_code="PY43-KC-003", mastery_score=0.1, unlocked=True,
            prerequisite_codes=["PY43-KC-001"],
        ),
    ]
    response = PathPlannerAgent().plan(_request(kcs=kcs))
    first_day = {}
    for node in response.nodes:
        if node.kc_code not in first_day:
            first_day[node.kc_code] = node.day_index
    assert first_day["PY43-KC-001"] <= first_day["PY43-KC-002"]
    assert first_day["PY43-KC-001"] <= first_day["PY43-KC-003"]


def test_signature_is_deterministic() -> None:
    response_a = PathPlannerAgent().plan(_request())
    response_b = PathPlannerAgent().plan(_request())
    assert response_a.deterministic_signature == response_b.deterministic_signature


def test_error_pattern_kc_is_frontloaded_to_day_one() -> None:
    # KC-008 掌握度不算最低，默认不会进入聚焦集合；被 error_pattern 标记后应前置到第 1 天。
    flagged = "PY43-KC-008"
    baseline = PathPlannerAgent().plan(_request())
    boosted = PathPlannerAgent().plan(_request(error_pattern_kcs=[flagged]))

    baseline_days = [
        n.day_index
        for n in baseline.nodes
        if n.kc_code == flagged and n.node_type in ("SOLVE_PROBLEM", "FIX_BUG")
    ]
    boosted_days = [
        n.day_index
        for n in boosted.nodes
        if n.kc_code == flagged and n.node_type in ("SOLVE_PROBLEM", "FIX_BUG")
    ]
    assert boosted_days, "被标记的高频坑应获得练习节点"
    assert min(boosted_days) == 1
    baseline_earliest = min(baseline_days) if baseline_days else 99
    assert min(boosted_days) < baseline_earliest


def test_no_error_pattern_preserves_original_plan() -> None:
    # 不传 / 传空 error_pattern_kcs，路径完全一致（回归不破坏既有排序）。
    default_plan = PathPlannerAgent().plan(_request())
    empty_plan = PathPlannerAgent().plan(_request(error_pattern_kcs=[]))
    assert default_plan.deterministic_signature == empty_plan.deterministic_signature


def test_error_pattern_changes_signature_when_flag_present() -> None:
    default_plan = PathPlannerAgent().plan(_request())
    boosted_plan = PathPlannerAgent().plan(_request(error_pattern_kcs=["PY43-KC-008"]))
    assert default_plan.deterministic_signature != boosted_plan.deterministic_signature


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
