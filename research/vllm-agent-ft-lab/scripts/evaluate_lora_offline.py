#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import time
from pathlib import Path

import torch
from peft import PeftModel
from transformers import AutoModelForMultimodalLM, AutoTokenizer, BitsAndBytesConfig

from pyt_agent_lab.baseline import BaselineResult, build_case_messages, load_cases, write_results
from pyt_agent_lab.experiment_logging import ExperimentLogger
from pyt_agent_lab.lora_evaluation import (
    generation_token_budget,
    load_sft_source_case_ids,
    normalize_generation_inputs,
    select_cases_by_ids,
)
from pyt_agent_lab.lora_training import load_training_plan
from pyt_agent_lab.reporting import aggregate_fold_metrics, build_report
from pyt_agent_lab.scoring import score_case_response


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate a local QLoRA adapter offline on heldout Pythontopia Agent cases.")
    parser.add_argument("--plan", required=True)
    parser.add_argument("--adapter", required=True)
    parser.add_argument("--cases", required=True)
    parser.add_argument("--heldout-sft", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--max-new-tokens", type=int, default=384)
    args = parser.parse_args()

    plan = load_training_plan(args.plan)
    tokenizer = AutoTokenizer.from_pretrained(plan["model_path"], trust_remote_code=False)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    quantization_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.bfloat16,
        bnb_4bit_use_double_quant=True,
    )
    base = AutoModelForMultimodalLM.from_pretrained(
        plan["model_path"],
        device_map="auto",
        torch_dtype=torch.bfloat16,
        quantization_config=quantization_config,
        trust_remote_code=False,
    )
    model = PeftModel.from_pretrained(base, args.adapter)
    model.eval()

    case_ids = load_sft_source_case_ids(args.heldout_sft)
    if args.limit is not None:
        case_ids = case_ids[: args.limit]
    cases = select_cases_by_ids(load_cases(args.cases), case_ids)
    output_dir = Path(args.output_dir)
    logger = ExperimentLogger(output_dir)

    results: list[BaselineResult] = []
    for index, case in enumerate(cases):
        logger.event("case_started", {"case_id": case["id"], "agent": case["agent"], "fold": 0, "index": index})
        started = time.perf_counter()
        response = _generate(
            model,
            tokenizer,
            build_case_messages(case),
            max_new_tokens=generation_token_budget(case, args.max_new_tokens),
        )
        latency_ms = int((time.perf_counter() - started) * 1000)
        score = score_case_response(case, response)
        logger.metric({**score.as_row(fold=0), "latency_ms": latency_ms, "response_chars": len(response)})
        logger.event("case_finished", {"case_id": case["id"], "agent": case["agent"], "fold": 0, "index": index, "score": score.score})
        results.append(BaselineResult(case, response, latency_ms, score))

    write_results(results, output_dir / "responses.jsonl")
    metrics = list(csv.DictReader((output_dir / "metrics.csv").open(encoding="utf-8")))
    summary = aggregate_fold_metrics(metrics)
    build_report(summary, output_dir)
    print(output_dir)
    print(summary)


def _generate(model, tokenizer, messages: list[dict[str, str]], max_new_tokens: int) -> str:
    try:
        template_output = tokenizer.apply_chat_template(
            messages,
            tokenize=True,
            add_generation_prompt=True,
            return_tensors="pt",
            enable_thinking=False,
        )
    except TypeError:
        template_output = tokenizer.apply_chat_template(messages, tokenize=True, add_generation_prompt=True, return_tensors="pt")
    inputs, prompt_length = normalize_generation_inputs(template_output, model.device)
    with torch.inference_mode():
        output_ids = model.generate(
            **inputs,
            max_new_tokens=max_new_tokens,
            do_sample=False,
            pad_token_id=tokenizer.pad_token_id,
            eos_token_id=tokenizer.eos_token_id,
        )
    generated = output_ids[0, prompt_length:]
    return str(tokenizer.decode(generated, skip_special_tokens=True)).strip()


if __name__ == "__main__":
    main()
