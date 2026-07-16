from __future__ import annotations

from typing import Any

import pytest

from pyt_agent_lab.rsage_length_preflight import (
    RsageLengthPreflightError,
    run_rsage_length_preflight,
)


class _RecordingTokenizer:
    eos_token = "<eos>"

    def __init__(self) -> None:
        self.template_calls: list[dict[str, Any]] = []
        self.tokenize_calls: list[dict[str, Any]] = []

    def apply_chat_template(
        self,
        messages,  # noqa: ANN001
        *,
        tokenize=False,  # noqa: ANN001
        add_generation_prompt=False,  # noqa: ANN001
    ) -> str:
        self.template_calls.append(
            {
                "messages": list(messages),
                "tokenize": tokenize,
                "add_generation_prompt": add_generation_prompt,
            }
        )
        rendered = "".join(
            f"<{message['role']}:{message['content']}>" for message in messages
        )
        if add_generation_prompt:
            rendered += "<assistant:>"
        return rendered

    def __call__(self, *, text: str, **kwargs: Any) -> dict[str, list[int]]:
        self.tokenize_calls.append({"text": text, **kwargs})
        return {"input_ids": list(range(len(text)))}


def _sft_row(row_id: str, answer: str) -> dict[str, Any]:
    return {
        "id": row_id,
        "messages": [
            {"role": "system", "content": "S"},
            {"role": "user", "content": "U"},
            {"role": "assistant", "content": answer},
        ],
    }


def _preference_row(
    row_id: str,
    *,
    chosen: str,
    rejected: str,
) -> dict[str, Any]:
    return {
        "id": row_id,
        "prompt": [
            {"role": "system", "content": "S"},
            {"role": "user", "content": "U"},
        ],
        "chosen": chosen,
        "rejected": rejected,
    }


def test_uses_the_exact_training_chat_template_and_eos_paths() -> None:
    tokenizer = _RecordingTokenizer()
    sft = _sft_row("sft-1", "answer")
    preference = _preference_row("pref-1", chosen="good", rejected="bad")

    report = run_rsage_length_preflight(
        sft_rows=[sft],
        preference_rows=[preference],
        tokenizer=tokenizer,
        max_seq_length=10_000,
    )

    prompt = "<system:S><user:U><assistant:>"
    assert [call["text"] for call in tokenizer.tokenize_calls] == [
        prompt + "answer<eos>",
        prompt + "good<eos>",
        prompt + "bad<eos>",
    ]
    assert all(call["tokenize"] is False for call in tokenizer.template_calls)
    assert all(call["add_generation_prompt"] is True for call in tokenizer.template_calls)
    assert all("truncation" not in call and "max_length" not in call for call in tokenizer.tokenize_calls)
    assert report["by_kind"]["sft"]["sample_count"] == 1
    assert report["by_kind"]["preference"]["sample_count"] == 1
    assert report["samples"][1] == {
        "id": "pref-1",
        "kind": "preference",
        "length_tokens": len(prompt + "good<eos>"),
        "chosen_length_tokens": len(prompt + "good<eos>"),
        "rejected_length_tokens": len(prompt + "bad<eos>"),
    }


def test_reports_min_max_p50_p95_and_chinese_summary() -> None:
    tokenizer = _RecordingTokenizer()
    answers = ["a", "a" * 11, "a" * 21, "a" * 31]
    rows = [_sft_row(f"sft-{index}", answer) for index, answer in enumerate(answers)]
    prompt = "<system:S><user:U><assistant:>"
    lengths = [len(prompt + answer + "<eos>") for answer in answers]

    report = run_rsage_length_preflight(
        sft_rows=rows,
        preference_rows=[],
        tokenizer=tokenizer,
        max_seq_length=max(lengths),
    )

    assert report["passed"] is True
    assert report["length_tokens"] == {
        "min": lengths[0],
        "max": lengths[-1],
        "p50": (lengths[1] + lengths[2]) / 2,
        "p95": lengths[2] + (lengths[3] - lengths[2]) * 0.85,
    }
    assert report["overlength_ids"] == []
    assert "长度预检通过" in report["message_zh"]
    assert "最短" in report["message_zh"]
    assert "P50" in report["message_zh"]
    assert "P95" in report["message_zh"]


def test_fails_closed_without_truncation_when_any_sample_is_overlength() -> None:
    tokenizer = _RecordingTokenizer()
    preference = _preference_row("pref-too-long", chosen="x" * 20, rejected="ok")
    prompt = "<system:S><user:U><assistant:>"
    actual_length = len(prompt + "x" * 20 + "<eos>")

    with pytest.raises(RsageLengthPreflightError, match="长度预检失败") as raised:
        run_rsage_length_preflight(
            sft_rows=[],
            preference_rows=[preference],
            tokenizer=tokenizer,
            max_seq_length=actual_length - 1,
        )

    report = raised.value.report
    assert report["passed"] is False
    assert report["overlength_ids"] == ["pref-too-long"]
    assert report["overlength_details"] == [
        {
            "id": "pref-too-long",
            "kind": "preference",
            "length_tokens": actual_length,
            "chosen_length_tokens": actual_length,
            "rejected_length_tokens": len(prompt + "ok<eos>"),
        }
    ]
    assert report["samples"][0]["length_tokens"] == actual_length
    assert tokenizer.tokenize_calls[0]["text"].endswith("x" * 20 + "<eos>")


def test_accepts_a_sample_exactly_at_the_limit() -> None:
    tokenizer = _RecordingTokenizer()
    row = _sft_row("sft-boundary", "answer")
    prompt = "<system:S><user:U><assistant:>"
    exact_length = len(prompt + "answer<eos>")

    report = run_rsage_length_preflight(
        sft_rows=[row],
        preference_rows=[],
        tokenizer=tokenizer,
        max_seq_length=exact_length,
    )

    assert report["passed"] is True
    assert report["length_tokens"]["max"] == exact_length


@pytest.mark.parametrize(
    ("sft_rows", "preference_rows", "message"),
    [
        ([{"id": "missing-messages"}], [], "缺少 messages"),
        ([_sft_row("wrong-last-role", "answer") | {"messages": []}], [], "必须以 assistant 消息结束"),
        ([], [{"id": "missing-prompt", "chosen": "a", "rejected": "b"}], "缺少 prompt"),
    ],
)
def test_rejects_malformed_rows_fail_closed(
    sft_rows: list[dict[str, Any]],
    preference_rows: list[dict[str, Any]],
    message: str,
) -> None:
    with pytest.raises(ValueError, match=message):
        run_rsage_length_preflight(
            sft_rows=sft_rows,
            preference_rows=preference_rows,
            tokenizer=_RecordingTokenizer(),
            max_seq_length=384,
        )
