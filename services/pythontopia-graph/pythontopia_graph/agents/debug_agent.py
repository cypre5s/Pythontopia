"""DebugAgent：教育导向的确定性调试提示生成器。

不输出可复制粘贴的完整解答；第 3 层提示最多 prose + 单行伪代码，由 safety 审查强制约束。
"""

from __future__ import annotations

import re
from collections import Counter

from pythontopia_graph.schemas.debug import (
    CounterExampleCard,
    DebugAgentInput,
    DebugAgentOutput,
    HintLayer,
    RetryAdvice,
    VariableTrace,
    WrongAssumptionCard,
)

AGENT_NAME = "DebugAgent"


class DebugAgent:
    """据判题反例与变量轨迹生成三层渐进提示；泄答检测失败时降级第 3 层。"""

    AGENT_NAME = AGENT_NAME

    def analyse(self, payload: DebugAgentInput) -> DebugAgentOutput:
        counter_example = payload.counter_example
        trace = payload.variable_trace
        if counter_example.redacted:
            counter_example = CounterExampleCard(
                stdin="隐藏测试用例（输入已隐藏）",
                expected_stdout="期望输出已隐藏",
                your_stdout="实际输出已隐藏",
                diff_summary="隐藏测试数据已脱敏，请按题面检查边界条件。",
                redacted=True,
            )
            trace = VariableTrace(steps=[], truncated=False)
        assumption = self._build_assumption(payload)
        hint_layers = self._build_hint_layers(payload)
        retry_advice = RetryAdvice(
            summary="先复读题面输入/输出，再逐行对比变量轨迹与预期。",
            expected_kc_codes=list(payload.kc_codes),
        )

        safety_passed, safety_reasons = self._safety_review(hint_layers)
        if not safety_passed:
            hint_layers = self._downgrade_layer3(hint_layers)
            safety_reasons.append("LAYER3_DOWNGRADED")
            safety_passed = True

        return DebugAgentOutput(
            counter_example=counter_example,
            variable_trace_summary=self._summarise_trace(trace),
            wrong_assumption=assumption,
            hint_layers=hint_layers,
            retry_advice=retry_advice,
            safety_passed=safety_passed,
            safety_reasons=safety_reasons,
        )

    def _build_assumption(self, payload: DebugAgentInput) -> WrongAssumptionCard:
        diff = payload.counter_example.diff_summary
        if payload.counter_example.redacted:
            you_thought = "你的程序未能通过一条隐藏测试用例。"
            actually = "具体输入和期望输出已隐藏，请根据题面检查边界条件和输出格式。"
        elif payload.verdict == "WA":
            you_thought = f"你以为输出 '{payload.counter_example.your_stdout.strip()[:80]}' 已经满足题意。"
            actually = (
                f"实际期望是 '{payload.counter_example.expected_stdout.strip()[:80]}'。"
                f"差异：{diff[:120]}"
            )
        elif payload.verdict == "RE":
            you_thought = "你以为代码能在所有输入下顺利运行。"
            actually = "实际触发了运行时错误，请关注末尾抛出的异常类型与所在行。"
        elif payload.verdict == "TLE":
            you_thought = "你以为算法在样例规模下也是高效的。"
            actually = "实际超过了单 case 5 秒上限，可能是死循环或量级问题。"
        elif payload.verdict == "CE":
            you_thought = "你以为这段代码不会被静态检查拦截。"
            actually = "实际包含被禁用的导入或表达式（请查看 failure_reason）。"
        else:
            you_thought = "你以为提交能通过。"
            actually = "实际判题失败，请综合反例与轨迹再次审视。"
        return WrongAssumptionCard(
            you_thought=you_thought,
            actually=actually,
            evidence_kc_codes=list(payload.kc_codes),
        )

    def _build_hint_layers(self, payload: DebugAgentInput) -> list[HintLayer]:
        layer1 = HintLayer(
            layer=1,
            headline="先想清楚一个问题",
            body=(
                "判题显示有一条隐藏测试用例未通过。请结合题面自行构造边界输入，逐步检查程序应如何产生输出。"
                if payload.counter_example.redacted
                else (
                    "在你点开下一层提示之前，请尝试回答："
                    "如果给定输入 '"
                    + payload.counter_example.stdin.strip()[:80]
                    + "'，你的程序应该按什么步骤产生输出？"
                )
            ),
            related_pages=payload.rag_pages[:2],
        )
        suspicious_line = self._guess_suspicious_line(payload.code)
        layer2 = HintLayer(
            layer=2,
            headline="检查关键代码段",
            body=(
                "具体输入和输出已隐藏，优先检查边界条件、分支和输出格式。"
                if payload.counter_example.redacted
                else (
                    "对比变量轨迹与你的预期，重点检查第 "
                    + str(suspicious_line)
                    + " 行附近：变量取值、循环边界和分支条件。"
                    "若与题面输入/输出描述不符，先修正这一处。"
                )
            ),
            related_pages=payload.rag_pages[:3],
        )
        layer3 = HintLayer(
            layer=3,
            headline="改造方向",
            body=(
                "可尝试用以下伪代码思路：先读取并校验输入，再按题面顺序计算中间结果，最后用 f-string 输出固定小数位。"
                " 注意：这里只是思路提示，请避免直接复制粘贴。"
            ),
            related_pages=payload.rag_pages[:3],
        )
        return [layer1, layer2, layer3]

    def _safety_review(self, hint_layers: list[HintLayer]) -> tuple[bool, list[str]]:
        reasons: list[str] = []
        for layer in hint_layers:
            if self._contains_runnable_code(layer.body):
                reasons.append(f"HINT_LAYER_{layer.layer}_LEAK")
        return (not reasons, reasons)

    def _contains_runnable_code(self, text: str) -> bool:
        # 任何连续 ≥3 行的、包含 Python 关键字的段都视为可粘贴代码并触发降级。
        runnable_lines = 0
        for line in text.splitlines():
            stripped = line.strip()
            if not stripped:
                runnable_lines = 0
                continue
            if re.search(r"^(def\s|class\s|for\s|while\s|if\s|print\(|return\s|import\s)", stripped):
                runnable_lines += 1
                if runnable_lines >= 3:
                    return True
            else:
                runnable_lines = 0
        return False

    def _downgrade_layer3(self, hint_layers: list[HintLayer]) -> list[HintLayer]:
        downgraded = []
        for layer in hint_layers:
            if layer.layer == 3:
                downgraded.append(
                    HintLayer(
                        layer=3,
                        headline="改造方向（已脱敏）",
                        body="请按题面输入输出顺序改写，避免复制提示中的代码片段。",
                        related_pages=layer.related_pages,
                    )
                )
            else:
                downgraded.append(layer)
        return downgraded

    def _guess_suspicious_line(self, code: str) -> int:
        lines = code.splitlines()
        if not lines:
            return 1
        keyword_counter: Counter[int] = Counter()
        for idx, line in enumerate(lines, start=1):
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            keyword_counter[idx] = len(stripped)
        if not keyword_counter:
            return 1
        return max(keyword_counter, key=lambda k: keyword_counter[k])

    def _summarise_trace(self, trace: VariableTrace) -> VariableTrace:
        if len(trace.steps) <= 20:
            return trace
        return VariableTrace(steps=trace.steps[:20], truncated=True)


def counter_example_from_payload(
    *, stdin: str, expected: str, actual: str, redacted: bool = False
) -> CounterExampleCard:
    """从 stdin/期望/实际输出构造 CounterExampleCard，含差异摘要。"""
    diff_summary = _diff_summary(expected, actual)
    return CounterExampleCard(
        stdin=stdin,
        expected_stdout=expected,
        your_stdout=actual,
        diff_summary=diff_summary,
        redacted=redacted,
    )


def _diff_summary(expected: str, actual: str) -> str:
    expected_lines = expected.splitlines()
    actual_lines = actual.splitlines()
    diffs: list[str] = []
    for i, exp in enumerate(expected_lines):
        if i >= len(actual_lines):
            diffs.append(f"缺少第 {i + 1} 行输出")
            break
        if actual_lines[i] != exp:
            diffs.append(f"第 {i + 1} 行：期望 '{exp}'，实际 '{actual_lines[i]}'")
    if len(actual_lines) > len(expected_lines):
        diffs.append(f"额外输出 {len(actual_lines) - len(expected_lines)} 行")
    if not diffs:
        diffs.append("文本完全一致，但 trailing 空白或类型可能不同")
    return "; ".join(diffs[:3])
