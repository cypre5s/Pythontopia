"""LLM 出题工厂单元测试：stub provider 覆盖正常/畸形 JSON、fence 包裹、嵌套 problem 字段及旧 provider 兼容。"""

from __future__ import annotations

import json
from typing import Any

import pytest

from pythontopia_graph.agents.boss.llm_factory import build_llm_problem_factory
from pythontopia_graph.llm.provider import LlmResponse


class _StubProvider:
    def __init__(self, response: LlmResponse, *, accept_response_format: bool = True) -> None:
        self._response = response
        self._accept_response_format = accept_response_format
        self.last_kwargs: dict[str, Any] = {}
        self.last_messages: list[dict[str, str]] = []

    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        self.last_messages = messages
        self.last_kwargs = dict(kwargs)
        if "response_format" in kwargs and not self._accept_response_format:
            raise TypeError("response_format unsupported")
        return self._response


def _good_payload() -> dict[str, Any]:
    return {
        "title": "测试题",
        "statement": "读入一个数 n，输出 n。",
        "reference_solution": "n = int(input())\nprint(n)",
        "test_cases": [
            {"input": "1", "expected_output": "1", "is_sample": True},
            {"input": "2", "expected_output": "2"},
            {"input": "3", "expected_output": "3"},
        ],
        "hints": ["int 转换", "print 输出"],
    }


@pytest.mark.asyncio
async def test_factory_builds_boss_problem_from_content_json() -> None:
    provider = _StubProvider(LlmResponse(content_json=_good_payload()))
    factory = build_llm_problem_factory(provider)
    problem = await factory(chapter=1, stage=1, kc_code="PY43-KC-001", difficulty=2)
    assert problem.title == "测试题"
    assert problem.kc_code == "PY43-KC-001"
    assert problem.chapter == 1
    assert problem.stage == 1
    assert problem.difficulty == 2
    assert len(problem.test_cases) == 3
    assert problem.test_cases[0].is_sample
    assert problem.metadata.get("source") == "llm"
    assert problem.problem_id.startswith("ai-c1-s1-py43_kc_001")
    assert "response_format" in provider.last_kwargs


@pytest.mark.asyncio
async def test_factory_disables_reasoning_for_structured_problem_generation() -> None:
    provider = _StubProvider(LlmResponse(content_json=_good_payload()))

    await build_llm_problem_factory(provider)(
        chapter=1, stage=1, kc_code="PY43-KC-001", difficulty=2
    )

    assert provider.last_kwargs["reasoning_effort"] == "none"


@pytest.mark.asyncio
async def test_factory_parses_content_text_json() -> None:
    payload = _good_payload()
    provider = _StubProvider(LlmResponse(content_text=json.dumps(payload)))
    factory = build_llm_problem_factory(provider)
    problem = await factory(chapter=2, stage=3, kc_code="PY43-KC-006", difficulty=4)
    assert problem.chapter == 2
    assert problem.stage == 3
    assert problem.difficulty == 4
    assert len(problem.test_cases) == 3


@pytest.mark.asyncio
async def test_factory_strips_code_fence() -> None:
    payload = json.dumps(_good_payload())
    fenced = f"```json\n{payload}\n```"
    provider = _StubProvider(LlmResponse(content_text=fenced))
    factory = build_llm_problem_factory(provider)
    problem = await factory(chapter=1, stage=1, kc_code="PY43-KC-001", difficulty=1)
    assert problem.title == "测试题"


@pytest.mark.asyncio
async def test_factory_repairs_json_object_with_wrapping_text_and_raw_newlines() -> None:
    """OpenAI-compatible models sometimes return JSON-ish text despite json_object mode."""

    text = """下面是题目 JSON：
{
  "title": "换行题",
  "statement": "读入一个数 n，
输出 n。",
  "reference_solution": "n = int(input())
print(n)",
  "test_cases": [
    {"input": "1", "expected_output": "1", "is_sample": true},
    {"input": "2", "expected_output": "2"},
    {"input": "3", "expected_output": "3"}
  ],
  "hints": ["int 转换"]
}
请直接使用。"""
    provider = _StubProvider(LlmResponse(content_text=text))
    factory = build_llm_problem_factory(provider)
    problem = await factory(chapter=1, stage=1, kc_code="PY43-KC-001", difficulty=1)
    assert problem.title == "换行题"
    assert problem.statement == "读入一个数 n，\n输出 n。"
    assert "print(n)" in problem.reference_solution
    assert len(problem.test_cases) == 3


@pytest.mark.asyncio
async def test_factory_repairs_common_llm_jsonish_quotes_and_trailing_commas() -> None:
    text = """{
  "title": "点亮灯牌",
  "statement": "输出字符串："灯亮了"",
  "reference_solution": "print("灯亮了")",
  "test_cases": [
    {"input": "", "expected_output": "灯亮了", "is_sample": true},
    {"input": "", "expected_output": "灯亮了"},
    {"input": "", "expected_output": "灯亮了"},
  ],
  "hints": ["使用 print"],
}"""
    provider = _StubProvider(LlmResponse(content_text=text))
    factory = build_llm_problem_factory(provider)
    problem = await factory(chapter=1, stage=1, kc_code="PY43-KC-001", difficulty=1)
    assert problem.title == "点亮灯牌"
    assert problem.reference_solution == 'print("灯亮了")'
    assert len(problem.test_cases) == 3


@pytest.mark.asyncio
async def test_factory_handles_nested_problem_field() -> None:
    payload = {"problem": _good_payload()}
    provider = _StubProvider(LlmResponse(content_json=payload))
    factory = build_llm_problem_factory(provider)
    problem = await factory(chapter=1, stage=1, kc_code="PY43-KC-001", difficulty=1)
    assert problem.title == "测试题"


@pytest.mark.asyncio
async def test_factory_raises_on_empty_response() -> None:
    provider = _StubProvider(LlmResponse())
    factory = build_llm_problem_factory(provider)
    with pytest.raises(ValueError):
        await factory(chapter=1, stage=1, kc_code="PY43-KC-001", difficulty=1)


@pytest.mark.asyncio
async def test_factory_raises_on_malformed_text() -> None:
    provider = _StubProvider(LlmResponse(content_text="not json at all"))
    factory = build_llm_problem_factory(provider)
    with pytest.raises(ValueError):
        await factory(chapter=1, stage=1, kc_code="PY43-KC-001", difficulty=1)


@pytest.mark.asyncio
async def test_factory_raises_when_no_test_cases() -> None:
    payload = _good_payload()
    payload["test_cases"] = []
    provider = _StubProvider(LlmResponse(content_json=payload))
    factory = build_llm_problem_factory(provider)
    with pytest.raises(ValueError):
        await factory(chapter=1, stage=1, kc_code="PY43-KC-001", difficulty=1)


@pytest.mark.asyncio
async def test_factory_retries_without_response_format_kwarg() -> None:
    """不支持 response_format kwarg 的旧 provider 应自动回落重试。"""

    provider = _StubProvider(
        LlmResponse(content_json=_good_payload()),
        accept_response_format=False,
    )
    factory = build_llm_problem_factory(provider)
    problem = await factory(chapter=1, stage=1, kc_code="PY43-KC-001", difficulty=1)
    assert problem.title == "测试题"
    # 回落重试后不再传入 response_format
    assert "response_format" not in provider.last_kwargs


@pytest.mark.asyncio
async def test_bug_mine_prompt_preserves_stdout_judge_contract() -> None:
    provider = _StubProvider(LlmResponse(content_json=_good_payload()))
    factory = build_llm_problem_factory(provider)

    await factory(
        chapter=1,
        stage=1,
        kc_code="PY43-KC-001",
        difficulty=1,
        preferred_kind="bug_mine",
    )

    user_prompt = provider.last_messages[-1]["content"]
    assert "Bug 矿洞仍然是可执行 Python 判题题" in user_prompt
    assert "stdin 输入" in user_prompt
    assert "stdout 输出" in user_prompt
    assert "至少 3 条 test_cases" in user_prompt


@pytest.mark.asyncio
async def test_debug_prompt_preserves_stdout_judge_contract() -> None:
    provider = _StubProvider(LlmResponse(content_json=_good_payload()))
    factory = build_llm_problem_factory(provider)

    await factory(
        chapter=1,
        stage=1,
        kc_code="PY43-KC-001",
        difficulty=1,
        preferred_kind="debug",
    )

    user_prompt = provider.last_messages[-1]["content"]
    assert "改错题仍然是可执行 Python 判题题" in user_prompt
    assert "stdin 输入" in user_prompt
    assert "stdout 输出" in user_prompt
    assert "至少 3 条 test_cases" in user_prompt


@pytest.mark.asyncio
async def test_profile_prompt_uses_preferences_but_ignores_inferred_ability_and_emotion() -> None:
    provider = _StubProvider(LlmResponse(content_json=_good_payload()))
    factory = build_llm_problem_factory(provider)

    await factory(
        chapter=4,
        stage=1,
        kc_code="PY43-KC-031",
        difficulty=2,
        profile_signals={
            "major": "NON_CS_HUMANITIES",
            "learningGoalKind": "JOB",
            "targetKcCodes": ["PY43-KC-031"],
            "fieldDependent": 0.7,
            "sequentialVsHolistic": -0.5,
            "successRate": 0.2,
            "resilience": 0.1,
            "sessionMinutesPerDay": 20,
        },
    )

    user_prompt = provider.last_messages[-1]["content"]
    assert "人文背景" in user_prompt
    assert "求职实践" in user_prompt
    assert "20 分钟" in user_prompt
    assert "按步骤" not in user_prompt
    assert "通过率" not in user_prompt
    assert "抗挫" not in user_prompt


@pytest.mark.asyncio
async def test_boss_problem_prompt_uses_town_hub_learning_scenes() -> None:
    provider = _StubProvider(LlmResponse(content_json=_good_payload()))
    await build_llm_problem_factory(provider)(
        chapter=1, stage=1, kc_code="PY43-KC-001", difficulty=1
    )

    system_prompt = provider.last_messages[0]["content"]
    assert "图书馆" in system_prompt
    assert "工坊" in system_prompt
    assert "浇花" not in system_prompt
    assert "收菜" not in system_prompt


@pytest.mark.asyncio
async def test_chapter_1_prompt_repeats_json_and_syntax_contract() -> None:
    provider = _StubProvider(LlmResponse(content_json=_good_payload()))
    factory = build_llm_problem_factory(provider)

    await factory(
        chapter=1,
        stage=2,
        kc_code="PY43-KC-004",
        difficulty=4,
        preferred_kind="dual_track",
    )

    user_prompt = provider.last_messages[-1]["content"]
    assert "只输出合法 JSON 对象" in user_prompt
    assert "至少 3 条 test_cases" in user_prompt
    assert "stdin 输入" in user_prompt
    assert "stdout 输出" in user_prompt
    assert "第 1 章只能使用 print / input / 变量赋值 / int / float / str / 简单四则运算" in user_prompt
    assert "禁止 if、for、while、list、dict、def、class、import" in user_prompt
