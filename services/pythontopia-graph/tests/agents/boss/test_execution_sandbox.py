from __future__ import annotations

from typing import Any

import httpx
import pytest

from pythontopia_graph.agents.boss import execution_sandbox


def test_piston_request_uses_existing_judge_contract(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("PYT_PISTON_URL", "https://judge.example/")
    monkeypatch.setenv("PYT_PISTON_PYTHON_VERSION", "3.11.13")
    monkeypatch.setenv("PYT_PISTON_AUTH_TOKEN", "test-token")
    captured: dict[str, Any] = {}

    def post(
        url: str,
        *,
        json: dict[str, Any],
        headers: dict[str, str],
        timeout: float,
    ) -> httpx.Response:
        captured.update(url=url, payload=json, headers=headers, timeout=timeout)
        return httpx.Response(
            200,
            json={"run": {"status": "AC", "code": 0, "stdout": "ok\n", "stderr": ""}},
            request=httpx.Request("POST", url),
        )

    monkeypatch.setattr(execution_sandbox.httpx, "post", post)

    result = execution_sandbox.run_python("print('ok')", "stdin", timeout_sec=4)

    assert captured["url"] == "https://judge.example/api/v2/execute"
    assert captured["headers"]["Authorization"] == "Bearer test-token"
    assert captured["payload"]["files"] == [{"name": "submission.py", "content": "print('ok')"}]
    assert captured["payload"]["stdin"] == "stdin"
    assert captured["payload"]["version"] == "3.11.13"
    assert captured["payload"]["run_timeout"] == 4000
    assert captured["payload"]["run_memory_limit"] == execution_sandbox.MEMORY_LIMIT_BYTES
    assert result.stdout == "ok\n"
    assert result.exit_code == 0
    assert result.runtime_error is None
