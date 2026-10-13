"""/agents/boss/* 题型分派集成测试：文本题接地评分 vs 代码题沙箱，经 HTTP 端点验证。"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from pythontopia_graph.llm.provider import LlmResponse
from pythontopia_graph.main import app


@pytest.fixture()
def client() -> TestClient:
    return TestClient(app)


def _clear_llm_env(monkeypatch: pytest.MonkeyPatch) -> None:
    for key in [
        "PYT_LLM_PROVIDER", "OPENAI_BASE_URL", "OPENAI_API_KEY", "OPENAI_MODEL",
        "LLM_BASE_URL", "LLM_API_KEY", "LLM_MODEL",
        "PYT_OPENAI_BASE_URL", "PYT_OPENAI_API_KEY", "PYT_OPENAI_MODEL",
    ]:
        monkeypatch.delenv(key, raising=False)


# ───────── evaluate：文本题接地评分 ─────────


def test_evaluate_short_answer_good_text_passes(client: TestClient) -> None:
    response = client.post(
        "/agents/boss/evaluate",
        json={
            "problemId": "ai-c7-s1-short",
            "kind": "short_answer",
            "code": "递归函数是在定义里调用自身的函数，必须有基准情形作为终止条件。",
            "testCases": [
                {"input": "", "expectedOutput": "递归 函数 基准情形 终止条件", "isSample": True},
                {"input": "", "expectedOutput": "调用 自身"},
            ],
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["passed"] is True
    assert body["passedTestCases"] == body["totalTestCases"] == 2
    assert body["runtimeError"] is None


def test_evaluate_text_vs_code_dispatch(client: TestClient) -> None:
    """同一段散文：text 路径过关，code 路径语法错（证明分派修复了 prose-as-code）。"""

    prose = "变量是用来存储数据的名字，可以随时修改它的值。"
    points = [
        {"input": "", "expectedOutput": "变量 存储 数据 名字", "isSample": True},
    ]
    text_resp = client.post(
        "/agents/boss/evaluate",
        json={"problemId": "p1", "kind": "short_answer", "code": prose, "testCases": points},
    )
    code_resp = client.post(
        "/agents/boss/evaluate",
        json={"problemId": "p1", "kind": "fill_blank", "code": prose, "testCases": points},
    )
    assert text_resp.json()["passed"] is True
    assert code_resp.json()["passed"] is False


def test_evaluate_text_no_evidence_reports_no_evidence_found(client: TestClient) -> None:
    response = client.post(
        "/agents/boss/evaluate",
        json={
            "problemId": "p-no-ev",
            "kind": "pdf_question",
            "code": "随便写一段话来占位测试一下",
            "testCases": [
                {"input": "", "expectedOutput": "", "isSample": True},
                {"input": "", "expectedOutput": ""},
            ],
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["passed"] is False
    assert body["runtimeError"] == "no_evidence_found"


# ───────── evaluate：缺省 kind 仍走代码沙箱（向后兼容） ─────────


def test_evaluate_default_kind_uses_code_sandbox(client: TestClient) -> None:
    response = client.post(
        "/agents/boss/evaluate",
        json={
            "problemId": "code-default",
            "code": "a, b = map(int, input().split())\nprint(a + b)",
            "testCases": [
                {"input": "1 2", "expectedOutput": "3", "isSample": True},
                {"input": "10 20", "expectedOutput": "30"},
                {"input": "-1 1", "expectedOutput": "0"},
            ],
        },
    )
    body = response.json()
    assert body["passed"] is True
    assert body["passedTestCases"] == 3


# ───────── problem：payload 透传 kind + 课程包正确默认 KC ─────────


def test_problem_payload_includes_kind_from_preferred_kind(
    client: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    class _StubProvider:
        async def chat(self, messages, **kwargs):  # type: ignore[no-untyped-def]
            return LlmResponse(content_json={
                "title": "简答：解释 list",
                "statement": "用 30-100 字说明 list 列表如何按下标访问元素。",
                "reference_solution": "x = 1\nprint(x)",
                "test_cases": [
                    {"input": "1", "expected_output": "1", "is_sample": True},
                    {"input": "2", "expected_output": "1"},
                    {"input": "3", "expected_output": "1"},
                ],
                "hints": ["变量是命名的存储"],
            })

    from pythontopia_graph.routes import boss as boss_route

    monkeypatch.setattr(boss_route, "provider_from_env", lambda: _StubProvider())
    response = client.post(
        "/agents/boss/problem",
        json={
            "chapter": 2,
            "stage": 1,
            "kcCode": "PY43-KC-010",
            "difficulty": 1,
            "preferredKind": "short_answer",
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert "kind" in body
    if body.get("aiGenerated"):
        assert body["kind"] == "short_answer"


def test_problem_default_kc_is_course_pack_correct(
    client: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    """章 4 缺省 KC 现解析为课程包正确的 PY43-KC-031（旧实现错误地给 KC-026）。"""

    _clear_llm_env(monkeypatch)
    from pythontopia_graph.routes.boss import _resolve_kc

    assert _resolve_kc(4, "") == "PY43-KC-031"
    assert _resolve_kc(4, "") != "PY43-KC-026"
