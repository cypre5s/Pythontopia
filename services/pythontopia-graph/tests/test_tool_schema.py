"""registry → OpenAI 兼容 function-calling schema 导出的单元测试（MVP Phase B）。"""

from __future__ import annotations

from pythontopia_graph.tools.registry import (
    ALL_TOOL_NAMES,
    NAVIGATE_TARGETS,
    TOOLS,
    to_tool_schema,
    tool_parameters,
    tool_schemas_for_scope,
)


def test_every_tool_has_valid_schema() -> None:
    for name, spec in TOOLS.items():
        schema = to_tool_schema(spec)
        assert schema["type"] == "function"
        fn = schema["function"]
        assert fn["name"] == name
        assert isinstance(fn["description"], str) and fn["description"]
        params = fn["parameters"]
        assert params["type"] == "object"
        assert isinstance(params["properties"], dict)


def test_required_params_declared() -> None:
    assert tool_parameters("EXPLAIN_CODE")["required"] == ["code"]
    assert tool_parameters("FIX_BUG")["required"] == ["code"]
    assert tool_parameters("NAVIGATE")["required"] == ["target"]
    assert tool_parameters("BROWSE")["required"] == ["goal"]
    # 可选参数工具无 required 键。
    assert "required" not in tool_parameters("ASK")


def test_navigate_target_enum_matches_registry() -> None:
    target = tool_parameters("NAVIGATE")["properties"]["target"]
    assert target["enum"] == list(NAVIGATE_TARGETS)


def test_common_tools_expose_topic_and_kc_codes() -> None:
    props = tool_parameters("ASK")["properties"]
    assert "topic" in props
    assert "kc_codes" in props
    assert props["kc_codes"]["type"] == "array"


def test_examples_folded_into_description() -> None:
    schema = to_tool_schema(TOOLS["EXPLAIN_CODE"])
    desc = schema["function"]["description"]
    assert "调用示例" in desc
    assert "code" in desc


def test_scope_shrinks_schemas() -> None:
    default_names = {s["function"]["name"] for s in tool_schemas_for_scope("default")}
    quiz_names = {s["function"]["name"] for s in tool_schemas_for_scope("quiz")}
    # BROWSE 仅 default scope；考核 scope 收缩不暴露（防借联网逃避作答）。
    assert "BROWSE" in default_names
    assert "BROWSE" not in quiz_names
    assert quiz_names.issubset(default_names)
    # 全集与注册表对齐。
    assert default_names == set(ALL_TOOL_NAMES)
