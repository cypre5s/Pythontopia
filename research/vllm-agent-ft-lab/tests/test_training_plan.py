from pathlib import Path

from pyt_agent_lab.training_plan import TrainingDependency, build_training_plan, dependency_status


def test_build_training_plan_defaults_to_qlora_for_local_qwen35() -> None:
    plan = build_training_plan(
        model_path="/models/qwen35",
        train_file="/data/train.jsonl",
        heldout_file="/data/heldout.jsonl",
        output_dir="/out/adapter",
    )

    assert plan["method"] == "qlora_4bit"
    assert plan["model_path"] == "/models/qwen35"
    assert plan["train_file"] == "/data/train.jsonl"
    assert plan["heldout_file"] == "/data/heldout.jsonl"
    assert plan["output_dir"] == "/out/adapter"
    assert plan["lora"]["r"] == 8
    assert plan["training"]["per_device_train_batch_size"] == 1
    assert plan["training"]["gradient_accumulation_steps"] >= 8
    assert plan["loss_masking"] == {"mode": "assistant_only"}


def test_dependency_status_marks_required_missing_dependencies() -> None:
    deps = [
        TrainingDependency("present", True, True, "1.0"),
        TrainingDependency("missing_optional", False, False, None),
        TrainingDependency("missing_required", True, False, None),
    ]

    status = dependency_status(deps)

    assert not status["ready"]
    assert status["missing_required"] == ["missing_required"]
    assert status["available"]["present"] == "1.0"


def test_training_plan_can_be_written_as_json(tmp_path: Path) -> None:
    plan = build_training_plan(
        model_path="/models/qwen35",
        train_file="/data/train.jsonl",
        heldout_file="/data/heldout.jsonl",
        output_dir="/out/adapter",
    )
    path = tmp_path / "plan.json"

    from pyt_agent_lab.training_plan import write_training_plan

    write_training_plan(plan, path)

    assert path.read_text(encoding="utf-8").startswith("{")
