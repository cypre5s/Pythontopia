import json

from pyt_agent_lab.scoring import score_case_response
from pyt_agent_lab.v2_preferences import (
    ADAPTER_ELIGIBLE_AGENTS,
    RESOURCE_IR_AGENTS,
    build_v2_eval_cases,
    build_v2_preference_dataset,
    split_and_write_v2_preferences,
)


def _cases(per_agent: int = 60) -> list[dict[str, object]]:
    rows = []
    for agent in ADAPTER_ELIGIBLE_AGENTS:
        for index in range(per_agent):
            rows.append(
                {
                    "id": f"{agent}-{index}",
                    "source_case_id": f"source-{agent}-{index}",
                    "agent": agent,
                    "system": "legacy",
                    "user": f"请围绕列表处理一次练习请求 {index}",
                    "kc": "列表",
                    "chapter": 5,
                    "evidence_ids": [f"page-5-{index:03d}"],
                    "requires_evidence": True,
                    "misconception_signal": "索引从 1 开始",
                }
            )
    return rows


def test_build_v2_preferences_balances_semantic_axes_and_agents() -> None:
    rows = build_v2_preference_dataset(
        source_cases=_cases(), protected_cases=[], target_count=200, seed=7
    )

    router_rows = [
        row for row in rows if row["metadata"]["agent"] == "IntentRouterAgent"
    ]
    assert router_rows

    assert len(rows) == 200
    assert {row["metadata"]["agent"] for row in rows} == set(ADAPTER_ELIGIBLE_AGENTS)
    assert {row["metadata"]["preference_axis"] for row in rows} == {
        "legacy_contract",
        "schema_constraint",
        "wrong_decision",
        "weak_semantics",
        "unbound_evidence",
        "safety_leak",
    }
    first = rows[0]
    case = {
        **_cases()[0],
        "id": "check",
        "schema_version": "agent-schema-v2",
        "expected_markers": [],
    }
    assert score_case_response(case, first["chosen"]).schema_valid


def test_guide_schema_constraint_preferences_capture_observed_raw_failure() -> None:
    source_cases = _cases(per_agent=320)
    intent_tokens = (
        "阅读页面", "复盘概念", "调试执行", "拆成步骤提示", "安排练习", "追问解释", "提交检查"
    )
    guide_index = 0
    for case in source_cases:
        if case["agent"] == "GuideCoachAgent":
            case["task_focus"] = intent_tokens[guide_index % len(intent_tokens)]
            case["learning_signal"] = ""
            case["misconception_signal"] = "概念边界"
            guide_index += 1
    rows = build_v2_preference_dataset(
        source_cases=source_cases,
        protected_cases=[],
        target_count=300,
        seed=13,
        agents=("GuideCoachAgent",),
    )
    row = next(item for item in rows if item["metadata"]["preference_axis"] == "schema_constraint")
    source = next(
        item
        for item in source_cases
        if item["id"] == row["metadata"]["source_case_id"]
    )
    case = build_v2_eval_cases([source], agents=("GuideCoachAgent",))[0]

    assert score_case_response(case, row["chosen"]).contract_pass
    rejected = score_case_response(case, row["rejected"])
    assert not rejected.schema_valid
    assert "event_intent" in rejected.schema_type_errors
    assert "state_delta.hint_level" in rejected.schema_type_errors


def test_guide_preferences_balance_semantic_intents_and_rejection_axes() -> None:
    cases = _cases(per_agent=300)
    guide_cases = [item for item in cases if item["agent"] == "GuideCoachAgent"]
    intent_tokens = (
        "阅读页面", "复盘概念", "调试执行", "拆成步骤提示", "安排练习", "追问解释", "提交检查"
    )
    for index, case in enumerate(guide_cases):
        case["task_focus"] = intent_tokens[index % len(intent_tokens)]
        case["learning_signal"] = ""
        case["misconception_signal"] = "概念边界"
    rows = build_v2_preference_dataset(
        source_cases=cases,
        protected_cases=[],
        target_count=252,
        seed=17,
        agents=("GuideCoachAgent",),
    )

    semantic_counts = {}
    axis_counts = {}
    for row in rows:
        label = row["metadata"]["semantic_label"]
        axis = row["metadata"]["preference_axis"]
        semantic_counts[label] = semantic_counts.get(label, 0) + 1
        axis_counts[axis] = axis_counts.get(axis, 0) + 1

    assert set(semantic_counts.values()) == {36}
    assert set(axis_counts.values()) == {42}


def test_build_v2_eval_cases_keeps_only_runtime_adapter_agents() -> None:
    source = _cases(per_agent=1) + [
        {"id": "path-1", "agent": "PathPlannerAgent", "system": "x", "user": "x"}
    ]

    rows = build_v2_eval_cases(source)

    assert len(rows) == 4
    assert all(row["schema_version"] == "agent-schema-v2" for row in rows)


def test_resource_designer_cases_expand_to_runtime_ir_subagents() -> None:
    source = [
        {
            "id": "resource-1",
            "agent": "ResourceDesignerAgent",
            "system": "legacy resource prompt",
            "user": "请生成 input 学习资源",
            "kc": "input 函数",
            "chapter": 1,
            "evidence_ids": ["page-101"],
            "requires_evidence": True,
            "misconception_signal": "误以为 input 返回整数",
        }
    ]

    rows = build_v2_eval_cases(source, agents=RESOURCE_IR_AGENTS)

    assert {row["agent"] for row in rows} == set(RESOURCE_IR_AGENTS)
    assert len({row["id"] for row in rows}) == 4
    assert all(row["source_case_id"] == "resource-1" for row in rows)
    assert all("产品 DTO" in row["system"] for row in rows)


def test_resource_ir_preferences_are_contract_valid_and_balanced() -> None:
    source = [
        {
            "id": f"resource-{index:03d}",
            "agent": "ResourceDesignerAgent",
            "system": "legacy resource prompt",
            "user": f"请生成 input 学习资源 {index}",
            "kc": "input 函数",
            "chapter": 1,
            "evidence_ids": [f"page-{100 + index}"],
            "requires_evidence": True,
            "misconception_signal": "误以为 input 返回整数",
        }
        for index in range(80)
    ]

    rows = build_v2_preference_dataset(
        source_cases=source,
        protected_cases=[],
        target_count=240,
        seed=23,
        agents=RESOURCE_IR_AGENTS,
    )

    assert len(rows) == 240
    assert {
        row["metadata"]["agent"] for row in rows
    } == set(RESOURCE_IR_AGENTS)
    counts = {
        agent: sum(row["metadata"]["agent"] == agent for row in rows)
        for agent in RESOURCE_IR_AGENTS
    }
    assert set(counts.values()) == {60}
    assert all(row["metadata"]["chosen_contract_pass"] for row in rows)


def test_resource_failure_directed_preferences_match_observed_categories() -> None:
    source = [
        {
            "id": f"resource-failure-{index:03d}",
            "agent": "ResourceDesignerAgent",
            "system": "legacy resource prompt",
            "user": f"请生成列表学习资源 {index}",
            "kc": "列表",
            "chapter": 5,
            "evidence_ids": [f"page-5-{100 + index}"],
            "requires_evidence": True,
            "misconception_signal": "误以为索引从 1 开始",
        }
        for index in range(80)
    ]

    rows = build_v2_preference_dataset(
        source_cases=source,
        protected_cases=[],
        target_count=240,
        seed=29,
        agents=RESOURCE_IR_AGENTS,
        failure_directed=True,
    )
    axes_by_agent = {
        agent: {
            row["metadata"]["preference_axis"]
            for row in rows
            if row["metadata"]["agent"] == agent
        }
        for agent in RESOURCE_IR_AGENTS
    }

    assert axes_by_agent["ResourceDesignerAgent.handout"] == {
        "non_json_yaml",
        "missing_sections",
        "legacy_contract",
        "weak_semantics",
        "unbound_evidence",
        "safety_leak",
    }
    assert {"non_json_prose", "too_few_items", "missing_evidence_refs"} <= axes_by_agent[
        "ResourceDesignerAgent.reading"
    ]
    assert {"empty_code", "scalar_common_mistakes"} <= axes_by_agent[
        "ResourceDesignerAgent.code_lab"
    ]
    assert {"too_few_scenes", "invalid_pacing"} <= axes_by_agent[
        "ResourceDesignerAgent.storyboard"
    ]
    assert all(row["metadata"]["failure_directed"] for row in rows)
    assert all(
        not row["metadata"]["rejected_contract_pass"]
        for row in rows
        if row["metadata"]["preference_axis"] != "weak_semantics"
    )


def test_v2_preferences_exclude_protected_lineage() -> None:
    source = _cases()
    protected = [dict(source[0])]
    rows = build_v2_preference_dataset(
        source_cases=source, protected_cases=protected, target_count=200, seed=9
    )

    assert protected[0]["id"] not in {
        row["metadata"]["source_case_id"] for row in rows
    }


def test_v2_manifest_records_hashes(tmp_path) -> None:
    source_path = tmp_path / "source.jsonl"
    protected_path = tmp_path / "protected.jsonl"
    source_path.write_text("{}\n", encoding="utf-8")
    protected_path.write_text("{}\n", encoding="utf-8")
    rows = build_v2_preference_dataset(
        source_cases=_cases(), protected_cases=[], target_count=200, seed=11
    )

    manifest = split_and_write_v2_preferences(
        rows,
        tmp_path / "out",
        heldout_ratio=0.1,
        seed=11,
        source_path=source_path,
        protected_paths=[protected_path],
    )

    assert manifest["train_count"] == 180
    assert manifest["heldout_count"] == 20
    assert len(manifest["train_sha256"]) == 64
    assert json.loads((tmp_path / "out" / "manifest.json").read_text())["schema_version"] == "agent-schema-v2"
