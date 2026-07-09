import json

from pyt_agent_lab.scoring import AGENT_SCHEMA, score_case_response


def test_score_case_response_marks_schema_invalid_when_required_key_missing() -> None:
    case = {
        "id": "schema-tutor-missing-key",
        "agent": "TutorAgent",
        "evidence_ids": ["page-2-014"],
        "expected_markers": [],
    }
    response = json.dumps(
        {
            "diagnosis": "range 右端开区间不清",
            "next_hint": "先预测 range(1, 4) 会出现几个数",
            "evidence_ids": ["page-2-014"],
        },
        ensure_ascii=False,
    )

    score = score_case_response(case, response)

    assert score.json_valid is True
    assert score.schema_valid is False
    assert score.missing_schema_keys == ("safety_flags",)
    assert score.as_row()["schema_valid"] is False


def test_agent_schema_covers_all_eval_pack_agents() -> None:
    assert set(AGENT_SCHEMA) == {
        "TutorAgent",
        "PracticeAgent",
        "ResourceDesignerAgent",
        "IntentRouterAgent",
        "GuideCoachAgent",
        "DebugAgent",
        "GroundingCriticAgent",
        "SafetyAgent",
        "PathPlannerAgent",
        "MindMapAgent",
    }
