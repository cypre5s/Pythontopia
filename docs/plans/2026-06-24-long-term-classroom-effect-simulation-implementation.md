# Long-Term Classroom Effect Simulation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a deterministic backend simulation test for long-term class learning effects.

**Architecture:** A test-only simulator seeds real classroom, mastery, review, resource, and learning-event tables, then aggregates metrics from SQL. No production endpoint is added.

**Tech Stack:** Java 21, JUnit 5, Testcontainers PostgreSQL, Flyway, Spring `JdbcTemplate`.

---

### Task 1: RED Test

**Files:**
- Create: `backend/src/test/java/com/pythontopia/learner/effect/LongTermClassroomEffectSimulationTest.java`

**Steps:**
1. Write a test that calls missing `ClassroomLearningEffectSimulator`.
2. Assert treatment beats baseline on mastery, review completion, and resource usage.
3. Run `mvn -s ~/.m2/settings.xml -q -Dtest=LongTermClassroomEffectSimulationTest test`.
4. Expected: compilation fails because simulator classes are missing.

### Task 2: Test Simulator

**Files:**
- Create: `backend/src/test/java/com/pythontopia/learner/effect/ClassroomLearningEffectSimulator.java`

**Steps:**
1. Implement deterministic class/cohort/table seeding.
2. Implement SQL aggregation for `LongTermEffectMetrics`.
3. Keep all data under `source='long-term-sim'` and unique usernames/titles.
4. Run the targeted test and verify it passes.

### Task 3: Report and Changelog

**Files:**
- Create: `docs/reports/2026-06-24-long-term-classroom-effect-simulation.md`
- Modify: `CHANGELOG.md`

**Steps:**
1. Record the A/B metric numbers and command output.
2. Update changelog with the simulation result.
3. Run `git diff --check` and targeted Maven test.
