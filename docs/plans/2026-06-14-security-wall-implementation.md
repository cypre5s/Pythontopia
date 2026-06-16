# Security Wall Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Execute 1,000 traceable security attack cases against Pythontopia, report confirmed vulnerabilities, and remediate them with regression tests.

**Architecture:** A deterministic catalog and allowlisted runner provide broad dynamic coverage while project-native tests prove and preserve each fix. Full active testing is local; ECS receives only low-frequency non-destructive checks.

**Tech Stack:** Python 3 standard library, Spring Boot/JUnit, Vue/Vitest/Playwright, FastAPI/pytest, Docker Compose

---

### Task 1: Establish audit baseline

**Files:**
- Read: `docs/dev-startup.md`
- Read: `deploy/docker-compose.yml`
- Read: `backend/src/main/java/com/pythontopia/auth/security/SecurityConfig.java`

**Steps:**
1. Probe ports `5173`, `18080`, `18091`, `18092`, `15432`, and `16379`.
2. Start the full stack with `./dev.sh`.
3. Verify all health endpoints and one functional course endpoint.
4. Record versions, git state, and exposed local routes.

### Task 2: Build and validate the 1,000-case catalog

**Files:**
- Create: `security/attack_catalog.py`
- Create: `security/tests/test_attack_catalog.py`
- Create: `security/README.md`

**Steps:**
1. Write a failing test requiring exactly 1,000 unique IDs and fingerprints.
2. Run the test and confirm it fails because the catalog is absent.
3. Implement ten 100-case domains with explicit invariants and safety levels.
4. Run catalog tests and inspect category counts.

### Task 3: Implement the allowlisted runner

**Files:**
- Create: `security/attack_runner.py`
- Create: `security/tests/test_attack_runner.py`

**Steps:**
1. Write failing tests for target allowlisting, redaction, response caps, and assertion evaluation.
2. Implement local full-run and public safe-subset modes.
3. Verify disallowed hosts and destructive public cases are rejected.
4. Add deterministic JSONL evidence output.

### Task 4: Execute baseline audits

**Files:**
- Generate: `.work/security/local-results.jsonl`
- Generate: `.work/security/public-results.jsonl`

**Steps:**
1. Run all 1,000 cases against the local isolated stack.
2. Rerun infrastructure-error cases after checking service health.
3. Run only safe public cases against the approved ECS endpoint with low concurrency.
4. Perform dependency and configuration checks without modifying lock files.

### Task 5: Publish the initial report

**Files:**
- Create: `security/report.py`
- Create: `docs/reports/2026-06-14-security-wall-audit.md`

**Steps:**
1. Group assertion failures by root cause rather than request count.
2. Add severity, CWE/OWASP mapping, evidence case IDs, affected files, and remediation.
3. Separate confirmed findings from observations and unverified hypotheses.
4. Record public-scope limitations and residual risk.

### Task 6: Remediate findings in severity order

**Files:**
- Modify only files named by confirmed findings.
- Add focused tests beside the affected component.

**Steps for each finding:**
1. Reproduce the root cause consistently.
2. Add the smallest native failing regression test.
3. Run it and confirm the expected failure.
4. Implement the minimal root-cause fix.
5. Run the focused test and affected module suite.
6. Update the report status and evidence.

### Task 7: Final verification

**Files:**
- Update: `docs/reports/2026-06-14-security-wall-audit.md`

**Steps:**
1. Rerun the full 1,000-case local suite.
2. Rerun the public safe subset.
3. Run backend, frontend, graph, and RAG security-focused suites.
4. Confirm no finding is marked fixed without fresh passing evidence.
5. Document unverified areas, accepted risks, and demo-path impact.
