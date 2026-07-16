from __future__ import annotations

import json
import threading
import time
from pathlib import Path

import pytest

from pyt_agent_lab.teacher_voting import (
    FixtureTeacherBackend,
    OpenAICompatibleTeacherBackend,
    aggregate_teacher_votes,
    redact_secrets,
    run_teacher_voting,
    verify_teacher_response,
)


def _case(case_id: str = "case-1") -> dict[str, object]:
    return {
        "case_id": case_id,
        "chapter_id": 1,
        "kc_code": "PY43-KC-001",
        "kc_name": "变量",
        "kc_summary": "理解变量绑定与重新赋值。",
        "student_input": "变量到底是什么？先提示我，不要直接给答案。",
        "event_intent": "SCAFFOLD",
        "failure_axis": "answer_leak",
        "evidence_refs": [
            {
                "evidence_id": "pack43-page-101",
                "excerpt": "变量用于引用对象，赋值语句建立名字与对象的绑定。",
            }
        ],
    }


def _vote(preferred: str = "SCAFFOLD", *, evidence_id: str = "pack43-page-101") -> dict[str, object]:
    return {
        "misconception_type": "把变量误认为固定容器",
        "action_goal": "用一个反例引导学生区分名字与对象",
        "admissible_intents": [preferred, "ASK"],
        "preferred_intent": preferred,
        "forbidden_intents": ["CHECK"],
        "preference_confidence": 0.8,
        "hint_level": 1,
        "evidence_refs": [evidence_id],
        "answer_leak": False,
        "safety_flags": [],
        "rationale": "先暴露误解，再给最小提示。",
    }


def _record(vote: dict[str, object], *, role: str, valid: bool = True) -> dict[str, object]:
    return {
        "reviewer_role": role,
        "parsed_vote": vote,
        "verifier": {"passed": valid, "errors": [] if valid else ["核验失败"]},
    }


def test_three_teacher_majority_produces_soft_probability() -> None:
    records = [
        _record(_vote("SCAFFOLD"), role="教学策略审查者"),
        _record(_vote("SCAFFOLD"), role="课程证据审查者"),
        _record(_vote("ASK"), role="安全边界审查者"),
    ]

    label = aggregate_teacher_votes(_case(), records)

    assert label["status"] == "accepted"
    assert label["preferred_intent"] == "SCAFFOLD"
    assert label["preference_q"] == 2 / 3
    assert label["valid_vote_count"] == 3
    assert label["message_zh"] == "案例 case-1 已通过三教师多数投票"


def test_unresolved_tie_is_excluded_instead_of_coerced() -> None:
    records = [
        _record(_vote("SCAFFOLD"), role="甲"),
        _record(_vote("ASK"), role="乙"),
        _record(_vote("READ"), role="丙"),
    ]

    label = aggregate_teacher_votes(_case(), records)

    assert label["status"] == "excluded"
    assert label["exclusion_code"] == "unresolved_tie"
    assert "平票" in label["message_zh"]


def test_verifier_rejects_evidence_outside_case_allowlist() -> None:
    raw = json.dumps(_vote(evidence_id="pack43-page-999"), ensure_ascii=False)

    checked = verify_teacher_response(raw, _case())

    assert checked.validation.passed is False
    assert any("白名单之外" in error for error in checked.validation.errors)


def test_runner_retries_invalid_votes_and_writes_incrementally(tmp_path: Path) -> None:
    class _RetryBackend:
        model_name = "fixture-retry"

        def __init__(self) -> None:
            self.calls: dict[str, int] = {}

        def review(self, case, reviewer_role: str, attempt: int) -> str:  # noqa: ANN001
            self.calls[reviewer_role] = self.calls.get(reviewer_role, 0) + 1
            if attempt == 1:
                return "不是 JSON"
            return json.dumps(_vote(), ensure_ascii=False)

    backend = _RetryBackend()
    raw_path = tmp_path / "raw_votes.jsonl"
    labels_path = tmp_path / "labels.jsonl"

    summary = run_teacher_voting(
        [_case()],
        backend,
        raw_votes_path=raw_path,
        labels_path=labels_path,
        max_attempts=2,
        retry_base_seconds=0,
    )

    raw_rows = [json.loads(line) for line in raw_path.read_text(encoding="utf-8").splitlines()]
    labels = [json.loads(line) for line in labels_path.read_text(encoding="utf-8").splitlines()]
    assert summary.accepted_count == 1
    assert len(raw_rows) == 6
    assert all("message_zh" in row for row in raw_rows)
    assert labels[0]["status"] == "accepted"


def test_runner_resume_skips_cases_already_in_label_file(tmp_path: Path) -> None:
    raw_path = tmp_path / "raw_votes.jsonl"
    labels_path = tmp_path / "labels.jsonl"
    first = FixtureTeacherBackend()
    run_teacher_voting(
        [_case()],
        first,
        raw_votes_path=raw_path,
        labels_path=labels_path,
        retry_base_seconds=0,
    )

    class _MustNotRun:
        model_name = "forbidden"

        def review(self, case, reviewer_role: str, attempt: int) -> str:  # noqa: ANN001
            raise AssertionError("断点续跑不应再次请求已完成案例")

    summary = run_teacher_voting(
        [_case()],
        _MustNotRun(),
        raw_votes_path=raw_path,
        labels_path=labels_path,
        retry_base_seconds=0,
        resume=True,
    )

    assert summary.skipped_count == 1
    assert len(labels_path.read_text(encoding="utf-8").splitlines()) == 1


def test_runner_resume_rejects_same_id_with_changed_prompt(tmp_path: Path) -> None:
    raw_path = tmp_path / "raw_votes.jsonl"
    labels_path = tmp_path / "labels.jsonl"
    run_teacher_voting(
        [_case()],
        FixtureTeacherBackend(),
        raw_votes_path=raw_path,
        labels_path=labels_path,
        retry_base_seconds=0,
    )
    changed = _case()
    changed["student_input"] = "同一编号但提示已经变化"

    with pytest.raises(ValueError, match="提示指纹发生变化"):
        run_teacher_voting(
            [changed],
            FixtureTeacherBackend(),
            raw_votes_path=raw_path,
            labels_path=labels_path,
            retry_base_seconds=0,
            resume=True,
        )


def test_fixture_backend_is_deterministic_and_passes_verification() -> None:
    backend = FixtureTeacherBackend()
    raw_a = backend.review(_case(), "教学策略审查者", 1)
    raw_b = backend.review(_case(), "教学策略审查者", 1)

    assert raw_a == raw_b
    assert verify_teacher_response(raw_a, _case()).validation.passed is True


def test_runner_can_vote_three_reviewer_roles_concurrently(tmp_path: Path) -> None:
    class _ConcurrentBackend:
        model_name = "fixture-concurrent"

        def __init__(self) -> None:
            self.active = 0
            self.max_active = 0
            self.lock = threading.Lock()

        def review(self, case, reviewer_role: str, attempt: int) -> str:  # noqa: ANN001
            with self.lock:
                self.active += 1
                self.max_active = max(self.max_active, self.active)
            time.sleep(0.02)
            with self.lock:
                self.active -= 1
            return json.dumps(_vote(), ensure_ascii=False)

    backend = _ConcurrentBackend()
    summary = run_teacher_voting(
        [_case()],
        backend,
        raw_votes_path=tmp_path / "raw.jsonl",
        labels_path=tmp_path / "labels.jsonl",
        retry_base_seconds=0,
        reviewer_concurrency=3,
    )

    assert summary.accepted_count == 1
    assert backend.max_active == 3


def test_live_backend_uses_configured_output_budget_and_disables_thinking() -> None:
    backend = OpenAICompatibleTeacherBackend(
        base_url="https://example.test/v1",
        api_key="secret",
        model="deepseek-v4-pro",
        max_tokens=2400,
    )
    captured: dict[str, object] = {}

    class _Client:
        def chat(self, messages, **kwargs):  # noqa: ANN001
            captured.update(kwargs)
            return json.dumps(_vote(), ensure_ascii=False)

    backend._client = _Client()  # type: ignore[assignment]

    backend.review(_case(), "教学策略审查者", 1)

    assert captured["max_tokens"] == 2400
    assert captured["enable_thinking"] is False


def test_secret_redaction_covers_explicit_and_bearer_tokens() -> None:
    value = "请求失败 Bearer sk-project-secret；环境值 token-abc 不应出现"

    redacted = redact_secrets(value, secrets=("sk-project-secret", "token-abc"))

    assert "sk-project-secret" not in redacted
    assert "token-abc" not in redacted
    assert redacted.count("[已脱敏]") >= 2
