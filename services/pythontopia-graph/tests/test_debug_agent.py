"""DebugAgent 单元测试。"""

from __future__ import annotations

from fastapi.testclient import TestClient

from pythontopia_graph.agents.debug_agent import (
    DebugAgent,
    counter_example_from_payload,
)
from pythontopia_graph.main import app
from pythontopia_graph.schemas.debug import (
    DebugAgentInput,
    HintLayer,
    VariableTrace,
)


def _input(verdict: str = "WA"):
    return DebugAgentInput(
        user_id=7,
        submission_id=10,
        problem_id=476,
        kc_codes=["PY43-KC-001"],
        verdict=verdict,
        code="print(input())\n",
        counter_example=counter_example_from_payload(
            stdin="5\n", expected="78.5375\n", actual="5\n"
        ),
        variable_trace=VariableTrace(steps=[], truncated=False),
        rag_pages=[12, 13],
    )


def test_produces_three_hint_layers_with_evidence() -> None:
    output = DebugAgent().analyse(_input())
    layers = sorted(layer.layer for layer in output.hint_layers)
    assert layers == [1, 2, 3]
    assert output.wrong_assumption.you_thought
    assert output.wrong_assumption.actually
    assert output.counter_example.stdin == "5\n"
    assert output.safety_passed is True


def test_re_verdict_changes_assumption_copy() -> None:
    output = DebugAgent().analyse(_input(verdict="RE"))
    assert "运行时" in output.wrong_assumption.actually


def test_layer3_does_not_contain_copyable_code() -> None:
    output = DebugAgent().analyse(_input())
    layer3 = next(layer for layer in output.hint_layers if layer.layer == 3)
    runnable_keywords = ["def ", "class ", "for ", "while "]
    runnable_lines = sum(1 for line in layer3.body.splitlines() if any(kw in line for kw in runnable_keywords))
    assert runnable_lines < 3


def test_unsafe_layer_is_downgraded() -> None:
    agent = DebugAgent()
    # 构造一个被 agent 误判为可粘贴代码的 hint，验证 downgrade 行为
    original_build = agent._build_hint_layers
    def replacement(payload):  # type: ignore[no-untyped-def]
        return [
            HintLayer(layer=1, headline="L1", body="思考一下输入输出"),
            HintLayer(layer=2, headline="L2", body="看代码"),
            HintLayer(
                layer=3,
                headline="L3",
                body="def foo():\n    for i in range(10):\n        print(i)",
            ),
        ]
    agent._build_hint_layers = replacement  # type: ignore[assignment]
    output = agent.analyse(_input())
    layer3 = next(layer for layer in output.hint_layers if layer.layer == 3)
    assert "脱敏" in layer3.headline
    assert "HINT_LAYER_3_LEAK" in output.safety_reasons


def test_http_route_returns_output() -> None:
    client = TestClient(app)
    payload = {
        "user_id": 7,
        "submission_id": 10,
        "problem_id": 476,
        "kc_codes": ["PY43-KC-001"],
        "verdict": "WA",
        "code": "print(1)",
        "counter_example": {
            "stdin": "5\n",
            "expected_stdout": "10\n",
            "your_stdout": "1\n",
            "diff_summary": "expect 10",
            "redacted": False,
        },
        "variable_trace": {"steps": [], "truncated": False},
        "rag_pages": [12],
    }
    response = client.post("/agents/debug/analyze", json=payload)
    assert response.status_code == 200
    body = response.json()
    assert len(body["hint_layers"]) == 3
    assert body["counter_example"]["stdin"] == "5\n"
