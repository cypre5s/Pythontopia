# Preference Optimization Experiment Log

Date: 2026-07-07

## Goal

Build the next post-training stage after the existing SFT + QLoRA adapter:

1. DPO v2 adapter from existing raw/E1/guarded response artifacts.
2. ORPO comparison run after the DPO pipeline is stable.
3. Small GRPO/RLVR prototype for verifiable JSON, evidence, and safety rewards.

The experiment must keep raw and repaired artifacts separate, record data as it is generated, and produce charts/reports before any result is claimed.

## Current Baseline

Already completed before this log:

- SFT + QLoRA adapter: `pythontopia-qwen35-e1-lora`.
- Main SFT data: `outputs/sft/e1-protocol-patch-v1-1000-v1/`.
- Raw 1000-case baseline: `runs/baseline-1000-v1-conc4-20260702T100600Z`.
- E1 1000-case teacher run: `runs/e1-protocol-patch-v1-1000-v1-conc4-20260702T103600Z`.
- Beginner-noise raw LoRA run: `runs/vllm-lora-domain-1000-beginner-noise-5fold-conc2-20260703T1559CST`.
- Beginner-noise guarded run: `runs/vllm-lora-domain-1000-beginner-noise-5fold-conc2-guarded-v1-20260703`.

## 2026-07-07 DPO v2 Data Preparation

### Code changes

- Added `src/pyt_agent_lab/preference_dataset.py`.
- Added `scripts/build_preference_dataset.py`.
- Added `tests/test_preference_dataset.py`.
- Extended `response_audit.py` so forbidden answer leaks become first-class audit flags.
- Extended `tests/test_response_audit.py` with a SafetyAgent forbidden-leak regression.

### TDD evidence

Red checks:

- `test_preference_dataset.py` initially failed with `ModuleNotFoundError: No module named 'pyt_agent_lab.preference_dataset'`.
- `test_audit_flags_forbidden_answer_leak` initially failed because audit did not emit `forbidden_leak`.

Green checks:

```bash
PYTHONPATH=research/vllm-agent-ft-lab/src python3 -m pytest research/vllm-agent-ft-lab/tests/test_response_audit.py -q
# 4 passed

PYTHONPATH=research/vllm-agent-ft-lab/src python3 -m pytest research/vllm-agent-ft-lab/tests/test_preference_dataset.py -q
# 3 passed
```

### Dataset 1: E1 clean chosen vs raw AWQ baseline rejected

Command:

```bash
PYTHONPATH=research/vllm-agent-ft-lab/src python3 research/vllm-agent-ft-lab/scripts/build_preference_dataset.py \
  --chosen-responses research/vllm-agent-ft-lab/runs/e1-protocol-patch-v1-1000-v1-conc4-20260702T103600Z/responses.jsonl \
  --chosen-source e1-protocol-patch-v1-1000-v1-clean \
  --rejected-responses research/vllm-agent-ft-lab/runs/baseline-1000-v1-conc4-20260702T100600Z/responses.jsonl \
  --rejected-source raw-awq-baseline-1000-v1 \
  --output-dir research/vllm-agent-ft-lab/outputs/preferences/dpo-v2-e1-vs-raw-baseline-1000-v1 \
  --heldout-ratio 0.2 \
  --seed 20260707
```

Manifest summary:

- Total preference pairs: 371.
- Train / heldout: 297 / 74.
- Main rejected flags:
  - `invalid_json`: 296
  - `markdown_fence`: 296
  - `missing_required_evidence_id`: 100
  - `forbidden_leak`: 12
- Main agents:
  - `GuideCoachAgent`: 100
  - `ResourceDesignerAgent`: 100
  - `MindMapAgent`: 82
  - `DebugAgent`: 54

Interpretation:

This is the main DPO v2 dataset for broad protocol improvement. It teaches the adapter to prefer clean E1 JSON over raw AWQ outputs with format, evidence, and safety defects.

### Dataset 2: Guarded chosen vs raw LoRA rejected on beginner-noise cases

Command:

```bash
PYTHONPATH=research/vllm-agent-ft-lab/src python3 research/vllm-agent-ft-lab/scripts/build_preference_dataset.py \
  --chosen-responses research/vllm-agent-ft-lab/runs/vllm-lora-domain-1000-beginner-noise-5fold-conc2-guarded-v1-20260703/responses.jsonl \
  --chosen-source guarded-v1-beginner-noise-1000 \
  --rejected-responses research/vllm-agent-ft-lab/runs/vllm-lora-domain-1000-beginner-noise-5fold-conc2-20260703T1559CST/responses.jsonl \
  --rejected-source raw-vllm-lora-beginner-noise-1000 \
  --output-dir research/vllm-agent-ft-lab/outputs/preferences/dpo-v2-guarded-vs-raw-lora-beginner-noise-1000 \
  --heldout-ratio 0.2 \
  --seed 20260707
```

Manifest summary:

- Total preference pairs: 41.
- Train / heldout: 33 / 8.
- Main rejected flags:
  - `missing_required_evidence_id`: 31
  - `invalid_json`: 10
  - `forbidden_leak`: 5
- Agents:
  - `DebugAgent`: 31
  - `SafetyAgent`: 8
  - `MindMapAgent`: 2

Interpretation:

This is the focused safety/debug preference slice. It is small but important because it directly targets the failures that survived SFT + QLoRA under beginner-noise prompts.

## Dependency Check

Global Python does not include TRL. The experiment virtualenv does:

```text
trl 0.29.1
DPOTrainer True
ORPOTrainer False
GRPOTrainer True
```

Implications:

- DPO can be implemented directly with the existing TRL environment.
- GRPO/RLVR can be prototyped later with TRL.
- ORPO needs a separate path: upgrade/change training environment, use LLaMA-Factory/Axolotl, or implement a small ORPO trainer only after DPO is stable.

## Next Experimental Steps

### DPO training plan and smoke

Added:

- `src/pyt_agent_lab/preference_training_plan.py`
- `scripts/build_dpo_training_plan.py`
- `scripts/train_dpo.py`
- `tests/test_preference_training_plan.py`

Training plan:

- `outputs/training/dpo-v2-e1-vs-raw-baseline-1000-v1/training_plan.json`
- Base model: `/home/cypress/.cache/modelscope/Qwen/Qwen3.5-4B`
- Starting adapter: `outputs/adapters/e1-protocol-patch-v1-1000-v1-qlora-2epoch`
- DPO data: `outputs/preferences/dpo-v2-e1-vs-raw-baseline-1000-v1`
- Planned full-run output: `outputs/adapters/e1-protocol-patch-v1-1000-v1-dpo-v2`

Smoke command:

```bash
PYTHONPATH=research/vllm-agent-ft-lab/src CUDA_VISIBLE_DEVICES=0 \
research/vllm-agent-ft-lab/.venv/bin/python research/vllm-agent-ft-lab/scripts/train_dpo.py \
  --plan research/vllm-agent-ft-lab/outputs/training/dpo-v2-e1-vs-raw-baseline-1000-v1/training_plan.json \
  --max-steps 1
```

Smoke result:

- Completed 1 DPO optimization step.
- Runtime: 135.47 seconds.
- GPU during step: about 7782 / 8151 MiB, 100% utilization.
- GPU after completion: 0 / 8151 MiB used.
- Loss: 0.693147.
- Mean token accuracy: 0.893974.
- Chosen logps: -84.8364.
- Rejected logps: -150.5967.
- Trainer log: `outputs/adapters/e1-protocol-patch-v1-1000-v1-dpo-v2-smoke1/trainer_log.jsonl`.
- Adapter smoke artifact exists at `outputs/adapters/e1-protocol-patch-v1-1000-v1-dpo-v2-smoke1/`.
- Smoke chart/report:
  - `outputs/training/dpo-v2-e1-vs-raw-baseline-1000-v1/smoke1-report/training_curve.json`
  - `outputs/training/dpo-v2-e1-vs-raw-baseline-1000-v1/smoke1-report/charts/training_loss.svg`

Interpretation:

The open-source TRL DPO path is viable for this project. The full run is likely slow on the 8 GB laptop GPU because a single DPO smoke step took about 135 seconds with memory nearly saturated. Before a full DPO run, reduce max length and/or evaluate Unsloth/LLaMA-Factory/Axolotl for better preference-training throughput.

## Next Experimental Steps

### Full DPO len768 attempt

Command:

```bash
PYTHONPATH=research/vllm-agent-ft-lab/src CUDA_VISIBLE_DEVICES=0 \
research/vllm-agent-ft-lab/.venv/bin/python research/vllm-agent-ft-lab/scripts/train_dpo.py \
  --plan research/vllm-agent-ft-lab/outputs/training/dpo-v2-e1-vs-raw-baseline-1000-v1/training_plan.json
```

Result:

- Failed after 14 / 19 optimization steps.
- Failure: CUDA OOM during DPO reference forward.
- Peak observed memory during training: about 7.8 / 8.1 GiB.
- Partial log preserved at `outputs/adapters/e1-protocol-patch-v1-1000-v1-dpo-v2-len768-failed-partial/trainer_log.jsonl`.
- Partial curve report: `outputs/training/dpo-v2-e1-vs-raw-baseline-1000-v1/len768-failed-report/`.

Root cause:

- `max_seq_length=768` is too close to the 8GB GPU memory ceiling for DPO batches in this dataset. DPO is heavier than SFT because it processes chosen/rejected responses and reference behavior.

Corrective action:

- Added `max_seq_length` support to `build_dpo_training_plan`.
- Generated a shorter len512 plan:
  `outputs/training/dpo-v2-e1-vs-raw-baseline-1000-v1-len512/training_plan.json`.
  Output adapter path:
  `outputs/adapters/e1-protocol-patch-v1-1000-v1-dpo-v2-len512`.

### Full DPO len512 run

Result:

- Completed 19 / 19 optimization steps.
- Completed 74-example heldout eval.
- Output adapter: `outputs/adapters/e1-protocol-patch-v1-1000-v1-dpo-v2-len512/`.
- Trainer log: `outputs/adapters/e1-protocol-patch-v1-1000-v1-dpo-v2-len512/trainer_log.jsonl`.
- Final training report:
  `outputs/training/dpo-v2-e1-vs-raw-baseline-1000-v1-len512/final-report/`.
- Realtime report:
  `outputs/training/dpo-v2-e1-vs-raw-baseline-1000-v1-len512/realtime-report/`.
- Train runtime: 2441 seconds.
- Train loss: 0.092069.
- Eval loss: 0.001634.
- Eval reward accuracy: 1.0.

Heldout generation comparison on the same 74 preference-heldout cases:

| Adapter | score_mean | JSON | grounding | leak |
| --- | ---: | ---: | ---: | ---: |
| SFT QLoRA | 0.999324 | 0.986486 | 1.000000 | 0.000000 |
| SFT + DPO len512 | 0.998649 | 0.986486 | 1.000000 | 0.000000 |

Comparison artifact:

- `outputs/comparisons/dpo-v2-len512-vs-sft-heldout-74/`.

Interpretation:

The DPO path is fully runnable and produces a valid adapter, but this first len512 DPO run does not improve the 74-case heldout generation metrics over the SFT adapter. The likely reason is that the heldout slice is already near saturated for SFT, so DPO needs harder evaluation slices, a safety/debug-focused preference set, or a broader 1000/5000-case generation test before claiming product improvement.

## ORPO Comparison

Tooling decision:

- `trl.experimental.orpo.ORPOTrainer` and `ORPOConfig` are available in the existing experiment venv.
- LLaMA-Factory, Axolotl, and Unsloth are not installed locally.
- Use TRL experimental ORPO first to avoid writing a custom ORPO loss.

Added:

- `build_orpo_training_plan` in `preference_training_plan.py`.
- `--method orpo` in `scripts/build_dpo_training_plan.py`.
- `scripts/train_orpo.py`.

ORPO len512 plan:

- `outputs/training/orpo-v1-e1-vs-raw-baseline-1000-v1-len512/training_plan.json`.
- Output adapter path for full run:
  `outputs/adapters/e1-protocol-patch-v1-1000-v1-orpo-v1-len512`.

ORPO smoke:

```bash
PYTHONPATH=research/vllm-agent-ft-lab/src CUDA_VISIBLE_DEVICES=0 \
research/vllm-agent-ft-lab/.venv/bin/python research/vllm-agent-ft-lab/scripts/train_orpo.py \
  --plan research/vllm-agent-ft-lab/outputs/training/orpo-v1-e1-vs-raw-baseline-1000-v1-len512/training_plan.json \
  --max-steps 1
```

Result:

- Completed 1 ORPO optimization step.
- Runtime: 159.7 seconds.
- Loss: 0.3708.
- Reward accuracy: 0.625.
- GPU was near full during the step and released after completion.
- Smoke output moved to:
  `outputs/adapters/e1-protocol-patch-v1-1000-v1-orpo-v1-len512-smoke1/`.
- Smoke chart/report:
  `outputs/training/orpo-v1-e1-vs-raw-baseline-1000-v1-len512/smoke1-report/`.

1. Decide whether the 1-step smoke adapter should be overwritten or moved to a `smoke` output path before full DPO.
2. Run a small 20-50 step DPO calibration with charts if time allows.
3. Full DPO v2 training if runtime and GPU memory are acceptable.
4. Evaluate DPO adapter on:
   - heldout preference examples,
   - 1000 beginner-noise cases,
   - 5000-case smoke/full pack.
5. Generate comparison charts:
   - SFT adapter vs SFT + DPO,
   - by agent,
   - by noise profile,
   - JSON/grounding/leak deltas.
6. Decide ORPO path based on DPO results and available trainer support.
