# Two-Day Optimization A/B Evaluation Hardening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add detailed, deterministic A/B gates for all 2026-06-23 and 2026-06-24 AI/runtime/evidence optimizations.

**Architecture:** Keep tests near the subsystem and add one optimizer policy field, `min_sample_win_rate`, so automatic prompt promotion requires stable sample-level wins in addition to mean uplift and regression gates.

**Tech Stack:** Java 21 + JUnit/Testcontainers, Python pytest, existing Pythontopia graph/RAG test utilities.

---

### Task 1: Optimizer Stable-Win Gate

**Files:**
- Modify: `services/pythontopia-graph/tests/test_eval_optimization.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/eval/optimization.py`

**Steps:**
1. Add a failing pytest case where a candidate has positive mean uplift but wins only a minority of samples.
2. Run the new test and confirm it fails because `OptimizationPolicy.min_sample_win_rate` is missing or ignored.
3. Add `min_sample_win_rate` to `OptimizationPolicy`.
4. Add `sample_win_rate` to `CandidateOptimizationSummary`.
5. Count samples where `delta > regression_tolerance` as wins and gate by `min_sample_win_rate`.
6. Include `sample_win_rate` in selected artifact metadata.
7. Run `python3 -m pytest -q tests/test_eval_optimization.py`.

### Task 2: RAG Graph Retrieval A/B Stability

**Files:**
- Modify or create: `services/pythontopia-rag/tests/test_recent_optimization_ab.py`

**Steps:**
1. Add deterministic mini-corpus cases for prerequisite, co-occurrence, and off-mode baselines.
2. Assert graph treatment improves evidence recall/coverage over strict baseline without admitting unrelated pages.
3. Require wins across multiple cases, not one case.
4. Run targeted RAG pytest.

### Task 3: Backend Evidence/Memory/Recommendation A/B Matrix

**Files:**
- Modify or create: `backend/src/test/java/com/pythontopia/learner/evidence/RecentOptimizationAbEvaluationTest.java`
- Potentially extend: `RecommendationServiceTest.java`, `EvidenceMemoryServiceTest.java`

**Steps:**
1. Add a compact A/B matrix that names baseline/treatment/metric/uplift for evidence graph and memory.
2. Assert graph/memory treatment wins all key rows and minimum uplift thresholds.
3. Confirm recommendation top-rank changes for evidence-memory signal before raw fallback.
4. Run targeted Maven tests.

### Task 4: Changelog and Verification

**Files:**
- Modify: `CHANGELOG.md`

**Steps:**
1. Add the A/B hardening entry with exact commands and numbers.
2. Run `git diff --check`.
3. Run targeted graph/RAG/backend tests.
4. If any treatment does not stably beat baseline, improve implementation and repeat the relevant A/B loop.
