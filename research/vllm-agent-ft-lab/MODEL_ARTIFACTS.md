# Qwen3.5 Fine-Tuning Artifacts

This lab contains the reproducible code and reports for the local Qwen3.5/vLLM fine-tuning experiment. The GitHub repository does not store heavyweight model artifacts directly.

## Reader Summary

The fine-tuning work is documented as a reproducible research lab, not as a model-weight dump. A GitHub reader should be able to understand:

- why the project used local Qwen3.5-4B AWQ + vLLM on an 8 GB GPU;
- how 1000 Pythontopia Agent cases were generated and audited;
- how clean E1 protocol responses were distilled into SFT train/heldout splits;
- how the QLoRA adapter was trained and served with vLLM;
- how the adapter performed before and after deterministic JSON/evidence/safety guards.

The headline result is: raw AWQ baseline JSON validity was `0.686` on 1000 Agent cases; E1 protocol prompt reached `0.980`; QLoRA adapter under beginner-noise evaluation reached `0.990`; guarded v1 reached JSON `1.000`, grounding `1.000`, and forbidden leak `0.000` on 1000 cases. See the reports linked below for the full tables and caveats.

## Included In Git

| Path | Content |
| --- | --- |
| `README.md` | End-to-end experiment commands and current status. |
| `docs/` | Baseline, E1 protocol patch, QLoRA distillation, process log, and story notes. |
| `scripts/` | Model inspection, baseline runs, SFT data building, QLoRA training, LoRA serving, repair, and report generation entry points. |
| `src/pyt_agent_lab/` | Reusable experiment library code. |
| `tests/` | Unit tests for case generation, logging, scoring, training plans, LoRA evaluation, and reports. |
| `cases/` | Curated Pythontopia domain evaluation cases, including the 1000-case and beginner-noise sets. |
| `configs/` and `docker/` | Dataset metadata and isolated repair container definitions. |
| `outputs/` small manifests, reports, charts, SFT splits, and smoke summaries | Lightweight evidence needed to understand the experiment without rerunning every long job. Heavy adapters, raw external dataset caches, and long-run caches stay ignored. |

## Excluded From Git

| Path | Reason |
| --- | --- |
| `.venv/` | Rebuildable local Python environment; about 10 GB on the development machine. |
| `models/` | Downloaded Qwen/AWQ base model weights; several GB and governed by upstream model licenses. |
| `outputs/adapters/` | LoRA adapter weight files; each adapter is around 200 MB, above the practical limit for a normal GitHub repo without Git LFS or a model registry. |
| `runs/` | Raw long-run response/event caches; reports summarize the durable results. |
| `outputs/datasets/raw/` and `outputs/datasets/normalized/` | External dataset cache and normalized derivatives; rebuild from the documented dataset process when needed. |

## Reproducing The Adapter

1. Prepare the isolated environment:

```bash
bash research/vllm-agent-ft-lab/scripts/setup_vllm_env.sh
```

2. Download or place the base model paths described in [`README.md`](./README.md).

3. Build the SFT dataset from the clean E1 run or reuse the documented split if available locally:

```bash
PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/build_sft_dataset.py \
  --responses research/vllm-agent-ft-lab/runs/e1-protocol-patch-v1-1000-v1-conc4-20260702T103600Z/responses.jsonl \
  --source-run e1-protocol-patch-v1-1000-v1-conc4-20260702T103600Z \
  --output-dir research/vllm-agent-ft-lab/outputs/sft/e1-protocol-patch-v1-1000-v1 \
  --heldout-ratio 0.2 \
  --seed 20260702
```

4. Run the QLoRA preflight and training commands from [`README.md`](./README.md).

5. Validate with:

```bash
PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 -m pytest research/vllm-agent-ft-lab/tests -q
```

## Result Reports

- Main method report: [`../../docs/reports/2026-07-03-qwen-vllm-finetuning-method-report.md`](../../docs/reports/2026-07-03-qwen-vllm-finetuning-method-report.md)
- Large-sample vLLM LoRA report: [`../../docs/reports/2026-07-03-vllm-1000-beginner-noise-multifold-eval.md`](../../docs/reports/2026-07-03-vllm-1000-beginner-noise-multifold-eval.md)
- Web/Graph A/B report: [`../../docs/reports/2026-07-02-fine-tuned-llm-web-ab.md`](../../docs/reports/2026-07-02-fine-tuned-llm-web-ab.md)
- Guard/fallback report: [`../../docs/reports/2026-07-03-vllm-safety-debug-guard-fix.md`](../../docs/reports/2026-07-03-vllm-safety-debug-guard-fix.md)
- Interview defense notes: [`../../docs/presentations/2026-07-06-ai-innovation-defense.md`](../../docs/presentations/2026-07-06-ai-innovation-defense.md)
