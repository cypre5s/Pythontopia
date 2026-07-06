# vLLM 1000+ Multi-Fold Evaluation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Run a large-sample, multi-fold reliability and correctness evaluation for the local vLLM LoRA model in the real Pythontopia Agent setting, after augmenting the sample prompts to mimic Python beginners' unclear, fragmented, and misconception-heavy help requests.

**Architecture:** Reuse the isolated `research/vllm-agent-ft-lab` harness and its existing domain case set. vLLM serves the local Qwen3.5 AWQ base plus `pythontopia-qwen35-e1-lora`; a deterministic beginner-noise generator rewrites the `user` prompt while preserving evidence, expected markers, Agent contracts, and scoring fields; `run_baseline.py` sends OpenAI-compatible chat requests, records events/metrics/responses, aggregates fold metrics, and generates charts.

**Tech Stack:** Python 3, vLLM, OpenAI-compatible HTTP API, JSONL/CSV logs, SVG charts, Pythontopia domain evaluation cases.

---

### Task 1: Changelog and Scope

**Files:**
- Modify: `docs/project/CHANGELOG.md`

**Steps:**
- Add a 2026-07-03 changelog entry before the long run starts.
- Record the case source, model target, reliability/correctness metrics, and expected output artifacts.
- After the run finishes, update the same entry with final metrics and evidence paths.

### Task 2: vLLM Serving Evidence

**Files:**
- Read: `research/vllm-agent-ft-lab/scripts/serve_qwen35_lora.py`
- Generate: `research/vllm-agent-ft-lab/outputs/serving/vllm_models_1000fold_20260703.json`

**Commands:**

```bash
PYTHONPATH=research/vllm-agent-ft-lab/src \
VLLM_HOST=127.0.0.1 VLLM_PORT=18094 GPU_MEMORY_UTILIZATION=0.74 \
MAX_MODEL_LEN=1024 MAX_NUM_SEQS=1 VLLM_API_KEY=local-dev-token \
research/vllm-agent-ft-lab/scripts/serve_qwen35_lora.py
```

```bash
curl --noproxy '*' -sS http://127.0.0.1:18094/health
curl --noproxy '*' -sS -H 'Authorization: Bearer local-dev-token' \
  http://127.0.0.1:18094/v1/models \
  > research/vllm-agent-ft-lab/outputs/serving/vllm_models_1000fold_20260703.json
```

### Task 3: 1000-Case 5-Fold Run

**Pre-step: Beginner-expression augmentation**

**Files:**
- Input: `research/vllm-agent-ft-lab/cases/domain_cases_1000_v1.jsonl`
- Generate: `research/vllm-agent-ft-lab/cases/domain_cases_1000_v1_beginner_noise.jsonl`

**Noise profile requirements:**
- vague reference: "这个不对/为啥跑不出来" without clean problem framing.
- fragmented sentence: broken clauses and mixed learning signals.
- mixed terminology: assignment/equality, `range` boundary, input/string/list confusion.
- incomplete error paste: partial traceback or red-text mention.
- misconception slang: beginner-style guesses and unstable wording.

**Fairness constraints:**
- Preserve `expected_markers`, `evidence_ids`, `requires_evidence`, `system`, `agent`, and `max_tokens`.
- Keep evidence IDs visible when `requires_evidence=true`.
- Do not add hidden answers or executable full solutions.

**Files:**
- Input: `research/vllm-agent-ft-lab/cases/domain_cases_1000_v1_beginner_noise.jsonl`
- Generate: `research/vllm-agent-ft-lab/runs/vllm-lora-domain-1000-5fold-20260703T*/`

**Command:**

```bash
NO_PROXY='*' no_proxy='*' PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/run_baseline.py \
  --base-url http://127.0.0.1:18094/v1 \
  --api-key local-dev-token \
  --model pythontopia-qwen35-e1-lora \
  --cases research/vllm-agent-ft-lab/cases/domain_cases_1000_v1_beginner_noise.jsonl \
  --folds 5 \
  --seed 20260703 \
  --concurrency 1 \
  --run-dir research/vllm-agent-ft-lab/runs/vllm-lora-domain-1000-5fold-20260703T1112Z
```

**Progress Evidence:**
- `events.jsonl`: `case_started` and `case_finished`
- `metrics.csv`: score, JSON validity, grounding safety, forbidden leak, latency, response length
- Periodic progress checks count finished rows and report fold distribution.

### Task 4: Report and Chart Audit

**Files:**
- Generate: `summary.json`
- Generate: `charts/score_by_fold.svg`
- Generate: `charts/rates_by_fold.svg`
- Create/Update: `docs/reports/2026-07-03-vllm-1000-multifold-eval.md`

**Checks:**
- `summary.json` overall `case_count` is at least 1000.
- Every fold has non-zero cases.
- `metrics.csv` has 1000 metric rows.
- `responses.jsonl` has 1000 response rows.
- Charts exist and are non-empty.
- Forbidden leak rate, JSON valid rate, grounding safe rate, score mean, and score std are reported.

### Task 5: Final Verification

**Commands:**

```bash
PYTHONPATH=research/vllm-agent-ft-lab/src python3 -m pytest research/vllm-agent-ft-lab/tests -q
git diff --check
```

**Done When:**
- The beginner-noise 1000+ case 5-fold run finishes without missing rows.
- Artifacts and charts are present.
- The final report and changelog include exact metrics and artifact paths.
