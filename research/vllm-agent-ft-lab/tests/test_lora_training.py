from pathlib import Path

from pyt_agent_lab.lora_training import (
    example_to_training_text,
    load_training_plan,
    should_use_conversational_sft_dataset,
    sft_loss_masking_config_kwargs,
)


class FakeTokenizer:
    def apply_chat_template(self, messages, tokenize=False, add_generation_prompt=False):
        assert not tokenize
        assert not add_generation_prompt
        return "\n".join(f"{message['role']}:{message['content']}" for message in messages)


def test_example_to_training_text_uses_chat_template() -> None:
    example = {
        "messages": [
            {"role": "system", "content": "s"},
            {"role": "user", "content": "u"},
            {"role": "assistant", "content": '{"ok": true}'},
        ]
    }

    text = example_to_training_text(example, FakeTokenizer())

    assert text == 'system:s\nuser:u\nassistant:{"ok": true}'


def test_load_training_plan_reads_json(tmp_path: Path) -> None:
    path = tmp_path / "plan.json"
    path.write_text('{"method": "qlora_4bit"}', encoding="utf-8")

    assert load_training_plan(path)["method"] == "qlora_4bit"


def test_sft_loss_masking_config_enables_assistant_only_loss() -> None:
    plan = {"loss_masking": {"mode": "assistant_only"}}

    assert sft_loss_masking_config_kwargs(plan) == {
        "assistant_only_loss": True,
        "completion_only_loss": False,
    }
    assert should_use_conversational_sft_dataset(plan) is True


def test_sft_loss_masking_defaults_to_legacy_full_text_loss() -> None:
    plan = {}

    assert sft_loss_masking_config_kwargs(plan) == {}
    assert should_use_conversational_sft_dataset(plan) is False
