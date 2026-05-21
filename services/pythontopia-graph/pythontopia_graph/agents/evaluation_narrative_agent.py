"""Deterministic narrative generator for the review hall (stage 13).

Per AGENTS.md, this agent **never invents numbers or KC codes**: every number
referenced in narrative_md must appear in `metrics`, and every KC code referenced
must appear in `top_kcs`. The grounding step rejects responses that violate
either rule.
"""

from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass

from pythontopia_graph.schemas.evaluation import (
    EvaluationMetric,
    EvaluationNarrativeRequest,
    EvaluationNarrativeResponse,
    GroundingResult,
    KcSummary,
    SafetyResult,
)

AGENT_NAME = "EvaluationNarrativeAgent"
NUMBER_PATTERN = re.compile(r"-?\d+(?:\.\d+)?")
KC_PATTERN = re.compile(r"PY43-KC-\d{3}")
FORBIDDEN_WORDS = ["杀", "自残", "歧视"]


@dataclass(frozen=True)
class _AllowedSet:
    numbers: frozenset[str]
    kc_codes: frozenset[str]


class EvaluationNarrativeAgent:
    AGENT_NAME = AGENT_NAME

    def narrate(self, request: EvaluationNarrativeRequest) -> EvaluationNarrativeResponse:
        if all(metric.value == 0 for metric in request.metrics) and not request.top_kcs:
            return self._idle_response(request)

        narrative = self._compose_narrative(request)
        recommendations = self._compose_recommendations(request)
        diff_explainer = self._compose_diff_explainer(request)

        allowed = self._build_allowed(request)
        narrative, recommendations, diff_explainer, grounding = self._enforce_grounding(
            narrative, recommendations, diff_explainer, allowed
        )

        safety = self._safety_check(narrative + " " + diff_explainer)
        signature = self._signature(request)
        status = "SUCCESS"
        if not safety.passed or not grounding.passed:
            status = "FAILED"
        return EvaluationNarrativeResponse(
            narrative_md=narrative,
            key_recommendations=recommendations[:5],
            path_diff_explainer=diff_explainer,
            safety_result=safety,
            grounding_result=grounding,
            deterministic_signature=signature,
            status=status,
        )

    def _compose_narrative(self, request: EvaluationNarrativeRequest) -> str:
        lines: list[str] = []
        lines.append("# 本周复盘")
        for metric in request.metrics:
            label = self._label(metric.metric_key)
            unit = metric.display_unit
            lines.append(f"- {label}：{metric.value}{unit}")
        if request.top_kcs:
            lines.append("\n## 关键 KC")
            for kc in request.top_kcs[:5]:
                arrow = "↑" if kc.delta_score >= 0 else "↓"
                lines.append(
                    f"- {kc.kc_code} {kc.kc_name}：掌握 {kc.mastery_score:.2f} ({arrow}{abs(kc.delta_score):.2f})"
                )
        if request.recent_events:
            lines.append("\n## 近期亮点")
            for event in request.recent_events[:3]:
                lines.append(f"- {event.event_kind}：{event.detail}")
        return "\n".join(lines)

    def _compose_recommendations(self, request: EvaluationNarrativeRequest) -> list[str]:
        recs: list[str] = []
        if request.profile_brief.has_recent_failures:
            recs.append("建议优先到 Bug 矿洞修复最近失败提交，巩固薄弱点。")
        weak = [kc for kc in request.top_kcs if kc.mastery_score < 0.5]
        if weak:
            kc = weak[0]
            recs.append(f"近期重点回顾 {kc.kc_code} {kc.kc_name}，平均掌握仅 {kc.mastery_score:.2f}。")
        improvements = [kc for kc in request.top_kcs if kc.delta_score > 0.05]
        if improvements:
            kc = improvements[0]
            recs.append(f"{kc.kc_code} 进步明显（+{kc.delta_score:.2f}），保持节奏。")
        if not recs:
            recs.append("本周表现稳定，可尝试新的拓展阅读资源。")
        return recs

    def _compose_diff_explainer(self, request: EvaluationNarrativeRequest) -> str:
        if not request.top_kcs:
            return "新路径相比当前路径变化不大，建议保留原路径。"
        weak = [kc for kc in request.top_kcs if kc.mastery_score < 0.5]
        focus = ", ".join(kc.kc_code for kc in weak[:3]) or "近期高进步 KC"
        return f"新路径会把节奏调整到 {focus}，删去已掌握 KC 的重复练习。"

    def _enforce_grounding(
        self,
        narrative: str,
        recommendations: list[str],
        diff: str,
        allowed: _AllowedSet,
    ) -> tuple[str, list[str], str, GroundingResult]:
        text = "\n".join([narrative, diff, *recommendations])
        # First validate KC codes against allow-list, then redact them so embedded digits
        # (e.g. `PY43-KC-001` → `43`, `-001`) are not double-counted as narrative numbers.
        unknown_kc: list[str] = []
        for kc in KC_PATTERN.findall(text):
            if kc not in allowed.kc_codes:
                unknown_kc.append(kc)
        scrubbed_text = KC_PATTERN.sub(" ", text)
        unknown_numbers: list[str] = []
        for number in NUMBER_PATTERN.findall(scrubbed_text):
            if number not in allowed.numbers:
                unknown_numbers.append(number)
        passed = not unknown_numbers and not unknown_kc
        result = GroundingResult(
            passed=passed,
            reasons=[] if passed else ["narrative references untracked numbers or KCs"],
            unknown_numbers=sorted(set(unknown_numbers)),
            unknown_kc=sorted(set(unknown_kc)),
        )
        return narrative, recommendations, diff, result

    def _safety_check(self, text: str) -> SafetyResult:
        for word in FORBIDDEN_WORDS:
            if word in text:
                return SafetyResult(passed=False, reasons=[f"forbidden word: {word}"])
        return SafetyResult(passed=True)

    def _signature(self, request: EvaluationNarrativeRequest) -> str:
        payload = request.model_dump(mode="json")
        encoded = json.dumps(payload, sort_keys=True).encode("utf-8")
        return hashlib.sha256(encoded).hexdigest()

    def _build_allowed(self, request: EvaluationNarrativeRequest) -> _AllowedSet:
        numbers: set[str] = set()
        for metric in request.metrics:
            for value in (metric.value, metric.numerator, metric.denominator):
                numbers.update(self._number_aliases(value))
            numbers.update(self._number_aliases(round(metric.value, 2)))
        for kc in request.top_kcs:
            for value in (kc.mastery_score, kc.delta_score, abs(kc.delta_score)):
                numbers.update(self._number_aliases(value))
                numbers.update(self._number_aliases(round(value, 2)))
        # 输入事件 detail 内出现的数字属于"真实事件锚点"，narrative 可以原样引用。
        for event in request.recent_events:
            for token in NUMBER_PATTERN.findall(KC_PATTERN.sub(" ", event.detail or "")):
                numbers.add(token)
        return _AllowedSet(
            numbers=frozenset(numbers),
            kc_codes=frozenset(kc.kc_code for kc in request.top_kcs),
        )

    def _number_aliases(self, value: float) -> list[str]:
        """Return both the f-string-friendly `0.10` form and the trimmed `0.1` form."""
        if value == int(value):
            return [str(int(value))]
        return [f"{value:.2f}", f"{value:.2f}".rstrip("0").rstrip(".")]

    def _idle_response(self, request: EvaluationNarrativeRequest) -> EvaluationNarrativeResponse:
        narrative = "# 本周复盘\n- 近期无活动\n本周指标全部为 0，建议先完成一次画像访谈或在知识田种 KC。"
        return EvaluationNarrativeResponse(
            narrative_md=narrative,
            key_recommendations=["先进入画像屋完成访谈。", "在知识田种植第一个 KC。"],
            path_diff_explainer="近期无数据，新路径保持系统默认。",
            safety_result=SafetyResult(passed=True),
            grounding_result=GroundingResult(passed=True),
            deterministic_signature=self._signature(request),
            status="PARTIAL",
        )

    def _label(self, metric_key: str) -> str:
        labels = {
            "kc_mastery_total": "KC 总掌握度",
            "kc_mastery_delta_total": "KC 掌握增量",
            "practice_pass_rate": "练习通过率",
            "bug_mine_resolve_rate": "Bug 矿洞修复率",
            "profile_confidence_delta": "画像置信度提升",
            "resource_consumption_count": "资源消化数",
        }
        return labels.get(metric_key, metric_key)
