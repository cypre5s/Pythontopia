from __future__ import annotations

import json
from copy import deepcopy
from typing import Any


CODE_PLAN_SCHEMA_VERSION = "agent-schema-v2.1"
CODE_PLAN_POLICY_VERSION = "code-plan-pattern-policy-v1"


def expected_code_patterns(case: dict[str, Any]) -> tuple[str, ...]:
    """Return admissible patterns from protected case facts, never model output."""
    text = " ".join(
        str(case.get(key) or "")
        for key in (
            "kc",
            "chapter_slice_focus",
            "misconception_signal",
            "task_focus",
            "learning_signal",
        )
    ).lower()
    rules = (
        (("行尾", "换行", "newline", "rstrip", "strip"), "string_cleanup"),
        (("while", "break", "continue", "无限循环"), "while_control"),
        (("作用域", "形参", "实参", "return", "function", "函数"), "function_scope"),
        (("if", "else", "布尔", "条件", "短路"), "conditional_branch"),
        (("列表", "list", "下标", "索引"), "list_iteration"),
        (("异常", "exception", "try", "except", "valueerror"), "exception_guard"),
        (("for", "range", "累加", "迭代"), "for_accumulate"),
        (("变量", "赋值", "绑定", "assignment"), "assignment_trace"),
    )
    for markers, pattern in rules:
        if any(marker in text for marker in markers):
            return (pattern,)
    return ("assignment_trace",)


def upgrade_code_lab_cases(cases: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Create a distinct mixed v2/v2.1 suite while preserving source lineage."""
    rows: list[dict[str, Any]] = []
    for source in cases:
        case = deepcopy(source)
        if str(case.get("agent")) != "ResourceDesignerAgent.code_lab":
            rows.append(case)
            continue
        patterns = expected_code_patterns(case)
        case["id"] = f"{case['id']}::codeplan-v2.1"
        case["base_schema_version"] = str(case.get("schema_version") or "")
        case["schema_version"] = CODE_PLAN_SCHEMA_VERSION
        case["code_plan_policy_version"] = CODE_PLAN_POLICY_VERSION
        case["expected_code_patterns"] = list(patterns)
        case["expected_markers"] = [
            str(case.get("kc") or ""),
            "walkthroughs",
            "example_pattern",
            "observation_goal",
            "common_mistakes",
            "evidence_refs",
        ]
        case["system"] = (
            "ResourceDesignerAgent.code_lab v2.1 CodePlan IR JSON："
            "walkthroughs[{title,narrative,example_pattern,observation_goal,"
            "common_mistakes,evidence_refs}]。example_pattern 只能从 schema 枚举中选择；"
            "不要生成 code 或 expected_output；只写教学语义；禁止产品 DTO、代码围栏、"
            "隐藏测试和答案泄露。"
        )
        rows.append(case)
    return rows


def jsonl_text(rows: list[dict[str, Any]]) -> str:
    return "".join(
        json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows
    )
