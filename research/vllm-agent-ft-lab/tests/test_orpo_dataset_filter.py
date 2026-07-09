from __future__ import annotations

from pyt_agent_lab.orpo_dataset_filter import should_keep_orpo_row


def test_should_keep_orpo_row_rejects_zero_completion_labels() -> None:
    assert not should_keep_orpo_row(
        chosen_label_count=0,
        rejected_label_count=8,
        chosen_length=120,
        rejected_length=120,
        max_seq_length=384,
    )


def test_should_keep_orpo_row_rejects_sequences_over_max_length() -> None:
    assert not should_keep_orpo_row(
        chosen_label_count=4,
        rejected_label_count=4,
        chosen_length=385,
        rejected_length=200,
        max_seq_length=384,
    )


def test_should_keep_orpo_row_accepts_non_empty_bounded_sequences() -> None:
    assert should_keep_orpo_row(
        chosen_label_count=4,
        rejected_label_count=4,
        chosen_length=384,
        rejected_length=200,
        max_seq_length=384,
    )
