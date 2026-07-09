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
from trl import DPOConfig, DPOTrainer

from pyt_agent_lab.lora_training import load_training_plan, require_dependency


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
    parser = argparse.ArgumentParser(description="Train a DPO v2 adapter from Pythontopia preference data.")
    parser.add_argument("--plan", required=True)
    parser.add_argument("--max-steps", type=int, default=None, help="Optional smoke-test cap.")
    args = parser.parse_args()

    plan = load_training_plan(args.plan)
    require_dependency("trl")
    require_dependency("peft")
    require_dependency("bitsandbytes")

    tokenizer = AutoTokenizer.from_pretrained(plan["model_path"], trust_remote_code=False)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

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
    model = PeftModel.from_pretrained(
        base_model,
        plan["sft_adapter_path"],
        is_trainable=True,
    )
    if plan["training"].get("gradient_checkpointing", False):
        model.gradient_checkpointing_enable()
        model.config.use_cache = False

    raw = load_dataset(
        "json",
        data_files={"train": plan["train_file"], "eval": plan["heldout_file"]},
    )
    dataset = raw.map(lambda item: _format_preference_item(item, tokenizer))

    training = dict(plan["training"])
    if args.max_steps is not None:
        training["max_steps"] = args.max_steps
        training["num_train_epochs"] = 1
        training["save_strategy"] = "no"
        training["eval_strategy"] = "no"

    dpo_config = DPOConfig(
        output_dir=plan["output_dir"],
        max_length=plan["max_seq_length"],
        beta=float(plan["dpo"]["beta"]),
        loss_type=[str(plan["dpo"].get("loss_type", "sigmoid"))],
        report_to=[],
        **training,
    )
    trainer = DPOTrainer(
        model=model,
        args=dpo_config,
        train_dataset=dataset["train"],
        eval_dataset=dataset["eval"],
        processing_class=tokenizer,
        callbacks=[JsonlTrainerLogCallback(Path(plan["output_dir"]) / "trainer_log.jsonl")],
    )
    trainer.train()
    trainer.save_model(plan["output_dir"])
    tokenizer.save_pretrained(plan["output_dir"])
    Path(plan["output_dir"], "training_complete.txt").write_text("ok\n", encoding="utf-8")


def _format_preference_item(item: dict[str, Any], tokenizer: Any) -> dict[str, str]:
    prompt = item["prompt"]
    prompt_text = str(
        tokenizer.apply_chat_template(prompt, tokenize=False, add_generation_prompt=True)
    )
    return {
        "prompt": prompt_text,
        "chosen": str(item["chosen"]),
        "rejected": str(item["rejected"]),
    }


if __name__ == "__main__":
    main()
