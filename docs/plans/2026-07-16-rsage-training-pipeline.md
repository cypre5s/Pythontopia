# R-SAGE-IR Training Pipeline Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build and run a resume-safe R-SAGE-IR automatic labeling, reduced-data SFT, GDPO+RPO preference training, staged evaluation, and live reporting pipeline for Pythontopia.

**Architecture:** Add testable core modules under `research/vllm-agent-ft-lab/src/pyt_agent_lab/` and keep CLI scripts thin. Reuse the existing QLoRA, TRL, scoring, experiment logging, and reporting code; protect existing dirty-worktree artifacts and keep full5000 behind a durable one-time gate.

**Tech Stack:** Python 3.10, pytest, Hugging Face Transformers/Datasets/PEFT, TRL, bitsandbytes NF4 QLoRA, OpenAI-compatible teacher API, JSONL/CSV/SVG artifacts.

---

### Task 1: Freeze baseline and define the R-SAGE data contract

**Files:**
- Create: `research/vllm-agent-ft-lab/tests/test_rsage_data.py`
- Create: `research/vllm-agent-ft-lab/src/pyt_agent_lab/rsage_data.py`

**Steps:**

1. Run the current lab test baseline and preserve its output.
2. Write failing tests for the seven intents, six failure axes, case/vote validation, evidence allowlists and stable fingerprints.
3. Run the new test and confirm failure because `rsage_data` is absent.
4. Implement frozen constants, typed validation helpers and canonical SHA-256 fingerprints without external dependencies.
5. Run the new test and then the complete lab tests.

### Task 2: Build 420 pack43-grounded non-protected cases

**Files:**
- Create: `research/vllm-agent-ft-lab/tests/test_rsage_case_builder.py`
- Create: `research/vllm-agent-ft-lab/src/pyt_agent_lab/rsage_case_builder.py`
- Create: `research/vllm-agent-ft-lab/scripts/build_rsage_cases.py`

**Steps:**

1. Write failing tests proving 7 x 6 x 10 coverage, valid pack43 refs, deterministic output and protected fingerprint exclusion.
2. Confirm the tests fail for the missing builder.
3. Implement bounded pack43 loading, templates and manifest/hash writing.
4. Add CLI arguments for count, seed, course directory, protected inputs, output and `--demo`.
5. Run tests and a 12-row demo; inspect JSONL and manifest counts.

### Task 3: Add resumable strong-teacher voting and rule verification

**Files:**
- Create: `research/vllm-agent-ft-lab/tests/test_teacher_voting.py`
- Create: `research/vllm-agent-ft-lab/src/pyt_agent_lab/teacher_voting.py`
- Create: `research/vllm-agent-ft-lab/scripts/label_rsage_teacher_votes.py`

**Steps:**

1. Write failing tests for three-vote aggregation, q calculation, invalid evidence rejection, retry/resume and secret redaction.
2. Confirm expected failures.
3. Implement a standard-library OpenAI-compatible client, deploy-env loader, deterministic fixture backend, verifier and incremental JSONL writer.
4. Implement CLI fixture demo and bounded live mode without logging API keys.
5. Run unit tests, the fixture demo, and a one-case live `deepseek-v4-pro` smoke.

### Task 4: Curate the 70% datasets without protected leakage

**Files:**
- Create: `research/vllm-agent-ft-lab/tests/test_rsage_curation.py`
- Create: `research/vllm-agent-ft-lab/src/pyt_agent_lab/rsage_curation.py`
- Create: `research/vllm-agent-ft-lab/scripts/curate_rsage_dataset.py`

**Steps:**

1. Write failing tests for deterministic stratification, exact 294 target + 252 retention = 546 SFT rows, 208 preference rows, duplicate rejection and disjoint dev/heldout/protected sets.
2. Confirm expected failures.
3. Implement confidence/coverage selection and conversion to TRL chat SFT/preference formats.
4. Emit row-level lineage and a manifest containing source and output hashes.
5. Run tests and a small demo with scaled counts; audit every split intersection.

### Task 5: Implement and unit-test the GDPO + RPO objective

**Files:**
- Create: `research/vllm-agent-ft-lab/tests/test_gdpo_rpo.py`
- Create: `research/vllm-agent-ft-lab/src/pyt_agent_lab/gdpo_rpo.py`
- Create: `research/vllm-agent-ft-lab/scripts/train_gdpo_rpo.py`

**Steps:**

1. Write tensor-level failing tests for q=1 DPO equivalence, q=0.5 zero margin scaling, monotonic confidence scaling, chosen token-average NLL and independent lambda weighting.
2. Confirm failure before implementation.
3. Implement pure loss helpers, then a narrow TRL `DPOTrainer` subclass that consumes per-row `soft_preference` and adds token-normalized chosen NLL.
4. Add preflight validation, failure archiving, seed propagation, incremental trainer logs, resume and `--max-steps`.
5. Run unit tests before any GPU training.

### Task 6: Build plans and multiple training launch scripts

**Files:**
- Create: `research/vllm-agent-ft-lab/tests/test_rsage_training_plan.py`
- Create: `research/vllm-agent-ft-lab/src/pyt_agent_lab/rsage_training_plan.py`
- Create: `research/vllm-agent-ft-lab/scripts/build_rsage_training_plans.py`
- Create: `research/vllm-agent-ft-lab/scripts/launch_rsage_sft.py`
- Create: `research/vllm-agent-ft-lab/scripts/launch_rsage_gdpo_rpo.py`

**Steps:**

1. Write failing plan tests for NF4 QLoRA, specialist metadata, exact data hashes, seed, max length, beta, lambda and output isolation.
2. Confirm failure before implementation.
3. Implement SFT and GDPO+RPO plan generation with atomic JSON writes.
4. Implement launchers that call the existing SFT trainer or the new preference trainer, preserve stdout/stderr, and support `--demo` one-step mode.
5. Run plan tests and CLI `--help`/fixture demos.

### Task 7: Add real-time experiment journal, GPU telemetry and SVG charts

**Files:**
- Create: `research/vllm-agent-ft-lab/tests/test_rsage_live_report.py`
- Create: `research/vllm-agent-ft-lab/src/pyt_agent_lab/rsage_live_report.py`
- Create: `research/vllm-agent-ft-lab/scripts/watch_rsage_training.py`

**Steps:**

1. Write failing tests for append-only events, atomic chart replacement, trainer JSONL parsing, GPU samples and Markdown journal updates.
2. Confirm failure before implementation.
3. Implement a poll-once core and bounded watcher loop; missing `nvidia-smi` must be explicit but non-fatal to chart regeneration.
4. Generate loss/reward/throughput/GPU SVG files without adding plotting dependencies.
5. Run unit tests and a demo against a synthetic growing trainer log.

### Task 8: Enforce 50 -> 500 -> one-time 5000 evaluation budgets

**Files:**
- Create: `research/vllm-agent-ft-lab/tests/test_rsage_eval_budget.py`
- Create: `research/vllm-agent-ft-lab/src/pyt_agent_lab/rsage_eval_budget.py`
- Create: `research/vllm-agent-ft-lab/scripts/run_rsage_eval_gate.py`

**Steps:**

1. Write failing tests for gate ordering, rejected-candidate stop, stratified500 count, seed promotion and durable full5000 receipt semantics.
2. Confirm failure.
3. Implement gate state and command planning around existing baseline/report scripts; do not duplicate scoring.
4. Make demo mode use fixture evaluators and prohibit creating a real full5000 receipt.
5. Run tests and the full gate demo.

### Task 9: Orchestrate, resume and fail fast

**Files:**
- Create: `research/vllm-agent-ft-lab/tests/test_rsage_pipeline.py`
- Create: `research/vllm-agent-ft-lab/src/pyt_agent_lab/rsage_pipeline.py`
- Create: `research/vllm-agent-ft-lab/scripts/run_rsage_pipeline.py`

**Steps:**

1. Write failing tests for stage ordering, resume, command hash mismatch, failed-partial preservation, demo isolation and no training before demos pass.
2. Confirm failure.
3. Implement a manifest-driven subprocess orchestrator with atomic stage receipts and explicit environment capture that redacts secrets.
4. Add `--demo`, `--prepare-only`, `--start-training`, `--resume` and stage-selection flags.
5. Run unit tests and a tiny end-to-end pipeline demo.

### Task 10: Run every script demo and real one-step GPU smokes

**Files:**
- Create: `research/vllm-agent-ft-lab/docs/rsage-experiment-log-2026-07-16.md`
- Create under runs/outputs: timestamped demo artifacts only

**Steps:**

1. Run every new script's `--help` and demo mode, recording command, exit code and artifact hash.
2. Run the one-case live teacher smoke.
3. Run one real SFT GPU step, save/reload the adapter, and regenerate charts.
4. Run one real GDPO+RPO GPU step, save/reload the adapter, and regenerate charts.
5. Run the complete lab test suite and inspect failures against pre-existing dirty changes.

### Task 11: Start and monitor the full mainline

**Files:**
- Create under `research/vllm-agent-ft-lab/runs/`: full run directory and incremental artifacts
- Update: `research/vllm-agent-ft-lab/docs/rsage-experiment-log-2026-07-16.md`

**Steps:**

1. Start full automatic case generation and teacher voting after all demo receipts pass.
2. Curate exact 546-row SFT and 208-row preference training sets and verify protected overlap is zero.
3. Train the single-seed specialist SFT and GDPO+RPO candidate with the watcher active.
4. Report progress to the user every hour, or immediately on completion/failure.
5. Run golden50 and stratified500 gates; promote only if all registered criteria pass.
6. Train seeds 43 and 44 only for the promoted configuration.
7. Execute the final full5000 stage once and preserve its durable receipt.

### Task 12: Completion audit

**Files:**
- Update: `todo.md` (moved to `research/vllm-agent-ft-lab/docs/finetuning-pain-points-report-2026-07-16.md`) only to append an implementation-status section if it does not overwrite the original report
- Update: `research/vllm-agent-ft-lab/docs/rsage-experiment-log-2026-07-16.md`

**Steps:**

1. Map every actionable requirement in `todo.md` and the user objective to code, command and artifact evidence.
2. Distinguish automated teacher proxy evidence from missing human/student validation.
3. Re-run the full relevant tests and verify all required demo receipts, charts, datasets, adapters and evaluation receipts.
4. Record unverified field-study items as remaining research boundaries, not completed results.
5. Mark the active goal complete only when all authorized automatic implementation, full training and final evaluation requirements are proven.
