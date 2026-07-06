import json
from pathlib import Path

from pyt_agent_lab.lora_evaluation import (
    generation_token_budget,
    load_sft_source_case_ids,
    normalize_generation_inputs,
    select_cases_by_ids,
)


def test_load_sft_source_case_ids_preserves_heldout_order(tmp_path: Path) -> None:
    path = tmp_path / "heldout.jsonl"
    path.write_text(
        "\n".join(
            [
                json.dumps({"metadata": {"source_case_id": "case-b"}}),
                json.dumps({"metadata": {"source_case_id": "case-a"}}),
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    assert load_sft_source_case_ids(path) == ["case-b", "case-a"]


def test_select_cases_by_ids_keeps_requested_order() -> None:
    cases = [{"id": "case-a"}, {"id": "case-b"}, {"id": "case-c"}]

    selected = select_cases_by_ids(cases, ["case-c", "case-a"])

    assert selected == [{"id": "case-c"}, {"id": "case-a"}]


def test_normalize_generation_inputs_accepts_batch_encoding_shape() -> None:
    input_ids = _FakeTensor((1, 13))
    attention_mask = _FakeTensor((1, 13))

    kwargs, prompt_length = normalize_generation_inputs({"input_ids": input_ids, "attention_mask": attention_mask}, device="cuda:0")

    assert prompt_length == 13
    assert kwargs == {"input_ids": input_ids, "attention_mask": attention_mask}
    assert input_ids.moved_to == "cuda:0"
    assert attention_mask.moved_to == "cuda:0"


def test_generation_token_budget_uses_case_max_tokens_when_available() -> None:
    assert generation_token_budget({"max_tokens": 640}, default_max_new_tokens=384) == 640


def test_generation_token_budget_falls_back_to_default_for_bad_values() -> None:
    assert generation_token_budget({"max_tokens": "bad"}, default_max_new_tokens=384) == 384


class _FakeTensor:
    def __init__(self, shape):
        self.shape = shape
        self.moved_to = None

    def to(self, device):
        self.moved_to = device
        return self
