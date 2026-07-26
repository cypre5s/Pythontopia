from __future__ import annotations

import json

from pyt_agent_lab.training_quality import (
    audit_one_variable_round,
    fail_evaluation_manifest,
    initialize_evaluation_manifest,
    run_preference_preflight,
)


class _Tokenizer:
    def apply_chat_template(self, prompt, tokenize=False, add_generation_prompt=True):  # noqa: ANN001
        return json.dumps(prompt, sort_keys=True)

    def __call__(self, text, add_special_tokens=False):  # noqa: ANN001
        return {"input_ids": list(text.encode("utf-8"))}


def _write(path, rows) -> None:  # noqa: ANN001
    path.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")


def test_preflight_checks_lengths_labels_duplicates_and_single_variable(tmp_path) -> None:
    train = [
        {
            "id": "p1",
            "prompt": [{"role": "user", "content": "q1"}],
            "chosen": '{"ok": true}',
            "rejected": '{"ok": "true"}',
            "metadata": {"protected_eval_overlap": False},
        }
    ]
    heldout = [
        {
            "id": "p2",
            "prompt": [{"role": "user", "content": "q2"}],
            "chosen": '{"ok": false}',
            "rejected": '{"ok": "false"}',
            "metadata": {"protected_eval_overlap": False},
        }
    ]
    train_path = tmp_path / "train.jsonl"
    heldout_path = tmp_path / "heldout.jsonl"
    _write(train_path, train)
    _write(heldout_path, heldout)

    report = run_preference_preflight(
        train_file=train_path,
        heldout_file=heldout_path,
        tokenizer=_Tokenizer(),
        max_seq_length=1024,
        changed_variables=["preference_dataset"],
    )

    assert report["passed"] is True
    assert report["zero_label_count"] == 0
    assert report["duplicate_prompt_count"] == 0
    assert report["train_heldout_prompt_overlap_count"] == 0
    assert report["one_variable_only"] is True


def test_preflight_fails_multiple_changed_variables(tmp_path) -> None:
    row = {
        "id": "p1",
        "prompt": [{"role": "user", "content": "q"}],
        "chosen": "chosen",
        "rejected": "rejected",
    }
    train_path = tmp_path / "train.jsonl"
    heldout_path = tmp_path / "heldout.jsonl"
    _write(train_path, [row])
    _write(heldout_path, [{**row, "id": "p2", "prompt": [{"role": "user", "content": "q2"}]}])

    report = run_preference_preflight(
        train_file=train_path,
        heldout_file=heldout_path,
        tokenizer=_Tokenizer(),
        max_seq_length=1024,
        changed_variables=["beta", "learning_rate"],
    )
    assert report["passed"] is False
    assert report["one_variable_only"] is False


def test_one_variable_audit_normalizes_legacy_default_seed() -> None:
    champion = {
        "method": "dpo_lora",
        "max_seq_length": 512,
        "lora": {"r": 8},
        "dpo": {"beta": 0.1},
        "training": {"learning_rate": 5e-5},
    }
    candidate = {
        **champion,
        "sft_adapter_path": "outputs/adapters/champion",
        "seed": 42,
        "training": {"learning_rate": 5e-5, "seed": 42, "data_seed": 42},
        "quality_round": {
            "champion_adapter_path": "outputs/adapters/champion",
            "changed_variables": ["preference_dataset"],
        },
    }

    report = audit_one_variable_round(candidate_plan=candidate, champion_plan=champion)

    assert report["passed"] is True
    assert report["training_control_differences"] == {}
    assert report["normalized_champion_seed"] == 42


def test_one_variable_audit_rejects_training_seed_change() -> None:
    champion = {
        "method": "dpo_lora",
        "max_seq_length": 512,
        "training": {"learning_rate": 5e-5},
    }
    candidate = {
        **champion,
        "sft_adapter_path": "outputs/adapters/champion",
        "seed": 20260710,
        "training": {"learning_rate": 5e-5, "seed": 20260710, "data_seed": 20260710},
        "quality_round": {
            "champion_adapter_path": "outputs/adapters/champion",
            "changed_variables": ["preference_dataset"],
        },
    }

    report = audit_one_variable_round(candidate_plan=candidate, champion_plan=champion)

    assert report["passed"] is False
    assert report["training_control_differences"]["training.seed"] == {
        "champion": 42,
        "candidate": 20260710,
    }


def test_evaluation_manifest_records_case_schema_and_decoding_mode(tmp_path) -> None:
    cases = tmp_path / "cases.jsonl"
    _write(
        cases,
        [
            {
                "id": "v2-1",
                "agent": "ResourceDesignerAgent.handout",
                "schema_version": "agent-schema-v2",
                "system": "system",
                "user": "user",
            }
        ],
    )

    manifest = initialize_evaluation_manifest(
        run_dir=tmp_path / "run",
        cases_path=cases,
        endpoint="http://127.0.0.1:18094/v1",
        model="qwen-candidate",
        prompt_variant="baseline",
        seed=42,
        folds=1,
        concurrency=1,
        decoding_mode="json_schema",
        adapter_path="outputs/adapters/qwen-candidate",
    )

    assert manifest["schema_version"] == "agent-schema-v2"
    assert manifest["decoding"]["mode"] == "json_schema"
    assert manifest["adapter"] == "outputs/adapters/qwen-candidate"
    assert manifest["adapter_path"] == "outputs/adapters/qwen-candidate"


def test_failed_evaluation_manifest_preserves_resume_metadata(tmp_path) -> None:
    run_dir = tmp_path / "run"
    run_dir.mkdir()
    (run_dir / "run_manifest.json").write_text(
        json.dumps({"status": "running", "resume_supported": True}), encoding="utf-8"
    )

    fail_evaluation_manifest(run_dir, RuntimeError("endpoint stopped"))

    manifest = json.loads((run_dir / "run_manifest.json").read_text(encoding="utf-8"))
    assert manifest["status"] == "failed"
    assert manifest["resume_supported"] is True
    assert manifest["failure"] == {
        "exception_type": "RuntimeError",
        "message": "endpoint stopped",
    }
