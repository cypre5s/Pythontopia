from __future__ import annotations

import hashlib
import json
import random
from collections import Counter, defaultdict
from copy import deepcopy
from pathlib import Path
from typing import Any, Iterable

from .code_plan import (
    CODE_PLAN_POLICY_VERSION,
    CODE_PLAN_SCHEMA_VERSION,
    expected_code_patterns,
    upgrade_code_lab_cases,
)
from .scoring import score_case_response
from .v2_preferences import build_v2_eval_cases, case_fingerprint


PATTERNS = (
    "assignment_trace",
    "conditional_branch",
    "while_control",
    "for_accumulate",
    "list_iteration",
    "function_scope",
    "string_cleanup",
    "exception_guard",
)
MUTATION_SCHEDULE = (
    "wrong_pattern",
    "wrong_pattern",
    "wrong_pattern",
    "wrong_pattern",
    "unbound_evidence",
    "legacy_free_code",
    "safety_leak",
    "weak_observation",
)


def build_code_plan_preference_dataset(
    *,
    source_cases: list[dict[str, Any]],
    protected_cases: list[dict[str, Any]],
    target_count: int = 240,
    seed: int = 20260713,
) -> list[dict[str, Any]]:
    if not 200 <= target_count <= 500:
        raise ValueError("target_count must be between 200 and 500")
    protected_lineage = set().union(
        *(_lineage_ids(case) for case in protected_cases)
    ) if protected_cases else set()
    protected_fingerprints = {case_fingerprint(case) for case in protected_cases}
    eligible = [
        case
        for case in source_cases
        if str(case.get("agent")) == "ResourceDesignerAgent"
        and not (_lineage_ids(case) & protected_lineage)
        and case_fingerprint(case) not in protected_fingerprints
    ]
    selected = _balanced_pattern_select(eligible, target_count, seed)
    if len(selected) != target_count:
        raise ValueError(f"only {len(selected)} uncontaminated CodePlan cases available")

    rows: list[dict[str, Any]] = []
    for index, source in enumerate(selected):
        case = upgrade_code_lab_cases(
            build_v2_eval_cases(
                [source], agents=("ResourceDesignerAgent.code_lab",)
            )
        )[0]
        chosen_payload = _chosen_payload(case)
        mutation = MUTATION_SCHEDULE[index % len(MUTATION_SCHEDULE)]
        rejected_payload = _rejected_payload(case, chosen_payload, mutation)
        chosen = json.dumps(chosen_payload, ensure_ascii=False, sort_keys=True)
        rejected = json.dumps(rejected_payload, ensure_ascii=False, sort_keys=True)
        chosen_score = score_case_response(case, chosen)
        rejected_score = score_case_response(case, rejected)
        if not chosen_score.promotion_pass:
            raise ValueError(f"CodePlan chosen failed: {case['id']} {chosen_score.as_row()}")
        if mutation != "weak_observation" and rejected_score.promotion_pass:
            raise ValueError(
                f"CodePlan rejected unexpectedly passed: {case['id']} {mutation}"
            )
        rows.append(
            {
                "id": f"codeplan-v21-pref-{index + 1:04d}-{source['id']}",
                "prompt": [
                    {"role": "system", "content": str(case["system"])},
                    {"role": "user", "content": str(case["user"])},
                ],
                "chosen": chosen,
                "rejected": rejected,
                "metadata": {
                    "source_case_id": str(source["id"]),
                    "source_case_fingerprint": case_fingerprint(source),
                    "agent": case["agent"],
                    "chapter": case.get("chapter"),
                    "eval_slice": case.get("eval_slice"),
                    "schema_version": CODE_PLAN_SCHEMA_VERSION,
                    "policy_version": CODE_PLAN_POLICY_VERSION,
                    "preference_axis": mutation,
                    "semantic_label": expected_code_patterns(case)[0],
                    "chosen_contract_pass": chosen_score.contract_pass,
                    "chosen_semantic_valid": chosen_score.semantic_valid,
                    "rejected_contract_pass": rejected_score.contract_pass,
                    "rejected_semantic_valid": rejected_score.semantic_valid,
                    "protected_eval_overlap": False,
                },
            }
        )
    _assert_unique(rows)
    return rows


def split_and_write_code_plan_preferences(
    rows: list[dict[str, Any]],
    output_dir: str | Path,
    *,
    heldout_ratio: float,
    seed: int,
    source_path: str | Path,
    protected_paths: Iterable[str | Path],
) -> dict[str, Any]:
    shuffled = list(rows)
    random.Random(seed).shuffle(shuffled)
    heldout_count = max(1, round(len(rows) * heldout_ratio))
    splits = {
        "train": sorted(shuffled[heldout_count:], key=lambda row: row["id"]),
        "heldout": sorted(shuffled[:heldout_count], key=lambda row: row["id"]),
    }
    target = Path(output_dir)
    target.mkdir(parents=True, exist_ok=True)
    for split, items in splits.items():
        _write_jsonl(target / f"{split}.jsonl", items)
    protected = [Path(path) for path in protected_paths]
    manifest = {
        "purpose": "code_plan_v21_semantic_pattern_preference_optimization",
        "schema_version": CODE_PLAN_SCHEMA_VERSION,
        "policy_version": CODE_PLAN_POLICY_VERSION,
        "total_count": len(rows),
        "train_count": len(splits["train"]),
        "heldout_count": len(splits["heldout"]),
        "seed": seed,
        "source_path": str(source_path),
        "source_sha256": _sha256(Path(source_path)),
        "protected_evaluation_paths": [str(path) for path in protected],
        "protected_evaluation_sha256": {
            str(path): _sha256(path) for path in protected
        },
        "protected_overlap_count": 0,
        "preference_axes": dict(
            sorted(Counter(row["metadata"]["preference_axis"] for row in rows).items())
        ),
        "semantic_labels": dict(
            sorted(Counter(row["metadata"]["semantic_label"] for row in rows).items())
        ),
        "train_sha256": _sha256(target / "train.jsonl"),
        "heldout_sha256": _sha256(target / "heldout.jsonl"),
        "contamination_policy": (
            "Exclude golden50 and stratified500 by id, source lineage and stable prompt fingerprint."
        ),
    }
    (target / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return manifest


def _balanced_pattern_select(
    cases: list[dict[str, Any]], target_count: int, seed: int
) -> list[dict[str, Any]]:
    groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for case in cases:
        groups[expected_code_patterns(case)[0]].append(case)
    randomizer = random.Random(seed)
    for group in groups.values():
        randomizer.shuffle(group)
    selected: list[dict[str, Any]] = []
    offsets = {pattern: 0 for pattern in PATTERNS}
    while len(selected) < target_count:
        made_progress = False
        for pattern in PATTERNS:
            group = groups.get(pattern, [])
            offset = offsets[pattern]
            if offset >= len(group):
                continue
            selected.append(group[offset])
            offsets[pattern] += 1
            made_progress = True
            if len(selected) == target_count:
                break
        if not made_progress:
            break
    return selected


def _chosen_payload(case: dict[str, Any]) -> dict[str, Any]:
    kc = str(case.get("kc") or "Python 基础")
    misconception = str(
        case.get("misconception_signal") or "概念与执行过程没有对应"
    )
    pattern = expected_code_patterns(case)[0]
    evidence = [str(value) for value in case.get("evidence_ids", [])]
    return {
        "walkthroughs": [
            {
                "title": f"{kc} 执行走读",
                "narrative": f"围绕“{misconception}”逐步观察状态变化。",
                "example_pattern": pattern,
                "observation_goal": _observation_goal(pattern),
                "common_mistakes": [f"没有把 {kc} 的概念与中间状态对应起来。"],
                "evidence_refs": evidence,
            }
        ]
    }


def _rejected_payload(
    case: dict[str, Any], chosen: dict[str, Any], mutation: str
) -> dict[str, Any]:
    rejected = deepcopy(chosen)
    item = rejected["walkthroughs"][0]
    if mutation == "wrong_pattern":
        expected = str(item["example_pattern"])
        item["example_pattern"] = PATTERNS[(PATTERNS.index(expected) + 1) % len(PATTERNS)]
    elif mutation == "unbound_evidence":
        item["evidence_refs"] = ["page-invented"]
    elif mutation == "legacy_free_code":
        item["code"] = "print('legacy free code')"
        item["expected_output"] = "legacy free code"
    elif mutation == "safety_leak":
        item["common_mistakes"] = ["直接提交完整答案并绕过练习检查。"]
    elif mutation == "weak_observation":
        item["narrative"] = "这是一个通用示例，请阅读并理解。"
        item["observation_goal"] = "观察代码。"
    else:
        raise ValueError(f"unknown CodePlan mutation: {mutation}")
    return rejected


def _observation_goal(pattern: str) -> str:
    return {
        "assignment_trace": "逐行记录变量名绑定的值，区分名称和值。",
        "conditional_branch": "先预测条件真假，再核对实际进入的分支。",
        "while_control": "逐轮记录条件和循环变量，定位继续或退出位置。",
        "for_accumulate": "记录每次迭代的当前值与累计结果。",
        "list_iteration": "对应列表元素、索引与每轮处理结果。",
        "function_scope": "区分形参、实参与局部返回值的作用范围。",
        "string_cleanup": "比较清理前后的字符串表示，识别行尾字符。",
        "exception_guard": "预测正常路径与异常路径各自触发的条件。",
    }[pattern]


def _lineage_ids(case: dict[str, Any]) -> set[str]:
    return {
        str(value)
        for key in ("id", "source_case_id", "base_source_case_id")
        if (value := case.get(key))
    }


def _assert_unique(rows: list[dict[str, Any]]) -> None:
    ids = [str(row["id"]) for row in rows]
    prompts = [
        json.dumps(row["prompt"], ensure_ascii=False, sort_keys=True) for row in rows
    ]
    if len(ids) != len(set(ids)):
        raise ValueError("duplicate CodePlan preference ids")
    if len(prompts) != len(set(prompts)):
        raise ValueError("duplicate CodePlan preference prompts")


def _write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.write_text(
        "".join(
            json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows
        ),
        encoding="utf-8",
    )


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()
