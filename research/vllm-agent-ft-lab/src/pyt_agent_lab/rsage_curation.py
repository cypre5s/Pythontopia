from __future__ import annotations

import copy
import hashlib
import json
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Mapping, Sequence, TypeVar

from .rsage_data import EVENT_INTENTS, stable_fingerprint


_SYSTEM_PROMPT = (
    "你是 Pythontopia 的 TutorDecisionAgent 专属模型。根据学生状态、课程知识点和证据白名单，"
    "只输出一个教学语义 JSON 对象，字段为 misconception_type、event_intent、hint_level、"
    "action_goal、evidence_refs、safety_flags。event_intent 只能从 READ、REVIEW、TRACE、"
    "SCAFFOLD、PRACTICE、ASK、CHECK 中选择；不得输出完整答案、隐藏测试或白名单外证据。"
)

_T = TypeVar("_T")


@dataclass(frozen=True)
class RsageCurationResult:
    target_sft_train: list[dict[str, Any]]
    retention_sft_train: list[dict[str, Any]]
    sft_train: list[dict[str, Any]]
    target_dev: list[dict[str, Any]]
    target_heldout: list[dict[str, Any]]
    preference_train: list[dict[str, Any]]
    preference_dev: list[dict[str, Any]]
    preference_heldout: list[dict[str, Any]]
    manifest: dict[str, Any]


def curate_rsage_dataset(
    cases: Sequence[Mapping[str, Any]],
    labels: Sequence[Mapping[str, Any]],
    retention_rows: Sequence[Mapping[str, Any]],
    *,
    seed: int = 20260716,
    target_sft_count: int = 294,
    retention_count: int = 252,
    preference_count: int = 208,
    protected_fingerprints: set[str] | None = None,
) -> RsageCurationResult:
    _require_positive_counts(target_sft_count, retention_count, preference_count)
    case_rows = [dict(row) for row in cases]
    label_rows = [dict(row) for row in labels]
    _require_unique(case_rows, "case_id", "候选案例编号")
    _require_unique(label_rows, "case_id", "教师标签案例编号")
    _require_unique(case_rows, "prompt_fingerprint", "候选提示指纹")

    labels_by_id = {
        str(row["case_id"]): row
        for row in label_rows
        if row.get("status") == "accepted"
    }
    paired: list[tuple[dict[str, Any], dict[str, Any]]] = []
    for case in case_rows:
        label = labels_by_id.get(str(case["case_id"]))
        if label is None:
            continue
        _validate_accepted_label(case, label)
        paired.append((case, label))

    protected = protected_fingerprints or set()
    protected_families = {
        _template_family(case)
        for case, _ in paired
        if str(case.get("prompt_fingerprint")) in protected
    }
    eligible = [pair for pair in paired if _template_family(pair[0]) not in protected_families]
    grouped: dict[str, list[tuple[dict[str, Any], dict[str, Any]]]] = defaultdict(list)
    for pair in eligible:
        grouped[_template_family(pair[0])].append(pair)
    if len(grouped) < 3:
        raise ValueError("模板族隔离后不足三个模板族，无法建立训练、开发和留出集")

    family_order = sorted(grouped, key=lambda family: _seeded_key(seed, "模板族", family))
    train_families = _choose_family_subset(grouped, family_order, target_sft_count)
    remaining_families = [family for family in family_order if family not in train_families]
    if len(remaining_families) < 2:
        raise ValueError("训练模板族选择后没有足够的独立开发与留出模板族")
    dev_family_count = max(1, len(remaining_families) - 1)
    dev_families = remaining_families[:dev_family_count]
    heldout_families = remaining_families[dev_family_count:]

    train_pairs = _flatten_families(grouped, train_families, seed)
    dev_pairs = _flatten_families(grouped, dev_families, seed)
    heldout_pairs = _flatten_families(grouped, heldout_families, seed)
    if len(train_pairs) != target_sft_count:
        raise AssertionError("模板族子集选择未达到目标训练数量")

    target_sft_train = [_build_target_sft(case, label, "train") for case, label in train_pairs]
    target_dev = [_build_target_sft(case, label, "dev") for case, label in dev_pairs]
    target_heldout = [_build_target_sft(case, label, "heldout") for case, label in heldout_pairs]

    retention_sft_train = _select_retention_rows(retention_rows, retention_count, seed)
    sft_train = sorted(
        [*target_sft_train, *retention_sft_train],
        key=lambda row: _seeded_key(seed, "混合SFT", str(row.get("id"))),
    )

    preference_train_pairs = _stratified_select(
        train_pairs,
        preference_count,
        key=lambda pair: (str(pair[1]["preferred_intent"]), str(pair[0]["failure_axis"])),
        seed=seed,
    )
    preference_train = [_build_preference(case, label, "train") for case, label in preference_train_pairs]
    preference_dev = [_build_preference(case, label, "dev") for case, label in dev_pairs]
    preference_heldout = [_build_preference(case, label, "heldout") for case, label in heldout_pairs]

    manifest = {
        "format": "rsage_curated_dataset_manifest_v1",
        "seed": seed,
        "counts": {
            "accepted_teacher_pairs": len(paired),
            "target_sft_train": len(target_sft_train),
            "retention_sft_train": len(retention_sft_train),
            "sft_train": len(sft_train),
            "target_dev": len(target_dev),
            "target_heldout": len(target_heldout),
            "preference_train": len(preference_train),
            "preference_dev": len(preference_dev),
            "preference_heldout": len(preference_heldout),
        },
        "split_template_families": {
            "train": sorted(train_families),
            "dev": sorted(dev_families),
            "heldout": sorted(heldout_families),
        },
        "protected_fingerprint_count": len(protected),
        "protected_family_count": len(protected_families),
        "protected_family_exclusion_count": sum(
            1 for case, _ in paired if _template_family(case) in protected_families
        ),
        "duplicate_prompt_count": 0,
        "preference_q_counts": dict(
            sorted(Counter(str(row["preference_q"]) for row in preference_train).items())
        ),
        "target_intent_failure_counts": dict(
            sorted(
                Counter(
                    f"{case['event_intent']}::{case['failure_axis']}"
                    for case, _ in train_pairs
                ).items()
            )
        ),
        "retention_agent_counts": dict(
            sorted(Counter(str(row["metadata"].get("agent", "未知")) for row in retention_sft_train).items())
        ),
        "message_zh": f"已整理 {len(sft_train)} 条 SFT 与 {len(preference_train)} 条软偏好训练数据",
    }
    manifest["dataset_sha256"] = stable_fingerprint(
        {
            "sft_train": sft_train,
            "sft_dev": target_dev,
            "sft_heldout": target_heldout,
            "preference_train": preference_train,
            "preference_dev": preference_dev,
            "preference_heldout": preference_heldout,
        }
    )
    return RsageCurationResult(
        target_sft_train=target_sft_train,
        retention_sft_train=retention_sft_train,
        sft_train=sft_train,
        target_dev=target_dev,
        target_heldout=target_heldout,
        preference_train=preference_train,
        preference_dev=preference_dev,
        preference_heldout=preference_heldout,
        manifest=manifest,
    )


def write_rsage_curation(result: RsageCurationResult, output_dir: str | Path) -> dict[str, Any]:
    root = Path(output_dir)
    root.mkdir(parents=True, exist_ok=True)
    files = {
        "sft_train.jsonl": result.sft_train,
        "sft_dev.jsonl": result.target_dev,
        "sft_heldout.jsonl": result.target_heldout,
        "preference_train.jsonl": result.preference_train,
        "preference_dev.jsonl": result.preference_dev,
        "preference_heldout.jsonl": result.preference_heldout,
        "target_sft_train.jsonl": result.target_sft_train,
        "retention_sft_train.jsonl": result.retention_sft_train,
    }
    hashes: dict[str, str] = {}
    for name, rows in files.items():
        payload = "".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows)
        _atomic_write_text(root / name, payload)
        hashes[name] = hashlib.sha256(payload.encode("utf-8")).hexdigest()
    manifest = copy.deepcopy(result.manifest)
    manifest["file_sha256"] = hashes
    _atomic_write_text(
        root / "manifest.json",
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
    )
    return manifest


def _build_target_sft(
    case: Mapping[str, Any],
    label: Mapping[str, Any],
    split: str,
) -> dict[str, Any]:
    chosen = _chosen_ir(label)
    return {
        "id": f"rsage-sft-{case['case_id']}",
        "messages": [
            {"role": "system", "content": _SYSTEM_PROMPT},
            {"role": "user", "content": _build_user_content(case)},
            {"role": "assistant", "content": json.dumps(chosen, ensure_ascii=False, sort_keys=True)},
        ],
        "metadata": _target_metadata(case, label, split, "teacher_verified_target"),
    }


def _build_preference(
    case: Mapping[str, Any],
    label: Mapping[str, Any],
    split: str,
) -> dict[str, Any]:
    chosen = _chosen_ir(label)
    rejected = _rejected_ir(case, label, chosen)
    return {
        "id": f"rsage-pref-{case['case_id']}",
        "prompt": [
            {"role": "system", "content": _SYSTEM_PROMPT},
            {"role": "user", "content": _build_user_content(case)},
        ],
        "chosen": json.dumps(chosen, ensure_ascii=False, sort_keys=True),
        "rejected": json.dumps(rejected, ensure_ascii=False, sort_keys=True),
        "preference_q": float(label["preference_q"]),
        "metadata": _target_metadata(case, label, split, "teacher_soft_preference"),
    }


def _chosen_ir(label: Mapping[str, Any]) -> dict[str, Any]:
    return {
        "misconception_type": str(label["misconception_type"]),
        "event_intent": str(label["preferred_intent"]),
        "hint_level": int(label["hint_level"]),
        "action_goal": str(label["action_goal"]),
        "evidence_refs": [str(item) for item in label["evidence_refs"]],
        "safety_flags": [str(item) for item in label.get("safety_flags", [])],
    }


def _rejected_ir(
    case: Mapping[str, Any],
    label: Mapping[str, Any],
    chosen: Mapping[str, Any],
) -> dict[str, Any]:
    preferred = str(label["preferred_intent"])
    candidate = str(case["event_intent"])
    admissible = {str(item) for item in label["admissible_intents"]}
    forbidden = [str(item) for item in label["forbidden_intents"]]
    if candidate == preferred:
        candidate = forbidden[0] if forbidden else next(
            intent for intent in EVENT_INTENTS if intent not in admissible
        )
    rejected = dict(chosen)
    rejected["event_intent"] = candidate
    axis = str(case["failure_axis"])
    rejected["action_goal"] = {
        "wrong_decision": "不分析学生状态，直接执行候选动作。",
        "weak_semantics": "继续学习。",
        "unbound_evidence": "引用未提供的材料推进学习。",
        "answer_leak": "绕过分层提示并直接公布最终做法。",
        "over_refusal": "拒绝处理这项正常的学习请求。",
        "chinese_noisy_input": "忽略含噪中文并套用固定回答。",
    }[axis]
    if axis == "unbound_evidence":
        rejected["evidence_refs"] = ["pack43-page-不在白名单"]
    if axis == "answer_leak":
        rejected["safety_flags"] = ["直接答案泄露"]
    return rejected


def _build_user_content(case: Mapping[str, Any]) -> str:
    context = {
        "student_input": case["student_input"],
        "learner_profile": case.get("learner_profile", {}),
        "kc": {
            "code": case["kc_code"],
            "name": case["kc_name"],
            "summary": case.get("kc_summary", ""),
        },
        "evidence_allowlist": [
            {
                "evidence_id": item["evidence_id"],
                "excerpt": item.get("excerpt", ""),
            }
            for item in case["evidence_refs"]
        ],
    }
    return json.dumps(context, ensure_ascii=False, sort_keys=True)


def _target_metadata(
    case: Mapping[str, Any],
    label: Mapping[str, Any],
    split: str,
    source_kind: str,
) -> dict[str, Any]:
    return {
        "agent": "TutorDecisionAgent",
        "task_type": "next_teaching_action",
        "source_kind": source_kind,
        "source_case_id": str(case["case_id"]),
        "chapter_id": int(case["chapter_id"]),
        "kc_code": str(case["kc_code"]),
        "failure_axis": str(case["failure_axis"]),
        "candidate_intent": str(case["event_intent"]),
        "preferred_intent": str(label["preferred_intent"]),
        "template_family": _template_family(case),
        "prompt_fingerprint": str(case["prompt_fingerprint"]),
        "teacher_model": str(label.get("model", "未知教师")),
        "valid_vote_count": int(label.get("valid_vote_count", 0)),
        "preference_q": float(label["preference_q"]),
        "split": split,
    }


def _select_retention_rows(
    rows: Sequence[Mapping[str, Any]],
    count: int,
    seed: int,
) -> list[dict[str, Any]]:
    candidates = [copy.deepcopy(dict(row)) for row in rows]
    _require_unique(candidates, "id", "保留回放编号")
    message_fingerprints = [stable_fingerprint(row.get("messages")) for row in candidates]
    if len(message_fingerprints) != len(set(message_fingerprints)):
        raise ValueError("发现重复的保留回放对话指纹")
    selected = _stratified_select(
        candidates,
        count,
        key=lambda row: (
            str((row.get("metadata") or {}).get("agent", "未知")),
            str((row.get("metadata") or {}).get("task_type", "未知")),
        ),
        seed=seed,
    )
    result: list[dict[str, Any]] = []
    for row in selected:
        metadata = dict(row.get("metadata") or {})
        metadata.update(
            {
                "source_kind": "champion_retention_replay",
                "split": "train",
                "original_id": str(row["id"]),
            }
        )
        row["id"] = f"rsage-retention-{row['id']}"
        row["metadata"] = metadata
        result.append(row)
    return result


def _stratified_select(
    rows: Sequence[_T],
    count: int,
    *,
    key: Callable[[_T], tuple[str, ...]],
    seed: int,
) -> list[_T]:
    if count > len(rows):
        raise ValueError(f"分层抽样需要 {count} 条，但只有 {len(rows)} 条可用")
    groups: dict[tuple[str, ...], list[_T]] = defaultdict(list)
    for row in rows:
        groups[key(row)].append(row)
    ordered_keys = sorted(groups, key=lambda value: _seeded_key(seed, "分层", repr(value)))
    for group_key, values in groups.items():
        values.sort(key=lambda value: _seeded_key(seed, repr(group_key), stable_fingerprint(value)))
    selected: list[_T] = []
    while len(selected) < count:
        progressed = False
        for group_key in ordered_keys:
            values = groups[group_key]
            if values and len(selected) < count:
                selected.append(values.pop(0))
                progressed = True
        if not progressed:
            break
    if len(selected) != count:
        raise AssertionError("分层抽样未达到目标数量")
    return selected


def _choose_family_subset(
    grouped: Mapping[str, Sequence[Any]],
    family_order: Sequence[str],
    target_count: int,
) -> list[str]:
    reachable: dict[int, list[str]] = {0: []}
    for family in family_order:
        size = len(grouped[family])
        updates: dict[int, list[str]] = {}
        for current, selected in sorted(reachable.items(), reverse=True):
            total = current + size
            if total <= target_count and total not in reachable and total not in updates:
                updates[total] = [*selected, family]
        reachable.update(updates)
    if target_count not in reachable:
        sizes = ", ".join(f"{family}={len(grouped[family])}" for family in family_order)
        raise ValueError(f"无法在模板族完全隔离条件下选出 {target_count} 条训练案例；可用模板族规模：{sizes}")
    return reachable[target_count]


def _flatten_families(
    grouped: Mapping[str, Sequence[tuple[dict[str, Any], dict[str, Any]]]],
    families: Sequence[str],
    seed: int,
) -> list[tuple[dict[str, Any], dict[str, Any]]]:
    rows = [pair for family in families for pair in grouped[family]]
    return sorted(rows, key=lambda pair: _seeded_key(seed, "案例", str(pair[0]["case_id"])))


def _validate_accepted_label(case: Mapping[str, Any], label: Mapping[str, Any]) -> None:
    preferred = label.get("preferred_intent")
    admissible = label.get("admissible_intents")
    forbidden = label.get("forbidden_intents")
    q = label.get("preference_q")
    if preferred not in EVENT_INTENTS:
        raise ValueError(f"案例 {case['case_id']} 的教师首选动作非法")
    if not isinstance(admissible, list) or preferred not in admissible:
        raise ValueError(f"案例 {case['case_id']} 的首选动作不在可接受集合")
    if not isinstance(forbidden, list) or set(admissible) & set(forbidden):
        raise ValueError(f"案例 {case['case_id']} 的可接受与禁止动作集合冲突")
    if isinstance(q, bool) or not isinstance(q, (int, float)) or not 0.5 <= float(q) <= 1.0:
        raise ValueError(f"案例 {case['case_id']} 的软偏好概率不在 0.5 到 1.0")
    allowlist = {
        str(item["evidence_id"])
        for item in case["evidence_refs"]
        if isinstance(item, Mapping)
    }
    chosen_refs = label.get("evidence_refs")
    if not isinstance(chosen_refs, list) or not chosen_refs or not set(chosen_refs) <= allowlist:
        raise ValueError(f"案例 {case['case_id']} 的教师证据不在案例白名单")


def _require_unique(rows: Sequence[Mapping[str, Any]], key: str, label: str) -> None:
    values = [str(row.get(key) or "") for row in rows]
    if any(not value for value in values):
        raise ValueError(f"{label}不能为空")
    duplicates = sorted(value for value, count in Counter(values).items() if count > 1)
    if duplicates:
        raise ValueError(f"发现重复的{label}：{duplicates[0]}")


def _require_positive_counts(target: int, retention: int, preference: int) -> None:
    if any(isinstance(value, bool) or not isinstance(value, int) or value <= 0 for value in (target, retention, preference)):
        raise ValueError("训练数据目标数量必须是正整数")
    if preference > target:
        raise ValueError("软偏好训练数量不能超过目标 SFT 训练数量")


def _template_family(case: Mapping[str, Any]) -> str:
    lineage = case.get("source_lineage")
    family = lineage.get("template_family") if isinstance(lineage, Mapping) else None
    if not isinstance(family, str) or not family:
        raise ValueError(f"案例 {case.get('case_id', '未知')} 缺少模板族")
    return family


def _seeded_key(seed: int, namespace: str, value: str) -> str:
    return stable_fingerprint({"seed": seed, "namespace": namespace, "value": value})


def _atomic_write_text(path: Path, content: str) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(content, encoding="utf-8")
    temporary.replace(path)
