from __future__ import annotations

import hashlib
import importlib.metadata
import json
import subprocess
import sys
import traceback
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable


def audit_one_variable_round(
    *, candidate_plan: dict[str, Any], champion_plan: dict[str, Any]
) -> dict[str, Any]:
    """Prove that a continuation round keeps trainer controls aligned with its champion.

    Older plans omitted seed/data_seed, so their effective Transformers default (42) is normalized
    before comparison. Dataset paths are deliberately excluded: they are the declared variable.
    """
    candidate_controls = _training_controls(candidate_plan)
    champion_controls = _training_controls(champion_plan)
    differences = {
        key: {"champion": champion_controls.get(key), "candidate": candidate_controls.get(key)}
        for key in sorted(set(candidate_controls) | set(champion_controls))
        if candidate_controls.get(key) != champion_controls.get(key)
    }
    quality_round = candidate_plan.get("quality_round") or {}
    changed_variables = quality_round.get("changed_variables") or []
    expected_input = quality_round.get("champion_adapter_path")
    actual_input = candidate_plan.get("sft_adapter_path")
    return {
        "passed": not differences
        and changed_variables == ["preference_dataset"]
        and bool(expected_input)
        and actual_input == expected_input,
        "declared_changed_variables": changed_variables,
        "training_control_differences": differences,
        "candidate_input_is_champion": actual_input == expected_input,
        "candidate_input_adapter": actual_input,
        "champion_adapter": expected_input,
        "normalized_champion_seed": champion_controls["training.seed"],
        "normalized_candidate_seed": candidate_controls["training.seed"],
    }


def _training_controls(plan: dict[str, Any]) -> dict[str, Any]:
    training = dict(plan.get("training") or {})
    effective_seed = int(training.get("seed", plan.get("seed", 42)))
    effective_data_seed = int(training.get("data_seed", effective_seed))
    training["seed"] = effective_seed
    training["data_seed"] = effective_data_seed
    controls = {
        "method": plan.get("method"),
        "max_seq_length": plan.get("max_seq_length"),
        "lora": plan.get("lora"),
        "preference": plan.get("dpo") or plan.get("orpo"),
    }
    controls.update({f"training.{key}": value for key, value in sorted(training.items())})
    return controls


def run_preference_preflight(
    *,
    train_file: str | Path,
    heldout_file: str | Path,
    tokenizer: Any,
    max_seq_length: int,
    changed_variables: list[str] | None = None,
) -> dict[str, Any]:
    train = _read_jsonl(train_file)
    heldout = _read_jsonl(heldout_file)
    rows = [("train", row) for row in train] + [("heldout", row) for row in heldout]
    lengths = []
    zero_label_ids = []
    identical_pair_ids = []
    overlength_ids = []
    ids = []
    prompts = []
    pairs = []
    protected_overlap_ids = []
    for split, row in rows:
        row_id = str(row.get("id", ""))
        ids.append(row_id)
        prompt_text = _format_prompt(tokenizer, row.get("prompt", []))
        chosen = str(row.get("chosen", ""))
        rejected = str(row.get("rejected", ""))
        prompt_tokens = _token_ids(tokenizer, prompt_text)
        chosen_tokens = _token_ids(tokenizer, chosen)
        rejected_tokens = _token_ids(tokenizer, rejected)
        chosen_length = len(prompt_tokens) + len(chosen_tokens)
        rejected_length = len(prompt_tokens) + len(rejected_tokens)
        lengths.append(max(chosen_length, rejected_length))
        if not chosen_tokens or not rejected_tokens:
            zero_label_ids.append(row_id)
        if chosen == rejected or chosen_tokens == rejected_tokens:
            identical_pair_ids.append(row_id)
        if max(chosen_length, rejected_length) > max_seq_length:
            overlength_ids.append(row_id)
        prompt_hash = _sha256_text(prompt_text)
        prompts.append(prompt_hash)
        pairs.append(_sha256_text(prompt_text + "\n" + chosen + "\n" + rejected))
        if row.get("metadata", {}).get("protected_eval_overlap"):
            protected_overlap_ids.append(row_id)

    duplicate_ids = _duplicates(ids)
    duplicate_prompts = _duplicates(prompts)
    duplicate_pairs = _duplicates(pairs)
    train_prompts = {_sha256_text(_format_prompt(tokenizer, row.get("prompt", []))) for row in train}
    heldout_prompts = {_sha256_text(_format_prompt(tokenizer, row.get("prompt", []))) for row in heldout}
    split_overlap_count = len(train_prompts & heldout_prompts)
    one_variable_ok = changed_variables is None or len(changed_variables) == 1
    passed = not any(
        (
            zero_label_ids,
            identical_pair_ids,
            overlength_ids,
            duplicate_ids,
            duplicate_prompts,
            duplicate_pairs,
            protected_overlap_ids,
        )
    ) and split_overlap_count == 0 and one_variable_ok
    return {
        "passed": passed,
        "train_count": len(train),
        "heldout_count": len(heldout),
        "max_seq_length": max_seq_length,
        "token_length": {
            "max": max(lengths, default=0),
            "mean": round(sum(lengths) / len(lengths), 2) if lengths else 0.0,
            "overlength_count": len(overlength_ids),
            "overlength_ids": overlength_ids,
        },
        "zero_label_count": len(zero_label_ids),
        "zero_label_ids": zero_label_ids,
        "identical_chosen_rejected_count": len(identical_pair_ids),
        "identical_chosen_rejected_ids": identical_pair_ids,
        "duplicate_id_count": len(duplicate_ids),
        "duplicate_prompt_count": len(duplicate_prompts),
        "duplicate_pair_count": len(duplicate_pairs),
        "train_heldout_prompt_overlap_count": split_overlap_count,
        "protected_eval_overlap_count": len(protected_overlap_ids),
        "protected_eval_overlap_ids": protected_overlap_ids,
        "changed_variables": changed_variables,
        "one_variable_only": one_variable_ok,
        "dataset_sha256": {
            "train": _sha256_file(Path(train_file)),
            "heldout": _sha256_file(Path(heldout_file)),
        },
    }


def write_preference_preflight(report: dict[str, Any], path: str | Path) -> None:
    _write_json_atomic(Path(path), report)


def initialize_experiment_manifest(
    *,
    plan: dict[str, Any],
    plan_path: str | Path,
    output_dir: str | Path,
    schema_version: str = "agent-schema-v1",
) -> dict[str, Any]:
    target = Path(output_dir)
    target.mkdir(parents=True, exist_ok=True)
    manifest = {
        "status": "preflight",
        "started_at": _now(),
        "code_commit": _git_commit(),
        "code_dirty": _git_dirty(),
        "plan_path": str(plan_path),
        "plan_sha256": _sha256_file(Path(plan_path)),
        "method": plan.get("method"),
        "base_model": plan.get("model_path"),
        "input_adapter": plan.get("sft_adapter_path"),
        "output_adapter": plan.get("output_dir"),
        "schema_version": schema_version,
        "random_seed": plan.get("seed", 20260710),
        "decoding": plan.get("decoding", {"temperature": 0.0, "prompt_variant": "baseline"}),
        "datasets": {
            "train": {"path": plan.get("train_file"), "sha256": _optional_hash(plan.get("train_file"))},
            "heldout": {"path": plan.get("heldout_file"), "sha256": _optional_hash(plan.get("heldout_file"))},
        },
        "quality_round": plan.get("quality_round"),
        "versions": _versions(("torch", "transformers", "peft", "trl", "datasets", "accelerate", "vllm")),
        "resume_supported": True,
        "launcher_argv": list(sys.argv),
        "required_temp_environment": {"TMP": "/tmp", "TEMP": "/tmp", "TMPDIR": "/tmp"},
    }
    _write_json_atomic(target / "experiment_manifest.json", manifest)
    append_experiment_event(target, "manifest_initialized", {"status": "preflight"})
    return manifest


def initialize_evaluation_manifest(
    *,
    run_dir: str | Path,
    cases_path: str | Path,
    endpoint: str,
    model: str,
    prompt_variant: str | None,
    seed: int,
    folds: int,
    concurrency: int,
) -> dict[str, Any]:
    target = Path(run_dir)
    target.mkdir(parents=True, exist_ok=True)
    cases = _read_jsonl(cases_path)
    manifest = {
        "status": "running",
        "started_at": _now(),
        "code_commit": _git_commit(),
        "code_dirty": _git_dirty(),
        "cases_path": str(cases_path),
        "cases_sha256": _sha256_file(Path(cases_path)),
        "case_count": len(cases),
        "case_ids_sha256": _sha256_text("\n".join(sorted(str(case["id"]) for case in cases))),
        "prompt_sha256": _sha256_text(
            "\n".join(
                json.dumps(
                    {"id": case["id"], "system": case.get("system"), "user": case.get("user")},
                    ensure_ascii=False,
                    sort_keys=True,
                )
                for case in cases
            )
        ),
        "endpoint": endpoint,
        "model": model,
        "adapter": model,
        "schema_version": "agent-schema-v1",
        "seed": seed,
        "folds": folds,
        "prompt_variant": prompt_variant,
        "decoding": {
            "temperature": 0.0,
            "max_tokens": "per_case.max_tokens",
            "top_p": "server_generation_default_not_explicit",
            "chat_template_kwargs": {"enable_thinking": False} if "qwen" in model.lower() else None,
        },
        "concurrency": concurrency,
        "versions": _versions(("vllm", "transformers", "peft", "trl")),
        "incremental_outputs": ["events.jsonl", "metrics.csv", "progress_snapshots.jsonl", "responses.jsonl"],
        "resume_supported": True,
    }
    _write_json_atomic(target / "run_manifest.json", manifest)
    append_experiment_event(target, "evaluation_started", {"model": model, "case_count": len(cases)})
    return manifest


def complete_evaluation_manifest(run_dir: str | Path, summary: dict[str, Any]) -> None:
    target = Path(run_dir)
    path = target / "run_manifest.json"
    manifest = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    manifest.update({"status": "evaluated", "completed_at": _now(), "summary": summary})
    _write_json_atomic(path, manifest)
    append_experiment_event(target, "evaluation_completed", {"case_count": summary.get("overall", {}).get("case_count")})


def update_experiment_status(output_dir: str | Path, status: str, payload: dict[str, Any] | None = None) -> None:
    target = Path(output_dir)
    path = target / "experiment_manifest.json"
    manifest = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    manifest["status"] = status
    manifest["updated_at"] = _now()
    if payload:
        manifest.update(payload)
    _write_json_atomic(path, manifest)
    append_experiment_event(target, f"status:{status}", payload or {})


def archive_training_failure(output_dir: str | Path, exc: BaseException, stage: str) -> Path:
    target = Path(output_dir) / "failed_runs" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    target.mkdir(parents=True, exist_ok=True)
    failure = {
        "ts": _now(),
        "stage": stage,
        "exception_type": type(exc).__name__,
        "message": str(exc),
        "traceback": traceback.format_exc(),
    }
    _write_json_atomic(target / "failure.json", failure)
    update_experiment_status(output_dir, "failed", {"failure_artifact": str(target / "failure.json")})
    return target


def append_experiment_event(output_dir: str | Path, event: str, payload: dict[str, Any]) -> None:
    target = Path(output_dir)
    target.mkdir(parents=True, exist_ok=True)
    with (target / "experiment_events.jsonl").open("a", encoding="utf-8") as handle:
        handle.write(json.dumps({"ts": _now(), "event": event, **payload}, ensure_ascii=False, sort_keys=True) + "\n")


def _format_prompt(tokenizer: Any, prompt: Any) -> str:
    if hasattr(tokenizer, "apply_chat_template"):
        return str(tokenizer.apply_chat_template(prompt, tokenize=False, add_generation_prompt=True))
    return json.dumps(prompt, ensure_ascii=False, sort_keys=True)


def _token_ids(tokenizer: Any, text: str) -> list[int]:
    encoded = tokenizer(text, add_special_tokens=False)
    if isinstance(encoded, dict):
        return list(encoded.get("input_ids", []))
    return list(getattr(encoded, "input_ids", []))


def _duplicates(values: Iterable[str]) -> list[str]:
    seen = set()
    duplicates = set()
    for value in values:
        if value in seen:
            duplicates.add(value)
        seen.add(value)
    return sorted(duplicates)


def _versions(names: Iterable[str]) -> dict[str, str | None]:
    versions = {}
    for name in names:
        try:
            versions[name] = importlib.metadata.version(name)
        except importlib.metadata.PackageNotFoundError:
            versions[name] = None
    return versions


def _git_commit() -> str | None:
    result = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, text=True, check=False)
    return result.stdout.strip() or None


def _git_dirty() -> bool:
    result = subprocess.run(["git", "status", "--porcelain"], capture_output=True, text=True, check=False)
    return bool(result.stdout.strip())


def _optional_hash(value: Any) -> str | None:
    path = Path(str(value)) if value else None
    return _sha256_file(path) if path and path.exists() else None


def _read_jsonl(path: str | Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in Path(path).read_text(encoding="utf-8").splitlines() if line.strip()]


def _sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _write_json_atomic(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    temporary.replace(path)


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")
