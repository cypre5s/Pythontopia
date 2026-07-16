#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from pyt_agent_lab.rsage_case_builder import (
    build_rsage_cases,
    load_protected_fingerprints,
    write_rsage_cases,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="构造基于 pack43 课程证据的 R-SAGE-IR 非保护训练候选案例。")
    parser.add_argument("--course-dir", default="data/course-pack-43")
    parser.add_argument("--count", type=int, default=420)
    parser.add_argument("--seed", type=int, default=20260716)
    parser.add_argument("--protected", action="append", default=[], help="需要排除的保护集 JSON/JSONL，可重复传入。")
    parser.add_argument(
        "--output",
        default="research/vllm-agent-ft-lab/outputs/rsage/data/candidates-420.jsonl",
    )
    parser.add_argument(
        "--manifest",
        default="research/vllm-agent-ft-lab/outputs/rsage/data/candidates-420-manifest.json",
    )
    parser.add_argument("--demo", action="store_true", help="仅生成 12 条案例，并自动改用 demo 输出目录。")
    args = parser.parse_args()

    count = min(args.count, 12) if args.demo else args.count
    output = Path(args.output)
    manifest = Path(args.manifest)
    if args.demo and args.output == parser.get_default("output"):
        output = Path("research/vllm-agent-ft-lab/outputs/rsage/demos/case-builder/cases.jsonl")
    if args.demo and args.manifest == parser.get_default("manifest"):
        manifest = Path("research/vllm-agent-ft-lab/outputs/rsage/demos/case-builder/manifest.json")

    protected = load_protected_fingerprints(args.protected)
    cases, result = build_rsage_cases(
        args.course_dir,
        count=count,
        seed=args.seed,
        protected_fingerprints=protected,
    )
    written = write_rsage_cases(cases, result, output, manifest)
    print(written["message_zh"])
    print(f"案例文件：{output}")
    print(f"清单文件：{manifest}")
    print(f"数据哈希：{written['dataset_sha256']}")


if __name__ == "__main__":
    main()
