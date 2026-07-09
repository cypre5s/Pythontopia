#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from transformers import AutoTokenizer
from trl.experimental.orpo.orpo_trainer import ORPOTrainer

from pyt_agent_lab.orpo_dataset_filter import filter_orpo_rows, read_jsonl, write_jsonl


def main() -> None:
    parser = argparse.ArgumentParser(description="Filter preference JSONL rows for stable TRL ORPO training.")
    parser.add_argument("--model-path", required=True)
    parser.add_argument("--train-file", required=True)
    parser.add_argument("--heldout-file", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--max-seq-length", type=int, required=True)
    args = parser.parse_args()

    tokenizer = AutoTokenizer.from_pretrained(args.model_path, trust_remote_code=False)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token
    token_stats = _build_orpo_token_stats(tokenizer, args.max_seq_length)

    output_dir = Path(args.output_dir)
    train_rows, train_manifest = filter_orpo_rows(
        read_jsonl(args.train_file),
        token_stats=token_stats,
        max_seq_length=args.max_seq_length,
    )
    heldout_rows, heldout_manifest = filter_orpo_rows(
        read_jsonl(args.heldout_file),
        token_stats=token_stats,
        max_seq_length=args.max_seq_length,
    )

    write_jsonl(train_rows, output_dir / "train.jsonl")
    write_jsonl(heldout_rows, output_dir / "heldout.jsonl")
    manifest = {
        "max_seq_length": args.max_seq_length,
        "model_path": args.model_path,
        "source_train_file": args.train_file,
        "source_heldout_file": args.heldout_file,
        "train": train_manifest,
        "heldout": heldout_manifest,
    }
    (output_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True),
        encoding="utf-8",
    )
    print(json.dumps(manifest, ensure_ascii=False, sort_keys=True))


def _build_orpo_token_stats(tokenizer: Any, max_seq_length: int):
    trainer = object.__new__(ORPOTrainer)
    trainer.processing_class = tokenizer
    trainer.is_encoder_decoder = False
    trainer.max_length = max_seq_length
    trainer.max_completion_length = None
    trainer.padding_value = tokenizer.pad_token_id
    trainer.truncation_mode = "keep_end"

    def token_stats(row: dict[str, Any]) -> dict[str, int]:
        prompt = tokenizer.apply_chat_template(row["prompt"], tokenize=False, add_generation_prompt=True)
        tokenized = trainer.tokenize_row(
            {
                "prompt": prompt,
                "chosen": str(row["chosen"]),
                "rejected": str(row["rejected"]),
            }
        )
        return {
            "chosen_label_count": _shifted_label_count(tokenized["chosen_labels"]),
            "rejected_label_count": _shifted_label_count(tokenized["rejected_labels"]),
            "chosen_length": len(tokenized["chosen_input_ids"]),
            "rejected_length": len(tokenized["rejected_input_ids"]),
        }

    return token_stats


def _shifted_label_count(labels: list[int]) -> int:
    return sum(1 for token in labels[1:] if token != -100)


if __name__ == "__main__":
    main()
