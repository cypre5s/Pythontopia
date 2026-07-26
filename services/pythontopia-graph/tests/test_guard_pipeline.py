from pythontopia_graph.contracts.semantic_ir import HandoutSemanticIR, PracticeSemanticIR
from pythontopia_graph.llm.guard_pipeline import GuardMetrics, GuardPipeline


def _payload(evidence_ref: str = "page-1") -> dict[str, object]:
    return {
        "problems": [
            {
                "title": "求和",
                "statement": "读取两个整数并输出它们的和。",
                "hints": ["先分别读取。"],
                "sample_input": "1 2",
                "sample_output": "3",
            }
        ],
        "evidence_refs": [evidence_ref],
    }


def test_guard_exposes_contract_grounding_and_safety_stages() -> None:
    result = GuardPipeline().evaluate(
        _payload(),
        {
            "json_schema": PracticeSemanticIR.model_json_schema(),
            "evidence_ids": ["page-1"],
            "evidence_fields": ["evidence_refs"],
        },
    )

    assert result.passed
    assert [stage.stage for stage in result.stages] == [
        "contract",
        "grounding",
        "safety",
    ]


def test_guard_rejects_unknown_evidence_after_contract_passes() -> None:
    result = GuardPipeline().evaluate(
        _payload("page-invented"),
        {
            "json_schema": PracticeSemanticIR.model_json_schema(),
            "evidence_ids": ["page-1"],
            "evidence_fields": ["evidence_refs"],
        },
    )

    assert not result.passed
    assert result.stages[-1].stage == "grounding"


def test_guard_checks_nested_wildcard_evidence_fields() -> None:
    payload = {
        "title": "输入讲义",
        "summary": "输入基础",
        "sections": [
            {
                "heading": "input",
                "paragraphs": ["input 会读取一行。"],
                "code_snippets": [],
                "evidence_refs": ["page-invented"],
            }
        ],
    }
    result = GuardPipeline().evaluate(
        payload,
        {
            "json_schema": HandoutSemanticIR.model_json_schema(),
            "evidence_ids": ["page-1"],
            "evidence_fields": ["sections.*.evidence_refs"],
        },
    )

    assert result.passed is False
    assert result.stages[-1].reason == "unknown_evidence:page-invented"


def test_guard_rejects_nested_pattern_violation_before_agent_parse() -> None:
    schema = {
        "$defs": {
            "Node": {
                "type": "object",
                "properties": {
                    "node_id": {
                        "type": "string",
                        "pattern": "^[A-Za-z][A-Za-z0-9_]{0,31}$",
                    }
                },
                "required": ["node_id"],
            }
        },
        "type": "object",
        "properties": {
            "nodes": {"type": "array", "items": {"$ref": "#/$defs/Node"}}
        },
        "required": ["nodes"],
    }

    result = GuardPipeline().evaluate(
        {"nodes": [{"node_id": "node-1"}]},
        {"agent": "MindMapAgent", "json_schema": schema},
    )

    assert result.passed is False
    assert result.stages[-1].reason == "nodes.0.node_id.pattern_mismatch"


def test_guard_metrics_keeps_repair_and_fallback_visible() -> None:
    metrics = GuardMetrics()
    result = GuardPipeline().evaluate(None, None)
    metrics.record_attempt()
    metrics.record_constrained_attempt(evidence_bound=True)
    metrics.record(result)
    metrics.record_fallback()

    snapshot = metrics.snapshot()
    assert snapshot["fallback_rate"] == 1.0
    assert snapshot["guard_repair_rate"] == 0.0
    assert snapshot["schema_constrained_rate"] == 1.0
    assert snapshot["evidence_bound_rate"] == 1.0


def test_guard_allows_empty_optional_evidence_collection() -> None:
    schema = {
        "type": "object",
        "properties": {
            "observations": {"type": "array", "items": {"type": "object"}}
        },
        "required": ["observations"],
    }
    result = GuardPipeline().evaluate(
        {"observations": []},
        {
            "json_schema": schema,
            "evidence_ids": ["event-10"],
            "evidence_fields": ["observations.*.evidence_refs"],
            "allow_empty_evidence_collection": True,
        },
    )

    assert result.passed


def test_guard_pipeline_fails_closed_on_semantic_validator_error() -> None:
    schema = {
        "type": "object",
        "properties": {"code": {"type": "string"}},
        "required": ["code"],
    }

    result = GuardPipeline().evaluate(
        {"code": "while (i < 5) { i++; }"},
        {
            "json_schema": schema,
            "semantic_validator": lambda payload: "invalid_python",
        },
    )

    assert not result.passed
    assert result.stages[-1].stage == "semantic"
    assert result.stages[-1].reason == "invalid_python"
