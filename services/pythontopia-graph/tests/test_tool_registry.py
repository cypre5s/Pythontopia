"""工具注册表（tools.registry）对账测试：注册表与 schema IntentKind 必须同源一致。"""

from __future__ import annotations

import typing

from pythontopia_graph.schemas import intent as intent_schemas
from pythontopia_graph.tools.registry import (
    ALL_TOOL_NAMES,
    NAVIGATE_TARGETS,
    TOOLS,
    intent_catalog_lines,
    tool_catalog_payload,
    tools_for_scope,
)

EXEC_MODES = {"tutor_sse", "workshop_sse", "orchestrator_sse", "bugmine", "frontend"}
RISK_CLASSES = {"read_only", "generative", "navigational"}
COST_CLASSES = {"zero", "low", "medium", "high"}


def test_registry_matches_intent_kind_literal() -> None:
    """ALL_TOOL_NAMES 与 schemas.intent.IntentKind 的 Literal 全集一致（顺序无关）。"""
    literal_values = set(typing.get_args(intent_schemas.IntentKind))
    assert set(ALL_TOOL_NAMES) == literal_values
    assert len(ALL_TOOL_NAMES) == 17
    # 注册表 key 与 spec.name 一致，无重复。
    assert len(set(ALL_TOOL_NAMES)) == len(ALL_TOOL_NAMES)
    for name, spec in TOOLS.items():
        assert spec.name == name


def test_quiz_scope_shrinks_to_ask_and_review() -> None:
    """考核模式工具集收缩：只剩 ASK/REVIEW（防止被考时跑去生成讲义逃避作答）。"""
    quiz_names = {spec.name for spec in tools_for_scope("quiz")}
    assert quiz_names == {"ASK", "REVIEW"}
    # default 作用域必须包含全部 17 工具。
    default_names = {spec.name for spec in tools_for_scope("default")}
    assert default_names == set(ALL_TOOL_NAMES)


def test_intent_catalog_lines_follow_scope() -> None:
    """目录行按 scope 自动收缩，且每行以工具名开头（prompt 可解析）。"""
    default_lines = intent_catalog_lines("default")
    assert len(default_lines) == 17
    quiz_lines = intent_catalog_lines("quiz")
    assert len(quiz_lines) == 2
    for line in default_lines:
        name = line.split("=", 1)[0]
        assert name in ALL_TOOL_NAMES


def test_tool_catalog_payload_fields_complete() -> None:
    """GET /agents/intent/tools 载荷字段完整且取值合法。"""
    payload = tool_catalog_payload()
    assert len(payload) == 17
    names = {entry["name"] for entry in payload}
    assert names == set(ALL_TOOL_NAMES)
    for entry in payload:
        assert set(entry.keys()) == {
            "name",
            "zhLabel",
            "desc",
            "examples",
            "execMode",
            "riskClass",
            "costClass",
            "scopes",
            "version",
            "namespace",
            "capabilityLabel",
            "renderer",
            "readOnly",
            "destructive",
            "openWorld",
            "idempotent",
            "visibility",
            "invocationLabel",
            "completionLabel",
            "allowedAgents",
            "requiredContext",
            "resultSchema",
            "sensitiveData",
            "sideEffects",
        }
        assert isinstance(entry["zhLabel"], str) and entry["zhLabel"]
        assert isinstance(entry["desc"], str) and entry["desc"]
        assert isinstance(entry["examples"], list) and entry["examples"]
        assert entry["execMode"] in EXEC_MODES
        assert entry["riskClass"] in RISK_CLASSES
        assert entry["costClass"] in COST_CLASSES
        assert isinstance(entry["scopes"], list) and "default" in entry["scopes"]
        assert isinstance(entry["version"], int) and entry["version"] >= 1
        assert entry["namespace"] in {"ask", "quiz", "make", "practice", "plan", "find"}
        assert isinstance(entry["capabilityLabel"], str) and entry["capabilityLabel"]
        assert entry["visibility"] in {"default_hidden", "default_visible", "debug_only"}
        assert isinstance(entry["invocationLabel"], str) and entry["invocationLabel"]
        assert isinstance(entry["completionLabel"], str) and entry["completionLabel"]
        assert isinstance(entry["allowedAgents"], list)
        assert isinstance(entry["requiredContext"], list)
        assert isinstance(entry["resultSchema"], str) and entry["resultSchema"]
        assert entry["sensitiveData"] in {"none", "learner", "submission", "external"}
        assert entry["sideEffects"] in {
            "none",
            "navigation",
            "resource_write",
            "external_read",
        }


def test_capability_groups_cover_all_17_tools() -> None:
    """17 个内部意图必须被稳定映射到 6 个学生可见能力组。"""
    payload = tool_catalog_payload()
    by_name = {str(tool["name"]): tool for tool in payload}

    assert set(by_name) == set(ALL_TOOL_NAMES)
    assert by_name["ASK"]["namespace"] == "ask"
    assert by_name["EXPLAIN_CODE"]["namespace"] == "ask"
    assert by_name["FIX_BUG"]["namespace"] == "ask"
    assert by_name["ASKME"]["namespace"] == "quiz"
    assert by_name["REVIEW"]["namespace"] == "quiz"
    assert by_name["HANDOUT"]["namespace"] == "make"
    assert by_name["MINDMAP"]["namespace"] == "make"
    assert by_name["READING"]["namespace"] == "make"
    assert by_name["STORYBOARD"]["namespace"] == "make"
    assert by_name["PRACTICE"]["namespace"] == "practice"
    assert by_name["CODE_LAB"]["namespace"] == "practice"
    assert by_name["PLAN"]["namespace"] == "plan"
    assert by_name["PROGRESS"]["namespace"] == "plan"
    assert by_name["MY_STUDY"]["namespace"] == "plan"
    assert by_name["VIDEO"]["namespace"] == "find"
    assert by_name["NAVIGATE"]["namespace"] == "find"
    assert by_name["BROWSE"]["namespace"] == "find"


def test_navigate_targets_constant() -> None:
    """NAVIGATE 地标全集：前端路由表据此映射跳转。"""
    assert NAVIGATE_TARGETS == ("library", "arena", "bugmine", "world", "workshop")
