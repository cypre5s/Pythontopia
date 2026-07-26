import json

from pyt_agent_lab.fallback import build_fallback_response
from pyt_agent_lab.scoring import score_case_response


# ---------------------------------------------------------------------------
# Common case factory
# ---------------------------------------------------------------------------


def _case(agent: str, **kwargs: object) -> dict:
    return {
        "id": f"test-{agent.lower()}",
        "agent": agent,
        "kc": "变量",
        "learning_signal": "学生不理解变量赋值",
        "misconception_signal": "以为等号是数学相等",
        "task_focus": "解释变量概念",
        "expected_markers": ["explanation"],
        "evidence_ids": kwargs.get("evidence_ids", []),
        **kwargs,
    }


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------


def test_fallback_returns_valid_json() -> None:
    for agent in [
        "SafetyAgent",
        "MindMapAgent",
        "PathPlannerAgent",
        "PracticeAgent",
        "TutorAgent",
        "ResourceDesignerAgent",
        "IntentRouterAgent",
        "GuideCoachAgent",
        "DebugAgent",
        "GroundingCriticAgent",
    ]:
        response = build_fallback_response(_case(agent), agent, ["json_invalid"])
        parsed = json.loads(response)
        assert isinstance(parsed, dict), f"{agent} fallback should be valid JSON dict"


def test_fallback_includes_grounding() -> None:
    case = _case("TutorAgent", evidence_ids=["page-1-006", "page-2-012"])
    response = build_fallback_response(case, "TutorAgent", ["grounding_failed"])
    parsed = json.loads(response)
    assert parsed.get("evidence_ids") == ["page-1-006", "page-2-012"]


def test_fallback_common_knowledge_when_no_evidence() -> None:
    case = _case("TutorAgent", evidence_ids=[])
    response = build_fallback_response(case, "TutorAgent", ["grounding_failed"])
    parsed = json.loads(response)
    assert parsed.get("grounding_note") == "common_knowledge"
    assert parsed.get("evidence_ids") == []


def test_fallback_safety_agent_blocks() -> None:
    case = _case("SafetyAgent")
    response = build_fallback_response(case, "SafetyAgent", ["forbidden_leak"])
    parsed = json.loads(response)
    assert parsed.get("allow") is False
    assert "redactions" in parsed
    assert "safer_alternative" in parsed
    assert "safety_flags" in parsed


def test_fallback_mindmap_has_nodes_and_edges() -> None:
    case = _case("MindMapAgent")
    response = build_fallback_response(case, "MindMapAgent", ["json_invalid"])
    parsed = json.loads(response)
    assert "nodes" in parsed
    assert "edges" in parsed
    assert len(parsed["nodes"]) >= 3
    assert len(parsed["edges"]) >= 2


def test_fallback_path_planner_has_gate() -> None:
    case = _case("PathPlannerAgent")
    response = build_fallback_response(case, "PathPlannerAgent", ["json_invalid"])
    parsed = json.loads(response)
    assert "mastery_gate" in parsed
    assert "next_resource" in parsed
    assert "next_practice" in parsed


def test_fallback_practice_has_problems() -> None:
    case = _case("PracticeAgent")
    response = build_fallback_response(case, "PracticeAgent", ["incomplete_markers"])
    parsed = json.loads(response)
    assert "misconception" in parsed
    assert "hint_steps" in parsed
    assert len(parsed["hint_steps"]) > 0
    assert "blocked_content" in parsed


def test_fallback_debug_has_diagnosis() -> None:
    case = _case("DebugAgent")
    response = build_fallback_response(case, "DebugAgent", ["json_invalid"])
    parsed = json.loads(response)
    assert "bug_type" in parsed
    assert "minimal_repro_hint" in parsed
    assert "next_probe" in parsed


def test_fallback_unknown_agent_uses_generic() -> None:
    case = _case("UnknownAgent")
    response = build_fallback_response(case, "UnknownAgent", ["json_invalid"])
    parsed = json.loads(response)
    assert parsed.get("guard_fallback") is True
    assert "guard_failures" in parsed


def test_fallback_preserves_failure_info_in_generic() -> None:
    case = _case("UnknownAgent")
    failures = ["json_invalid", "grounding_failed"]
    response = build_fallback_response(case, "UnknownAgent", failures)
    parsed = json.loads(response)
    assert parsed["guard_failures"] == failures


def test_fallback_all_agents_produce_deterministic_output() -> None:
    """Same inputs should produce identical fallback responses."""
    agents = ["TutorAgent", "SafetyAgent", "MindMapAgent"]
    for agent in agents:
        case = _case(agent)
        r1 = build_fallback_response(case, agent, ["json_invalid"])
        r2 = build_fallback_response(case, agent, ["json_invalid"])
        assert r1 == r2, f"{agent} fallback should be deterministic"


def test_v2_semantic_fallbacks_pass_contract_for_adapter_agents() -> None:
    agents = (
        "GuideCoachAgent",
        "PracticeAgent",
        "MindMapAgent",
        "IntentRouterAgent",
        "ResourceDesignerAgent.handout",
        "ResourceDesignerAgent.reading",
        "ResourceDesignerAgent.code_lab",
        "ResourceDesignerAgent.storyboard",
    )
    for agent in agents:
        evidence_ids = [] if agent == "IntentRouterAgent" else ["page-1"]
        case = {
            **_case(agent, evidence_ids=evidence_ids),
            "id": f"v2-{agent}",
            "schema_version": "agent-schema-v2",
            "expected_markers": [],
        }
        response = build_fallback_response(case, agent, ["schema_invalid"])

        assert score_case_response(case, response).contract_pass
