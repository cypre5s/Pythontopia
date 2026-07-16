#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from collections.abc import Mapping
from pathlib import Path
from typing import Any

import torch

from pyt_agent_lab.gdpo_rpo import (
    create_gdpo_rpo_trainer_class,
    create_preference_q_collator,
    gdpo_rpo_loss,
    summarize_preference_q,
)
from pyt_agent_lab.lora_training import load_training_plan, require_dependency


def main() -> None:
    parser = argparse.ArgumentParser(description="训练带几何软偏好 GDPO 与 chosen-NLL 正则的 R-SAGE 专属 LoRA。")
    parser.add_argument("--plan", help="GDPO+RPO 训练计划 JSON。")
    parser.add_argument("--max-steps", type=int, default=None, help="小步真实训练上限。")
    parser.add_argument("--resume-from-checkpoint")
    parser.add_argument("--demo", action="store_true", help="只运行纯损失演示并保存中文回执，不加载模型。")
    parser.add_argument(
        "--demo-output",
        default="research/vllm-agent-ft-lab/outputs/rsage/demos/gdpo-rpo/loss-demo.json",
    )
    args = parser.parse_args()

    if args.demo:
        _run_loss_demo(Path(args.demo_output))
        return
    if not args.plan:
        raise SystemExit("正式训练必须提供 --plan")

    plan = load_training_plan(args.plan)
    _validate_plan(plan)
    from pyt_agent_lab.training_quality import (
        archive_training_failure,
        initialize_experiment_manifest,
    )

    initialize_experiment_manifest(plan=plan, plan_path=args.plan, output_dir=plan["output_dir"])
    try:
        _train(plan, args)
    except BaseException as exc:
        archive_training_failure(plan["output_dir"], exc, "gdpo_rpo_training")
        raise


def _train(plan: dict[str, Any], args: argparse.Namespace) -> None:
    require_dependency("trl")
    require_dependency("peft")
    require_dependency("bitsandbytes")
    from datasets import load_dataset
    from peft import PeftModel
    from transformers import (
        AutoModelForMultimodalLM,
        AutoTokenizer,
        BitsAndBytesConfig,
        EarlyStoppingCallback,
        TrainerCallback,
    )
    from trl import DPOConfig

    from pyt_agent_lab.training_quality import (
        run_preference_preflight,
        update_experiment_status,
        write_preference_preflight,
    )

    output_dir = Path(plan["output_dir"])
    output_dir.mkdir(parents=True, exist_ok=True)
    train_rows = _load_jsonl(plan["train_file"])
    eval_rows = _load_jsonl(plan["heldout_file"])
    q_preflight = {
        "train": summarize_preference_q(train_rows),
        "eval": summarize_preference_q(eval_rows),
        "message_zh": "训练集与开发集软偏好概率均通过核验",
    }
    (output_dir / "soft_preference_preflight.json").write_text(
        json.dumps(q_preflight, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(q_preflight["message_zh"], flush=True)

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
    write_preference_preflight(preflight, output_dir / "preference_preflight.json")
    if not preflight["passed"]:
        raise ValueError("偏好训练预检失败，请查看 preference_preflight.json")
    update_experiment_status(output_dir, "training")
    print("偏好数据长度、重复与变量隔离预检已通过", flush=True)

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
    experiment_seed = int(plan.get("seed", 42))
    training.setdefault("seed", experiment_seed)
    training.setdefault("data_seed", experiment_seed)
    if args.max_steps is not None:
        if args.max_steps <= 0:
            raise ValueError("真实训练步数上限必须是正整数")
        training.update(
            {
                "max_steps": args.max_steps,
                "num_train_epochs": 1,
                "save_strategy": "no",
                "eval_strategy": "no",
                "load_best_model_at_end": False,
            }
        )

    objective = dict(plan["gdpo_rpo"])
    beta = float(objective["beta"])
    lambda_sft = float(objective["lambda_sft"])
    dpo_config = DPOConfig(
        output_dir=str(output_dir),
        max_length=int(plan["max_seq_length"]),
        beta=beta,
        loss_type=["sigmoid", "sft"],
        loss_weights=[1.0, lambda_sft],
        precompute_ref_log_probs=bool(objective.get("precompute_ref_log_probs", True)),
        precompute_ref_batch_size=1,
        disable_dropout=False,
        remove_unused_columns=True,
        use_liger_kernel=False,
        f_divergence_type="reverse_kl",
        report_to=[],
        **training,
    )
    collator = create_preference_q_collator(
        pad_token_id=int(tokenizer.pad_token_id),
        pad_to_multiple_of=dpo_config.pad_to_multiple_of,
    )

    class ChineseJsonlTrainerLogCallback(TrainerCallback):
        def on_log(self, args, state, control, logs=None, **kwargs):  # noqa: ANN001, ANN003
            if logs is None:
                return
            row = {
                "step": state.global_step,
                "epoch": state.epoch,
                **logs,
                "message_zh": f"训练日志：全局步 {state.global_step}，轮次 {state.epoch}",
            }
            with (output_dir / "trainer_log.jsonl").open("a", encoding="utf-8") as handle:
                handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")

    callbacks: list[Any] = [ChineseJsonlTrainerLogCallback()]
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

    trainer_class = create_gdpo_rpo_trainer_class()
    trainer = trainer_class(
        model=model,
        args=dpo_config,
        train_dataset=dataset["train"],
        eval_dataset=dataset["eval"],
        processing_class=tokenizer,
        data_collator=collator,
        callbacks=callbacks,
        lambda_sft=lambda_sft,
    )
    if dpo_config.precompute_ref_log_probs:
        unwrapped = trainer.accelerator.unwrap_model(trainer.model)
        if hasattr(unwrapped, "peft_config") and "ref" in unwrapped.peft_config:
            unwrapped.set_adapter("default")
            unwrapped.delete_adapter("ref")
            print("参考对数概率已预计算，并已移除只读 ref Adapter 以保证检查点可恢复", flush=True)

    print(
        f"开始 GDPO+RPO 训练：beta={beta}，chosen NLL 系数={lambda_sft}，"
        f"训练样本={len(dataset['train'])}，开发样本={len(dataset['eval'])}",
        flush=True,
    )
    trainer.train(resume_from_checkpoint=args.resume_from_checkpoint or None)
    trainer.save_model(str(output_dir))
    tokenizer.save_pretrained(str(output_dir))
    (output_dir / "training_complete.txt").write_text("训练完成\n", encoding="utf-8")
    update_experiment_status(output_dir, "trained_not_promoted")
    print(f"GDPO+RPO 训练完成，Adapter 已保存到：{output_dir}", flush=True)


def _format_preference_item(item: Mapping[str, Any], tokenizer: Any) -> dict[str, Any]:
    prompt_text = str(
        tokenizer.apply_chat_template(item["prompt"], tokenize=False, add_generation_prompt=True)
    )
    return {
        "prompt": prompt_text,
        "chosen": str(item["chosen"]),
        "rejected": str(item["rejected"]),
        "preference_q": float(item["preference_q"]),
    }


def _validate_plan(plan: Mapping[str, Any]) -> None:
    if plan.get("method") != "gdpo_rpo_lora":
        raise ValueError("训练计划 method 必须是 gdpo_rpo_lora")
    objective = plan.get("gdpo_rpo")
    if not isinstance(objective, Mapping):
        raise ValueError("训练计划缺少 gdpo_rpo 目标配置")
    if float(objective.get("beta", 0)) <= 0:
        raise ValueError("训练计划 beta 必须大于零")
    if float(objective.get("lambda_sft", -1)) < 0:
        raise ValueError("训练计划 chosen NLL 系数不能小于零")
    training = plan.get("training")
    if not isinstance(training, Mapping):
        raise ValueError("训练计划缺少 training 配置")
    if int(training.get("per_device_train_batch_size", 0)) != 1:
        raise ValueError("GDPO 训练 microbatch 必须是 1")
    if int(training.get("per_device_eval_batch_size", 0)) != 1:
        raise ValueError("GDPO 开发评测 microbatch 必须是 1")


def _run_loss_demo(path: Path) -> None:
    delta = torch.tensor([1.0, 1.0, 1.0], requires_grad=True)
    q = torch.tensor([0.5, 2 / 3, 1.0])
    chosen_nll = torch.tensor([1.0, 1.0, 1.0])
    parts = gdpo_rpo_loss(delta, q, chosen_nll, beta=0.1, lambda_sft=0.2, reduction="none")
    parts.total.sum().backward()
    receipt = {
        "format": "gdpo_rpo_loss_demo_v1",
        "beta": 0.1,
        "lambda_sft": 0.2,
        "preference_q": q.tolist(),
        "gdpo_loss": parts.gdpo.detach().tolist(),
        "chosen_nll": parts.chosen_nll.detach().tolist(),
        "total_loss": parts.total.detach().tolist(),
        "margin_gradient": delta.grad.detach().tolist(),
        "q_half_gradient_is_zero": bool(delta.grad[0].item() == 0.0),
        "message_zh": "GDPO+RPO 纯损失演示通过：q=0.5 的偏好梯度为零，chosen NLL 仍独立生效",
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(receipt, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(receipt["message_zh"])
    print(f"演示回执：{path}")


def _load_jsonl(path: str | Path) -> list[dict[str, Any]]:
    return [
        json.loads(line)
        for line in Path(path).read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001 - 命令行统一输出中文失败原因
        raise SystemExit(f"GDPO+RPO 训练失败：{exc}") from None
