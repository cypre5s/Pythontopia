from pathlib import Path

from pyt_agent_lab.web_ab_report import WebAbInput, build_web_ab_report, write_web_ab_report


def test_build_web_ab_report_marks_candidate_ready_when_repair_gate_passes() -> None:
    report = build_web_ab_report(
        WebAbInput(
            baseline_summary={"overall": {"score_mean": 0.96, "json_valid_rate": 0.74, "grounding_safe_rate": 0.80, "forbidden_leak_rate": 0.01}},
            adapter_summary={"overall": {"score_mean": 0.98, "json_valid_rate": 0.92, "grounding_safe_rate": 0.84, "forbidden_leak_rate": 0.0}},
            repaired_summary={"overall": {"score_mean": 1.0, "json_valid_rate": 1.0, "grounding_safe_rate": 1.0, "forbidden_leak_rate": 0.0}},
            serving_models=["pythontopia-qwen35-awq-base", "pythontopia-qwen35-e1-lora"],
        )
    )

    assert report["recommendation"] == "enable_fine_tuned_provider_with_grounding_guard"
    assert report["ab"]["repaired_vs_baseline"]["story_gate_passed"] is True
    assert report["serving"]["adapter_model"] == "pythontopia-qwen35-e1-lora"
    assert report["web_embedding"]["fallback_required"] is True


def test_write_web_ab_report_outputs_json_and_markdown(tmp_path: Path) -> None:
    report = build_web_ab_report(
        WebAbInput(
            baseline_summary={"overall": {"score_mean": 0.9, "json_valid_rate": 0.7, "grounding_safe_rate": 0.7, "forbidden_leak_rate": 0.01}},
            adapter_summary={"overall": {"score_mean": 0.95, "json_valid_rate": 0.8, "grounding_safe_rate": 0.8, "forbidden_leak_rate": 0.0}},
            repaired_summary={"overall": {"score_mean": 1.0, "json_valid_rate": 1.0, "grounding_safe_rate": 1.0, "forbidden_leak_rate": 0.0}},
            serving_models=["base", "adapter"],
        )
    )

    outputs = write_web_ab_report(report, tmp_path)

    assert outputs["json"].exists()
    assert outputs["markdown"].exists()
    assert "Fine-Tuned LLM Web A/B Report" in outputs["markdown"].read_text(encoding="utf-8")
