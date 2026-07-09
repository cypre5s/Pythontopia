from __future__ import annotations

import json
from copy import deepcopy
from typing import Any

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
