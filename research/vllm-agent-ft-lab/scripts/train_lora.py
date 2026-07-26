#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

import torch
from datasets import Dataset, DatasetDict, disable_progress_bars
from peft import LoraConfig, PeftModel
from transformers import (
    AutoModelForMultimodalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    EarlyStoppingCallback,
    PrinterCallback,
    ProgressCallback,
    TrainerCallback,
)
from trl import SFTConfig, SFTTrainer

from pyt_agent_lab.lora_training import (
    example_to_prompt_completion,
    example_to_training_text,
    load_training_plan,
    require_dependency,
    sft_loss_masking_config_kwargs,
    should_use_prompt_completion_sft_dataset,
)
from pyt_agent_lab.rsage_training_plan import (
    filter_rows_for_training_plan,
    validate_training_plan_hashes,
)
from pyt_agent_lab.rsage_length_preflight import (
    RsageLengthPreflightError,
    run_rsage_length_preflight,
)


def main() -> None:
    disable_progress_bars()
    parser = argparse.ArgumentParser(description="按冻结计划训练 R-SAGE QLoRA SFT Adapter。")
    parser.add_argument("--plan", required=True, help="训练计划 JSON。")
    parser.add_argument("--max-steps", type=int, default=None, help="小步真实训练上限，仅用于演示验证。")
    args = parser.parse_args()

    plan = load_training_plan(args.plan)
    if plan.get("selected_train_ids"):
        validate_training_plan_hashes(plan)
    if plan["method"] == "qlora_4bit":
        require_dependency("bitsandbytes")
    initial_adapter_path = plan.get("initial_adapter_path")
    if initial_adapter_path and not Path(str(initial_adapter_path)).exists():
        raise FileNotFoundError(f"冻结 champion Adapter 不存在：{initial_adapter_path}")

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

    base_model = AutoModelForMultimodalLM.from_pretrained(
        plan["model_path"],
        device_map="auto",
        torch_dtype=torch.bfloat16,
        quantization_config=quantization_config,
        trust_remote_code=False,
    )
    model = (
        PeftModel.from_pretrained(base_model, str(initial_adapter_path), is_trainable=True)
        if initial_adapter_path
        else base_model
    )
    if initial_adapter_path:
        print(f"已从冻结 champion Adapter 初始化 specialist：{initial_adapter_path}", flush=True)
    if plan["training"].get("gradient_checkpointing", False):
        model.gradient_checkpointing_enable()
        model.config.use_cache = False

    all_train_rows = _load_jsonl(plan["train_file"])
    selected_train = (
        filter_rows_for_training_plan(all_train_rows, plan)
        if plan.get("selected_train_ids")
        else all_train_rows
    )
    eval_rows = _load_jsonl(plan["heldout_file"])
    raw = DatasetDict(
        {
            "train": Dataset.from_list(selected_train),
            "eval": Dataset.from_list(eval_rows),
        }
    )
    print(
        f"训练子集哈希校验通过：本轮使用 {len(selected_train)} 条训练数据，开发集 {len(eval_rows)} 条",
        flush=True,
    )
    try:
        length_report = run_rsage_length_preflight(
            sft_rows=[*selected_train, *eval_rows],
            preference_rows=[],
            tokenizer=tokenizer,
            max_seq_length=int(plan["max_seq_length"]),
        )
    except RsageLengthPreflightError as exc:
        _write_json(Path(plan["output_dir"]) / "length_preflight.json", exc.report)
        raise
    _write_json(Path(plan["output_dir"]) / "length_preflight.json", length_report)
    print(length_report["message_zh"], flush=True)

    if should_use_prompt_completion_sft_dataset(plan):
        def format_prompt_completion(example):
            return example_to_prompt_completion(example, tokenizer)

        dataset = raw.map(format_prompt_completion, remove_columns=raw["train"].column_names)
        dataset_text_field = None
    else:
        def format_batch(example):
            return {"text": example_to_training_text(example, tokenizer)}

        dataset = raw.map(format_batch, remove_columns=raw["train"].column_names)
        dataset_text_field = "text"

    training = dict(plan["training"])
    if args.max_steps is not None:
        training["max_steps"] = args.max_steps
        training["num_train_epochs"] = 1
        training["save_strategy"] = "no"
        training["eval_strategy"] = "no"

    sft_config_kwargs = {
        "output_dir": plan["output_dir"],
        "max_length": plan["max_seq_length"],
        "packing": False,
        "report_to": [],
        **sft_loss_masking_config_kwargs(plan),
        **training,
    }
    if dataset_text_field is not None:
        sft_config_kwargs["dataset_text_field"] = dataset_text_field
    sft_config = SFTConfig(**sft_config_kwargs)
    lora = plan["lora"]
    peft_config = None
    if not initial_adapter_path:
        peft_config = LoraConfig(
            r=int(lora["r"]),
            lora_alpha=int(lora["alpha"]),
            lora_dropout=float(lora["dropout"]),
            target_modules=list(lora["target_modules"]),
            task_type="CAUSAL_LM",
        )

    output_dir = Path(plan["output_dir"])

    class ChineseJsonlTrainerLogCallback(TrainerCallback):
        def on_log(self, args, state, control, logs=None, **kwargs):  # noqa: ANN001, ANN003
            if logs is None:
                return
            row = {
                "step": state.global_step,
                "epoch": state.epoch,
                **logs,
                "message_zh": f"SFT 训练日志：全局步 {state.global_step}，轮次 {state.epoch}",
            }
            output_dir.mkdir(parents=True, exist_ok=True)
            with (output_dir / "trainer_log.jsonl").open("a", encoding="utf-8") as handle:
                handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
            summary = [f"全局步 {state.global_step}", f"轮次 {state.epoch}"]
            if "loss" in logs:
                summary.append(f"训练损失 {logs['loss']}")
            if "eval_loss" in logs:
                summary.append(f"开发损失 {logs['eval_loss']}")
            if "grad_norm" in logs:
                summary.append(f"梯度范数 {logs['grad_norm']}")
            print("SFT 进度：" + "，".join(summary), flush=True)

    callbacks: list[object] = [ChineseJsonlTrainerLogCallback()]
    patience = int(plan.get("fit_safeguards", {}).get("early_stopping_patience", 0))
    if patience > 0 and str(training.get("eval_strategy", "no")) != "no":
        callbacks.append(
            EarlyStoppingCallback(
                early_stopping_patience=patience,
                early_stopping_threshold=float(
                    plan.get("fit_safeguards", {}).get("early_stopping_threshold", 0.005)
                ),
            )
        )
    trainer = SFTTrainer(
        model=model,
        args=sft_config,
        train_dataset=dataset["train"],
        eval_dataset=dataset["eval"],
        processing_class=tokenizer,
        peft_config=peft_config,
        callbacks=callbacks,
    )
    trainer.remove_callback(PrinterCallback)
    trainer.remove_callback(ProgressCallback)
    print(
        f"开始 R-SAGE SFT 训练：训练样本 {len(dataset['train'])} 条，开发样本 {len(dataset['eval'])} 条",
        flush=True,
    )
    trainer.train()
    trainer.save_model(plan["output_dir"])
    tokenizer.save_pretrained(plan["output_dir"])
    Path(plan["output_dir"], "training_complete.txt").write_text("训练完成\n", encoding="utf-8")
    print(f"SFT 训练完成，Adapter 已保存到：{plan['output_dir']}", flush=True)


def _load_jsonl(path: str | Path) -> list[dict[str, object]]:
    return [
        json.loads(line)
        for line in Path(path).read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def _write_json(path: Path, value: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001 - 命令行统一输出中文失败原因
        raise SystemExit(f"SFT 训练失败：{exc}") from None
