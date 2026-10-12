"""Run Boss unit cases through a test-only fake Piston endpoint.

Production code has no local-execution fallback; this test server evaluates only
the fixed, trusted snippets in the unit suite so existing result mapping tests
remain useful without a deployed Piston service.
"""

from __future__ import annotations

import subprocess
import sys
import time
from typing import Any

import httpx
import pytest


@pytest.fixture(autouse=True)
def _fake_piston_endpoint(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("PYT_PISTON_URL", "http://piston.test")
    monkeypatch.setenv("PYT_PISTON_PYTHON_VERSION", "3.11.13")

    def post(
        url: str,
        *,
        json: dict[str, Any],
        headers: dict[str, str],
        timeout: float,
    ) -> httpx.Response:
        del url, headers, timeout
        source = json["files"][0]["content"]
        started = time.monotonic()
        try:
            result = subprocess.run(
                [sys.executable, "-I", "-B", "-c", source],
                input=json["stdin"],
                capture_output=True,
                text=True,
                timeout=json["run_timeout"] / 1000,
                env={"PYTHONIOENCODING": "utf-8"},
            )
        except subprocess.TimeoutExpired as exc:
            stdout = exc.stdout or ""
            stderr = exc.stderr or ""
            if isinstance(stdout, bytes):
                stdout = stdout.decode("utf-8", errors="replace")
            if isinstance(stderr, bytes):
                stderr = stderr.decode("utf-8", errors="replace")
            run = {"status": "TO", "code": -1, "stdout": stdout, "stderr": stderr}
        else:
            run = {
                "status": None if result.returncode == 0 else "RE",
                "code": result.returncode,
                "stdout": result.stdout,
                "stderr": result.stderr,
            }
        run["wall_time"] = int((time.monotonic() - started) * 1000)
        return httpx.Response(
            200,
            json={"run": run},
            request=httpx.Request("POST", "http://piston.test/api/v2/execute"),
        )

    monkeypatch.setattr("pythontopia_graph.agents.boss.execution_sandbox.httpx.post", post)
