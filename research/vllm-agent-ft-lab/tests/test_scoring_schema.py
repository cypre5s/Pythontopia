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


def test_v2_guide_semantic_gate_distinguishes_contract_from_event_selection() -> None:
    case = {
        "id": "guide-semantic",
        "agent": "GuideCoachAgent",
        "schema_version": "agent-schema-v2",
        "evidence_ids": ["page-1"],
        "expected_markers": [],
        "expected_event_intent": "READ",
    }
    payload = {
        "event_intent": "TRACE",
        "action_goal": "检查变量执行过程",
        "stop_condition": "学生说明一个检查点后继续。",
        "state_delta": {
            "hint_level": 1,
            "diagnosis_label": "misconception",
            "student_state_label": "scaffold",
        },
        "evidence_refs": ["page-1"],
        "confidence": 0.8,
        "reason_summary": "先检查执行过程。",
    }

    score = score_case_response(case, json.dumps(payload, ensure_ascii=False))

    assert score.contract_pass
    assert not score.semantic_valid
    assert not score.promotion_pass


def test_v2_grounding_rejects_extra_nested_invented_evidence() -> None:
    case = {
        "id": "reading-grounding",
        "agent": "ResourceDesignerAgent.reading",
        "schema_version": "agent-schema-v2",
        "evidence_ids": ["page-1"],
        "expected_markers": [],
    }
    payload = {
        "items": [
            {
                "summary_md": "第一项正确引用受控课件证据。",
                "evidence_refs": ["page-1"],
            },
            {
                "summary_md": "第二项夹带了不存在的课件证据。",
                "evidence_refs": ["page-invented"],
            },
        ]
    }

    score = score_case_response(case, json.dumps(payload, ensure_ascii=False))

    assert score.schema_valid
    assert not score.grounding_safe
    assert not score.contract_pass


def test_v2_grounding_allows_selecting_a_subset_of_allowed_evidence() -> None:
    case = {
        "id": "reading-subset",
        "agent": "ResourceDesignerAgent.reading",
        "schema_version": "agent-schema-v2",
        "requires_evidence": True,
        "evidence_ids": ["page-1-006", "page-1-009"],
    }
    payload = {
        "items": [
            {
                "summary_md": "变量名和值不同。",
                "evidence_refs": ["page-1-006"],
            }
        ]
    }

    score = score_case_response(case, json.dumps(payload, ensure_ascii=False))

    assert score.grounding_safe


def test_v2_code_lab_semantic_gate_rejects_non_python_code() -> None:
    case = {
        "id": "code-c-language",
        "agent": "ResourceDesignerAgent.code_lab",
        "schema_version": "agent-schema-v2",
        "requires_evidence": True,
        "evidence_ids": ["page-4-006"],
    }
    payload = {
        "walkthroughs": [
            {
                "title": "循环",
                "narrative": "观察 break 如何改变循环的执行路径。",
                "code": "while (i < 5) { i++; }",
                "expected_output": "",
                "common_mistakes": ["忘记更新 i"],
                "evidence_refs": ["page-4-006"],
            }
        ]
    }

    score = score_case_response(case, json.dumps(payload, ensure_ascii=False))

    assert score.contract_pass
    assert not score.semantic_valid
    assert "code_lab:0:invalid_python" in score.semantic_errors


def test_v21_code_plan_semantic_gate_scores_pattern_not_python_syntax() -> None:
    case = {
        "id": "code-plan-selection",
        "agent": "ResourceDesignerAgent.code_lab",
        "schema_version": "agent-schema-v2.1",
        "requires_evidence": True,
        "evidence_ids": ["page-4-006"],
        "expected_code_patterns": ["while_control"],
    }
    payload = {
        "walkthroughs": [
            {
                "title": "循环退出",
                "narrative": "观察 break 和 continue 如何改变循环执行过程。",
                "example_pattern": "conditional_branch",
                "observation_goal": "先预测每轮状态，再核对退出位置。",
                "common_mistakes": ["忘记更新循环变量"],
                "evidence_refs": ["page-4-006"],
            }
        ]
    }

    score = score_case_response(case, json.dumps(payload, ensure_ascii=False))

    assert score.contract_pass
    assert not score.semantic_valid
    assert "pattern:conditional_branch:not_in:while_control" in score.semantic_errors[0]
