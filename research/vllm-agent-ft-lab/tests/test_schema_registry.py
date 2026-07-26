import json
from pathlib import Path

import pytest

from pyt_agent_lab.schema_registry import (
    SCHEMA_VERSION_V1,
    SchemaRegistryError,
    load_agent_schemas,
    schema_for_agent,
    validate_object,
)
from pyt_agent_lab.scoring import AGENT_SCHEMA


def test_v1_registry_is_the_source_for_all_legacy_agent_contracts() -> None:
    schemas = load_agent_schemas()

    assert set(schemas) == set(AGENT_SCHEMA)
    assert schemas["GuideCoachAgent"]["x-contract-status"] == "frozen-legacy-benchmark"
    assert schemas["PracticeAgent"]["properties"]["blocked_content"]["type"] == "array"


def test_product_v2_registry_is_explicitly_separate_from_legacy() -> None:
    schemas = load_agent_schemas("agent-schema-v2")

    assert set(AGENT_SCHEMA).issubset(schemas)
    assert "state_delta" in schemas["GuideCoachAgent"]["required"]
    assert "learner_state_update" not in schemas["GuideCoachAgent"]["properties"]
    assert schemas["IntentRouterAgent"]["x-adapter-eligible"] is True
    assert schemas["PathPlannerAgent"]["x-adapter-eligible"] is False
    assert schemas["ResourceDesignerAgent.handout"]["x-adapter-eligible"] is True
    assert schemas["ResourceDesignerAgent.reading"]["x-adapter-eligible"] is True
    assert schemas["ResourceDesignerAgent.code_lab"]["x-adapter-eligible"] is True
    assert schemas["ResourceDesignerAgent.storyboard"]["x-adapter-eligible"] is True
    assert (
        schemas["ResourceDesignerAgent"]["x-product-contract"]
        == "subagent-ir-split-in-progress"
    )


def test_resource_subagent_v2_schemas_hide_product_page_refs() -> None:
    schemas = load_agent_schemas("agent-schema-v2")

    handout = schemas["ResourceDesignerAgent.handout"]
    reading = schemas["ResourceDesignerAgent.reading"]
    code_lab = schemas["ResourceDesignerAgent.code_lab"]
    storyboard = schemas["ResourceDesignerAgent.storyboard"]
    serialized = json.dumps(
        {
            "handout": handout,
            "reading": reading,
            "code_lab": code_lab,
            "storyboard": storyboard,
        }
    )
    assert "evidence_refs" in serialized
    assert "page_id" not in serialized
    assert "page_no" not in serialized


def test_code_plan_v21_schema_forbids_free_code_and_extra_fields() -> None:
    schema = schema_for_agent(
        "ResourceDesignerAgent.code_lab", "agent-schema-v2.1"
    )
    item = schema["$defs"]["CodePlanWalkthroughIR"]

    assert schema["x-product-contract"] == "semantic-code-plan-evidence-bound"
    assert "example_pattern" in item["required"]
    assert "code" not in item["properties"]
    assert item["additionalProperties"] is False


def test_registry_rejects_boolean_for_number() -> None:
    schema = schema_for_agent("IntentRouterAgent")
    value = {
        "intent": "learn",
        "target_agent": "TutorAgent",
        "required_tools": [],
        "confidence": True,
        "reason": "x",
    }

    valid, missing, type_errors = validate_object(value, schema)

    assert valid is False
    assert missing == ()
    assert type_errors == ("confidence",)


def test_v2_registry_recursively_rejects_missing_nested_field() -> None:
    schema = schema_for_agent("PracticeAgent", "agent-schema-v2")
    value = {
        "problems": [{
            "title": "列表去重",
            "statement": "实现一个保留原始顺序的列表去重函数",
            "sample_input": "[1, 2, 1]",
        }],
        "evidence_refs": ["page-1"],
    }

    valid, missing, type_errors = validate_object(value, schema)

    assert valid is False
    assert "problems[0].sample_output" in missing
    assert type_errors == ()


def test_v2_registry_recursively_rejects_nested_type_and_constraint() -> None:
    schema = schema_for_agent("MindMapAgent", "agent-schema-v2")
    value = {
        "nodes": [
            {"node_id": "1-root", "label": "循环"},
            {"node_id": "child", "label": "for 循环"},
        ],
        "edges": [{"source": "root", "target": 1, "relation": "包含"}],
        "evidence_refs": [],
    }

    valid, missing, type_errors = validate_object(value, schema)

    assert valid is False
    assert missing == ()
    assert "nodes[0].node_id" in type_errors
    assert "edges[0].target" in type_errors


def test_registry_fails_fast_for_duplicate_titles(tmp_path: Path) -> None:
    version = tmp_path / "v1"
    version.mkdir()
    schema = {
        "title": "TutorAgent",
        "type": "object",
        "properties": {"x": {"type": "string"}},
        "required": ["x"],
    }
    for name in ("a", "b"):
        (version / f"{name}.schema.json").write_text(json.dumps(schema), encoding="utf-8")

    with pytest.raises(SchemaRegistryError, match="duplicate"):
        load_agent_schemas(SCHEMA_VERSION_V1, tmp_path)
