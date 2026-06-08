"""LLM 出题工厂：提示模板、``LlmProvider`` 对接与 JSON 解析；审查由 orchestrator 负责。"""

from __future__ import annotations

import json
import logging
import re
from typing import Any, Awaitable, Callable

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse

from .schemas import BossProblem, TestCase

LOG = logging.getLogger(__name__)


PROBLEM_GEN_SYSTEM_PROMPT = """你是 Python 编程题命题官，专注「Python 语言基础」（packId=43）课程的初学者题目。

【输出协议（不可违反）】
你必须返回**合法 JSON 对象**（``response_format=json_object``），schema：
{
  "title": "题目标题，中文，不超过 20 字",
  "statement": "题面，中文，含输入格式 + 输出格式 + 1 个 sample；总长 <= 600 字",
  "reference_solution": "可以独立运行的 Python 3 代码字符串，stdin 读输入，stdout 写输出",
  "test_cases": [
    {"input": "...", "expected_output": "...", "is_sample": true},
    {"input": "...", "expected_output": "..."},
    {"input": "...", "expected_output": "..."}
  ],
  "hints": ["短提示1", "短提示2"]
}

【硬性约束】
- 至少 3 条 test_cases，且至少 1 条 is_sample=true。
- reference_solution 必须能在 5 秒内跑过所有 test_cases。
- reference_solution 禁止 import：socket / subprocess / shutil / ctypes /
  multiprocessing / urllib.request / requests / httpx / smtplib / ftplib / ssl。
- reference_solution 仅使用对应章节及之前章节的 Python 语法
  （章 1 = print/input/赋值；章 2 = list/dict/str；章 3 = if/elif/else；
   章 4 = for/while/break/continue；章 5 = def/return；章 6 = class/继承；
   章 7 = import/try/except/with）。
- 题目必须明确扣紧给定 KC 关键词，至少在 statement 或 reference_solution 中
  出现 1 次中文关键词或对应英文语法。

【风格】
- 题面像星露谷小镇生活：浇花、收菜、点灯、记账等具体场景；
- 不要在题面里写「这是 KC-xxx」之类的元信息；玩家只关心故事。
"""


def _strip_json_fence(text: str) -> str:
    """剥离 ```json``` 围栏后再 ``json.loads``。"""

    fence = re.match(r"^\s*```(?:json)?\s*(.+?)\s*```\s*$", text, re.DOTALL)
    if fence:
        return fence.group(1)
    return text


def _coerce_problem_payload(response: LlmResponse) -> dict[str, Any]:
    if response.content_json:
        return response.content_json
    if response.content_text:
        try:
            return json.loads(_strip_json_fence(response.content_text))
        except json.JSONDecodeError as exc:
            raise ValueError(f"LLM returned non-JSON content: {exc}") from exc
    raise ValueError("LLM response has neither content_json nor content_text")


def _coerce_test_cases(raw: Any) -> list[TestCase]:
    if not isinstance(raw, list):
        return []
    cases: list[TestCase] = []
    for item in raw:
        if not isinstance(item, dict):
            continue
        cases.append(
            TestCase(
                input=str(item.get("input", "")),
                expected_output=str(item.get("expected_output", "")),
                is_sample=bool(item.get("is_sample", False)),
            )
        )
    return cases


ProblemFactory = Callable[..., Awaitable[BossProblem]]


def build_llm_problem_factory(provider: LlmProvider) -> ProblemFactory:
    """构建异步出题 factory；解析失败抛 ``ValueError``，由 orchestrator 重试或回落 golden。"""

    async def factory(
        *,
        chapter: int,
        stage: int,
        kc_code: str,
        difficulty: int,
        temperature: float = 0.4,
        preferred_kind: str | None = None,
        profile_signals: dict[str, Any] | None = None,
    ) -> BossProblem:
        # 将画像信号与关卡题型偏好写入 prompt。
        extras: list[str] = []
        if preferred_kind:
            kind_map = {
                "fill_blank": "代码填空（题面包含 ___BLANK___ 让玩家填）",
                "debug": "改错题（给出有 bug 的代码片段让玩家修）",
                "short_answer": "简答题（学生用 30-100 字解释概念，AI 评分）",
                "closed_loop": "调试闭环（堆栈 trace 定位 → 修复）",
                "dual_track": "双轨（前端 Pyodide 预览 + 服务端判题）",
                "multi_round": "3 题组合（同主题 3 道渐进式小题）",
                "teach_back": "教学反转（学生向 NPC 解释 KC，AI 评分）",
                "bug_mine": "Bug 矿洞（30 秒限时 ImportError/NameError 突袭）",
                "pdf_question": "课件问答（基于课件页 1-3 单选 + 引用页号）",
            }
            extras.append(
                f"题型偏好: {kind_map.get(preferred_kind, preferred_kind)}，请按对应玩法出题。"
            )
        if profile_signals:
            weak = profile_signals.get("weakKcCodes") or []
            if weak:
                extras.append(f"学生弱项 KC: {','.join(weak[:4])} —— 优先围绕这些 KC 出题。")
            level = profile_signals.get("pythonSelfRatedLevel")
            if isinstance(level, int):
                extras.append(f"学生 Python 自评水平: {level}/5。")
            success = profile_signals.get("successRate")
            if isinstance(success, (int, float)):
                if success < 0.4:
                    extras.append("学生近况通过率偏低，请降低难度且多给上下文。")
                elif success > 0.75:
                    extras.append("学生近况通过率较高，可以略提高综合度。")
            media = profile_signals.get("mediaPriority") or []
            if "DIAGRAM" in media:
                extras.append("学生偏好可视化，可在 statement 末尾用 mermaid 或 ascii 简图。")
            resilience = profile_signals.get("resilience")
            if isinstance(resilience, (int, float)) and resilience < 0.4:
                extras.append("学生抗挫折度较低，请把 hints 写得鼓励一些。")

        extras_block = ("\n\n【画像与题型提示】\n" + "\n".join(extras)) if extras else ""

        user_prompt = (
            f"请为第 {chapter} 章 Stage {stage} 出一道难度 {difficulty}/5 的 Python 题。\n"
            f"目标 KC: {kc_code}\n"
            f"难度参考：1=只需 1 行 print；3=两步运算；5=综合 3+ 步逻辑。"
            f"{extras_block}"
        )
        messages = [
            {"role": "system", "content": PROBLEM_GEN_SYSTEM_PROMPT},
            {"role": "user", "content": user_prompt},
        ]
        try:
            response = await provider.chat(
                messages,
                temperature=temperature,
                max_tokens=1500,
                response_format={"type": "json_object"},
            )
        except TypeError:
            response = await provider.chat(
                messages, temperature=temperature, max_tokens=1500
            )

        data = _coerce_problem_payload(response)
        # 兼容嵌套在 problem 键下的响应体
        if "title" not in data and isinstance(data.get("problem"), dict):
            data = data["problem"]

        test_cases = _coerce_test_cases(data.get("test_cases", []))
        if not test_cases:
            raise ValueError("LLM produced no usable test_cases")

        hints_raw = data.get("hints", [])
        hints = [str(h) for h in hints_raw] if isinstance(hints_raw, list) else []

        slug = kc_code.lower().replace("-", "_")
        meta = {"source": "llm", "temperature": temperature}
        if preferred_kind:
            meta["preferred_kind"] = preferred_kind
        if profile_signals:
            meta["profile_signals_used"] = sorted(profile_signals.keys())
        return BossProblem(
            problem_id=f"ai-c{chapter}-s{stage}-{slug}",
            chapter=chapter,
            stage=stage,
            kc_code=kc_code,
            title=str(data.get("title", "")).strip(),
            statement=str(data.get("statement", "")).strip(),
            reference_solution=str(data.get("reference_solution", "")),
            test_cases=test_cases,
            difficulty=difficulty,
            hints=hints,
            metadata=meta,
        )

    return factory
