# QLoRA Distillation From 1000-Case E1 Run: 2026-07-02

## Purpose

Distill the measured E1 protocol behavior into a local adapter so Pythontopia can move from prompt-only control toward an evidence-aware Agent adapter.

## Source Data

- Source run: `runs/e1-protocol-patch-v1-1000-v1-conc4-20260702T103600Z`.
- Clean responses after audit: 975 / 1000.
- SFT output: `outputs/sft/e1-protocol-patch-v1-1000-v1/`.
- Split: 780 train, 195 heldout.
- Input policy: original case system/user prompts without `protocol_patch_v1` scaffold.
- Target: clean E1 assistant JSON response.

Agent distribution:

| Agent | SFT Rows |
|---|---:|
| DebugAgent | 91 |
| GroundingCriticAgent | 100 |
| GuideCoachAgent | 100 |
| IntentRouterAgent | 100 |
| MindMapAgent | 84 |
| PathPlannerAgent | 100 |
| PracticeAgent | 100 |
| ResourceDesignerAgent | 100 |
| SafetyAgent | 100 |
| TutorAgent | 100 |

## Training Plan

- Base model: `/home/cypress/.cache/modelscope/Qwen/Qwen3.5-4B`.
- Loader: `AutoModelForMultimodalLM`; this matches the current Qwen3.5 model card usage.
- Method: 4-bit QLoRA.
- LoRA rank: 8.
- LoRA alpha: 16.
- Dropout: 0.05.
- Target modules: `q_proj`, `k_proj`, `v_proj`, `o_proj`, `gate_proj`, `up_proj`, `down_proj`.
- Epochs: 2.
- Gradient accumulation: 16.
- Max sequence length: 768.
- Adapter output: `outputs/adapters/e1-protocol-patch-v1-1000-v1-qlora-2epoch`.

Preflight dependencies:

- `torch==2.10.0+cu128`
- `transformers==5.13.0.dev0`
- `peft==0.19.1`
- `trl==0.29.1`
- `datasets==4.8.5`
- `accelerate==1.14.0`
- `bitsandbytes==0.49.2`

The Transformers package was upgraded to the source build because the local Qwen3.5 architecture requires newer loader support than the original PyPI install provided.

## Training Result

- Runtime: 4566 seconds.
- Train examples per second: 0.342.
- Train steps per second: 0.021.
- Global steps: 98.
- Final train loss reported by Trainer: 0.619 aggregate.
- GPU memory during training: about 7.7-7.8 GiB.
- GPU memory after training: 0 MiB.

Heldout metrics:

| Epoch | Step | Eval loss | Eval token accuracy |
|---:|---:|---:|---:|
| 1 | 49 | 0.290419 | 0.907434 |
| 2 | 98 | 0.241026 | 0.917043 |

Training curve artifacts:

- `outputs/training/e1-protocol-patch-v1-1000-v1-full/report/training_curve.json`
- `outputs/training/e1-protocol-patch-v1-1000-v1-full/report/charts/training_loss.svg`

Adapter artifacts:

- `outputs/adapters/e1-protocol-patch-v1-1000-v1-qlora-2epoch/adapter_model.safetensors`
- `outputs/adapters/e1-protocol-patch-v1-1000-v1-qlora-2epoch/adapter_config.json`
- `outputs/adapters/e1-protocol-patch-v1-1000-v1-qlora-2epoch/training_complete.txt`
- checkpoints: `checkpoint-49`, `checkpoint-98`.

## Offline Generation Evaluation

After training, the adapter was evaluated offline on the 195 heldout SFT source cases. This uses the original case prompts without the E1 protocol patch text.

Run:

- Adapter heldout run: `runs/adapter-offline-heldout-195-20260702T142200Z`.
- Baseline heldout slice: `outputs/slices/baseline-1000-v1-heldout-195`.
- E1 heldout slice: `outputs/slices/e1-protocol-patch-v1-1000-v1-heldout-195`.

Results:

| Metric | Baseline Heldout | Adapter Heldout | Delta |
|---|---:|---:|---:|
| Mean score | 0.968120 | 0.989060 | +0.020940 |
| JSON valid rate | 0.748718 | 0.943590 | +0.194872 |
| Grounding safe rate | 0.800000 | 0.953846 | +0.153846 |
| Forbidden leak rate | 0.015385 | 0.000000 | -0.015385 |

Against E1 prompt on the same heldout slice:

| Metric | E1 Heldout | Adapter Heldout | Delta |
|---|---:|---:|---:|
| Mean score | 0.998718 | 0.989060 | -0.009658 |
| JSON valid rate | 1.000000 | 0.943590 | -0.056410 |
| Grounding safe rate | 0.974359 | 0.953846 | -0.020513 |
| Forbidden leak rate | 0.005128 | 0.000000 | -0.005128 |

Adapter audit:

- Clean count: 184 / 195.
- Clean rate: 0.943590.
- Flags: invalid JSON 11, missing evidence 8.

Comparison artifacts:

- `outputs/comparisons/adapter-offline-heldout-195-vs-baseline-heldout-195/`
- `outputs/comparisons/adapter-offline-heldout-195-vs-e1-heldout-195/`

## Boundary

This proves that a 1000-scale QLoRA adapter can be trained locally on this laptop, that heldout token-level loss improves across epochs, and that offline adapter generation beats the original baseline on the same heldout cases. It does not yet prove that the adapter should replace E1 prompts, because the E1 prompt still scores higher on heldout. The next required step is vLLM LoRA serving or a stronger adapter/grounding repair pass.
