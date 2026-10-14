from __future__ import annotations

import subprocess
import sys
import os
from pathlib import Path

import pytest


GRAPH_ROOT = Path(__file__).resolve().parents[1]


@pytest.mark.parametrize(
    "script_name",
    ["optimize_guide_coach.py", "optimize_practice.py"],
)
def test_optimizer_cli_rejects_api_key_argument(script_name: str) -> None:
    script = GRAPH_ROOT / "scripts" / script_name
    env = os.environ.copy()
    for key in (
        "OPENAI_BASE_URL",
        "OPENAI_API_KEY",
        "OPENAI_MODEL",
        "LLM_BASE_URL",
        "LLM_API_KEY",
        "LLM_MODEL",
        "PYT_OPENAI_BASE_URL",
        "PYT_OPENAI_API_KEY",
        "PYT_OPENAI_MODEL",
    ):
        env.pop(key, None)
    result = subprocess.run(
        [
            sys.executable,
            str(script),
            "--api-key",
            "test-only-fake-key",
            "--base-url",
            "https://invalid.example",
            "--task-model",
            "deepseek-v4-pro",
        ],
        env=env,
        capture_output=True,
        text=True,
        check=False,
    )

    assert result.returncode == 2
    assert "unrecognized arguments: --api-key" in result.stderr
