from __future__ import annotations

import json
from collections import Counter
from pathlib import Path

from pyt_agent_lab.rsage_case_builder import (
    build_rsage_cases,
    case_prompt_fingerprint,
    write_rsage_cases,
)


COURSE_DIR = Path(__file__).parents[3] / "data" / "course-pack-43"


def test_build_420_cases_has_complete_intent_failure_and_chapter_coverage() -> None:
    cases, manifest = build_rsage_cases(COURSE_DIR, count=420, seed=20260716)

    assert len(cases) == 420
    pair_counts = Counter((row["event_intent"], row["failure_axis"]) for row in cases)
    assert len(pair_counts) == 42
    assert set(pair_counts.values()) == {10}
    assert Counter(row["chapter_id"] for row in cases) == {index: 60 for index in range(1, 8)}
    assert Counter(row["source_lineage"]["template_family"] for row in cases) == {
        f"prompt-frame-{index}": 42 for index in range(10)
    }
    assert manifest["total_count"] == 420
    assert manifest["message_zh"] == "已构造 420 条 R-SAGE-IR 非保护训练候选案例"


def test_every_case_uses_existing_pack43_kc_and_page_evidence() -> None:
    kcs = json.loads((COURSE_DIR / "json" / "course_kc.json").read_text(encoding="utf-8"))
    pages = json.loads((COURSE_DIR / "json" / "course_page.json").read_text(encoding="utf-8"))
    kc_codes = {row["kc_code"] for row in kcs}
    page_ids = {row["id"] for row in pages}

    cases, _ = build_rsage_cases(COURSE_DIR, count=84, seed=20260716)

    assert all(row["kc_code"] in kc_codes for row in cases)
    assert all(ref["page_id"] in page_ids for row in cases for ref in row["evidence_refs"])
    assert all(ref["evidence_id"] == f"pack43-page-{ref['page_id']}" for row in cases for ref in row["evidence_refs"])
    assert all(ref["excerpt"].strip() for row in cases for ref in row["evidence_refs"])


def test_case_generation_is_deterministic_for_same_seed() -> None:
    first, first_manifest = build_rsage_cases(COURSE_DIR, count=42, seed=123)
    second, second_manifest = build_rsage_cases(COURSE_DIR, count=42, seed=123)

    assert first == second
    assert first_manifest["dataset_sha256"] == second_manifest["dataset_sha256"]


def test_protected_prompt_fingerprint_is_excluded_and_replaced() -> None:
    baseline, _ = build_rsage_cases(COURSE_DIR, count=42, seed=99)
    blocked = case_prompt_fingerprint(baseline[0])

    filtered, manifest = build_rsage_cases(
        COURSE_DIR,
        count=42,
        seed=99,
        protected_fingerprints={blocked},
    )

    assert len(filtered) == 42
    assert blocked not in {case_prompt_fingerprint(row) for row in filtered}
    assert manifest["protected_exclusion_count"] >= 1


def test_write_rsage_cases_emits_jsonl_and_hash_manifest(tmp_path: Path) -> None:
    cases, manifest = build_rsage_cases(COURSE_DIR, count=12, seed=7)
    cases_path = tmp_path / "cases.jsonl"
    manifest_path = tmp_path / "manifest.json"

    written = write_rsage_cases(cases, manifest, cases_path, manifest_path)

    rows = [json.loads(line) for line in cases_path.read_text(encoding="utf-8").splitlines()]
    saved_manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    assert rows == cases
    assert saved_manifest["dataset_sha256"] == written["dataset_sha256"]
    assert saved_manifest["total_count"] == 12
