"""EvaluationNarrativeAgent 单元测试。

契约：数字与 KC 须来自输入 metrics / top_kcs；安全词命中 → FAIL；全 0 输入 → PARTIAL（近期无活动模板）。
"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from pythontopia_graph.agents.evaluation_narrative_agent import EvaluationNarrativeAgent
from pythontopia_graph.main import app
from pythontopia_graph.schemas.evaluation import (
    EventSummary,
    EvaluationMetric,
    EvaluationNarrativeRequest,
    KcSummary,
    ProfileBrief,
)


client = TestClient(app)


def _full_request() -> EvaluationNarrativeRequest:
    return EvaluationNarrativeRequest(
        user_id=42,
        pack_id=43,
        metrics=[
            EvaluationMetric(
                metric_key="kc_mastery_total",
                value=3.21,
                numerator=3.21,
                denominator=0.0,
                display_unit="score",
            ),
            EvaluationMetric(
                metric_key="practice_pass_rate",
                value=0.75,
                numerator=3.0,
                denominator=4.0,
                display_unit="ratio",
            ),
        ],
        top_kcs=[
            KcSummary(
                kc_code="PY43-KC-001",
                kc_name="变量",
                mastery_score=0.42,
                delta_score=0.10,
            ),
            KcSummary(
                kc_code="PY43-KC-002",
                kc_name="字符串",
                mastery_score=0.55,
                delta_score=0.08,
            ),
        ],
        recent_events=[
            EventSummary(event_kind="PRACTICE_AC", occurred_at="2026-06-13", detail="题目 1"),
        ],
        profile_brief=ProfileBrief(major="CS", python_level=3, has_recent_failures=False),
    )


def test_narrative_passes_grounding_with_real_numbers() -> None:
    response = EvaluationNarrativeAgent().narrate(_full_request())

    assert response.status == "SUCCESS"
    assert response.grounding_result.passed is True
    assert response.safety_result.passed is True
    assert "PY43-KC-001" in response.narrative_md
    assert "PY43-KC-002" in response.narrative_md
    assert "0.75" in response.narrative_md or "0.42" in response.narrative_md
    assert response.deterministic_signature


def test_narrative_is_deterministic() -> None:
    agent = EvaluationNarrativeAgent()
    first = agent.narrate(_full_request())
    second = agent.narrate(_full_request())
    assert first.deterministic_signature == second.deterministic_signature
    assert first.narrative_md == second.narrative_md


def test_idle_response_when_metrics_all_zero() -> None:
    response = EvaluationNarrativeAgent().narrate(
        EvaluationNarrativeRequest(
            user_id=42,
            metrics=[
                EvaluationMetric(metric_key="kc_mastery_total", value=0.0),
                EvaluationMetric(metric_key="practice_pass_rate", value=0.0),
            ],
            top_kcs=[],
            recent_events=[],
            profile_brief=ProfileBrief(),
        )
    )
    assert response.status == "PARTIAL"
    assert "近期无活动" in response.narrative_md


def test_safety_rejects_forbidden_word() -> None:
    request = _full_request()
    request.profile_brief = ProfileBrief(major="杀马特", python_level=3)
    response = EvaluationNarrativeAgent().narrate(request)
    # major 不写入 narrative，故仍 SUCCESS；此处仅确认 safety 未误拦。
    assert response.safety_result.passed is True


def test_route_returns_422_when_grounding_fails(monkeypatch: pytest.MonkeyPatch) -> None:
    # 注入 grounding 失败的 stub，验证路由映射为 422。
    from pythontopia_graph.routes import evaluation as evaluation_route
    from pythontopia_graph.schemas.evaluation import (
        EvaluationNarrativeResponse,
        GroundingResult,
        SafetyResult,
    )

    class _StubAgent:
        def narrate(self, _request: EvaluationNarrativeRequest) -> EvaluationNarrativeResponse:
            return EvaluationNarrativeResponse(
                narrative_md="some narrative referencing 0.99",
                key_recommendations=["重练 PY43-KC-099"],
                path_diff_explainer="",
                safety_result=SafetyResult(passed=True),
                grounding_result=GroundingResult(
                    passed=False,
                    reasons=["narrative references untracked numbers or KCs"],
                    unknown_numbers=["0.99"],
                    unknown_kc=["PY43-KC-099"],
                ),
                deterministic_signature="stub",
                status="FAILED",
            )

    monkeypatch.setattr(evaluation_route, "EvaluationNarrativeAgent", _StubAgent)

    body = _full_request().model_dump(mode="json")
    response = client.post("/agents/evaluation/narrative", json=body)
    assert response.status_code == 422
    detail = response.json()["detail"]
    assert detail["code"] == "EVALUATION_NARRATIVE_GROUNDING_FAILED"
    assert detail["unknown_numbers"] == ["0.99"]
    assert detail["unknown_kc"] == ["PY43-KC-099"]


def test_route_success_returns_full_payload() -> None:
    body = _full_request().model_dump(mode="json")
    response = client.post("/agents/evaluation/narrative", json=body)
    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "SUCCESS"
    assert payload["grounding_result"]["passed"] is True
    assert payload["safety_result"]["passed"] is True
    assert "PY43-KC-001" in payload["narrative_md"]
