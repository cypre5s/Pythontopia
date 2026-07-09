# Pythontopia vLLM Agent Fine-tuning Lab

This is an isolated research lab for local Qwen3.5/vLLM baseline evaluation and later LoRA fine-tuning. It intentionally does not modify Pythontopia web frontend or backend code.

## Current Status

- GPU: NVIDIA GeForce RTX 5060 Laptop GPU, 8 GB VRAM.
- vLLM/training environment: `.venv`, `vllm==0.17.1`, `torch==2.10.0+cu128`, `transformers==5.13.0.dev0`, `peft==0.19.1`, `trl==0.29.1`, `bitsandbytes==0.49.2`.
- Baseline model: `models/QuantTrio-Qwen3.5-4B-AWQ`, served as `pythontopia-qwen35-baseline`.
- Smoke baseline run: `runs/baseline-20260702T042521Z`.
- Main domain baseline run: `runs/baseline-20260702T043813Z`.
- Main domain baseline summary: 140 cases, mean score 0.977619, JSON valid rate 0.600000, grounding safe rate 0.642857, forbidden leak rate 0.000000.
- Clean v2 domain baseline run: `runs/baseline-v2-20260702T091400Z`.
- E1 protocol candidate on v2: `runs/e1-protocol-patch-v1-v2-20260702T093000Z`.
- E1 v2 comparison: JSON +0.400000, grounding +0.357143, forbidden leak unchanged at 0.000000, story gate passed.
- Current main 1000-case baseline: `runs/baseline-1000-v1-conc4-20260702T100600Z`.
- Current main 1000-case E1 run: `runs/e1-protocol-patch-v1-1000-v1-conc4-20260702T103600Z`.
- 1000-case E1 comparison: score +0.034583, JSON +0.294000, grounding +0.141000, forbidden leak -0.007000. The strict story gate is false because grounding delta is below +0.20.
- 1000-scale QLoRA adapter: `outputs/adapters/e1-protocol-patch-v1-1000-v1-qlora-2epoch`, trained from 780 train / 195 heldout clean E1 examples.
- Final QLoRA heldout metrics: eval loss 0.241026, token accuracy 0.917043.
- Offline adapter heldout generation: `runs/adapter-offline-heldout-195-20260702T142200Z`.
- Adapter vs raw baseline on the same 195 heldout cases: score +0.020940, JSON +0.194872, grounding +0.153846, forbidden leak -0.015385.
- Adapter does not yet beat the E1 prompt module on the same heldout cases.
- Grounding repair run: `runs/adapter-offline-heldout-195-grounding-repair-v1`; repaired adapter gate passed with score +0.031880, JSON +0.251282, grounding +0.200000, forbidden leak -0.015385 versus the heldout baseline.
- vLLM LoRA serving model ids: `pythontopia-qwen35-awq-base` and `pythontopia-qwen35-e1-lora`.
- Latest live Graph route A/B: `outputs/web-ab/graph-route-live-v5`; candidate gate passed with fallback-like rate 0.000000 and forbidden leak 0.000000.
- Latest backend Web SSE smoke: `outputs/web-ab/backend-workshop-stream-v2`; generated resource 107 with non-empty `practice.problems`, safety `PASSED`, and grounding `PASSED`.
- Latest browser smoke: `outputs/web-ab/workshop-browser-smoke-v2`; `/workshop`, chat pane, study drawer, and calls drawer were visible with no page errors.

## Commands

```bash
# 1. Create isolated environment.
bash research/vllm-agent-ft-lab/scripts/setup_vllm_env.sh

# 2. Inspect local Qwen3.5 candidates.
PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/inspect_models.py --gpu-memory-gb 8 \
  research/vllm-agent-ft-lab/models/QuantTrio-Qwen3.5-4B-AWQ \
  /home/cypress/.cache/modelscope/Qwen/Qwen3.5-4B

# 3. Serve local Qwen3.5 AWQ with vLLM.
VLLM_HOST=0.0.0.0 MAX_MODEL_LEN=1024 GPU_MEMORY_UTILIZATION=0.82 MAX_NUM_SEQS=4 \
bash research/vllm-agent-ft-lab/scripts/serve_qwen35_local.sh

# 4. Run 5-fold smoke baseline.
NO_PROXY='*' no_proxy='*' PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/run_baseline.py \
  --base-url http://127.0.0.1:18093/v1 \
  --api-key local-dev-token \
  --model pythontopia-qwen35-baseline \
  --folds 5 \
  --seed 20260702

# 5. Verify lab code.
PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 -m pytest research/vllm-agent-ft-lab/tests -q

# 6. Generate larger Pythontopia domain eval cases.
PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/generate_domain_cases.py \
  --count 140 \
  --output research/vllm-agent-ft-lab/cases/domain_cases_140.jsonl

# 6a. Generate the current 1000-case research set.
PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/generate_domain_cases.py \
  --count 1000 \
  --output research/vllm-agent-ft-lab/cases/domain_cases_1000_v1.jsonl

# 6b. Run the main 140-case domain baseline.
NO_PROXY='*' no_proxy='*' PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/run_baseline.py \
  --base-url http://127.0.0.1:18093/v1 \
  --api-key local-dev-token \
  --model pythontopia-qwen35-baseline \
  --cases research/vllm-agent-ft-lab/cases/domain_cases_140.jsonl \
  --folds 5 \
  --seed 20260702

# 6c. Run the protocol-patch candidate on the cleaned v2 set.
NO_PROXY='*' no_proxy='*' PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/run_baseline.py \
  --base-url http://127.0.0.1:18093/v1 \
  --api-key local-dev-token \
  --model pythontopia-qwen35-baseline \
  --cases research/vllm-agent-ft-lab/cases/domain_cases_140_v2.jsonl \
  --folds 5 \
  --seed 20260702 \
  --prompt-variant protocol_patch_v1

# 6d. Run the current 1000-case baseline with controlled concurrency.
NO_PROXY='*' no_proxy='*' PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/run_baseline.py \
  --base-url http://127.0.0.1:18093/v1 \
  --api-key local-dev-token \
  --model pythontopia-qwen35-baseline \
  --cases research/vllm-agent-ft-lab/cases/domain_cases_1000_v1.jsonl \
  --folds 5 \
  --seed 20260702 \
  --concurrency 4

# 6e. Run the current 1000-case protocol candidate.
NO_PROXY='*' no_proxy='*' PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/run_baseline.py \
  --base-url http://127.0.0.1:18093/v1 \
  --api-key local-dev-token \
  --model pythontopia-qwen35-baseline \
  --cases research/vllm-agent-ft-lab/cases/domain_cases_1000_v1.jsonl \
  --folds 5 \
  --seed 20260702 \
  --concurrency 4 \
  --prompt-variant protocol_patch_v1

# 7. Normalize Google ED eval data after downloading the raw JSON.
PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/normalize_open_dataset.py \
  --dataset google-ed \
  --input research/vllm-agent-ft-lab/outputs/datasets/raw/google-ed/conversations_eval.json \
  --output research/vllm-agent-ft-lab/outputs/datasets/normalized/google-ed-eval.jsonl \
  --split eval

# 8. Compare two experiment summaries and enforce the story gate.
PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/compare_experiments.py \
  --baseline-summary research/vllm-agent-ft-lab/runs/baseline-v2-20260702T091400Z/summary.json \
  --candidate-summary path/to/candidate/summary.json \
  --output-dir research/vllm-agent-ft-lab/outputs/comparisons/candidate-vs-baseline

# 9. Audit protocol/data-contract risks in a run.
PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/audit_responses.py \
  --responses path/to/run/responses.jsonl \
  --output-dir path/to/run/audit

# 10. Distill clean E1 responses into SFT data.
PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/build_sft_dataset.py \
  --responses research/vllm-agent-ft-lab/runs/e1-protocol-patch-v1-1000-v1-conc4-20260702T103600Z/responses.jsonl \
  --source-run e1-protocol-patch-v1-1000-v1-conc4-20260702T103600Z \
  --output-dir research/vllm-agent-ft-lab/outputs/sft/e1-protocol-patch-v1-1000-v1 \
  --heldout-ratio 0.2 \
  --seed 20260702

# 11. Preflight and train the local QLoRA adapter.
PYTHONPATH=research/vllm-agent-ft-lab/src research/vllm-agent-ft-lab/.venv/bin/python \
research/vllm-agent-ft-lab/scripts/preflight_training.py \
  --model-path /home/cypress/.cache/modelscope/Qwen/Qwen3.5-4B \
  --train-file research/vllm-agent-ft-lab/outputs/sft/e1-protocol-patch-v1-1000-v1/train.jsonl \
  --heldout-file research/vllm-agent-ft-lab/outputs/sft/e1-protocol-patch-v1-1000-v1/heldout.jsonl \
  --adapter-output research/vllm-agent-ft-lab/outputs/adapters/e1-protocol-patch-v1-1000-v1-qlora-2epoch \
  --output-dir research/vllm-agent-ft-lab/outputs/training/e1-protocol-patch-v1-1000-v1-full

PYTHONPATH=research/vllm-agent-ft-lab/src CUDA_VISIBLE_DEVICES=0 \
research/vllm-agent-ft-lab/.venv/bin/python \
research/vllm-agent-ft-lab/scripts/train_lora.py \
  --plan research/vllm-agent-ft-lab/outputs/training/e1-protocol-patch-v1-1000-v1-full/training_plan.json

# 12. Export QLoRA training curves.
PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/report_training_curve.py \
  --trainer-state research/vllm-agent-ft-lab/outputs/adapters/e1-protocol-patch-v1-1000-v1-qlora-2epoch/checkpoint-98/trainer_state.json \
  --output-dir research/vllm-agent-ft-lab/outputs/training/e1-protocol-patch-v1-1000-v1-full/report

# 13. Evaluate the adapter offline on heldout cases.
PYTHONPATH=research/vllm-agent-ft-lab/src CUDA_VISIBLE_DEVICES=0 \
research/vllm-agent-ft-lab/.venv/bin/python \
research/vllm-agent-ft-lab/scripts/evaluate_lora_offline.py \
  --plan research/vllm-agent-ft-lab/outputs/training/e1-protocol-patch-v1-1000-v1-full/training_plan.json \
  --adapter research/vllm-agent-ft-lab/outputs/adapters/e1-protocol-patch-v1-1000-v1-qlora-2epoch \
  --cases research/vllm-agent-ft-lab/cases/domain_cases_1000_v1.jsonl \
  --heldout-sft research/vllm-agent-ft-lab/outputs/sft/e1-protocol-patch-v1-1000-v1/heldout.jsonl \
  --output-dir research/vllm-agent-ft-lab/runs/adapter-offline-heldout-195

# 14. Serve the LoRA adapter with vLLM.
PYTHONPATH=research/vllm-agent-ft-lab/src \
VLLM_HOST=127.0.0.1 VLLM_PORT=18094 GPU_MEMORY_UTILIZATION=0.74 \
MAX_MODEL_LEN=1024 MAX_NUM_SEQS=1 VLLM_API_KEY=local-dev-token \
research/vllm-agent-ft-lab/scripts/serve_qwen35_lora.py

# 15. Repair JSON/evidence grounding and generate the Web A/B report.
PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/repair_run_responses.py \
  --responses research/vllm-agent-ft-lab/runs/adapter-offline-heldout-195-20260702T142200Z/responses.jsonl \
  --output-dir research/vllm-agent-ft-lab/runs/adapter-offline-heldout-195-grounding-repair-v1

PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/build_web_ab_report.py \
  --baseline-summary research/vllm-agent-ft-lab/outputs/slices/baseline-1000-v1-heldout-195/summary.json \
  --adapter-summary research/vllm-agent-ft-lab/runs/adapter-offline-heldout-195-20260702T142200Z/summary.json \
  --repaired-summary research/vllm-agent-ft-lab/runs/adapter-offline-heldout-195-grounding-repair-v1/summary.json \
  --models-json research/vllm-agent-ft-lab/outputs/serving/vllm_models.json \
  --output-dir research/vllm-agent-ft-lab/outputs/web-ab/fine-tuned-provider-v1

# 16. Run live Graph route A/B against the local vLLM LoRA endpoint.
PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/run_graph_route_ab.py \
  --base-url http://127.0.0.1:18094/v1 \
  --api-key local-dev-token \
  --baseline-model pythontopia-qwen35-awq-base \
  --candidate-model pythontopia-qwen35-e1-lora \
  --internal-token local-dev-token \
  --timeout-seconds 180 \
  --output-dir research/vllm-agent-ft-lab/outputs/web-ab/graph-route-live-v5

# 17. Run the full backend SSE workshop smoke through the Vite proxy.
PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/run_backend_workshop_stream_smoke.py \
  --base-url http://127.0.0.1:5173 \
  --type PRACTICE \
  --kc-code PY43-KC-005 \
  --output-dir research/vllm-agent-ft-lab/outputs/web-ab/backend-workshop-stream-v2

# 18. Run the browser workshop smoke.
PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/run_workshop_browser_smoke.py \
  --base-url http://127.0.0.1:5173 \
  --api-base-url http://127.0.0.1:18080 \
  --output-dir research/vllm-agent-ft-lab/outputs/web-ab/workshop-browser-smoke-v2

# 19. Build the 5000-case anti-overfit evaluation pack.
PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/build_5000_eval_pack.py \
  --count 5000 \
  --seed 20260706 \
  --output research/vllm-agent-ft-lab/cases/domain_cases_5000_v1.jsonl \
  --manifest research/vllm-agent-ft-lab/outputs/datasets/domain_cases_5000_v1_manifest.json

# 20. Run the conservative full 5000-case LoRA evaluation.
NO_PROXY='*' no_proxy='*' PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/run_baseline.py \
  --base-url http://127.0.0.1:18094/v1 \
  --api-key local-dev-token \
  --model pythontopia-qwen35-e1-lora \
  --cases research/vllm-agent-ft-lab/cases/domain_cases_5000_v1.jsonl \
  --folds 5 \
  --seed 20260706 \
  --concurrency 1 \
  --progress-every 25 \
  --run-dir research/vllm-agent-ft-lab/runs/vllm-lora-domain-5000-v1-full-20260706

# 21. Generate 5000-run charts, progress tables, and blind-review sheets.
PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/analyze_run.py \
  --run-dir research/vllm-agent-ft-lab/runs/vllm-lora-domain-5000-v1-full-20260706

PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/report_run_progress.py \
  --run-dir research/vllm-agent-ft-lab/runs/vllm-lora-domain-5000-v1-full-20260706

PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/export_blind_review_pack.py \
  --responses research/vllm-agent-ft-lab/runs/vllm-lora-domain-5000-v1-full-20260706/responses.jsonl \
  --output-dir research/vllm-agent-ft-lab/runs/vllm-lora-domain-5000-v1-full-20260706/blind-review \
  --sample-size 120 \
  --seed 20260706
```

## Output Contract

Each experiment run writes:

- `events.jsonl`: real-time case start/finish events.
- `metrics.csv`: per-case metrics.
- `responses.jsonl`: prompt/response/score records.
- `summary.json`: fold and overall aggregates.
- `charts/score_by_fold.svg` and `charts/rates_by_fold.svg`: generated charts.

Training reports additionally write `training_curve.json` and `charts/training_loss.svg`.

Generated raw datasets, normalized external data, model weights, virtualenvs, and run outputs are ignored by Git. The reusable code, configs, docs, and domain case set are tracked.
