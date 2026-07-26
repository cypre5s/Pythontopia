from __future__ import annotations

import hashlib
import json
import random
from collections import Counter
from pathlib import Path
from typing import Any, Iterable

from .scoring import score_case_response
from .v2_preferences import case_fingerprint


DIMENSIONS = (
    "ERROR_PATTERN",
    "COGNITIVE_STYLE",
    "EMOTION_PERSISTENCE",
    "PRACTICAL_ABILITY",
    "KNOWLEDGE_BASE",
)
FAILURE_AXES = (
    "wrong_dimension",
    "wrong_evidence",
    "unbound_evidence",
    "safety_leak",
    "weak_claim",
)


def build_reflection_eval_cases(source_cases: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [_reflection_case(source, index) for index, source in enumerate(source_cases)]


def build_reflection_preferences(
    *,
    source_cases: list[dict[str, Any]],
    protected_cases: list[dict[str, Any]],
    target_count: int = 240,
    seed: int = 42,
) -> list[dict[str, Any]]:
    if not 200 <= target_count <= 500:
        raise ValueError("target_count must be between 200 and 500")
    protected_ids = {str(case.get("id") or "") for case in protected_cases}
    protected_lineage = {
        str(case.get("source_case_id") or case.get("id") or "")
        for case in protected_cases
    }
    protected_fingerprints = {case_fingerprint(case) for case in protected_cases}
    eligible = [
        source
        for source in source_cases
        if str(source.get("id") or "") not in protected_ids
        and str(source.get("source_case_id") or source.get("id") or "")
        not in protected_lineage
        and case_fingerprint(source) not in protected_fingerprints
    ]
    eligible = _dedupe_sources(eligible)
    random.Random(seed).shuffle(eligible)
    if len(eligible) < target_count:
        raise ValueError(f"only {len(eligible)} uncontaminated reflection cases available")

    rows: list[dict[str, Any]] = []
    for index, source in enumerate(eligible[:target_count]):
        case = _reflection_case(source, index)
        chosen_payload = _chosen_payload(case)
        axis = FAILURE_AXES[index % len(FAILURE_AXES)]
        rejected_payload = _rejected_payload(case, chosen_payload, axis)
        chosen = json.dumps(chosen_payload, ensure_ascii=False, sort_keys=True)
        rejected = json.dumps(rejected_payload, ensure_ascii=False, sort_keys=True)
        chosen_score = score_case_response(case, chosen)
        rejected_score = score_case_response(case, rejected)
        if not chosen_score.promotion_pass:
            raise ValueError(f"reflection chosen failed: {case['id']} {chosen_score.as_row()}")
        if rejected_score.promotion_pass:
            raise ValueError(f"reflection rejected passed: {case['id']} axis={axis}")
        rows.append(
            {
                "id": f"reflection-v2-pref-{index + 1:04d}-{source['id']}",
                "prompt": [
                    {"role": "system", "content": str(case["system"])},
                    {"role": "user", "content": str(case["user"])},
                ],
                "chosen": chosen,
                "rejected": rejected,
                "metadata": {
                    "source_case_id": str(source["id"]),
                    "source_case_fingerprint": case_fingerprint(source),
                    "agent": "ReflectionAgent",
                    "chapter": case.get("chapter"),
                    "schema_version": "agent-schema-v2",
                    "preference_axis": axis,
                    "semantic_label": case["expected_reflection_dimension"],
                    "chosen_contract_pass": chosen_score.contract_pass,
                    "chosen_promotion_pass": chosen_score.promotion_pass,
                    "rejected_contract_pass": rejected_score.contract_pass,
                    "rejected_promotion_pass": rejected_score.promotion_pass,
                    "protected_eval_overlap": False,
                },
            }
        )
    return rows


def split_and_write_reflection_preferences(
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
        "heldout": sorted(shuffled[:heldout_count], key=lambda row: row["id"]),
        "train": sorted(shuffled[heldout_count:], key=lambda row: row["id"]),
    }
    target = Path(output_dir)
    target.mkdir(parents=True, exist_ok=True)
    for split, items in splits.items():
        _write_jsonl(target / f"{split}.jsonl", items)
    protected = [Path(path) for path in protected_paths]
    manifest = {
        "purpose": "reflection_v2_event_evidence_preference_optimization",
        "schema_version": "agent-schema-v2",
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
        "dimensions": dict(
            sorted(Counter(row["metadata"]["semantic_label"] for row in rows).items())
        ),
        "preference_axes": dict(
            sorted(Counter(row["metadata"]["preference_axis"] for row in rows).items())
        ),
        "train_sha256": _sha256(target / "train.jsonl"),
        "heldout_sha256": _sha256(target / "heldout.jsonl"),
        "contamination_policy": (
            "Exclude golden50, reflection golden50 and stratified500 by id, lineage and fingerprint."
        ),
    }
    (target / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return manifest


def jsonl_text(rows: list[dict[str, Any]]) -> str:
    return "".join(
        json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows
    )


def _reflection_case(source: dict[str, Any], index: int) -> dict[str, Any]:
    dimension = DIMENSIONS[index % len(DIMENSIONS)]
    event_ids = _event_ids(str(source["id"]))
    refs = [f"event-{event_id}" for event_id in event_ids]
    marker, primary_summary, secondary_summary = _dimension_context(dimension, source)
    case = dict(source)
    case.update(
        {
            "id": f"reflection-v2-{source['id']}",
            "base_source_case_id": str(source["id"]),
            "source_case_id": str(source.get("source_case_id") or source["id"]),
            "agent": "ReflectionAgent",
            "schema_version": "agent-schema-v2",
            "evidence_ids": refs,
            "requires_evidence": True,
            "expected_markers": [
                "observations",
                "claim",
                "dimension_intent",
                "evidence_refs",
                marker,
            ],
            "expected_reflection_dimension": dimension,
            "expected_reflection_evidence_refs": [refs[0]],
            "expected_reflection_claim_markers": [marker],
            "system": (
                "ReflectionAgent v2 IR JSON：observations[{claim,dimension_intent,tags,"
                "evidence_refs,confidence}]。只做画像维度判断、学习洞察和 event-N 证据选择；"
                "禁止真实 event_id 产品字段、题目答案、隐藏测试和课件页码。"
            ),
            "user": (
                "受控学习记忆："
                + json.dumps(
                    {
                        "memories": [
                            {"evidence_ref": refs[0], "summary": primary_summary},
                            {"evidence_ref": refs[1], "summary": secondary_summary},
                            {
                                "evidence_ref": refs[2],
                                "summary": "一次普通页面浏览，无稳定画像信号。",
                            },
                        ],
                        "task": "只在证据充分时输出一条最可信的学情观察。",
                        "safety": str(source.get("instruction_style") or "不给完整答案"),
                    },
                    ensure_ascii=False,
                    sort_keys=True,
                )
            ),
        }
    )
    return case


def _dimension_context(
    dimension: str, source: dict[str, Any]
) -> tuple[str, str, str]:
    misconception = str(source.get("misconception_signal") or "概念边界混淆")
    learning = str(source.get("learning_signal") or "完成一次练习")
    profile = str(source.get("learner_profile") or "初学者")
    contexts = {
        "ERROR_PATTERN": (
            "反复出错",
            f"连续两次练习都出现同类误区：{misconception}，属于反复出错。",
            f"调试记录再次指向同一误区：{misconception}。",
        ),
        "COGNITIVE_STYLE": (
            "步骤讲解",
            f"学生在步骤讲解后能复述，而只看结论时停滞；画像背景：{profile}。",
            "同一知识点用分步追踪后完成得更稳定。",
        ),
        "EMOTION_PERSISTENCE": (
            "坚持完成",
            f"遇到失败后仍继续修订并坚持完成；最近信号：{learning}。",
            "连续两次失败后仍主动请求下一层提示。",
        ),
        "PRACTICAL_ABILITY": (
            "调试实践",
            f"能依据运行反馈进行调试实践；最近信号：{learning}。",
            "能修改局部代码并再次提交，但尚需提示定位根因。",
        ),
        "KNOWLEDGE_BASE": (
            "概念基础",
            f"对当前概念基础仍不稳定：{misconception}。",
            "换一个题面后无法独立迁移同一概念。",
        ),
    }
    return contexts[dimension]


def _chosen_payload(case: dict[str, Any]) -> dict[str, Any]:
    dimension = str(case["expected_reflection_dimension"])
    marker = str(case["expected_reflection_claim_markers"][0])
    return {
        "observations": [
            {
                "claim": f"多条学习记忆共同显示学生存在{marker}信号。",
                "dimension_intent": dimension,
                "tags": [dimension.lower()],
                "evidence_refs": list(case["expected_reflection_evidence_refs"]),
                "confidence": 0.82,
            }
        ]
    }


def _rejected_payload(
    case: dict[str, Any], chosen: dict[str, Any], axis: str
) -> dict[str, Any]:
    rejected = json.loads(json.dumps(chosen, ensure_ascii=False))
    observation = rejected["observations"][0]
    if axis == "wrong_dimension":
        current = str(case["expected_reflection_dimension"])
        observation["dimension_intent"] = DIMENSIONS[(DIMENSIONS.index(current) + 1) % len(DIMENSIONS)]
    elif axis == "wrong_evidence":
        observation["evidence_refs"] = [case["evidence_ids"][1]]
    elif axis == "unbound_evidence":
        observation["evidence_refs"] = ["event-99999999"]
    elif axis == "safety_leak":
        observation["claim"] = "直接给出完整答案并记录为高能力。"
    elif axis == "weak_claim":
        observation["claim"] = "这个学生有问题。"
    else:
        raise ValueError(f"unknown reflection preference axis: {axis}")
    return rejected


def _event_ids(source_id: str) -> tuple[int, int, int]:
    base = int(hashlib.sha256(source_id.encode("utf-8")).hexdigest()[:8], 16)
    start = 100000 + base % 800000
    return start, start + 1, start + 2


def _dedupe_sources(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    seen: set[str] = set()
    result: list[dict[str, Any]] = []
    for row in rows:
        fingerprint = case_fingerprint(row)
        if fingerprint in seen:
            continue
        seen.add(fingerprint)
        result.append(row)
    return result


def _write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.write_text(jsonl_text(rows), encoding="utf-8")


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()
