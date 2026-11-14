"""Explicit local sandbox acceptance/load test; never executes submitted code here."""
import argparse
import asyncio
import json
import math
import os
import statistics
import time
from pathlib import Path

import httpx


def payload(code, stdin=""):
    return {"cmd": [{
        "args": ["/usr/local/bin/python3", "-I", "-B", "/w/main.py"],
        "env": ["PATH=/usr/local/bin:/usr/bin", "LANG=C.UTF-8"],
        "files": [{"content": stdin}, {"name": "stdout", "max": 16384}, {"name": "stderr", "max": 8192}],
        "cpuLimit": 2_000_000_000, "clockLimit": 5_000_000_000,
        "memoryLimit": 128 * 1024 * 1024, "procLimit": 16,
        "copyIn": {"main.py": {"content": code}},
    }]}


async def main(args):
    headers = {"Authorization": "Bearer " + os.environ["REBORN_GO_JUDGE_AUTH_TOKEN"]}
    report = {"url": args.url, "checks": {}, "benchmarks": []}
    async with httpx.AsyncClient(base_url=args.url, headers=headers, timeout=30, trust_env=False) as client:
        report["version"] = (await client.get("/version")).json()
        config = (await client.get("/config")).json()
        report["config"] = config
        unauth = await client.post("/run", headers={"Authorization": "Bearer invalid"}, json=payload("print(1)"))
        assert unauth.status_code == 401, unauth.status_code
        report["checks"]["authentication"] = True

        async def run(code, stdin=""):
            response = await client.post("/run", json=payload(code, stdin))
            response.raise_for_status()
            return response.json()[0]

        checks = [
            ("python_unicode", "print('你好，' + input() + '！')", "小林", "Accepted", "你好，小林！\n"),
            ("runtime_error", "raise ValueError('test')", "", "Nonzero Exit Status", None),
            ("timeout", "while True: pass", "", "Time Limit Exceeded", None),
            ("memory", "x = bytearray(512 * 1024 * 1024)", "", "Memory Limit Exceeded", None),
            ("output_limit", "print('x' * 200000)", "", "Output Limit Exceeded", None),
            ("filesystem_write", "open('/w/secret-test', 'w').write('test'); print('ok')", "", "Accepted", "ok\n"),
            ("filesystem_reset", "import os; print(os.path.exists('/w/secret-test'))", "", "Accepted", "False\n"),
            ("private_paths", "import os; print(any(os.path.exists(p) for p in ['/app', '/opt', '/var/run/docker.sock', '/sys/fs/cgroup']))", "", "Accepted", "False\n"),
            ("network", "import socket\ntry:\n socket.create_connection(('1.1.1.1', 53), timeout=0.3)\n print('CONNECTED')\nexcept OSError:\n print('BLOCKED')", "", "Accepted", "BLOCKED\n"),
        ]
        for name, code, stdin, expected, stdout in checks:
            result = await run(code, stdin)
            # Python may surface an allocation failure before a cgroup kill.
            status_ok = result["status"].lower() == expected.lower()
            if name == "memory":
                status_ok |= "MemoryError" in result.get("files", {}).get("stderr", "")
            assert status_ok, (name, result)
            if stdout is not None:
                assert result["files"]["stdout"] == stdout, (name, result)
            report["checks"][name] = {"status": result["status"], "runtime_ms": result.get("runTime", 0) / 1e6}
        for concurrency in [1, 4, 8, 16]:
            semaphore = asyncio.Semaphore(concurrency)
            latencies = []

            async def job(slots=semaphore, timings=latencies):
                async with slots:
                    start = time.perf_counter()
                    # One submission consists of five separate, isolated test cases.
                    for _ in range(5):
                        result = await run("print(sum(map(int, input().split())))", "12 30")
                        assert result["status"] == "Accepted" and result["files"]["stdout"] == "42\n"
                    timings.append((time.perf_counter() - start) * 1000)

            start = time.perf_counter()
            await asyncio.gather(*(job() for _ in range(args.jobs)))
            elapsed = time.perf_counter() - start
            row = {"concurrent_submissions": concurrency, "submissions": args.jobs, "cases_per_submission": 5,
                   "median_ms": round(statistics.median(latencies), 1),
                   "p95_ms": round(sorted(latencies)[math.ceil(len(latencies) * .95) - 1], 1),
                   "submissions_per_second": round(args.jobs / elapsed, 1)}
            report["benchmarks"].append(row)
            print(json.dumps(row), flush=True)
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    Path(args.output).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print("All isolation checks passed; report:", args.output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default=os.getenv("REBORN_GO_JUDGE_URL", "http://judge:5050"))
    parser.add_argument("--jobs", type=int, default=40)
    parser.add_argument("--output", default=".tmp/go-judge-benchmark.json")
    asyncio.run(main(parser.parse_args()))
