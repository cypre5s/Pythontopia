import json
from pathlib import Path

from pyt_agent_lab.beginner_noise_cases import (
    NOISE_PROFILES,
    augment_cases_with_beginner_noise,
    write_beginner_noise_cases_jsonl,
)
from pyt_agent_lab.domain_case_factory import generate_domain_cases


def test_beginner_noise_preserves_scoring_contracts() -> None:
    source_cases = generate_domain_cases(target_count=30)

    augmented = augment_cases_with_beginner_noise(source_cases, seed=20260703)

    assert len(augmented) == len(source_cases)
    assert len({case["id"] for case in augmented}) == len(source_cases)
    for original, noisy in zip(source_cases, augmented, strict=True):
        assert noisy["source_case_id"] == original["id"]
        assert noisy["id"].startswith(f'{original["id"]}-beginner-')
        assert noisy["agent"] == original["agent"]
        assert noisy["system"] == original["system"]
        assert noisy["expected_markers"] == original["expected_markers"]
        assert noisy["evidence_ids"] == original["evidence_ids"]
        assert noisy["requires_evidence"] == original["requires_evidence"]
        assert noisy["forbidden_answer_risk"] == original["forbidden_answer_risk"]
        assert noisy["max_tokens"] == original["max_tokens"]
        assert noisy["beginner_noise_profile"] in NOISE_PROFILES
        assert noisy["user"] != original["user"]
        assert original["kc"] in noisy["user"]
        for evidence_id in original["evidence_ids"]:
            assert evidence_id in noisy["user"]


def test_beginner_noise_covers_multiple_novice_expression_patterns() -> None:
    source_cases = generate_domain_cases(target_count=60)

    augmented = augment_cases_with_beginner_noise(source_cases, seed=20260703)
    profiles = {case["beginner_noise_profile"] for case in augmented}
    joined_users = "\n".join(case["user"] for case in augmented)

    assert profiles >= {
        "vague_reference",
        "fragmented_sentence",
        "mixed_terminology",
        "incomplete_error_paste",
        "low_confidence_help",
        "typo_pinyin_mix",
        "mobile_short_message",
        "wrong_kc_guess",
    }
    assert "这个" in joined_users
    assert "红字" in joined_users or "Traceback" in joined_users
    assert "zheli" in joined_users or "手机上看的" in joined_users
    assert "别直接给答案" in joined_users


def test_beginner_noise_generation_is_deterministic() -> None:
    source_cases = generate_domain_cases(target_count=12)

    first = augment_cases_with_beginner_noise(source_cases, seed=7)
    second = augment_cases_with_beginner_noise(source_cases, seed=7)
    third = augment_cases_with_beginner_noise(source_cases, seed=8)

    assert first == second
    assert [case["user"] for case in first] != [case["user"] for case in third]


def test_write_beginner_noise_cases_jsonl_outputs_manifest_ready_file(tmp_path: Path) -> None:
    source_cases = generate_domain_cases(target_count=5)
    output = tmp_path / "beginner_noise.jsonl"

    count = write_beginner_noise_cases_jsonl(source_cases, output, seed=20260703)

    lines = output.read_text(encoding="utf-8").splitlines()
    assert count == 5
    assert len(lines) == 5
    records = [json.loads(line) for line in lines]
    assert all(record["source_case_id"] for record in records)
    assert all(record["beginner_noise_profile"] in NOISE_PROFILES for record in records)
