import json
from pathlib import Path

from pyt_agent_lab.training_report import extract_training_curve, write_training_curve_report


def test_extract_training_curve_splits_train_and_eval_points() -> None:
    state = {
        "log_history": [
            {"step": 1, "loss": 1.2, "mean_token_accuracy": 0.7},
            {"step": 2, "eval_loss": 0.9, "eval_mean_token_accuracy": 0.8},
            {"step": 3, "loss": 0.6, "mean_token_accuracy": 0.9},
        ]
    }

    curve = extract_training_curve(state)

    assert curve["train_loss"] == [
        {"step": 1, "loss": 1.2, "mean_token_accuracy": 0.7},
        {"step": 3, "loss": 0.6, "mean_token_accuracy": 0.9},
    ]
    assert curve["eval_loss"] == [{"step": 2, "eval_loss": 0.9, "eval_mean_token_accuracy": 0.8}]


def test_write_training_curve_report_outputs_json_and_svg(tmp_path: Path) -> None:
    state_path = tmp_path / "trainer_state.json"
    state_path.write_text(
        json.dumps(
            {
                "log_history": [
                    {"step": 1, "loss": 1.0, "mean_token_accuracy": 0.5},
                    {"step": 2, "loss": 0.5, "mean_token_accuracy": 0.8},
                    {"step": 2, "eval_loss": 0.4, "eval_mean_token_accuracy": 0.82},
                ]
            }
        ),
        encoding="utf-8",
    )

    report = write_training_curve_report(state_path, tmp_path / "report")

    assert report["train_loss"][0]["loss"] == 1.0
    assert json.loads((tmp_path / "report" / "training_curve.json").read_text(encoding="utf-8")) == report
    assert (tmp_path / "report" / "charts" / "training_loss.svg").read_text(encoding="utf-8").startswith("<svg")
