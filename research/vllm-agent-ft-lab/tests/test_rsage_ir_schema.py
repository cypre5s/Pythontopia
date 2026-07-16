from __future__ import annotations

from copy import deepcopy

import pytest

from pyt_agent_lab.schema_registry import load_agent_schemas, validate_object


def _valid_ir() -> dict[str, object]:
    return {
        "misconception_type": "把变量名误认为不可变化的固定容器",
        "event_intent": "SCAFFOLD",
        "hint_level": 1,
        "action_goal": "用一次最小追踪区分变量名与当前绑定的值",
        "evidence_refs": ["pack43-page-10724"],
        "safety_flags": [],
    }


def _schema() -> dict[str, object]:
    return load_agent_schemas("rsage")["TutorDecisionAgent"]


def test_rsage_registry_only_exposes_frozen_tutor_decision_v1_contract() -> None:
    schemas = load_agent_schemas("rsage")

    assert set(schemas) == {"TutorDecisionAgent"}, "R-SAGE 注册表只能暴露独立教学决策契约"
    schema = schemas["TutorDecisionAgent"]
    assert schema["$schema"] == "https://json-schema.org/draft/2020-12/schema"
    assert schema["x-schema-version"] == "rsage-ir-v1"
    assert schema["x-product-contract"] == "grounded-teaching-decision-ir"
    assert schema["x-adapter-eligible"] is True
    assert schema["additionalProperties"] is False, "语义 IR 不得混入产品 DTO 字段"
    assert set(schema["required"]) == {
        "misconception_type",
        "event_intent",
        "hint_level",
        "action_goal",
        "evidence_refs",
        "safety_flags",
    }


def test_rsage_schema_accepts_complete_grounded_teaching_decision() -> None:
    valid, missing, invalid = validate_object(_valid_ir(), _schema())

    assert valid is True, "完整且证据绑定的教学决策应通过校验"
    assert missing == ()
    assert invalid == ()


def test_rsage_schema_rejects_missing_required_field() -> None:
    value = _valid_ir()
    del value["action_goal"]

    valid, missing, invalid = validate_object(value, _schema())

    assert valid is False
    assert missing == ("action_goal",), "缺少教学动作目标必须失败关闭"
    assert invalid == ()


@pytest.mark.parametrize(
    ("field", "value", "expected_path"),
    [
        ("event_intent", "PLAN", "event_intent"),
        ("hint_level", 4, "hint_level"),
        ("hint_level", True, "hint_level"),
        ("misconception_type", "", "misconception_type"),
        ("action_goal", "", "action_goal"),
        ("evidence_refs", [], "evidence_refs"),
        ("evidence_refs", ["page-10724"], "evidence_refs[0]"),
        (
            "evidence_refs",
            ["pack43-page-10724", "pack43-page-10724"],
            "evidence_refs",
        ),
        ("safety_flags", ["答案泄露", "答案泄露"], "safety_flags"),
    ],
    ids=[
        "未知教学动作",
        "提示等级越界",
        "布尔值不能冒充提示等级",
        "空错因",
        "空动作目标",
        "空证据",
        "非法证据编号",
        "重复证据",
        "重复安全标记",
    ],
)
def test_rsage_schema_rejects_invalid_ir_values(
    field: str,
    value: object,
    expected_path: str,
) -> None:
    payload = deepcopy(_valid_ir())
    payload[field] = value

    valid, missing, invalid = validate_object(payload, _schema())

    assert valid is False, "非法 R-SAGE IR 不得通过校验"
    assert missing == ()
    assert expected_path in invalid, f"错误路径应明确指向 {expected_path}"


def test_rsage_schema_rejects_product_dto_and_uncontrolled_extra_fields() -> None:
    value = _valid_ir()
    value["workflow_event"] = {"type": "OPEN_RESOURCE", "resource_id": 43}

    valid, missing, invalid = validate_object(value, _schema())

    assert valid is False
    assert missing == ()
    assert invalid == ("workflow_event",), "模型输出不得直接携带产品状态机字段"
