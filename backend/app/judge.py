"""Isolated Judge0 HTTP adapter. No student code is executed by the API process."""

from __future__ import annotations

import os
from dataclasses import dataclass

import httpx


class JudgeUnavailable(Exception):
    pass


class JudgeSubmissionUnknown(Exception):
    """The remote POST may have succeeded. Never resubmit automatically."""


@dataclass(frozen=True)
class JudgeResult:
    status: str
    feedback: dict


STATUS = {
    1: "queued",
    2: "running",
    3: "accepted",
    4: "wrong_answer",
    5: "time_limit",
    6: "compile_error",
    7: "runtime_error",
    8: "runtime_error",
    9: "runtime_error",
    10: "runtime_error",
    11: "runtime_error",
    12: "runtime_error",
    13: "infrastructure_error",
    14: "runtime_error",
}


class JudgeAdapter:
    def __init__(self, base_url: str | None = None, transport=None):
        self.base_url = (base_url if base_url is not None else os.getenv("REBORN_JUDGE0_URL", "")).rstrip("/")
        self.transport = transport
        self.language_id = int(os.getenv("REBORN_JUDGE0_PYTHON_LANGUAGE_ID", "71"))

    def client(self):
        headers = {"X-Auth-Token": os.getenv("REBORN_JUDGE0_AUTH_TOKEN", "")}
        return httpx.AsyncClient(base_url=self.base_url, headers=headers, timeout=httpx.Timeout(8.0), transport=self.transport)

    async def submit(self, code: str, cases: list[dict]) -> list[str]:
        if not self.base_url:
            raise JudgeUnavailable("Judge0 未配置，正式判题不可用；仍可在浏览器运行公开样例")
        payload = {
            "submissions": [
                {
                    "source_code": code,
                    "language_id": self.language_id,
                    "stdin": str(c.get("input", "")),
                    "expected_output": str(c.get("expected_output", c.get("output", ""))),
                    "cpu_time_limit": 2,
                    "wall_time_limit": 5,
                    "memory_limit": 128000,
                    "max_file_size": 256,
                    "max_processes_and_or_threads": 16,
                    "enable_network": False,
                }
                for c in cases
            ]
        }
        try:
            async with self.client() as client:
                response = await client.post("/submissions/batch", params={"base64_encoded": "false"}, json=payload)
                response.raise_for_status()
                items = response.json()
                if not isinstance(items, list) or len(items) != len(cases) or any(not x.get("token") for x in items):
                    raise JudgeSubmissionUnknown("Judge0 返回不完整 token；需要管理员对账，不自动重送")
                return [x["token"] for x in items]
        except JudgeSubmissionUnknown:
            raise
        except httpx.ConnectError as exc:
            raise JudgeUnavailable("Judge0 连接失败") from exc
        except (httpx.TimeoutException, httpx.HTTPError, ValueError, TypeError) as exc:
            raise JudgeSubmissionUnknown("Judge0 送交结果不确定；请等待管理员对账，不自动重送") from exc

    async def poll(self, tokens: list[str], public_count: int = 0) -> JudgeResult:
        if not self.base_url:
            raise JudgeUnavailable("Judge0 未配置")
        try:
            async with self.client() as client:
                response = await client.get(
                    "/submissions/batch",
                    params={
                        "tokens": ",".join(tokens),
                        "base64_encoded": "false",
                        "fields": "status,stdout,stderr,compile_output,time,memory,exit_signal,message",
                    },
                )
                response.raise_for_status()
                items = response.json()["submissions"]
                if len(items) != len(tokens) or any(not item for item in items):
                    raise JudgeUnavailable("Judge0 token 未找到，需要管理员对账")
        except (httpx.HTTPError, ValueError, KeyError, TypeError) as exc:
            raise JudgeUnavailable("Judge0 结果暂不可用，将稍后继续查询") from exc
        states = [
            "memory_limit"
            if "MemoryError" in str(x.get("stderr", "")) or "memory limit" in str(x.get("message", "")).lower()
            else STATUS.get(x.get("status", {}).get("id"), "infrastructure_error")
            for x in items
        ]
        if "infrastructure_error" in states:
            state = "infrastructure_error"
        elif "queued" in states or "running" in states:
            state = "running" if "running" in states else "queued"
        else:
            state = next((s for s in states if s != "accepted"), "accepted")
        # Hidden-case output can contain secret input echoed by the student's program.
        # Only public sample output is returned. Never expose a hidden-case index.
        public = [
            {
                "status": states[i],
                "stdout": str(x.get("stdout") or "")[:4000],
                "stderr": str(x.get("stderr") or "")[:2000],
                "compile_output": str(x.get("compile_output") or "")[:2000],
            }
            for i, x in enumerate(items[:public_count])
        ]
        return JudgeResult(state, {"status": state, "samples": public})
