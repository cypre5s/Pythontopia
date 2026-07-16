#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from pyt_agent_lab.rsage_curation import (
    curate_rsage_dataset,
    filter_retention_rows_by_length,
    write_rsage_curation,
)
from pyt_agent_lab.teacher_voting import load_jsonl


def main() -> None:
    parser = argparse.ArgumentParser(description="整理模板族隔离、无保护集泄漏的 R-SAGE SFT 与软偏好数据。")
    parser.add_argument(
        "--cases",
        default="research/vllm-agent-ft-lab/outputs/rsage/data/candidates-420.jsonl",
    )
    parser.add_argument(
        "--labels",
        default="research/vllm-agent-ft-lab/outputs/rsage/data/teacher-votes/labels.jsonl",
    )
    parser.add_argument(
        "--retention",
        default="research/vllm-agent-ft-lab/outputs/sft/e1-protocol-patch-v1-1000-v1/train.jsonl",
    )
    parser.add_argument(
        "--output-dir",
        default="research/vllm-agent-ft-lab/outputs/rsage/data/curated",
    )
    parser.add_argument("--protected", action="append", default=[], help="保护集 JSONL，可重复传入。")
    parser.add_argument("--seed", type=int, default=20260716)
    parser.add_argument("--target-count", type=int, default=294)
    parser.add_argument("--retention-count", type=int, default=252)
    parser.add_argument("--preference-count", type=int, default=208)
    parser.add_argument(
        "--model-path",
        default="/home/cypress/.cache/modelscope/Qwen/Qwen3.5-4B",
        help="用于 retention 严格长度筛选的本地 tokenizer。",
    )
    parser.add_argument("--max-seq-length", type=int, default=384)
    parser.add_argument("--demo", action="store_true", help="用已验证的 3 条教师演示标签整理 1+2 条小数据。")
    args = parser.parse_args()

    cases_path = Path(args.cases)
    labels_path = Path(args.labels)
    output_dir = Path(args.output_dir)
    target_count = args.target_count
    retention_count = args.retention_count
    preference_count = args.preference_count
    if args.demo:
        if args.cases == parser.get_default("cases"):
            cases_path = Path("research/vllm-agent-ft-lab/outputs/rsage/demos/case-builder/cases.jsonl")
        if args.labels == parser.get_default("labels"):
            labels_path = Path("research/vllm-agent-ft-lab/outputs/rsage/demos/teacher-voting/labels.jsonl")
        if args.output_dir == parser.get_default("output_dir"):
            output_dir = Path("research/vllm-agent-ft-lab/outputs/rsage/demos/curation")
        if args.target_count == parser.get_default("target_count"):
            target_count = 1
        if args.retention_count == parser.get_default("retention_count"):
            retention_count = 2
        if args.preference_count == parser.get_default("preference_count"):
            preference_count = 1

    protected = _load_protected(args.protected)
    from transformers import AutoTokenizer

    raw_retention = load_jsonl(args.retention)
    tokenizer = AutoTokenizer.from_pretrained(args.model_path, trust_remote_code=False)
    retention, retention_length_report = filter_retention_rows_by_length(
        raw_retention,
        tokenizer=tokenizer,
        max_seq_length=args.max_seq_length,
    )
    print(retention_length_report["message_zh"], flush=True)
    result = curate_rsage_dataset(
        load_jsonl(cases_path),
        load_jsonl(labels_path),
        retention,
        seed=args.seed,
        target_sft_count=target_count,
        retention_count=retention_count,
        preference_count=preference_count,
        protected_fingerprints=protected,
    )
    result.manifest["retention_length_filter"] = retention_length_report
    result.manifest["retention_excluded_target_agent_count"] = retention_length_report[
        "target_agent_excluded_count"
    ]
    result.manifest["input_files"] = {
        "cases": {"path": str(cases_path), "sha256": _file_hash(cases_path)},
        "labels": {"path": str(labels_path), "sha256": _file_hash(labels_path)},
        "retention": {"path": str(args.retention), "sha256": _file_hash(Path(args.retention))},
        "protected": [
            {"path": str(path), "sha256": _file_hash(Path(path))}
            for path in args.protected
        ],
    }
    manifest = write_rsage_curation(result, output_dir)
    print(manifest["message_zh"])
    print(f"模板族划分：{json.dumps(manifest['split_template_families'], ensure_ascii=False, sort_keys=True)}")
    print(f"保护模板族排除案例：{manifest['protected_family_exclusion_count']} 条")
    print(f"数据目录：{output_dir}")
    print(f"数据哈希：{manifest['dataset_sha256']}")


def _load_protected(paths: list[str]) -> set[str]:
    result: set[str] = set()
    for path in paths:
        for row in load_jsonl(path):
            fingerprint = row.get("prompt_fingerprint")
            if isinstance(fingerprint, str) and fingerprint:
                result.add(fingerprint)
    return result


def _file_hash(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001 - 命令行统一输出中文失败原因
        raise SystemExit(f"R-SAGE 数据整理失败：{exc}") from None
