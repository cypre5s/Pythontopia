#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

from pyt_agent_lab.teacher_voting import (
    FixtureTeacherBackend,
    OpenAICompatibleTeacherBackend,
    load_jsonl,
    resolve_teacher_settings,
    run_teacher_voting,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="对 R-SAGE-IR 案例执行三教师独立投票、规则核验和断点续跑。")
    parser.add_argument(
        "--cases",
        default="research/vllm-agent-ft-lab/outputs/rsage/data/candidates-420.jsonl",
        help="候选案例 JSONL。",
    )
    parser.add_argument(
        "--output-dir",
        default="research/vllm-agent-ft-lab/outputs/rsage/data/teacher-votes",
        help="增量原始投票、标签和清单目录。",
    )
    parser.add_argument("--backend", choices=("fixture", "live"), default="live")
    parser.add_argument("--env-file", default="deploy/.env")
    parser.add_argument("--base-url")
    parser.add_argument("--model")
    parser.add_argument(
        "--proxy-url",
        default="http://127.0.0.1:7892",
        help="教师接口代理；传入空字符串可关闭。",
    )
    parser.add_argument("--request-timeout", type=int, default=60, help="单次网络请求超时秒数。")
    parser.add_argument("--transport-retries", type=int, default=1, help="网络层重试次数。")
    parser.add_argument("--max-tokens", type=int, default=2400, help="单次教师响应的最大输出词元数。")
    parser.add_argument("--limit", type=int, default=0, help="仅处理前 N 条；0 表示全部。")
    parser.add_argument("--max-attempts", type=int, default=3)
    parser.add_argument(
        "--concurrency",
        type=int,
        default=3,
        help="同一案例三个独立教师角色的并发数，范围 1 到 3。",
    )
    parser.add_argument("--no-resume", action="store_true", help="关闭按标签文件跳过已完成案例。")
    parser.add_argument("--demo", action="store_true", help="用固定规则教师处理 3 条案例，证明脚本链路可运行。")
    args = parser.parse_args()

    cases_path = Path(args.cases)
    output_dir = Path(args.output_dir)
    backend_name = args.backend
    if args.demo:
        backend_name = "fixture"
        if args.cases == parser.get_default("cases"):
            cases_path = Path("research/vllm-agent-ft-lab/outputs/rsage/demos/case-builder/cases.jsonl")
        if args.output_dir == parser.get_default("output_dir"):
            output_dir = Path("research/vllm-agent-ft-lab/outputs/rsage/demos/teacher-voting")

    cases = load_jsonl(cases_path)
    effective_limit = 3 if args.demo and args.limit <= 0 else args.limit
    if effective_limit > 0:
        cases = cases[:effective_limit]
    if not cases:
        raise SystemExit("没有可供教师投票的候选案例")

    secrets: tuple[str, ...] = ()
    if backend_name == "fixture":
        backend = FixtureTeacherBackend()
    else:
        settings = resolve_teacher_settings(
            args.env_file,
            base_url=args.base_url,
            model=args.model,
        )
        secrets = (settings["api_key"],)
        backend = OpenAICompatibleTeacherBackend(
            base_url=settings["base_url"],
            api_key=settings["api_key"],
            model=settings["model"],
            timeout=args.request_timeout,
            transport_retries=args.transport_retries,
            proxy_url=args.proxy_url or None,
            max_tokens=args.max_tokens,
        )

    output_dir.mkdir(parents=True, exist_ok=True)
    raw_path = output_dir / "raw_votes.jsonl"
    labels_path = output_dir / "labels.jsonl"

    def show_event(event) -> None:  # noqa: ANN001
        message = event.get("message_zh")
        if message:
            print(message, flush=True)

    summary = run_teacher_voting(
        cases,
        backend,
        raw_votes_path=raw_path,
        labels_path=labels_path,
        max_attempts=args.max_attempts,
        retry_base_seconds=0 if args.demo else 1,
        resume=not args.no_resume,
        secret_values=secrets,
        event_callback=show_event,
        reviewer_concurrency=args.concurrency,
    )
    manifest = {
        "format": "rsage_teacher_voting_manifest_v1",
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "backend": backend_name,
        "model": backend.model_name,
        "cases_path": str(cases_path),
        "cases_sha256": _file_hash(cases_path),
        "raw_votes_path": str(raw_path),
        "raw_votes_sha256": _file_hash(raw_path),
        "labels_path": str(labels_path),
        "labels_sha256": _file_hash(labels_path),
        "total_count": summary.total_count,
        "processed_count": summary.processed_count,
        "accepted_count": summary.accepted_count,
        "excluded_count": summary.excluded_count,
        "skipped_count": summary.skipped_count,
        "request_count": summary.request_count,
        "reviewer_concurrency": args.concurrency,
        "resume_enabled": not args.no_resume,
        "credentials_logged": False,
        "message_zh": summary.message_zh,
    }
    manifest_path = output_dir / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(summary.message_zh)
    print(f"原始投票：{raw_path}")
    print(f"聚合标签：{labels_path}")
    print(f"运行清单：{manifest_path}")


def _file_hash(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else ""


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        raise SystemExit("教师投票已由操作者中止，已写入的增量记录可以继续使用")
    except Exception as exc:  # noqa: BLE001 - 命令行统一输出中文失败原因
        raise SystemExit(f"教师投票运行失败：{exc}") from None
