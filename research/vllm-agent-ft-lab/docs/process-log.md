# Process Log

## 2026-07-02

### Scope

- User selected route B: configure local vLLM, run baseline, search top-tier paper modules, stitch modules, then tell the story only if metrics improve.
- User updates:
  - Use Qwen3.5, maximum model adapted to this machine.
  - Record experiment data in real time and generate charts.
  - Use larger open-source datasets where possible.
  - Use multi-fold testing for reliability.
- Boundary: all work stays under `research/vllm-agent-ft-lab/`; no web frontend or backend files are edited.

### Hardware

- GPU: NVIDIA GeForce RTX 5060 Laptop GPU.
- VRAM: 8151 MiB total.
- Baseline AWQ serving observed memory: about 6726 MiB used after service startup.

### Model Inventory

Checked candidates:

- `/home/cypress/.cache/modelscope/Qwen/Qwen3.5-4B`
  - Complete: yes.
  - Type: `qwen3_5`.
  - Weight size: 8.68 GiB.
  - vLLM status: architecture import and weight loading succeeded, but service failed with CPU weight offload due vLLM V1 hybrid KV/offload runtime restriction.
- `/home/cypress/.cache/modelscope/Qwen/Qwen3.5-4B-GPTQ-Int4`
  - Complete: no, no config or weights.
- `/home/cypress/.cache/modelscope/Qwen/Qwen3.5-4B-AWQ`
  - Complete: no, no config or weights.
- `~/.cache/huggingface/hub/models--cyankiwi--Qwen3.5-4B-AWQ-4bit/...`
  - Complete: no, missing `model-00001-of-00001.safetensors`.
- `research/vllm-agent-ft-lab/models/QuantTrio-Qwen3.5-4B-AWQ`
  - Download source: Hugging Face `QuantTrio/Qwen3.5-4B-AWQ`.
  - Complete: yes.
  - Type: `qwen3_5`.
  - Quantization: AWQ.
  - Weight size: 5.655 GiB.
  - vLLM status: served successfully.

### vLLM Setup

Installed isolated environment:

- `vllm==0.17.1`
- `torch==2.10.0+cu128`
- `torchvision==0.25.0+cu128`
- `torchaudio==2.10.0+cu128`
- `transformers==4.57.6`

CUDA smoke:

- `torch.cuda.is_available() == True`
- GPU name: NVIDIA GeForce RTX 5060 Laptop GPU.
- vLLM imports `Qwen3_5ForConditionalGeneration`.

### vLLM Startup Attempts

Attempt 1, FP/BF16 4B with CPU offload:

- Command used `Qwen3.5-4B`, `max_model_len=1024`, `cpu_offload_gb=8`.
- Model architecture resolved.
- Weights loaded.
- Failure: TorchDynamo could not trace UVA offloader state dict access.

Attempt 2, FP/BF16 4B with `--enforce-eager`:

- Model weights loaded.
- KV cache estimated.
- Failure: `AssertionError: Cannot re-initialize the input batch when CPU weight offloading is enabled`.

Attempt 3, AWQ 4B without CPU offload:

- Command: `serve_qwen35_local.sh` with `MAX_MODEL_LEN=1024`, `MAX_NUM_SEQS=4`, `GPU_MEMORY_UTILIZATION=0.82`.
- vLLM selected `awq_marlin`.
- Offloader: `NoopOffloader`.
- Model loading memory: about 4.95 GiB.
- KV cache memory: about 1.36 GiB.
- `/health` and `/v1/models` passed.

### Baseline Runs

Run 1:

- Path: `runs/baseline-20260702T042018Z`.
- Issue found: Qwen3.5 thinking mode returned `content=null` and put text under `reasoning`; small token budget caused many `"None"` responses.
- Fix: baseline client now sends `chat_template_kwargs: {"enable_thinking": false}` and falls back to `reasoning` if content is empty.

Run 2:

- Path: `runs/baseline-20260702T042521Z`.
- Cases: 10 local Pythontopia agent cases.
- Folds: 5.
- Mean score: 0.995.
- Score std: 0.015811.
- JSON valid rate: 0.6.
- Grounding safe rate: 0.7.
- Forbidden leak rate: 0.0.
- Artifacts:
  - `events.jsonl`
  - `metrics.csv`
  - `responses.jsonl`
  - `summary.json`
  - `charts/score_by_fold.svg`
  - `charts/rates_by_fold.svg`

### Interpretation

The second baseline is not a final win. The small 10-case set shows that Qwen3.5-4B-AWQ can follow many local prompts once thinking is disabled, but the stricter protocol gaps remain:

- JSON-valid output is only 60%.
- Grounding-safe output is only 70%.
- Evaluation set is still too small for story claims.

The next research step should therefore expand data, strengthen scoring, and train/evaluate candidate modules before claiming improvement.

### 2026-07-02 Continuation: Larger Data and Story Gate

Implemented larger-data and comparison infrastructure:

- Added `domain_case_factory.py` and generated `cases/domain_cases_140.jsonl`.
- The domain set contains 140 Pythontopia Agent cases, spans 10 Agent roles and chapters 1-7, and includes forbidden-answer-risk rows.
- Downloaded Google Education Dialogue eval raw JSON to ignored `outputs/datasets/raw/google-ed/conversations_eval.json`.
- Normalized all 7,234 Google ED eval conversations to ignored `outputs/datasets/normalized/google-ed-eval.jsonl`.
- Added text cleanup for ED generation artifacts such as trailing `\"\",`.
- Added `experiment_compare.py` to compare baseline and candidate summaries and enforce the story gate.
- Ran comparison self-check with baseline vs baseline; deltas were all zero and `story_gate_passed` was false.

Verification:

- `PYTHONPATH=research/vllm-agent-ft-lab/src python3 -m pytest research/vllm-agent-ft-lab/tests -q` passed with 12 tests.
- `wc -l` confirmed 140 domain cases and 7,234 normalized ED rows.

Current data scale:

- Tracked domain eval: 140 cases.
- Ignored normalized external eval data: 7,234 conversations.
- Planned training-scale source: Google ED train files totaling 40,000 conversations.

### 2026-07-02 Continuation: 140-Case Baseline

Ran the larger Pythontopia domain baseline:

- Path: `runs/baseline-20260702T043813Z`.
- Cases: 140 generated Pythontopia Agent cases.
- Folds: 5, with 28 cases per fold.
- Mean score: 0.977619.
- Score std: 0.067375.
- JSON valid rate: 0.600000.
- Grounding safe rate: 0.642857.
- Forbidden leak rate after scorer correction: 0.000000.

Scoring correction:

- Found a false positive in the forbidden-answer detector.
- The SafetyAgent can safely mention that it redacted a "complete answer"; this should not count as leakage unless the text resembles submittable code or an answer payload.
- Added `test_scoring_safety.py` and `rescore.py`.
- Rescored `runs/baseline-20260702T043813Z` in place, preserving fold assignment and regenerating charts.

Agent-level observations:

- JSON protocol failures concentrate in GuideCoachAgent, MindMapAgent, PathPlannerAgent, and ResourceDesignerAgent.
- Grounding-safe failures concentrate in IntentRouterAgent, PathPlannerAgent, and SafetyAgent, partly because those agents often have empty `evidence_ids` and need a clearer common-knowledge or no-evidence contract.
- DebugAgent has the lowest mean score at 0.914 and should be included in the first LoRA/error-taxonomy pass.

Conclusion:

- This is now the main local baseline for candidate comparisons.
- The model can run locally under vLLM on the laptop with Qwen3.5-4B-AWQ.
- The story gate remains closed because no candidate has improved over baseline yet.

### 2026-07-02 Continuation: E1 Protocol Patch and V2 Data Cleanup

Implemented `protocol_patch_v1`:

- Added `prompt_variants.py`.
- Added `--prompt-variant` to `scripts/run_baseline.py`.
- Added strict JSON, evidence, common-knowledge, and tutoring-safety contracts.
- Added tests for prompt variants.

Exploratory E1 on v1 data:

- Run: `runs/e1-protocol-patch-v1-20260702T090200Z`.
- Comparison: `outputs/comparisons/e1-protocol-patch-v1-vs-baseline-140`.
- E1 reached score mean 1.000000, JSON valid 1.000000, grounding safe 1.000000, leak 0.000000.
- Story gate passed, but audit found 30 `page_mention_without_evidence_contract` flags.

Data issue found:

- The original generated 140-case set had 42 no-evidence cases for IntentRouterAgent, SafetyAgent, and PathPlannerAgent.
- Those 42 cases still asked the user to cite a `page-*` ID.
- This made grounding interpretation ambiguous.

Fix:

- Added `response_audit.py` and `scripts/audit_responses.py`.
- Added a regression test ensuring generated no-evidence cases do not request page citations.
- Fixed `domain_case_factory.py`.
- Generated `cases/domain_cases_140_v2.jsonl`.
- Verification: v2 has 140 cases, 42 no-evidence cases, and 0 no-evidence page-citation prompts.

V2 replication:

- Baseline V2 run: `runs/baseline-v2-20260702T091400Z`.
- E1 V2 run: `runs/e1-protocol-patch-v1-v2-20260702T093000Z`.
- Comparison: `outputs/comparisons/e1-protocol-patch-v1-v2-vs-baseline-v2`.

V2 result:

- Baseline V2 score mean 0.975714, JSON valid 0.600000, grounding safe 0.642857, leak 0.000000.
- E1 V2 score mean 1.000000, JSON valid 1.000000, grounding safe 1.000000, leak 0.000000.
- Deltas: score +0.024286, JSON +0.400000, grounding +0.357143, leak +0.000000.
- Story gate passed.
- Baseline V2 audit clean rate: 0.571429.
- E1 V2 audit clean rate: 1.000000.

Interpretation:

- A real protocol-layer uplift exists on the cleaned local domain set.
- This is not yet LoRA fine-tuning.
- The next step should distill E1 behavior into SFT/LoRA training pairs and retest on held-out reviewed cases.

### 2026-07-02 Continuation: 1000-Case Evaluation and QLoRA Distillation

User update:

- Increase sample size to about 1000.
- Keep the workflow research-like, with detailed process records, real-time data, charts, and multi-fold reliability.

Data expansion:

- Added a 1000-case generation requirement and regression test.
- Expanded `domain_case_factory.py` to include learner profiles, learning signals, classroom scenes, instruction styles, chapter-specific misconceptions, and per-Agent task foci.
- Generated `cases/domain_cases_1000_v1.jsonl`.
- Dataset profile:
  - 1000 cases.
  - 1000 unique case IDs.
  - 1000 unique user prompts.
  - 10 Agents x 100 cases each.
  - Chapters 1-7 covered with 142 or 143 cases per chapter.
  - 300 no-evidence cases.
  - 66 forbidden-answer-risk cases.
  - 0 no-evidence prompts requesting `page-*` citations.
- Profile artifact: `outputs/datasets/domain_cases_1000_v1_profile.json`.

Runner scaling:

- The first 1000-case baseline attempt used the old sequential runner and was stopped after it proved too slow.
- Added bounded concurrency to `run_cases`, protected `ExperimentLogger` with a thread lock, and exposed `--concurrency` in `scripts/run_baseline.py`.
- Verified with tests that concurrent runs preserve result order and do not lose event/metric logs.

1000-case baseline:

- vLLM served `models/QuantTrio-Qwen3.5-4B-AWQ` with `MAX_MODEL_LEN=1024`, `MAX_NUM_SEQS=4`, `GPU_MEMORY_UTILIZATION=0.82`.
- GPU memory during vLLM serving: about 6734 MiB used after startup.
- Run: `runs/baseline-1000-v1-conc4-20260702T100600Z`.
- Cases: 1000.
- Folds: 5, with 200 validation cases per fold.
- Mean score: 0.962467.
- JSON valid rate: 0.686000.
- Grounding safe rate: 0.822000.
- Forbidden leak rate: 0.014000.
- Audit clean rate: 0.612000.
- Main audit flags: invalid JSON 314, Markdown fences 314, missing evidence 112.

1000-case E1 protocol patch:

- Run: `runs/e1-protocol-patch-v1-1000-v1-conc4-20260702T103600Z`.
- Mean score: 0.997050.
- JSON valid rate: 0.980000.
- Grounding safe rate: 0.963000.
- Forbidden leak rate: 0.007000.
- Audit clean rate: 0.975000.
- Main audit flags: invalid JSON 20, missing evidence 8.
- Comparison: `outputs/comparisons/e1-protocol-patch-v1-1000-v1-vs-baseline-1000-v1`.
- Deltas: score +0.034583, JSON +0.294000, grounding +0.141000, forbidden leak -0.007000.
- Strict story gate result: false, because the pre-set grounding improvement threshold is +0.20 and this run reaches +0.141.

Interpretation:

- E1 produces real improvement on the 1000-case set.
- The strongest result is protocol reliability: JSON validity and audit cleanliness improve sharply.
- The safe story is "local Agent outputs became governable," not "grounding is solved."

SFT distillation:

- Source responses: clean E1 1000-case run.
- SFT output: `outputs/sft/e1-protocol-patch-v1-1000-v1`.
- Train examples: 780.
- Heldout examples: 195.
- Total clean examples: 975.
- Input policy: original case system/user prompts without the protocol patch text.

QLoRA training:

- Base model: `/home/cypress/.cache/modelscope/Qwen/Qwen3.5-4B`.
- Adapter output: `outputs/adapters/e1-protocol-patch-v1-1000-v1-qlora-2epoch`.
- Dependencies ready: torch 2.10.0+cu128, transformers 5.13.0.dev0, peft 0.19.1, trl 0.29.1, datasets 4.8.5, accelerate 1.14.0, bitsandbytes 0.49.2.
- vLLM was stopped before training; GPU memory released to 0 MiB.
- Training memory: about 7.7-7.8 GiB.
- Training runtime: 4566 seconds.
- Steps: 98.
- Epoch 1 heldout eval: loss 0.290419, token accuracy 0.907434.
- Epoch 2 heldout eval: loss 0.241026, token accuracy 0.917043.
- Adapter artifact: `adapter_model.safetensors`, about 21 MiB.
- Training curve: `outputs/training/e1-protocol-patch-v1-1000-v1-full/report/charts/training_loss.svg`.
- GPU memory after training: 0 MiB.

Verification:

- `PYTHONPATH=research/vllm-agent-ft-lab/src python3 -m pytest research/vllm-agent-ft-lab/tests -q` passed with 34 tests.

Boundary:

- The 1000-scale adapter training is complete and token-level heldout loss improved.
- Adapter generation quality was not tested through vLLM LoRA yet, but offline PEFT generation was tested next.
- No web frontend or backend files were edited.

### 2026-07-02 Continuation: Offline Adapter Heldout Generation

Implemented offline adapter evaluation:

- Added `lora_evaluation.py`.
- Added `scripts/evaluate_lora_offline.py`.
- Added `scripts/summarize_run_slice.py`.
- Fixed a generation-input bug found during smoke testing: Qwen3.5 tokenizer returned a `BatchEncoding`, so generation inputs must be passed as keyword tensors instead of a raw positional object.
- Verified with regression tests.

Smoke:

- Run: `runs/adapter-offline-heldout-smoke3-20260702T142000Z`.
- Cases: 3.
- Score: 1.000000.
- JSON valid rate: 1.000000.
- Grounding safe rate: 1.000000.
- Forbidden leak rate: 0.000000.
- Audit clean rate: 1.000000.

Full heldout generation:

- Run: `runs/adapter-offline-heldout-195-20260702T142200Z`.
- Cases: 195 heldout source cases from `outputs/sft/e1-protocol-patch-v1-1000-v1/heldout.jsonl`.
- Mean score: 0.989060.
- JSON valid rate: 0.943590.
- Grounding safe rate: 0.953846.
- Forbidden leak rate: 0.000000.
- Audit clean rate: 0.943590.
- Audit flags: invalid JSON 11, missing evidence 8.

Heldout comparison against raw baseline:

- Baseline heldout slice: `outputs/slices/baseline-1000-v1-heldout-195`.
- Adapter vs baseline comparison: `outputs/comparisons/adapter-offline-heldout-195-vs-baseline-heldout-195`.
- Deltas: score +0.020940, JSON +0.194872, grounding +0.153846, forbidden leak -0.015385.

Heldout comparison against E1 prompt:

- E1 heldout slice: `outputs/slices/e1-protocol-patch-v1-1000-v1-heldout-195`.
- Adapter vs E1 comparison: `outputs/comparisons/adapter-offline-heldout-195-vs-e1-heldout-195`.
- Deltas: score -0.009658, JSON -0.056410, grounding -0.020513, forbidden leak -0.005128.

Interpretation:

- The adapter beats the raw baseline on heldout generation.
- The adapter has not yet surpassed the E1 prompt module.
- The next engineering step is either vLLM LoRA serving validation or a second training/repair pass focused on the remaining invalid JSON and missing evidence failures.

Verification:

- `PYTHONPATH=research/vllm-agent-ft-lab/src python3 -m pytest research/vllm-agent-ft-lab/tests -q` passed with 38 tests.

### 2026-07-03 Continuation: vLLM LoRA Serving, Grounding Repair, and Web Provider Embedding

Implemented vLLM LoRA serving support:

- Added `serving.py` and `scripts/serve_qwen35_lora.py`.
- Static adapter serving command uses `--enable-lora --lora-modules pythontopia-qwen35-e1-lora=<adapter_dir>`.
- Serving base: `models/QuantTrio-Qwen3.5-4B-AWQ`.
- Adapter: `outputs/adapters/e1-protocol-patch-v1-1000-v1-qlora-2epoch`.
- vLLM `/v1/models` exposed both `pythontopia-qwen35-awq-base` and `pythontopia-qwen35-e1-lora`.
- Serving smoke output: `outputs/serving/lora_smoke_mindmap_if.json`.
- Observed serving GPU memory: about 6.16 GiB.

Implemented second-round grounding repair:

- Added `grounding_repair.py` and `scripts/repair_run_responses.py`.
- Root cause of remaining adapter failures: mostly MindMapAgent long JSON truncation plus missing top-level evidence/common_knowledge markers.
- Full heldout adapter + repair run: `runs/adapter-offline-heldout-195-grounding-repair-v1-20260702Tnow`.
- Metrics on the same 195 heldout source cases: score 1.000000, JSON 1.000000, grounding 1.000000, forbidden leak 0.000000.
- Comparison vs raw baseline heldout passed the strict story gate: JSON +0.251282, grounding +0.200000, forbidden leak -0.015385.

Validated vLLM serving on prior failure cases:

- Failure slice: `cases/adapter_heldout_failure_cases_v1.jsonl`, 12 cases.
- Raw vLLM LoRA run: `runs/vllm-lora-failure-slice-12-20260703T0018Z`.
- Raw serving metrics: score 0.979167, JSON 0.916667, grounding 0.833333, forbidden leak 0.000000.
- Repaired serving run: `runs/vllm-lora-failure-slice-12-grounding-repair-v1-20260703T0031Z`.
- Repaired serving metrics: score 1.000000, JSON 1.000000, grounding 1.000000, forbidden leak 0.000000.

Embedded into the Web Agent path:

- Added `FineTunedFallbackProvider` in `services/pythontopia-graph/pythontopia_graph/llm/provider.py`.
- Feature flag: `PYT_FINE_TUNED_LLM_ENABLED=true`.
- Adapter is used only for structured JSON Agent calls.
- Text streaming, tool calling, `model_tier=fast`, and adapter failures fall back to the existing provider.
- Config documented in `deploy/.env.example`, `deploy/docker-compose.yml`, and `services/pythontopia-graph/README.md`.
- Graph provider smoke: `outputs/serving/graph_provider_finetuned_smoke.json`.
- Graph provider fallback smoke: `outputs/serving/graph_provider_fallback_smoke.json`.

A/B report:

- Generated `outputs/web-ab/fine-tuned-provider-v1/web_ab_report.json`.
- Generated `outputs/web-ab/fine-tuned-provider-v1/web_ab_report.md`.
- Repo report: `docs/reports/2026-07-02-fine-tuned-llm-web-ab.md`.

Verification:

- `PYTHONPATH=research/vllm-agent-ft-lab/src python3 -m pytest research/vllm-agent-ft-lab/tests -q` passed with 47 tests.
- `cd services/pythontopia-graph && python3 -m pytest -q tests/test_openai_compat_provider.py` passed with 10 tests.
- `cd services/pythontopia-graph && python3 -m pytest -q tests/test_resource_stream.py` passed with 5 tests.
- `tests/test_resource_graph.py` pure graph cases passed through the first six cases, but the TestClient route case hangs in this local environment; the provider integration was verified separately with direct graph-provider smoke.

### 2026-07-03 Continuation: Method Report and Beginner-Noise Evaluation Scope

User update:

- Before the full 1000+ vLLM reliability run, write a detailed report explaining how Qwen was fine-tuned, why the method was chosen, how it performed, and what role vLLM plays in the project.
- Enhance the large-sample evaluation data to mimic Python beginners: unclear descriptions, fragmented sentences, incomplete error descriptions, confused terminology, and other common novice help-seeking patterns.
- Maintain a detailed experiment log.

Report written:

- Repo report: `docs/reports/2026-07-03-qwen-vllm-finetuning-method-report.md`.
- Scope: model/hardware choice, AWQ serving rationale, E1 protocol teacher generation, 975-clean-response SFT distillation, 4-bit QLoRA settings, training results, heldout generation results, vLLM LoRA serving evidence, Graph provider integration, fallback boundary, and remaining verification work.
- Boundary explicitly recorded: vLLM is an optional local OpenAI-compatible inference service for structured JSON Agent calls; the Agent runtime remains runnable without the local vLLM process through fallback providers.
- User then expanded the report requirement so a reader who has never seen Pythontopia can understand the fine-tuned LLM and its role. The report was expanded with a project primer, terminology table, system layering, Agent responsibility map, artifact index, metric definitions, non-claim boundaries, configuration switches, and reproducibility commands.

Novice-expression research notes:

- Introductory programming literature identifies syntactic, conceptual, and strategic difficulties; natural language transfer, math transfer, inaccurate mental models, and lack of strategies all contribute to novice mistakes.
- Python code-explanation research shows beginners often focus on output, omit variable assignment/input details, and get distracted by nearby code constructs.
- Python misconception-detection work supports evaluating not just output correctness, but symptoms of underlying misconceptions.
- Error-message design research motivates adding incomplete traceback snippets, jargon confusion, vocabulary noise, and long/fragmented wording to the evaluation prompts.

Next implementation step:

- Add a deterministic beginner-noise case generator that preserves `expected_markers`, `evidence_ids`, Agent output contracts, and scoring fairness while rewriting the `user` field into novice-like prompts.
- Generate a 1000+ augmented case file and use it for the formal 5-fold vLLM LoRA run.

Beginner-noise generator:

- Added `beginner_noise_cases.py` and `scripts/build_beginner_noise_cases.py`.
- Added regression tests in `tests/test_beginner_noise_cases.py`.
- Red check: the test first failed with `ModuleNotFoundError: No module named 'pyt_agent_lab.beginner_noise_cases'`.
- Green check: `PYTHONPATH=research/vllm-agent-ft-lab/src python3 -m pytest research/vllm-agent-ft-lab/tests/test_beginner_noise_cases.py -q` passed with 4 tests.

Generated augmented data:

- Source: `cases/domain_cases_1000_v1.jsonl`.
- Output: `cases/domain_cases_1000_v1_beginner_noise.jsonl`.
- Command: `PYTHONPATH=research/vllm-agent-ft-lab/src python3 research/vllm-agent-ft-lab/scripts/build_beginner_noise_cases.py --input research/vllm-agent-ft-lab/cases/domain_cases_1000_v1.jsonl --output research/vllm-agent-ft-lab/cases/domain_cases_1000_v1_beginner_noise.jsonl --seed 20260703`.
- Count: 1000 source cases, 1000 augmented cases, 1000 unique augmented IDs.
- Changed user prompts: 1000 / 1000.
- Contract preservation audit: 0 errors across `agent`, `system`, `expected_markers`, `evidence_ids`, `requires_evidence`, `forbidden_answer_risk`, `max_tokens`, and `kc`.
- Profile distribution: anxious_answer_request 167, fragmented_sentence 167, incomplete_error_paste 167, low_confidence_help 166, mixed_terminology 167, vague_reference 166.

Formal vLLM beginner-noise run:

- vLLM LoRA service restarted on `127.0.0.1:18094`.
- `/health` returned HTTP 200.
- `/v1/models` evidence saved to `outputs/serving/vllm_models_beginner_noise_20260703.json`.
- GPU after serving startup: NVIDIA GeForce RTX 5060 Laptop GPU, 8151 MiB total, 6160 MiB used, 1651 MiB free.
- Run directory: `runs/vllm-lora-domain-1000-beginner-noise-5fold-20260703T1538CST`.
- Command uses `cases/domain_cases_1000_v1_beginner_noise.jsonl`, `folds=5`, `seed=20260703`, `concurrency=1`, model `pythontopia-qwen35-e1-lora`.
- Progress snapshots are appended to `runs/vllm-lora-domain-1000-beginner-noise-5fold-20260703T1538CST/progress_snapshots.jsonl`.
- Early progress snapshot: 31 / 1000 metric rows, fold 0 only so far, score mean 1.000000, JSON valid 1.000000, grounding safe 1.000000, forbidden leak 0.000000, latency mean about 10.9s. This is an early partial snapshot, not a final result.
- Single-concurrency calibration was stopped at 87 metric rows because the measured ETA stayed above two hours. The partial run remains preserved as calibration data and is not the final 1000-case evidence.
- vLLM was restarted with `MAX_NUM_SEQS=4`, `GPU_MEMORY_UTILIZATION=0.78`, and the formal complete run was restarted with `--concurrency 4`.
- Formal complete run directory: `runs/vllm-lora-domain-1000-beginner-noise-5fold-conc4-20260703T1553CST`.
- Concurrent serving evidence saved to `outputs/serving/vllm_models_beginner_noise_conc4_20260703.json`.
- Root cause of the failed `conc4` attempt: vLLM HTTP became unreachable with `ConnectionRefusedError` while GPU memory was about 7440 / 8151 MiB used. The API server parent process remained visible, but `/health` refused connections, so `MAX_NUM_SEQS=4` was treated as too aggressive for this 8GB LoRA serving run.
- Added an evaluation-client retry guard for transient `URLError` / refused-once failures; regression test `test_openai_compat_client_retries_transient_url_errors` passed.
- Lab verification after the retry guard: `PYTHONPATH=research/vllm-agent-ft-lab/src python3 -m pytest research/vllm-agent-ft-lab/tests -q` passed with 57 tests.
- vLLM was then restarted with `MAX_NUM_SEQS=2`, `GPU_MEMORY_UTILIZATION=0.74`; `/health` returned HTTP 200 and `/v1/models` evidence was saved to `outputs/serving/vllm_models_beginner_noise_conc2_20260703.json`.
- Formal complete run directory after the stability fallback: `runs/vllm-lora-domain-1000-beginner-noise-5fold-conc2-20260703T1559CST`.

Final beginner-noise 5-fold run:

- Run directory: `runs/vllm-lora-domain-1000-beginner-noise-5fold-conc2-20260703T1559CST`.
- Model: `pythontopia-qwen35-e1-lora`.
- Cases: `cases/domain_cases_1000_v1_beginner_noise.jsonl`.
- Folds: 5, with 200 validation cases per fold.
- Client concurrency: 2.
- vLLM config: `MAX_MODEL_LEN=1024`, `MAX_NUM_SEQS=2`, `GPU_MEMORY_UTILIZATION=0.74`.
- Completed 1000 / 1000 cases without a transport failure during the conc2 formal run.
- Progress recording: `progress_snapshots.jsonl` contains 134 snapshots.
- Event recording: `events.jsonl` contains 2000 case start/finish events.
- Metric recording: `metrics.csv` contains 1000 rows.
- Response recording: `responses.jsonl` contains 1000 rows.

Final overall metrics:

- Score mean: 0.991083.
- Score std: 0.057171.
- JSON valid rate: 0.990000, with 10 invalid JSON rows.
- Grounding safe rate: 0.961000, with 39 grounding-false rows.
- Forbidden leak rate: 0.005000, with 5 leak rows.
- Latency mean: 9201.83 ms.
- Latency p50: 8164.50 ms.
- Latency p95: 18268.20 ms.
- Latency max: 29322.00 ms.

Final fold metrics:

- Fold 0: 200 cases, score 0.992583, JSON 0.990, grounding 0.960, leak 0.005.
- Fold 1: 200 cases, score 0.989833, JSON 0.990, grounding 0.960, leak 0.000.
- Fold 2: 200 cases, score 0.989583, JSON 0.990, grounding 0.960, leak 0.010.
- Fold 3: 200 cases, score 0.991833, JSON 0.985, grounding 0.975, leak 0.005.
- Fold 4: 200 cases, score 0.991583, JSON 0.995, grounding 0.950, leak 0.005.

Final failure concentration:

- `DebugAgent`: 31 / 100 grounding false, JSON 1.000, leak 0.000. Main risk is missing required `page-*` evidence in debug hints.
- `SafetyAgent`: JSON valid 0.920, grounding safe 0.960, forbidden leak 0.050. Main risk is safety refusal/allowance JSON and deterministic redaction boundary.
- Beginner-noise risk was highest for `incomplete_error_paste`, `low_confidence_help`, and `anxious_answer_request`.
- `mixed_terminology` and `vague_reference` had perfect scores in this run.

Final artifacts:

- `summary.json`
- `extended_summary.json`
- `charts/score_by_fold.svg`
- `charts/rates_by_fold.svg`
- `charts/error_counts_by_agent.svg`
- `charts/rates_by_noise_profile.svg`
- `charts/latency_by_fold.svg`

Final reports:

- Method and role report: `docs/reports/2026-07-03-qwen-vllm-finetuning-method-report.md`.
- Large-sample reliability report: `docs/reports/2026-07-03-vllm-1000-beginner-noise-multifold-eval.md`.
- Changelog entry updated: `docs/project/CHANGELOG.md`.

Final verification:

- Artifact assertion checked: summary case count 1000, metrics rows 1000, responses rows 1000, five folds each with 200 cases, and all SVG charts non-empty.
- `PYTHONPATH=research/vllm-agent-ft-lab/src python3 -m pytest research/vllm-agent-ft-lab/tests -q` passed with 59 tests.

### 2026-07-03 Continuation: Safety/Debug Guard Fix

User update:

- Fix SafetyAgent JSON/leak boundaries and DebugAgent missing evidence references.
- Preserve schema validation, evidence guard, forbidden scanner, repair/fallback for formal project integration.
- Run complete tests, produce charts, iterate if problems remain, and keep the experiment log current.

Root cause:

- The 1000-case raw vLLM run was transport-stable but had output-contract defects: 10 invalid JSON rows, 39 grounding-false rows, and 5 forbidden-leak rows.
- Failure concentration: SafetyAgent carried most invalid JSON and all forbidden leaks; DebugAgent carried most missing-evidence failures.
- Existing `grounding_repair.py` handled generic JSON/evidence repair but did not specialize SafetyAgent refusal schema or replace valid-but-leaky SafetyAgent JSON.
- `FineTunedFallbackProvider` routed structured JSON calls to the adapter but did not validate adapter output before returning it; invalid adapter JSON could return as `content_json=None` instead of falling back.

TDD red checks:

- Added SafetyAgent repair tests for truncated safety JSON and leaky valid JSON. Initial result: 2 failed, both with `KeyError: 'allow'`.
- Added Graph provider tests for invalid JSON fallback, SafetyAgent guarded refusal, and DebugAgent evidence insertion. Initial result: 3 failed.
- Added fold-preservation test for repaired runs. Initial result: `TypeError: repair_response_records() got an unexpected keyword argument 'fold_by_case_id'`.

Implementation:

- `grounding_repair.py`
  - SafetyAgent now uses a deterministic refusal template when JSON is invalid, required safety fields are missing, or forbidden leak is detected.
  - The template includes `allow=false`, `redactions`, `blocked_content`, `safer_alternative`, and `safety_flags`.
  - Grounding normalization still injects `evidence_ids` and `grounding_note`.
  - `repair_response_records()` accepts `fold_by_case_id` so repaired 5-fold metrics preserve original fold assignments.
- `scripts/repair_run_responses.py`
  - Loads sibling `metrics.csv` from the raw run to restore fold metadata.
- `pythontopia_graph/llm/provider.py`
  - `FineTunedFallbackProvider.chat()` now strips optional `guard_context` before upstream calls.
  - Adapter output with no JSON falls back to the existing provider.
  - With `guard_context`, adapter output is normalized through schema-like JSON parsing, SafetyAgent forbidden scanner/refusal repair, and evidence guard.
  - Missing `evidence_ids` and `grounding_note` are inserted before returning to graph callers.

Green checks:

- `PYTHONPATH=research/vllm-agent-ft-lab/src python3 -m pytest research/vllm-agent-ft-lab/tests/test_grounding_repair.py -q` passed with 6 tests.
- `cd services/pythontopia-graph && python3 -m pytest -q tests/test_openai_compat_provider.py` passed with 14 tests.
- Full lab verification after the guard change: `PYTHONPATH=research/vllm-agent-ft-lab/src python3 -m pytest research/vllm-agent-ft-lab/tests -q` passed with 62 tests.
- Provider-adjacent graph verification: `cd services/pythontopia-graph && python3 -m pytest -q tests/test_openai_compat_provider.py tests/test_provider_tool_calling.py tests/test_resource_stream.py tests/test_debug_agent.py` passed with 34 tests.
- Full graph verification: `cd services/pythontopia-graph && timeout 180s python3 -m pytest -q` passed with 1230 tests, 3 skipped, 1 warning.
- Whitespace verification: `git diff --check` passed.

Guarded 1000-case repair run:

- Raw source: `runs/vllm-lora-domain-1000-beginner-noise-5fold-conc2-20260703T1559CST/responses.jsonl`.
- Guarded output: `runs/vllm-lora-domain-1000-beginner-noise-5fold-conc2-guarded-v1-20260703`.
- Folds preserved: five folds, 200 cases each.
- Overall guarded metrics: score 1.000000, JSON 1.000000, grounding 1.000000, forbidden leak 0.000000.
- SafetyAgent guarded metrics: JSON 1.000000, grounding 1.000000, forbidden leak 0.000000.
- DebugAgent guarded metrics: grounding 1.000000.
- Audit: 1000 / 1000 clean, `flag_counts={}`.

Artifacts:

- Guarded summary: `runs/vllm-lora-domain-1000-beginner-noise-5fold-conc2-guarded-v1-20260703/summary.json`.
- Guarded extended summary: `runs/vllm-lora-domain-1000-beginner-noise-5fold-conc2-guarded-v1-20260703/extended_summary.json`.
- Guarded charts: `score_by_fold.svg`, `rates_by_fold.svg`, `error_counts_by_agent.svg`, `rates_by_noise_profile.svg`, `latency_by_fold.svg`.
- Raw-vs-guarded comparison: `outputs/comparisons/raw-vs-guarded-safety-debug-v1/comparison.json`.
- Raw-vs-guarded chart: `outputs/comparisons/raw-vs-guarded-safety-debug-v1/charts/baseline_vs_candidate.svg`.
- Repo report: `docs/reports/2026-07-03-vllm-safety-debug-guard-fix.md`.
- Changelog updated: `docs/project/CHANGELOG.md`.

### 2026-07-09 Continuation: Adapter Golden Set, Schema Scoring, CI Gate

User update:

- 固化已有手动实验数据：从 5000 条 eval pack 抽 50 条 golden set，10 个 Agent 各 5 条。
- 将 adapter 输出存成 expected baseline，并写 `test_adapter_golden.py`，用 mock/fixture 重放 guard + scoring。
- 将 `score_case_response()` 从只看 `json.loads` 扩展为 Agent schema 校验。
- 将 `research/vllm-agent-ft-lab` 加入 GitHub Actions `python-services` matrix，并保持 `pytest -q` 纯逻辑通过。

TDD red checks:

- `test_scoring_schema.py` 初始失败于 `ImportError: cannot import name 'AGENT_SCHEMA'`。
- `test_adapter_golden.py` 初始失败于缺少 `cases/golden_adapter_50_v1.jsonl`。
- summary 层新增 schema 断言后，`test_folds_reporting.py` 和 `test_run_analysis.py` 分别失败于缺少 `schema_valid_rate` 与 `schema_invalid_count`。

Implementation:

- `scoring.py`
  - 增加 `AGENT_SCHEMA`，覆盖 Tutor / Practice / ResourceDesigner / IntentRouter / GuideCoach / Debug / GroundingCritic / Safety / PathPlanner / MindMap 共 10 个 Agent。
  - `CaseScore` 增加 `schema_valid`、`missing_schema_keys`、`schema_type_errors`。
  - `score_case_response()` 保留 `json_valid` 作为 JSON object 解析结果，并单独计算 schema 有效性。
- `guard.py`
  - `apply_guard()` 在 JSON 有效但 schema 无效时追加 `schema_invalid`。
- `golden_adapter.py` + `scripts/build_adapter_golden.py`
  - 固定从 `cases/domain_cases_5000_v1.jsonl` 生成 50 条 golden case。
  - 通过 deterministic guarded adapter baseline 生成 `outputs/golden/adapter_baseline_50_v1.jsonl`。
- `fallback.py`
  - 统一 deterministic fallback 输出到新的 Agent schema，避免 Practice / Guide / Debug 等旧模板绕过 schema。
- Reporting:
  - `reporting.py` 与 `run_analysis.py` 增加 schema rate / invalid count，并将 schema 加入可靠性图表序列。
- CI:
  - `.github/workflows/ci.yml` 的 `python-services` matrix 改为 `{ path, name }`，新增 `research/vllm-agent-ft-lab`。
  - 新增 `research/vllm-agent-ft-lab/pyproject.toml`，支持 `pip install -e ".[dev]"`。

Golden fixture data:

- Golden cases: 50 total.
- Agent coverage: 10 agents x 5 cases.
- Scenario coverage: `with_evidence=30`, `without_evidence=10`, `safety_sensitive=10`.
- Expected baseline: 50 records.
- Baseline score checks: `schema_valid=50/50`, `grounding_safe=50/50`, `forbidden_leak=0/50`.

Verification:

- Target RED/GREEN check: `PYTHONPATH=research/vllm-agent-ft-lab/src python3 -m pytest research/vllm-agent-ft-lab/tests/test_scoring_schema.py research/vllm-agent-ft-lab/tests/test_adapter_golden.py -q` passed with 4 tests.
- Summary schema check: `PYTHONPATH=research/vllm-agent-ft-lab/src python3 -m pytest research/vllm-agent-ft-lab/tests/test_folds_reporting.py research/vllm-agent-ft-lab/tests/test_run_analysis.py -q` passed with 4 tests.
- CI install equivalent: `cd research/vllm-agent-ft-lab && python3 -m pip install -e ".[dev]"` succeeded.
- Lab CI equivalent: `cd research/vllm-agent-ft-lab && python3 -m pytest -q` passed with 123 tests.
- Whitespace verification: `git diff --check` passed.

### 2026-07-09 Continuation: P0 Loss Masking and llguidance Engineering Gate

User/reference update:

- The pasted research note recommends two P0 moves before more DPO/ORPO/GRPO work:
  - SFT loss masking so training loss focuses on the final assistant JSON response.
  - vLLM llguidance / guided JSON so inference can enforce schema-level JSON structure.
- The note frames this as moving an already-strong setup toward 100% format reliability rather than fixing a broken baseline.

Current-state checks:

- Local TRL check: `research/vllm-agent-ft-lab/.venv/bin/python` reported `trl 0.29.1`.
- `trl.DataCollatorForCompletionOnlyLM` is not available in this local TRL, but `SFTConfig` exposes `assistant_only_loss` and `completion_only_loss`.
- Local vLLM help search found `StructuredOutputsConfig`, but no clear `--guided-decoding-backend` help text in this installed command output. The code now exposes the flag, but live llguidance smoke remains a separate runtime validation.
- Existing dirty worktree note: `process-log.md` already contained an uncommitted 2026-07-07 DPO section before this P0 continuation.

TDD red checks:

- `tests/test_lora_training.py` first failed because `should_use_conversational_sft_dataset` and `sft_loss_masking_config_kwargs` did not exist.
- `tests/test_serving.py` first failed because `build_agent_guided_json_extra_body` and the llguidance config field did not exist.
- `tests/test_training_plan.py` first failed because `build_training_plan()` did not emit `loss_masking`.

Implementation:

- `lora_training.py`
  - Added `sft_loss_masking_config_kwargs(plan)`.
  - Added `should_use_conversational_sft_dataset(plan)`.
  - `loss_masking.mode=assistant_only` maps to `assistant_only_loss=True` and `completion_only_loss=False`.
- `train_lora.py`
  - Reads the plan loss masking mode.
  - Uses conversational `messages` records for assistant-only SFT so TRL can apply assistant loss masking.
  - Keeps the previous full-text `text` mapping for `full_text` / unset mode.
- `training_plan.py` and `preflight_training.py`
  - Training plans now default to `loss_masking: {mode: assistant_only}`.
  - Preflight CLI accepts `--loss-masking-mode assistant_only|full_text`.
- `serving.py`
  - `VllmLoraServeConfig` now accepts `guided_decoding_backend`.
  - `build_vllm_lora_serve_command()` emits `--guided-decoding-backend llguidance` when configured.
  - Added `build_agent_guided_json_extra_body(agent)` to convert the local `AGENT_SCHEMA` into an OpenAI-compatible `extra_body={"guided_json": ...}` payload.

Verification so far:

- Target tests: `cd research/vllm-agent-ft-lab && python3 -m pytest tests/test_training_plan.py tests/test_lora_training.py tests/test_serving.py -q` passed with 11 tests.
- Script compile check: `python3 -m py_compile research/vllm-agent-ft-lab/scripts/train_lora.py research/vllm-agent-ft-lab/scripts/preflight_training.py` passed.
- Full lab logic check: `cd research/vllm-agent-ft-lab && python3 -m pytest -q` passed with 127 tests.
- Whitespace check: `git diff --check` passed.

Not run in this step:

- No GPU SFT retraining yet.
- No live vLLM llguidance serving smoke yet, because the local vLLM help output did not clearly expose the guided-decoding flag and this step is a pure logic/CI gate.
