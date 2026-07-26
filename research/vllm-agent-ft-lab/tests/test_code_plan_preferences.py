import json

from pyt_agent_lab.code_plan_preferences import build_code_plan_preference_dataset


def _source_case(index: int) -> dict[str, object]:
    chapters = (
        ("变量", "变量绑定", "混淆变量名和值"),
        ("if", "条件分支", "遗漏 else"),
        ("while", "循环控制", "break 与 continue 混用"),
        ("for", "range 累加", "循环边界错误"),
        ("列表", "列表迭代", "索引与元素混淆"),
        ("作用域", "函数 return", "形参实参混淆"),
        ("异常", "行尾换行", "读取行尾未处理"),
        ("异常", "try except", "没有捕获 ValueError"),
    )
    kc, focus, misconception = chapters[index % len(chapters)]
    return {
        "id": f"source-{index}",
        "source_case_id": f"domain-{index}",
        "agent": "ResourceDesignerAgent",
        "kc": kc,
        "chapter_slice_focus": focus,
        "misconception_signal": misconception,
        "learner_profile": f"profile-{index}",
        "learning_signal": f"signal-{index}",
        "task_focus": f"focus-{index}",
        "instruction_style": "避免答案泄露",
        "evidence_ids": [f"page-{index}"],
        "requires_evidence": True,
    }


def test_code_plan_preferences_are_unique_semantic_and_uncontaminated() -> None:
    source = [_source_case(index) for index in range(230)]
    protected = [source[0], source[7]]

    rows = build_code_plan_preference_dataset(
        source_cases=source,
        protected_cases=protected,
        target_count=200,
        seed=42,
    )

    assert len(rows) == 200
    assert len({row["id"] for row in rows}) == 200
    assert not {row["metadata"]["source_case_id"] for row in rows} & {
        "source-0",
        "source-7",
    }
    assert {row["metadata"]["schema_version"] for row in rows} == {
        "agent-schema-v2.1"
    }
    assert all("code" not in json.loads(row["chosen"])["walkthroughs"][0] for row in rows)
    assert {row["metadata"]["preference_axis"] for row in rows} == {
        "wrong_pattern",
        "unbound_evidence",
        "legacy_free_code",
        "safety_leak",
        "weak_observation",
    }
