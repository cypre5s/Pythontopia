from fastapi.testclient import TestClient

from pythontopia_graph.llm.provider import DryRunProvider
from pythontopia_graph.main import app


def test_tutor_validation_error_does_not_echo_inline_image_input(monkeypatch) -> None:
    # 校验应在服务入口验证，不需要 runner 配置真实 LLM。
    monkeypatch.setattr(app.state, "llm_provider", DryRunProvider(), raising=False)
    marker = "PRIVATE_IMAGE_MARKER"
    response = TestClient(app).post(
        "/agents/tutor-workflow/run/stream",
        json={
            "session_id": "validation-test",
            "user_id": 7,
            "event": "CHAT",
            "problem": {"problem_id": 1},
            "event_data": {"message": f"data:image/png;base64,{marker}"},
        },
        headers={"X-Internal-Subject": "user:7"},
    )

    assert response.status_code == 422
    assert marker not in response.text
    error = response.json()["detail"][0]
    assert set(error) == {"loc", "msg", "type"}
    assert error["loc"] == ["body"]
