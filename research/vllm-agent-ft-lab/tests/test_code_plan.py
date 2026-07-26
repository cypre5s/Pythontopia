from pyt_agent_lab.code_plan import expected_code_patterns, upgrade_code_lab_cases


def _case(**overrides: object) -> dict[str, object]:
    return {
        "id": "code-case",
        "agent": "ResourceDesignerAgent.code_lab",
        "schema_version": "agent-schema-v2",
        "kc": "变量",
        "chapter_slice_focus": "变量命名与值绑定",
        "misconception_signal": "混淆变量名和值",
        "expected_markers": ["code"],
        **overrides,
    }


def test_expected_code_pattern_uses_case_semantics_with_specific_precedence() -> None:
    assert expected_code_patterns(_case()) == ("assignment_trace",)
    assert expected_code_patterns(_case(kc="while", misconception_signal="break 混用")) == (
        "while_control",
    )
    assert expected_code_patterns(
        _case(kc="异常", chapter_slice_focus="行尾换行处理")
    ) == ("string_cleanup",)
    assert expected_code_patterns(_case(kc="作用域", chapter_slice_focus="return 与 print")) == (
        "function_scope",
    )


def test_upgrade_changes_only_code_lab_contract_and_preserves_source() -> None:
    source = _case()
    other = {"id": "reading", "agent": "ResourceDesignerAgent.reading", "schema_version": "agent-schema-v2"}

    rows = upgrade_code_lab_cases([source, other])

    assert source["schema_version"] == "agent-schema-v2"
    assert rows[0]["schema_version"] == "agent-schema-v2.1"
    assert rows[0]["base_schema_version"] == "agent-schema-v2"
    assert rows[0]["expected_code_patterns"] == ["assignment_trace"]
    assert "code" not in rows[0]["expected_markers"]
    assert rows[1] == other
