from pyt_agent_lab.baseline import build_case_messages
from pyt_agent_lab.prompt_variants import apply_prompt_variant


def test_protocol_patch_v1_adds_strict_json_and_evidence_contract() -> None:
    case = {
        "system": "你是资源设计 Agent。输出 JSON：resource_type,learning_goal,activity_steps,evidence_ids,grounding_note。",
        "user": "引用 page-3-021。",
        "evidence_ids": ["page-3-021"],
    }

    patched = apply_prompt_variant(case, "protocol_patch_v1")

    assert patched is not case
    assert patched["system"].startswith(case["system"])
    assert "Return only one valid JSON object" in patched["system"]
    assert "Do not wrap the JSON in Markdown" in patched["system"]
    assert "page-3-021" in patched["system"]
    assert case["system"].endswith("grounding_note。")


def test_protocol_patch_v1_marks_common_knowledge_when_no_evidence() -> None:
    case = {
        "system": "你是 Agent 路由器。输出 JSON：intent,target_agent,required_tools,confidence,reason。",
        "user": "路由这个请求。",
        "evidence_ids": [],
    }

    patched = apply_prompt_variant(case, "protocol_patch_v1")

    assert '"evidence_ids": []' in patched["system"]
    assert '"grounding_note": "common_knowledge"' in patched["system"]


def test_build_case_messages_applies_prompt_variant() -> None:
    case = {
        "system": "你是导学编排 Agent。",
        "user": "给出下一步。",
        "evidence_ids": [],
    }

    messages = build_case_messages(case, prompt_variant="protocol_patch_v1")

    assert messages[0]["role"] == "system"
    assert "protocol_patch_v1" in messages[0]["content"]
    assert messages[1] == {"role": "user", "content": "给出下一步。"}
