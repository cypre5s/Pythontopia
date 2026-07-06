#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Exercise the real backend workshop SSE route through Java -> graph -> vLLM."
    )
    parser.add_argument("--base-url", default="http://127.0.0.1:5173")
    parser.add_argument(
        "--output-dir",
        default="research/vllm-agent-ft-lab/outputs/web-ab/backend-workshop-stream-v1",
    )
    parser.add_argument("--type", default="PRACTICE")
    parser.add_argument("--kc-code", default="PY43-KC-005")
    parser.add_argument(
        "--focus-hint",
        default="验证微调 LoRA 在 Web 工坊链路中生成非兜底资源，保留样例但不要泄露隐藏答案。",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    username = f"pyt.ft.web.{int(time.time())}"
    password = f"FtWebSmoke{int(time.time())}!"
    base = args.base_url.rstrip("/")

    _post_json(
        f"{base}/api/auth/register",
        {
            "username": username,
            "password": password,
            "displayName": "FT Web Smoke",
            "role": "STUDENT",
        },
    )
    login = _post_json(
        f"{base}/api/auth/login",
        {"username": username, "password": password},
    )
    token = login["token"]
    request_body = {
        "kcCodes": [args.kc_code],
        "type": args.type,
        "focusHint": args.focus_hint,
    }
    events = _post_sse(
        f"{base}/api/workshop/resources/stream",
        request_body,
        token=token,
        timeout=180,
    )
    result_payload = _last_result(events)
    resource_id = int(result_payload["resourceId"])
    detail = _get_json(f"{base}/api/workshop/resources/{resource_id}", token=token)
    report = {
        "base_url": base,
        "request": request_body,
        "result": result_payload,
        "detail": detail,
        "event_count": len(events),
        "events": events,
        "checks": _checks(detail),
    }
    (output_dir / "backend_workshop_stream_smoke.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True),
        encoding="utf-8",
    )
    (output_dir / "backend_workshop_stream_events.jsonl").write_text(
        "\n".join(json.dumps(event, ensure_ascii=False, sort_keys=True) for event in events)
        + "\n",
        encoding="utf-8",
    )
    print(f"resource_id={resource_id}")
    print(f"status={result_payload.get('status')}")
    print(f"title={detail.get('title')}")
    print(f"checks={report['checks']}")
    print(f"wrote {output_dir / 'backend_workshop_stream_smoke.json'}")
    if not all(report["checks"].values()):
        return 2
    return 0


def _post_json(url: str, payload: dict[str, Any], token: str | None = None) -> dict[str, Any]:
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        if exc.code == 409 and url.endswith("/api/auth/register"):
            return {}
        raise RuntimeError(f"POST {url} failed: HTTP {exc.code} {detail}") from exc


def _get_json(url: str, *, token: str) -> dict[str, Any]:
    req = urllib.request.Request(
        url,
        headers={"Authorization": f"Bearer {token}"},
        method="GET",
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))


def _post_sse(
    url: str,
    payload: dict[str, Any],
    *,
    token: str,
    timeout: int,
) -> list[dict[str, str]]:
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
            "Authorization": f"Bearer {token}",
        },
        method="POST",
    )
    events: list[dict[str, str]] = []
    current_event = "message"
    current_data: list[str] = []
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        for raw in resp:
            line = raw.decode("utf-8").rstrip("\n")
            if not line:
                if current_data:
                    events.append(
                        {"event": current_event, "data": "\n".join(current_data)}
                    )
                current_event = "message"
                current_data = []
                continue
            if line.startswith("event:"):
                current_event = line[len("event:"):].strip()
            elif line.startswith("data:"):
                current_data.append(line[len("data:"):].strip())
    return events


def _last_result(events: list[dict[str, str]]) -> dict[str, Any]:
    for event in reversed(events):
        if event["event"] == "result":
            return json.loads(event["data"])
    raise RuntimeError("SSE stream did not emit result event")


def _checks(detail: dict[str, Any]) -> dict[str, bool]:
    content = detail.get("content")
    return {
        "resource_id_present": isinstance(detail.get("id"), int),
        "status_grounded": detail.get("groundingStatus") == "PASSED",
        "status_safe": detail.get("safetyStatus") == "PASSED",
        "evidence_bound": bool(detail.get("evidencePageIds")),
        "content_non_empty": isinstance(content, dict) and bool(content),
        "not_fallback_practice": not _fallback_practice(detail),
    }


def _fallback_practice(detail: dict[str, Any]) -> bool:
    if detail.get("type") != "PRACTICE":
        return False
    content = detail.get("content")
    if not isinstance(content, dict):
        return True
    problems = content.get("problems")
    if not isinstance(problems, list) or not problems:
        return True
    first = problems[0]
    if not isinstance(first, dict):
        return True
    return (
        str(first.get("title") or "").startswith("练习：")
        and "编写一段程序读入数据并输出处理结果" in str(first.get("statement_md") or "")
    )


if __name__ == "__main__":
    raise SystemExit(main())
