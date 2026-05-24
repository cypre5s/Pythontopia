"""R-13 §5 Phase 6 续：LLM 出题工厂单元测试。

不调真 LLM，用 stub provider 喂结构化 JSON / 各种坏数据，确保：
    * 正常 JSON → 正确转换成 BossProblem；
    * fence-wrapped JSON 也能解析；
    * 缺失 test_cases / 非法 JSON / 嵌套 problem 字段 / 不带 response_format kwarg 的旧 provider 兼容；
    * problem_id / metadata 标记正确。
"""

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
    """旧 provider（如 spark adapter 早期版本）不支持 response_format kwarg 时应回落。"""

    provider = _StubProvider(
        LlmResponse(content_json=_good_payload()),
        accept_response_format=False,
    )
    factory = build_llm_problem_factory(provider)
    problem = await factory(chapter=1, stage=1, kc_code="PY43-KC-001", difficulty=1)
    assert problem.title == "测试题"
    # 第二次调用应已剔除 response_format kwarg
    assert "response_format" not in provider.last_kwargs
