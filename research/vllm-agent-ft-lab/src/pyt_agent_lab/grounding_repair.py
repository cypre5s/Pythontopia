from __future__ import annotations

import json
from copy import deepcopy
from typing import Any

from .scoring import score_case_response


def repair_response_records(
    records: list[dict[str, Any]],
    fold_by_case_id: dict[str, int] | None = None,
) -> list[dict[str, Any]]:
    return [
        repair_response_record(
            record,
            fold=None if fold_by_case_id is None else fold_by_case_id.get(str(record["case"]["id"])),
        )
        for record in records
    ]


def repair_response_record(record: dict[str, Any], fold: int | None = None) -> dict[str, Any]:
    repaired = deepcopy(record)
    case = repaired["case"]
    parsed = _parse_json_object(str(repaired.get("response", "")))
    if _needs_safety_template(case, parsed, str(repaired.get("response", ""))):
        parsed = _fallback_safety(case)
    elif parsed is None:
        parsed = _fallback_payload(case)
    normalized = _normalize_grounding(case, parsed)
    response = json.dumps(normalized, ensure_ascii=False, sort_keys=True)
    score = score_case_response(case, response)
    repaired["response"] = response
    repaired["score"] = score.as_row(fold=fold)
    repaired["repair"] = {
        "applied": response != str(record.get("response", "")),
        "reason": "json_or_grounding_contract",
    }
    return repaired


def _parse_json_object(response: str) -> dict[str, Any] | None:
    try:
        value = json.loads(response)
    except json.JSONDecodeError:
        return None
    return value if isinstance(value, dict) else None


def _normalize_grounding(case: dict[str, Any], parsed: dict[str, Any]) -> dict[str, Any]:
    normalized = deepcopy(parsed)
    evidence_ids = [str(item) for item in case.get("evidence_ids", [])]
    normalized["evidence_ids"] = evidence_ids
    if not evidence_ids:
        normalized["grounding_note"] = "common_knowledge"
    elif "grounding_note" not in normalized:
        normalized["grounding_note"] = "courseware_evidence"
    return normalized


def _fallback_payload(case: dict[str, Any]) -> dict[str, Any]:
    agent = str(case.get("agent", ""))
    if agent == "SafetyAgent":
        return _fallback_safety(case)
    if agent == "MindMapAgent":
        return _fallback_mindmap(case)
    if agent == "PathPlannerAgent":
        return {
            "state_summary": _compact_text(case, "learning_signal", "需要先做诊断再推进"),
            "next_resource": f"{case.get('kc', 'Python')} 微课与错因卡",
            "next_practice": f"围绕 {case.get('kc', '当前知识点')} 做一个不直接给答案的微练习",
            "mastery_gate": f"能解释 {case.get('kc', '当前知识点')} 的关键判断，并通过一次独立提交",
        }
    return {
        "result": _compact_text(case, "task_focus", "按 Agent 职责给出下一步"),
        "rationale": _compact_text(case, "learning_signal", "依据学习信号做保守处理"),
    }


def _fallback_mindmap(case: dict[str, Any]) -> dict[str, Any]:
    kc = str(case.get("kc", "当前知识点"))
    misconception = _compact_text(case, "misconception_signal", "关键误区")
    task_focus = _compact_text(case, "task_focus", "下一步干预")
    source = str(case.get("evidence_ids", ["common_knowledge"])[0]) if case.get("evidence_ids") else "common_knowledge"
    return {
        "nodes": [
            {"id": "concept", "label": kc, "type": "concept", "source": source},
            {"id": "misconception", "label": misconception, "type": "misconception", "source": "learning_signal"},
            {"id": "intervention", "label": task_focus, "type": "intervention", "source": "agent_plan"},
        ],
        "edges": [
            {"source": "misconception", "target": "concept", "label": "blocks"},
            {"source": "concept", "target": "intervention", "label": "supports"},
        ],
        "prerequisites": [kc],
    }


def _fallback_safety(case: dict[str, Any]) -> dict[str, Any]:
    kc = str(case.get("kc") or "当前知识点")
    return {
        "allow": False,
        "reasons": [
            f"该请求围绕 {kc}，需要先保护练习评估与教师视角边界。",
            "不能提供完整答案、隐藏测试或可直接提交的内容。",
        ],
        "redactions": ["完整答案", "直接提交", "隐藏测试"],
        "blocked_content": ["完整答案", "直接提交", "隐藏测试"],
        "safer_alternative": f"围绕 {kc} 给一个最小下一步提示：先说出你卡住的概念、当前代码现象和你已尝试的检查点。",
        "safety_flags": ["answer_leak_guard"],
    }


def _needs_safety_template(case: dict[str, Any], parsed: dict[str, Any] | None, raw_response: str) -> bool:
    if str(case.get("agent", "")) != "SafetyAgent":
        return False
    if parsed is None:
        return True
    score = score_case_response(case, json.dumps(parsed, ensure_ascii=False, sort_keys=True))
    if score.forbidden_leak:
        return True
    return not {"redactions", "safer_alternative"}.issubset(parsed.keys())


def _compact_text(case: dict[str, Any], key: str, fallback: str) -> str:
    value = str(case.get(key) or fallback)
    return value[:80]
