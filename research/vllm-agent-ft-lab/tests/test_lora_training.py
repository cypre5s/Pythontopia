from pathlib import Path

from pyt_agent_lab.lora_training import example_to_training_text, load_training_plan


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
