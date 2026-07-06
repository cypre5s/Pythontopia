# Pythontopia Research Labs

`research/` stores isolated experiment harnesses that support the main Pythontopia product without mixing temporary research state into `backend/`, `frontend/`, or `services/`.

## Labs

| Lab | Purpose | Read first |
| --- | --- | --- |
| [`vllm-agent-ft-lab/`](./vllm-agent-ft-lab/) | Local Qwen3.5/vLLM baseline evaluation, E1 protocol distillation, QLoRA training, LoRA serving, and Graph provider A/B checks. | [`vllm-agent-ft-lab/README.md`](./vllm-agent-ft-lab/README.md) |

## Artifact Policy

The GitHub snapshot includes experiment source code, test code, case generators, curated case JSONL files, process notes, and final reports. It intentionally excludes local virtual environments, downloaded model weights, LoRA adapter weight files, and raw long-run caches.

For the Qwen fine-tuning work, see:

- [`vllm-agent-ft-lab/MODEL_ARTIFACTS.md`](./vllm-agent-ft-lab/MODEL_ARTIFACTS.md) for what is included or excluded.
- [`../docs/reports/2026-07-03-qwen-vllm-finetuning-method-report.md`](../docs/reports/2026-07-03-qwen-vllm-finetuning-method-report.md) for the main method and effect report.
- [`../docs/reports/2026-07-03-vllm-1000-beginner-noise-multifold-eval.md`](../docs/reports/2026-07-03-vllm-1000-beginner-noise-multifold-eval.md) for the 1000-case vLLM LoRA reliability evaluation.
