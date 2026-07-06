#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

import torch
from datasets import load_dataset
from peft import LoraConfig
from transformers import AutoModelForMultimodalLM, AutoTokenizer, BitsAndBytesConfig
from trl import SFTConfig, SFTTrainer

from pyt_agent_lab.lora_training import example_to_training_text, load_training_plan, require_dependency


def main() -> None:
    parser = argparse.ArgumentParser(description="Train a small QLoRA adapter from distilled protocol-patch SFT data.")
    parser.add_argument("--plan", required=True)
    parser.add_argument("--max-steps", type=int, default=None, help="Optional smoke-test cap. Use a positive value for dry training runs.")
    args = parser.parse_args()

    plan = load_training_plan(args.plan)
    if plan["method"] == "qlora_4bit":
        require_dependency("bitsandbytes")

    tokenizer = AutoTokenizer.from_pretrained(plan["model_path"], trust_remote_code=False)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    quantization_config = None
    if plan["method"] == "qlora_4bit":
        quantization_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=torch.bfloat16,
            bnb_4bit_use_double_quant=True,
        )

    model = AutoModelForMultimodalLM.from_pretrained(
        plan["model_path"],
        device_map="auto",
        torch_dtype=torch.bfloat16,
        quantization_config=quantization_config,
        trust_remote_code=False,
    )
    if plan["training"].get("gradient_checkpointing", False):
        model.gradient_checkpointing_enable()
        model.config.use_cache = False

    raw = load_dataset("json", data_files={"train": plan["train_file"], "eval": plan["heldout_file"]})

    def format_batch(example):
        return {"text": example_to_training_text(example, tokenizer)}

    dataset = raw.map(format_batch, remove_columns=raw["train"].column_names)

    training = dict(plan["training"])
    if args.max_steps is not None:
        training["max_steps"] = args.max_steps
        training["num_train_epochs"] = 1
        training["save_strategy"] = "no"
        training["eval_strategy"] = "no"

    sft_config = SFTConfig(
        output_dir=plan["output_dir"],
        dataset_text_field="text",
        max_length=plan["max_seq_length"],
        packing=False,
        report_to=[],
        **training,
    )
    lora = plan["lora"]
    peft_config = LoraConfig(
        r=int(lora["r"]),
        lora_alpha=int(lora["alpha"]),
        lora_dropout=float(lora["dropout"]),
        target_modules=list(lora["target_modules"]),
        task_type="CAUSAL_LM",
    )
    trainer = SFTTrainer(
        model=model,
        args=sft_config,
        train_dataset=dataset["train"],
        eval_dataset=dataset["eval"],
        processing_class=tokenizer,
        peft_config=peft_config,
    )
    trainer.train()
    trainer.save_model(plan["output_dir"])
    tokenizer.save_pretrained(plan["output_dir"])
    Path(plan["output_dir"], "training_complete.txt").write_text("ok\n", encoding="utf-8")


if __name__ == "__main__":
    main()
