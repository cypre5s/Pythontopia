"""Python-only remote sandbox; grading and durable queue stay in our backend."""
from __future__ import annotations

import os
import time

import httpx

from .judge import JudgeResult, JudgeSubmissionUnknown, JudgeUnavailable


class GoJudgeAdapter:
    def __init__(self, base_url: str | None = None, transport=None):
        self.base_url = (base_url if base_url is not None else os.getenv("REBORN_GO_JUDGE_URL", "")).rstrip("/")
        self.transport = transport

    async def execute(self, code: str, cases: list[dict], public_count: int = 0) -> JudgeResult:
        if not self.base_url:
            raise JudgeUnavailable("go-judge 未配置，正式判题不可用；仍可在浏览器运行公开样例")
        if not 1 <= len(cases) <= 20 or not 0 <= public_count <= len(cases):
            raise JudgeUnavailable("测试用例配置无效")
        states, samples = [], []
        start = time.monotonic()
        headers = {"Authorization": "Bearer " + os.getenv("REBORN_GO_JUDGE_AUTH_TOKEN", "")}
        async with httpx.AsyncClient(base_url=self.base_url, headers=headers, timeout=15, transport=self.transport,
                                    trust_env=False) as client:
            for index, case in enumerate(cases):
                payload = {"cmd": [{
                    "args": ["/usr/local/bin/python3", "-I", "-B", "/w/main.py"],
                    "env": ["PATH=/usr/local/bin:/usr/bin", "LANG=C.UTF-8"],
                    "files": [{"content": str(case.get("input", ""))},
                              {"name": "stdout", "max": 16384}, {"name": "stderr", "max": 8192}],
                    "cpuLimit": 2_000_000_000, "clockLimit": 5_000_000_000,
                    "memoryLimit": 128 * 1024 * 1024, "procLimit": 16,
                    "copyIn": {"main.py": {"content": code}},
                }]}
                try:
                    response = await client.post("/run", json=payload)
                    response.raise_for_status()
                    items = response.json()
                    if not isinstance(items, list) or len(items) != 1 or not isinstance(items[0], dict):
                        raise ValueError("Invalid execution response")
                    item = items[0]
                    remote = item["status"]
                    files = item.get("files", {})
                    stdout, stderr = files.get("stdout", ""), files.get("stderr", "")
                    if not isinstance(stdout, str) or not isinstance(stderr, str) or not isinstance(remote, str):
                        raise ValueError("Invalid execution fields")
                except httpx.ConnectError as exc:
                    if states:
                        raise JudgeSubmissionUnknown("判题连接中断，已有部分用例执行；需管理员对账，不自动重送") from exc
                    raise JudgeUnavailable("go-judge 连接失败") from exc
                except (httpx.HTTPError, ValueError, KeyError, TypeError, AttributeError) as exc:
                    raise JudgeSubmissionUnknown("go-judge 执行结果不确定；需管理员对账，不自动重送") from exc
                if remote == "Accepted" and item.get("exitStatus") == 0:
                    expected = str(case.get("expected_output", case.get("output", "")))
                    state = "accepted" if stdout.replace("\r\n", "\n").rstrip() == expected.replace("\r\n", "\n").rstrip() else "wrong_answer"
                else:
                    state = {
                        "Time Limit Exceeded": "time_limit", "Memory Limit Exceeded": "memory_limit",
                        "Output Limit Exceeded": "runtime_error", "Nonzero Exit Status": "runtime_error",
                        "Signalled": "runtime_error", "Dangerous Syscall": "runtime_error",
                    }.get(remote, "infrastructure_error")
                    if remote == "Nonzero Exit Status":
                        last_line = stderr.rstrip().split("\n")[-1]
                        if last_line.startswith("MemoryError"):
                            state = "memory_limit"
                        elif last_line.startswith(("SyntaxError:", "IndentationError:", "TabError:")):
                            state = "compile_error"
                states.append(state)
                # No hidden output, diagnostic text, index or timing is exposed.
                if index < public_count:
                    samples.append({"status": state, "stdout": stdout[:4000], "stderr": stderr[:2000], "compile_output": ""})
                if state == "infrastructure_error":
                    return JudgeResult(state, {"status": state, "detail": "隔离执行服务异常，请联系管理员", "samples": samples})
        state = next((s for s in states if s != "accepted"), "accepted")
        return JudgeResult(state, {"status": state, "samples": samples, "passed_count": states.count("accepted"),
                                  "total_count": len(states), "elapsed_ms": round((time.monotonic() - start) * 1000)})
