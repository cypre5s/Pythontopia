"""R-13 §5 Phase 6 续：LLM 出题工厂。

封装：
    * 系统提示 + 用户提示模板（中文，强调输出 JSON / 沙箱限制 / KC 范围）；
    * 与 ``LlmProvider`` 抽象的对接（OpenAiCompat / Spark / DryRun 均可）；
    * JSON 解析容错（content_json 优先 / content_text 兜底 fence 剥离）。

不在这里跑 5 重审查；交给 ``orchestrator.generate_boss_problem``。
"""

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
    """LLM 偶尔会包 ```json ... ``` 围栏，剥掉再 json.loads。"""

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
    """返回一个异步 factory，签名 ``(chapter, stage, kc_code, difficulty, temperature)``。

    这个 factory 直接调 LLM 一次，把响应转成 ``BossProblem``。任何解析失败抛
    ``ValueError``；orchestrator 会捕获后退到下一次重试，最终回落到 golden。
    """

    async def factory(
        *,
        chapter: int,
        stage: int,
        kc_code: str,
        difficulty: int,
        temperature: float = 0.4,
    ) -> BossProblem:
        user_prompt = (
            f"请为第 {chapter} 章 Stage {stage} 出一道难度 {difficulty}/5 的 Python 题。\n"
            f"目标 KC: {kc_code}\n"
            f"难度参考：1=只需 1 行 print；3=两步运算；5=综合 3+ 步逻辑。"
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
        # LLM 可能把 problem 字段嵌套在 problem / boss 里，挖一下
        if "title" not in data and isinstance(data.get("problem"), dict):
            data = data["problem"]

        test_cases = _coerce_test_cases(data.get("test_cases", []))
        if not test_cases:
            raise ValueError("LLM produced no usable test_cases")

        hints_raw = data.get("hints", [])
        hints = [str(h) for h in hints_raw] if isinstance(hints_raw, list) else []

        slug = kc_code.lower().replace("-", "_")
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
            metadata={"source": "llm", "temperature": temperature},
        )

    return factory
