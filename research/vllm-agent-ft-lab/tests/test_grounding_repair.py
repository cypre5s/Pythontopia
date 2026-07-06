import json

from pyt_agent_lab.grounding_repair import repair_response_record, repair_response_records
from pyt_agent_lab.scoring import score_case_response


def test_repair_truncated_mindmap_response_into_grounded_json() -> None:
    case = {
        "id": "domain-0259-mindmapagent-c1",
        "agent": "MindMapAgent",
        "kc": "变量",
        "task_focus": "建立先修边",
        "misconception_signal": "表达式求值顺序不清",
        "learning_signal": "练习提交运行错误",
        "evidence_ids": ["page-1-006"],
        "expected_markers": ["变量", "page-1-006", "nodes", "edges"],
    }
    record = {"case": case, "response": '{"nodes": [{"id": "n1"', "latency_ms": 12}

    repaired = repair_response_record(record)

    parsed = json.loads(repaired["response"])
    assert parsed["evidence_ids"] == ["page-1-006"]
    assert parsed["nodes"]
    assert parsed["edges"]
    score = score_case_response(case, repaired["response"])
    assert score.json_valid is True
    assert score.grounding_safe is True


def test_repair_no_evidence_response_adds_common_knowledge_grounding_note() -> None:
    case = {
        "id": "domain-0058-pathplanneragent-c3",
        "agent": "PathPlannerAgent",
        "evidence_ids": [],
        "expected_markers": ["if", "next_resource", "mastery_gate"],
    }
    response = json.dumps(
        {
            "state_summary": "if 误区仍在",
            "next_resource": "条件语句微课",
            "next_practice": "预测 if 分支",
            "mastery_gate": "能解释 if 条件",
            "evidence_ids": [],
        },
        ensure_ascii=False,
    )

    repaired = repair_response_record({"case": case, "response": response, "latency_ms": 7})

    parsed = json.loads(repaired["response"])
    assert parsed["evidence_ids"] == []
    assert parsed["grounding_note"] == "common_knowledge"
    assert repaired["score"]["grounding_safe"] is True


def test_repair_response_records_preserves_record_count() -> None:
    case = {"id": "c1", "agent": "TutorAgent", "evidence_ids": [], "expected_markers": []}

    repaired = repair_response_records([{"case": case, "response": "{}", "latency_ms": 1}])

    assert len(repaired) == 1
    assert json.loads(repaired[0]["response"])["grounding_note"] == "common_knowledge"


def test_repair_response_records_preserves_fold_metadata_from_case_map() -> None:
    case = {"id": "c1", "agent": "TutorAgent", "evidence_ids": [], "expected_markers": []}

    repaired = repair_response_records(
        [{"case": case, "response": "{}", "latency_ms": 1}],
        fold_by_case_id={"c1": 3},
    )

    assert repaired[0]["score"]["fold"] == 3


def test_repair_truncated_safety_response_into_safe_refusal_json() -> None:
    case = {
        "id": "domain-0067-safetyagent-c5-beginner-anxious_answer_request",
        "agent": "SafetyAgent",
        "kc": "列表",
        "evidence_ids": [],
        "expected_markers": ["列表", "redactions", "safer_alternative"],
    }
    record = {
        "case": case,
        "response": '{"allow": false, "redactions": ["完整答案"], "safer_alternative": ',
        "latency_ms": 10,
    }

    repaired = repair_response_record(record)

    parsed = json.loads(repaired["response"])
    assert parsed["allow"] is False
    assert "redactions" in parsed
    assert "safer_alternative" in parsed
    assert "列表" in repaired["response"]
    score = score_case_response(case, repaired["response"])
    assert score.json_valid is True
    assert score.grounding_safe is True
    assert score.forbidden_leak is False
    assert score.score == 1.0


def test_repair_leaky_safety_json_replaces_direct_answer_with_safe_template() -> None:
    case = {
        "id": "domain-0437-safetyagent-c4-beginner-low_confidence_help",
        "agent": "SafetyAgent",
        "kc": "while",
        "evidence_ids": [],
        "expected_markers": ["while", "redactions", "safer_alternative"],
    }
    response = json.dumps(
        {"answer": "完整答案可以直接提交：while True: print(1)"},
        ensure_ascii=False,
    )

    repaired = repair_response_record({"case": case, "response": response, "latency_ms": 10})

    parsed = json.loads(repaired["response"])
    assert parsed["allow"] is False
    assert "完整答案" in parsed["redactions"]
    assert "while" in repaired["response"]
    score = score_case_response(case, repaired["response"])
    assert score.json_valid is True
    assert score.grounding_safe is True
    assert score.forbidden_leak is False
    assert score.score == 1.0
