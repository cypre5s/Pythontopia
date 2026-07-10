#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import torch
from datasets import load_dataset
from peft import PeftModel
from transformers import AutoModelForMultimodalLM, AutoTokenizer, BitsAndBytesConfig, TrainerCallback
from trl.experimental.orpo import ORPOConfig, ORPOTrainer

from pyt_agent_lab.lora_training import load_training_plan, require_dependency
from pyt_agent_lab.training_quality import (
    archive_training_failure,
    initialize_experiment_manifest,
    run_preference_preflight,
    update_experiment_status,
    write_preference_preflight,
)


class JsonlTrainerLogCallback(TrainerCallback):
    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)

    def on_log(self, args, state, control, logs=None, **kwargs):  # noqa: ANN001, ANN003
        if logs is None:
            return
        row = {"step": state.global_step, "epoch": state.epoch, **logs}
        with self.path.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser(description="Train an ORPO adapter from Pythontopia preference data.")
    parser.add_argument("--plan", required=True)
    parser.add_argument("--max-steps", type=int, default=None)
    parser.add_argument("--resume-from-checkpoint", default=None)
    args = parser.parse_args()

    plan = load_training_plan(args.plan)
    initialize_experiment_manifest(plan=plan, plan_path=args.plan, output_dir=plan["output_dir"])
    try:
        _train(plan, args)
    except BaseException as exc:
        archive_training_failure(plan["output_dir"], exc, "orpo_training")
        raise


def _train(plan: dict[str, Any], args: argparse.Namespace) -> None:
    require_dependency("trl")
    require_dependency("peft")
    require_dependency("bitsandbytes")

    tokenizer = AutoTokenizer.from_pretrained(plan["model_path"], trust_remote_code=False)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    quality_round = plan.get("quality_round") or {}
    preflight = run_preference_preflight(
        train_file=plan["train_file"],
        heldout_file=plan["heldout_file"],
        tokenizer=tokenizer,
        max_seq_length=int(plan["max_seq_length"]),
        changed_variables=quality_round.get("changed_variables"),
    )
    write_preference_preflight(preflight, Path(plan["output_dir"]) / "preference_preflight.json")
    if not preflight["passed"]:
        raise ValueError("preference training preflight failed; see preference_preflight.json")
    update_experiment_status(plan["output_dir"], "training")

    quantization_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.bfloat16,
        bnb_4bit_use_double_quant=True,
    )
    base_model = AutoModelForMultimodalLM.from_pretrained(
        plan["model_path"],
        device_map="auto",
        torch_dtype=torch.bfloat16,
        quantization_config=quantization_config,
        trust_remote_code=False,
    )
    model = PeftModel.from_pretrained(base_model, plan["sft_adapter_path"], is_trainable=True)
    if plan["training"].get("gradient_checkpointing", False):
        model.gradient_checkpointing_enable()
        model.config.use_cache = False

    raw = load_dataset(
        "json",
        data_files={"train": plan["train_file"], "eval": plan["heldout_file"]},
    )
    dataset = raw.map(lambda item: _format_preference_item(item, tokenizer))

    training = dict(plan["training"])
    # Keep model and dataset shuffling aligned with the seed recorded in the experiment manifest.
    experiment_seed = int(plan.get("seed", 42))
    training.setdefault("seed", experiment_seed)
    training.setdefault("data_seed", experiment_seed)
    if args.max_steps is not None:
        training["max_steps"] = args.max_steps
        training["num_train_epochs"] = 1
        training["save_strategy"] = "no"
        training["eval_strategy"] = "no"

    orpo_config = ORPOConfig(
        output_dir=plan["output_dir"],
        max_length=plan["max_seq_length"],
        beta=float(plan["orpo"]["beta"]),
        report_to=[],
        **training,
    )
    trainer = ORPOTrainer(
        model=model,
        args=orpo_config,
        train_dataset=dataset["train"],
        eval_dataset=dataset["eval"],
        processing_class=tokenizer,
        callbacks=[JsonlTrainerLogCallback(Path(plan["output_dir"]) / "trainer_log.jsonl")],
    )
    trainer.train(resume_from_checkpoint=args.resume_from_checkpoint or None)
    trainer.save_model(plan["output_dir"])
    tokenizer.save_pretrained(plan["output_dir"])
    Path(plan["output_dir"], "training_complete.txt").write_text("ok\n", encoding="utf-8")
    update_experiment_status(plan["output_dir"], "trained_not_promoted")


def _format_preference_item(item: dict[str, Any], tokenizer: Any) -> dict[str, str]:
    prompt_text = str(
        tokenizer.apply_chat_template(item["prompt"], tokenize=False, add_generation_prompt=True)
    )
    return {
        "prompt": prompt_text,
        "chosen": str(item["chosen"]),
        "rejected": str(item["rejected"]),
    }


if __name__ == "__main__":
    main()
