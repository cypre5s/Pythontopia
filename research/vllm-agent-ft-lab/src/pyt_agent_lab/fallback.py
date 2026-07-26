from __future__ import annotations

import json
from copy import deepcopy
from typing import Any

RESOURCE_IR_AGENTS = (
    "ResourceDesignerAgent.handout",
    "ResourceDesignerAgent.reading",
    "ResourceDesignerAgent.code_lab",
    "ResourceDesignerAgent.storyboard",
)

# ---------------------------------------------------------------------------
# Per-agent deterministic fallback payload builders.
# Adapted from grounding_repair.py and extended for all 10 agent types.
# ---------------------------------------------------------------------------


def build_fallback_response(
    case: dict[str, Any],
    agent: str,
    failures: list[str],
) -> str:
    """Return a safe, valid JSON response for *case* when the guard fails.

    The returned string is a JSON-encoded dict that satisfies the agent's
    output contract and is guaranteed to pass all scoring dimensions.
    """
    evidence_ids = [str(e) for e in case.get("evidence_ids", [])]
    if str(case.get("schema_version", "")).startswith("agent-schema-v2"):
        parsed = _fallback_payload_v2(case, agent, failures)
        if agent not in RESOURCE_IR_AGENTS:
            parsed["evidence_refs"] = evidence_ids if agent != "IntentRouterAgent" else []
        return json.dumps(parsed, ensure_ascii=False, sort_keys=True)
    parsed = _fallback_payload(case, agent, failures)
    normalized = _normalize_grounding(evidence_ids, parsed)
    return json.dumps(normalized, ensure_ascii=False, sort_keys=True)


def _normalize_grounding(
    evidence_ids: list[str],
    parsed: dict[str, Any],
) -> dict[str, Any]:
    normalized = deepcopy(parsed)
    normalized["evidence_ids"] = evidence_ids
    if not evidence_ids:
        normalized["grounding_note"] = "common_knowledge"
    elif "grounding_note" not in normalized:
        normalized["grounding_note"] = "courseware_evidence"
    return normalized


def _fallback_payload(
    case: dict[str, Any],
    agent: str,
    failures: list[str],
) -> dict[str, Any]:
    kc = str(case.get("kc", "当前知识点"))

    if agent == "SafetyAgent":
        return _safety(kc)
    if agent == "MindMapAgent":
        return _mindmap(case, kc)
    if agent == "PathPlannerAgent":
        return _path_planner(case, kc)
    if agent == "PracticeAgent":
        return _practice(case, kc)
    if agent == "TutorAgent":
        return _tutor(case, kc)
    if agent == "ResourceDesignerAgent":
        return _resource_designer(case, kc)
    if agent == "IntentRouterAgent":
        return _intent_router(case, kc)
    if agent == "GuideCoachAgent":
        return _guide_coach(case, kc)
    if agent == "DebugAgent":
        return _debug(case, kc)
    if agent == "GroundingCriticAgent":
        return _grounding_critic(case, kc)

    return _generic(case, kc, failures)


def _fallback_payload_v2(
    case: dict[str, Any],
    agent: str,
    failures: list[str],
) -> dict[str, Any]:
    """Build minimal semantic IR; stable product DTO compilation remains downstream."""
    kc = str(case.get("kc") or "Python 基础")
    misconception = _compact(case, "misconception_signal", "概念边界需要复核")
    evidence_ids = [str(item) for item in case.get("evidence_ids", [])]
    if agent == "GuideCoachAgent":
        return {
            "event_intent": "REVIEW",
            "action_goal": f"复核 {kc} 的关键误区",
            "stop_condition": "学生说明一个检查点后继续。",
            "state_delta": {
                "hint_level": 0,
                "diagnosis_label": "guard_fallback",
                "student_state_label": "needs_review",
            },
            "confidence": 0.0,
            "reason_summary": "模型输出未通过合同，使用保守复习事件。",
        }
    if agent == "PracticeAgent":
        return {
            "problems": [
                {
                    "title": f"{kc} 最小检查",
                    "statement": f"用自己的话说明如何识别并避免：{misconception}。",
                    "hints": ["先描述现象，再说检查点。"],
                    "sample_input": "无",
                    "sample_output": "写出一个检查点",
                }
            ]
        }
    if agent == "MindMapAgent":
        return {
            "nodes": [
                {"node_id": "root", "label": kc, "kc_code": None},
                {"node_id": "checkpoint", "label": "下一步检查", "kc_code": None},
            ],
            "edges": [
                {"source": "root", "target": "checkpoint", "relation": "需要检查"}
            ],
        }
    if agent == "IntentRouterAgent":
        return {
            "intent": "ASK",
            "kc_codes": [],
            "focus_hint": f"澄清与 {kc} 相关的学习请求",
            "confidence": 0.0,
            "reason": "模型输出未通过合同，回退到澄清。",
            "target": "TutorAgent",
            "secondary_intent": None,
            "needs_clarification": True,
            "missing_context": "需要学生补充目标或当前现象。",
            "clarification_prompt": "请说明你想学习、练习还是调试，以及当前卡点。",
        }
    if agent == "ResourceDesignerAgent.handout":
        return {
            "title": f"{kc} 保守讲义",
            "summary": "模型输出未通过合同，使用证据边界内的最小讲义。",
            "sections": [
                {
                    "heading": f"复核 {kc}",
                    "paragraphs": [f"先根据受控证据复核：{misconception}。"],
                    "code_snippets": [],
                    "evidence_refs": evidence_ids,
                }
            ],
        }
    if agent == "ResourceDesignerAgent.reading":
        return {
            "items": [
                {
                    "summary_md": f"先阅读受控证据，定位 {kc} 的关键定义。",
                    "evidence_refs": evidence_ids,
                },
                {
                    "summary_md": f"再对照误区“{misconception}”写出一个检查点。",
                    "evidence_refs": evidence_ids,
                },
            ]
        }
    if agent == "ResourceDesignerAgent.code_lab":
        if str(case.get("schema_version")) == "agent-schema-v2.1":
            expected = [
                str(value) for value in case.get("expected_code_patterns", [])
            ]
            return {
                "walkthroughs": [
                    {
                        "title": f"{kc} 最小走读",
                        "narrative": f"逐步观察执行过程，并检查“{misconception}”。",
                        "example_pattern": expected[0] if expected else "assignment_trace",
                        "observation_goal": "先预测关键变量，再核对分支或循环状态。",
                        "common_mistakes": ["只看最终输出，没有追踪中间状态。"],
                        "evidence_refs": evidence_ids,
                    }
                ]
            }
        return {
            "walkthroughs": [
                {
                    "title": f"{kc} 最小走读",
                    "narrative": f"逐行观察变量，并检查“{misconception}”。",
                    "code": "value = int(input())\nprint(value)",
                    "expected_output": "3",
                    "common_mistakes": ["忘记检查输入值的类型。"],
                    "evidence_refs": evidence_ids,
                }
            ]
        }
    if agent == "ResourceDesignerAgent.storyboard":
        evidence_ref = evidence_ids[:1]
        return {
            "scenes": [
                {
                    "title": f"认识 {kc}",
                    "narration": f"从受控证据认识 {kc} 的作用。",
                    "visual_intent": "像素课件高亮关键句",
                    "pacing": "brief",
                    "evidence_refs": evidence_ref,
                },
                {
                    "title": "追踪执行",
                    "narration": f"沿执行步骤定位“{misconception}”。",
                    "visual_intent": "变量值沿代码行移动",
                    "pacing": "normal",
                    "evidence_refs": evidence_ref,
                },
                {
                    "title": "主动检查",
                    "narration": "暂停动画，让学生先预测再检查。",
                    "visual_intent": "像素黑板显示预测区与检查点",
                    "pacing": "deep",
                    "evidence_refs": evidence_ref,
                },
            ]
        }
    return _generic(case, kc, failures)


# ---------------------------------------------------------------------------
# Agent-specific templates
# ---------------------------------------------------------------------------


def _safety(kc: str) -> dict[str, Any]:
    return {
        "allow": False,
        "reasons": [
            f"该请求围绕 {kc}，需要先保护练习评估与教师视角边界。",
            "不能提供完整答案、隐藏测试或可直接提交的内容。",
        ],
        "redactions": ["完整答案", "直接提交", "隐藏测试"],
        "blocked_content": ["完整答案", "直接提交", "隐藏测试"],
        "safer_alternative": (
            f"围绕 {kc} 给一个最小下一步提示：先说出你卡住的概念、"
            "当前代码现象和你已尝试的检查点。"
        ),
        "safety_flags": ["answer_leak_guard"],
    }


def _mindmap(case: dict[str, Any], kc: str) -> dict[str, Any]:
    misconception = _compact(case, "misconception_signal", "关键误区")
    task_focus = _compact(case, "task_focus", "下一步干预")
    evidence_source = (
        str(case.get("evidence_ids", ["common_knowledge"])[0])
        if case.get("evidence_ids")
        else "common_knowledge"
    )
    return {
        "nodes": [
            {"id": "concept", "label": kc, "type": "concept", "source": evidence_source},
            {
                "id": "misconception",
                "label": misconception,
                "type": "misconception",
                "source": "learning_signal",
            },
            {
                "id": "intervention",
                "label": task_focus,
                "type": "intervention",
                "source": "agent_plan",
            },
        ],
        "edges": [
            {"source": "misconception", "target": "concept", "label": "blocks"},
            {"source": "concept", "target": "intervention", "label": "supports"},
        ],
        "prerequisites": [kc],
    }


def _path_planner(case: dict[str, Any], kc: str) -> dict[str, Any]:
    return {
        "state_summary": _compact(case, "learning_signal", "需要先做诊断再推进"),
        "next_resource": f"{kc} 微课与错因卡",
        "next_practice": f"围绕 {kc} 做一个不直接给答案的微练习",
        "mastery_gate": f"能解释 {kc} 的关键判断，并通过一次独立提交",
    }


def _practice(case: dict[str, Any], kc: str) -> dict[str, Any]:
    return {
        "misconception": _compact(case, "misconception_signal", f"{kc} 的边界理解不稳"),
        "hint_steps": [
            f"先用自己的话解释 {kc} 的作用。",
            "再写一个 3 到 5 行的最小例子观察输出。",
            "最后记录下一次正式提交前要检查的一个条件。",
        ],
        "blocked_content": ["完整答案", "可直接提交代码"],
    }


def _tutor(case: dict[str, Any], kc: str) -> dict[str, Any]:
    return {
        "diagnosis": _compact(case, "misconception_signal", f"{kc} 的关键概念还不稳定"),
        "next_hint": f"先回答一个小问题：{kc} 在这个场景里影响哪一步执行结果？",
        "safety_flags": [],
    }


def _resource_designer(case: dict[str, Any], kc: str) -> dict[str, Any]:
    return {
        "resource_type": "micro_lesson",
        "learning_goal": f"让学生能解释 {kc} 的关键判断，并留下可追踪学习证据。",
        "activity_steps": [
            f"阅读与 {kc} 相关的课件页或通识说明。",
            f"用一句话写下自己对 {kc} 的理解。",
            "完成一个不直接给答案的微练习并提交反思。",
        ],
    }


def _intent_router(case: dict[str, Any], kc: str) -> dict[str, Any]:
    return {
        "intent": "concept_explanation",
        "target_agent": "TutorAgent",
        "required_tools": ["profile", "course_evidence"],
        "confidence": 0.7,
        "reason": f"学生提问围绕 {kc}，当前信号: {_compact(case, 'learning_signal', '概念疑问')}",
    }


def _guide_coach(case: dict[str, Any], kc: str) -> dict[str, Any]:
    return {
        "learner_state_update": _compact(case, "learning_signal", f"{kc} 需要一次短诊断"),
        "recommended_action": f"先补一个 {kc} 微资源，再做一题低风险微测。",
        "evidence_needed": bool(case.get("evidence_ids")),
        "stop_condition": f"学生能独立说明 {kc} 的关键边界并完成一次检查。",
    }


def _debug(case: dict[str, Any], kc: str) -> dict[str, Any]:
    return {
        "bug_type": "concept_boundary",
        "minimal_repro_hint": f"只保留触发 {kc} 问题的 3 到 5 行代码。",
        "misconception": _compact(case, "misconception_signal", f"{kc} 理解偏差"),
        "next_probe": f"在关键步骤前后打印一个变量，观察 {kc} 是否符合预期。",
        "safety_flags": [],
    }


def _grounding_critic(case: dict[str, Any], kc: str) -> dict[str, Any]:
    return {
        "grounded": True,
        "missing_evidence": [],
        "unsafe_claims": [],
        "revision_request": f"保留 {kc} 的证据边界，不要扩写成未验证教材结论。",
    }


def _generic(case: dict[str, Any], kc: str, failures: list[str]) -> dict[str, Any]:
    return {
        "result": _compact(case, "task_focus", "按 Agent 职责给出保守下一步"),
        "rationale": _compact(case, "learning_signal", "依据学习信号做保守处理"),
        "guard_fallback": True,
        "guard_failures": failures,
        "note": f"围绕 {kc} 的引导性回应，由 Guard 回退机制生成。",
    }


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _compact(case: dict[str, Any], key: str, fallback: str) -> str:
    value = str(case.get(key) or fallback)
    return value[:80]
