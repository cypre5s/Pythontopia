"""消费侧工具收敛（17→9）的单元测试（MVP Phase D）。"""

from __future__ import annotations

from pythontopia_graph.tools.consolidated import (
    CONSOLIDATED_TOOL_NAMES,
    consolidated_tool_schemas,
    expand_tool_call,
)


def test_schemas_valid_and_count() -> None:
    schemas = consolidated_tool_schemas("default")
    names = {s["function"]["name"] for s in schemas}
    assert names == set(CONSOLIDATED_TOOL_NAMES)
    assert len(names) == 9
    for schema in schemas:
        assert schema["type"] == "function"
        assert schema["function"]["parameters"]["type"] == "object"


def test_quiz_scope_shrinks() -> None:
    names = {s["function"]["name"] for s in consolidated_tool_schemas("quiz")}
    assert names == {"ask", "quiz"}  # 考核 scope 只暴露 ask/quiz
    assert "browse" not in names


def test_expand_selector_modes() -> None:
    assert expand_tool_call("ask", {"mode": "qa"})[0] == "ASK"
    assert expand_tool_call("ask", {"mode": "explain_code"})[0] == "EXPLAIN_CODE"
    assert expand_tool_call("ask", {"mode": "fix_bug"})[0] == "FIX_BUG"
    assert expand_tool_call("make_resource", {"kind": "mindmap"})[0] == "MINDMAP"
    assert expand_tool_call("make_practice", {"kind": "code_lab"})[0] == "CODE_LAB"
    assert expand_tool_call("my_space", {"kind": "progress"})[0] == "PROGRESS"
    assert expand_tool_call("quiz", {"mode": "review"})[0] == "REVIEW"


def test_expand_strips_selector_from_args() -> None:
    intent, args = expand_tool_call("ask", {"mode": "qa", "topic": "x", "kc_codes": ["K1"]})
    assert intent == "ASK"
    assert "mode" not in args
    assert args == {"topic": "x", "kc_codes": ["K1"]}


def test_expand_no_selector_tools() -> None:
    assert expand_tool_call("plan_study", {"topic": "t"})[0] == "PLAN"
    assert expand_tool_call("video", {"topic": "t"})[0] == "VIDEO"
    assert expand_tool_call("navigate", {"target": "library"})[0] == "NAVIGATE"
    assert expand_tool_call("browse", {"goal": "g"})[0] == "BROWSE"


def test_expand_invalid_or_missing_selector_failsoft() -> None:
    # 非法 mode → 回落第一个枚举值意图（确定性）。
    assert expand_tool_call("ask", {"mode": "nonsense"})[0] == "ASK"
    # 缺 selector → 同样回落。
    assert expand_tool_call("make_resource", {})[0] == "HANDOUT"


def test_expand_unknown_tool() -> None:
    assert expand_tool_call("nope", {}) == (None, {})
