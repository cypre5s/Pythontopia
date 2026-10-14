"""Remote isolated Python execution for Boss code cases.

User supplied Python must never execute in the Graph worker: it can inspect the
worker's ``/proc/<pid>/environ`` and reach its internal network. This adapter
delegates execution to the same Piston service used by the Java judge and fails
closed when that service is not configured or unavailable.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass

import httpx

MEMORY_LIMIT_BYTES = 256 * 1024 * 1024
MAX_RESPONSE_BYTES = 1024 * 1024
MAX_STDOUT_CHARS = 64 * 1024
MAX_STDERR_CHARS = 16 * 1024
PISTON_FAILURE_STATUSES = frozenset({"RE", "SG", "TO", "OL", "EL", "XX"})


@dataclass(frozen=True)
class PythonExecutionResult:
    """Normalized response from the isolated Piston runner."""

    stdout: str = ""
    stderr: str = ""
    exit_code: int = -1
    timed_out: bool = False
    runtime_error: str | None = None


def run_python(code: str, stdin: str, timeout_sec: int) -> PythonExecutionResult:
    """Execute one Python case remotely; never fall back to a local process."""

    base_url = os.getenv("PYT_PISTON_URL", "").strip().rstrip("/")
    python_version = os.getenv("PYT_PISTON_PYTHON_VERSION", "").strip()
    if not base_url or not python_version:
        return PythonExecutionResult(runtime_error="sandbox_unavailable")

    timeout_ms = max(1, timeout_sec * 1000)
    payload = {
        "language": "python",
        "version": python_version,
        "files": [{"name": "submission.py", "content": code}],
        "stdin": stdin,
        "args": [],
        "run_timeout": timeout_ms,
        "run_cpu_time": timeout_ms,
        "run_memory_limit": MEMORY_LIMIT_BYTES,
        "compile_timeout": timeout_ms,
        "compile_cpu_time": timeout_ms,
        "compile_memory_limit": MEMORY_LIMIT_BYTES,
    }
    headers = {"Content-Type": "application/json"}
    auth_token = os.getenv("PYT_PISTON_AUTH_TOKEN", "").strip()
    if auth_token:
        headers["Authorization"] = f"Bearer {auth_token}"

    try:
        with httpx.stream(
            "POST",
            f"{base_url}/api/v2/execute",
            json=payload,
            headers=headers,
            timeout=max(3.0, timeout_sec + 3.0),
            trust_env=False,
        ) as response:
            response.raise_for_status()
            response_body = bytearray()
            for chunk in response.iter_bytes(chunk_size=64 * 1024):
                if len(response_body) + len(chunk) > MAX_RESPONSE_BYTES:
                    return PythonExecutionResult(runtime_error="sandbox_unavailable")
                response_body.extend(chunk)
            body = json.loads(response_body)
    except (httpx.HTTPError, ValueError):
        return PythonExecutionResult(runtime_error="sandbox_unavailable")

    run = body.get("run") if isinstance(body, dict) else None
    if not isinstance(run, dict):
        return PythonExecutionResult(runtime_error="sandbox_unavailable")

    status = run.get("status")
    if status is not None and (
        not isinstance(status, str) or status not in PISTON_FAILURE_STATUSES
    ):
        return PythonExecutionResult(runtime_error="sandbox_unavailable")
    if status == "XX":
        return PythonExecutionResult(runtime_error="sandbox_unavailable")

    stdout = run.get("stdout")
    stderr = run.get("stderr")
    code_value = run.get("code")
    signal = run.get("signal")
    message = run.get("message")
    if (
        not isinstance(stdout, str)
        or not isinstance(stderr, str)
        or (code_value is not None and (not isinstance(code_value, int) or isinstance(code_value, bool)))
        or (signal is not None and not isinstance(signal, str))
        or (message is not None and not isinstance(message, str))
        or (status is None and code_value is None and signal is None)
    ):
        return PythonExecutionResult(runtime_error="sandbox_unavailable")

    exit_code = (
        code_value
        if isinstance(code_value, int)
        else {"SIGKILL": 137, "SIGXCPU": 152}.get(signal, -1)
    )
    if status is not None and status != "TO" and exit_code == 0:
        exit_code = -1
    if status is None and signal is not None and exit_code == 0:
        exit_code = -1
    stderr = stderr or message or (f"Piston execution status: {status}" if status else "")
    return PythonExecutionResult(
        stdout=_bounded_text(stdout, MAX_STDOUT_CHARS),
        stderr=_bounded_text(stderr, MAX_STDERR_CHARS),
        exit_code=exit_code,
        # shortcut: Piston v2 omits the kill cause with signal-only responses; upgrade when it
        # returns stable timeout versus memory-limit metadata.
        timed_out=status == "TO" or signal == "SIGKILL",
    )


def _bounded_text(value: object, max_chars: int) -> str:
    if not isinstance(value, str):
        return ""
    return value[:max_chars]
