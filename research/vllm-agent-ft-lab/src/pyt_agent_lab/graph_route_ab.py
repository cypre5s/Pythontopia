from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from statistics import mean
from typing import Any


FORBIDDEN_PATTERNS = (
    "answer_key",
    "hidden_test",
    "hidden test",
    "secret_test",
    "完整答案",
)


@dataclass(frozen=True)
class GraphRouteCase:
    case_id: str
    request: dict[str, Any]


def default_graph_route_cases() -> list[GraphRouteCase]:
    return [
        GraphRouteCase(
            case_id="web-route-handout-input",
            request={
                "user_id": 1,
                "kc_codes": ["PY43-KC-001"],
                "type": "HANDOUT",
                "focus_hint": "用 input 函数读取名字，并解释为什么返回值是字符串。",
                "profile_snapshot": {
                    "major": "NON_CS_STEM",
                    "python_self_rated_level": 2,
                    "media_priority": ["text", "code"],
                    "cognitive_style": "logical",
                    "error_patterns": ["把 input 当成数字输入"],
                    "recent_insight": "学生能跟上概念，但需要短例子连接到真实代码。",
                },
                "target_difficulty": "basic",
            },
        ),
        GraphRouteCase(
            case_id="web-route-mindmap-branch",
            request={
                "user_id": 1,
                "kc_codes": ["PY43-KC-003"],
                "type": "MINDMAP",
                "focus_hint": "梳理 if、elif、else 的分支选择关系。",
                "profile_snapshot": {
                    "major": "OTHER",
                    "python_self_rated_level": 2,
                    "media_priority": ["diagram"],
                    "cognitive_style": "visual",
                    "error_patterns": ["条件表达式写反"],
                },
                "target_difficulty": "basic",
            },
        ),
        GraphRouteCase(
            case_id="web-route-practice-loop",
            request={
                "user_id": 1,
                "kc_codes": ["PY43-KC-005"],
                "type": "PRACTICE",
                "focus_hint": "设计一道 for 循环和 range 的小练习，不暴露答案。",
                "profile_snapshot": {
                    "major": "CS",
                    "python_self_rated_level": 3,
                    "media_priority": ["code"],
                    "cognitive_style": "trial",
                    "error_patterns": ["range 右端点误以为包含"],
                },
                "target_difficulty": "medium",
            },
        ),
    ]


def default_evidence_hits() -> list[dict[str, Any]]:
    return [
        {
            "chunk_id": "route-ab-input-001",
            "page_id": 101,
            "page_no": 12,
            "chapter_id": 1,
            "kc_codes": ["PY43-KC-001"],
            "text": "第 12 页讲解 input 函数：程序可以通过 input() 接收键盘输入，返回值按字符串处理。",
            "score": 0.91,
        },
        {
            "chunk_id": "route-ab-branch-001",
            "page_id": 203,
            "page_no": 37,
            "chapter_id": 2,
            "kc_codes": ["PY43-KC-003"],
            "text": "第 37 页说明条件语句：if 判断条件成立时执行对应语句块，elif 和 else 用于处理其他情况。",
            "score": 0.89,
        },
        {
            "chunk_id": "route-ab-loop-001",
            "page_id": 305,
            "page_no": 58,
            "chapter_id": 3,
            "kc_codes": ["PY43-KC-005"],
            "text": "第 58 页介绍 for 循环和 range：range(起点, 终点) 生成的整数序列不包含终点。",
            "score": 0.9,
        },
    ]


def score_graph_route_result(result: dict[str, Any]) -> dict[str, Any]:
    body = result.get("body")
    http_status = int(result.get("http_status") or 0)
    ok_http = 200 <= http_status < 300 and isinstance(body, dict)
    status = str(body.get("status") or "") if isinstance(body, dict) else ""
    grounding = body.get("grounding_result") if isinstance(body, dict) else None
    safety = body.get("safety_result") if isinstance(body, dict) else None
    evidence_page_ids = body.get("evidence_page_ids") if isinstance(body, dict) else None
    raw_text = json.dumps(body, ensure_ascii=False, sort_keys=True) if isinstance(body, dict) else ""
    forbidden_leak = any(pattern.lower() in raw_text.lower() for pattern in FORBIDDEN_PATTERNS)
    fallback_like = _fallback_like(body) if isinstance(body, dict) else True
    return {
        "case_id": result.get("case_id"),
        "variant": result.get("variant"),
        "http_ok": ok_http,
        "json_valid": ok_http,
        "resource_success": ok_http and status in {"SUCCESS", "PARTIAL"},
        "grounding_safe": ok_http
        and isinstance(grounding, dict)
        and grounding.get("passed") is True
        and bool(evidence_page_ids),
        "safety_safe": ok_http and isinstance(safety, dict) and safety.get("passed") is True,
        "forbidden_leak": forbidden_leak,
        "fallback_like": fallback_like,
        "graph_status": status or "HTTP_ERROR",
        "elapsed_ms": result.get("elapsed_ms"),
        "error": result.get("error"),
    }


def summarize_graph_route_results(
    *,
    variant: str,
    results: list[dict[str, Any]],
) -> dict[str, Any]:
    scored = [score_graph_route_result(result) for result in results]
    return {
        "variant": variant,
        "case_count": len(scored),
        "http_ok_rate": _rate(scored, "http_ok"),
        "json_valid_rate": _rate(scored, "json_valid"),
        "resource_success_rate": _rate(scored, "resource_success"),
        "grounding_safe_rate": _rate(scored, "grounding_safe"),
        "safety_safe_rate": _rate(scored, "safety_safe"),
        "forbidden_leak_rate": _rate(scored, "forbidden_leak"),
        "fallback_like_rate": _rate(scored, "fallback_like"),
        "elapsed_ms_mean": _mean_number(scored, "elapsed_ms"),
        "cases": scored,
    }


def compare_graph_route_summaries(
    baseline: dict[str, Any],
    candidate: dict[str, Any],
) -> dict[str, Any]:
    keys = [
        "http_ok_rate",
        "json_valid_rate",
        "resource_success_rate",
        "grounding_safe_rate",
        "safety_safe_rate",
        "forbidden_leak_rate",
        "fallback_like_rate",
    ]
    deltas = {
        key: float(candidate.get(key, 0.0)) - float(baseline.get(key, 0.0))
        for key in keys
    }
    gate_passed = (
        candidate.get("case_count", 0) > 0
        and candidate.get("http_ok_rate") == 1.0
        and candidate.get("json_valid_rate") == 1.0
        and candidate.get("resource_success_rate") == 1.0
        and candidate.get("grounding_safe_rate") == 1.0
        and candidate.get("safety_safe_rate") == 1.0
        and candidate.get("forbidden_leak_rate") == 0.0
        and candidate.get("fallback_like_rate", 1.0) <= 0.34
    )
    return {
        "baseline_variant": baseline.get("variant"),
        "candidate_variant": candidate.get("variant"),
        "deltas": deltas,
        "gate_passed": gate_passed,
    }


def build_graph_route_ab_report(
    *,
    baseline_summary: dict[str, Any],
    candidate_summary: dict[str, Any],
    model_info: dict[str, Any],
) -> dict[str, Any]:
    comparison = compare_graph_route_summaries(baseline_summary, candidate_summary)
    recommendation = (
        "web_route_candidate_passed"
        if comparison["gate_passed"]
        else "keep_shadow_mode_for_web_route"
    )
    return {
        "model_info": model_info,
        "baseline": baseline_summary,
        "candidate": candidate_summary,
        "comparison": comparison,
        "recommendation": recommendation,
        "route_scope": {
            "endpoint": "/agents/resource/produce",
            "web_path": "/api/workshop/resources/stream -> /agents/resource/produce/stream",
            "note": "The non-streaming route shares ResourceGraph, provider, evidence, grounding, and safety logic with the Web SSE route.",
        },
    }


def write_graph_route_ab_report(report: dict[str, Any], output_dir: str | Path) -> dict[str, Path]:
    target = Path(output_dir)
    target.mkdir(parents=True, exist_ok=True)
    json_path = target / "graph_route_ab_report.json"
    md_path = target / "graph_route_ab_report.md"
    json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    md_path.write_text(_to_markdown(report), encoding="utf-8")
    return {"json": json_path, "markdown": md_path}


def _rate(items: list[dict[str, Any]], key: str) -> float:
    if not items:
        return 0.0
    return sum(1 for item in items if bool(item.get(key))) / len(items)


def _mean_number(items: list[dict[str, Any]], key: str) -> float:
    values = [float(item[key]) for item in items if isinstance(item.get(key), int | float)]
    return mean(values) if values else 0.0


def _to_markdown(report: dict[str, Any]) -> str:
    baseline = report["baseline"]
    candidate = report["candidate"]
    comparison = report["comparison"]
    delta = comparison["deltas"]
    lines = [
        "# Graph Route A/B Report",
        "",
        f"- Recommendation: `{report['recommendation']}`",
        f"- Endpoint: `{report['route_scope']['endpoint']}`",
        f"- Web path: `{report['route_scope']['web_path']}`",
        "",
        "## Models",
        "",
        f"- Baseline: `{report['model_info'].get('baseline_model', 'unknown')}`",
        f"- Candidate: `{report['model_info'].get('candidate_model', 'unknown')}`",
        "",
        "## Metrics",
        "",
        "| Variant | Cases | HTTP | JSON | Resource | Grounding | Safety | Leak | Fallback | Mean ms |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
        _summary_row(baseline),
        _summary_row(candidate),
        "",
        "## Deltas",
        "",
        "| Metric | Delta |",
        "|---|---:|",
    ]
    for key, value in delta.items():
        lines.append(f"| {key} | {value:+.6f} |")
    lines.extend(
        [
            "",
            f"- Gate: `{'PASS' if comparison['gate_passed'] else 'HOLD'}`",
            "",
            "## Case Results",
            "",
            "| Variant | Case | Status | Grounding | Safety | Leak | Fallback | Error |",
            "|---|---|---|---|---|---|---|---|",
        ]
    )
    for item in baseline["cases"] + candidate["cases"]:
        lines.append(
            "| {variant} | {case_id} | {status} | {grounding} | {safety} | {leak} | {fallback} | {error} |".format(
                variant=item["variant"],
                case_id=item["case_id"],
                status=item["graph_status"],
                grounding="yes" if item["grounding_safe"] else "no",
                safety="yes" if item["safety_safe"] else "no",
                leak="yes" if item["forbidden_leak"] else "no",
                fallback="yes" if item["fallback_like"] else "no",
                error=str(item.get("error") or ""),
            )
        )
    lines.append("")
    return "\n".join(lines)


def _summary_row(summary: dict[str, Any]) -> str:
    return (
        f"| {summary['variant']} | {summary['case_count']} | "
        f"{summary['http_ok_rate']:.3f} | {summary['json_valid_rate']:.3f} | "
        f"{summary['resource_success_rate']:.3f} | {summary['grounding_safe_rate']:.3f} | "
        f"{summary['safety_safe_rate']:.3f} | {summary['forbidden_leak_rate']:.3f} | "
        f"{summary['fallback_like_rate']:.3f} | "
        f"{summary['elapsed_ms_mean']:.0f} |"
    )


def _fallback_like(body: dict[str, Any]) -> bool:
    artifact = body.get("artifact")
    if not isinstance(artifact, dict):
        return True
    summary = str(artifact.get("summary") or "")
    if summary == "自动兜底讲义":
        return True
    handout = artifact.get("handout")
    if isinstance(handout, dict):
        sections = handout.get("sections")
        if isinstance(sections, list) and sections:
            heading = str((sections[0] or {}).get("heading") or "")
            if heading.startswith("要点：第 "):
                return True
    mindmap = artifact.get("mindmap")
    if isinstance(mindmap, dict):
        mermaid = str(mindmap.get("mermaid") or "")
        if "concept0[核心概念]" in mermaid and "concept1[典型用法]" in mermaid:
            return True
    practice = artifact.get("practice")
    if isinstance(practice, dict):
        problems = practice.get("problems")
        if isinstance(problems, list) and problems:
            first = problems[0] or {}
            statement = str(first.get("statement_md") or "")
            title = str(first.get("title") or "")
            if title.startswith("练习：") and "编写一段程序读入数据并输出处理结果" in statement:
                return True
    return False
