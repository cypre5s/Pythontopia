from __future__ import annotations

import copy
import json
import random
from collections import Counter
from pathlib import Path
from typing import Any

from .beginner_noise_cases import augment_cases_with_beginner_noise
from .domain_case_factory import CHAPTERS, generate_domain_cases


SLICE_RATIOS = {
    "new_question": 0.40,
    "new_chapter_slice": 0.20,
    "new_student_expression": 0.30,
    "web_workshop_input": 0.10,
}

QUESTION_FAMILIES = [
    "边界值追问",
    "反例诊断",
    "同伴解释改写",
    "错因归纳",
    "迁移小题",
    "微测补救",
    "项目任务拆解",
    "提交前自检",
    "教师可追踪反馈",
    "证据引用审查",
]

CHAPTER_SLICE_FOCI = {
    1: ["input 返回字符串", "变量命名与值绑定", "表达式求值顺序", "类型转换前置判断"],
    2: ["range 右端开区间", "累计变量初始化", "循环体缩进", "遍历次数解释"],
    3: ["比较运算符", "elif 边界覆盖", "布尔短路", "else 兜底"],
    4: ["循环收敛条件", "哨兵值", "break/continue", "无限循环排查"],
    5: ["零基索引", "切片右端", "append 返回值", "遍历时修改列表"],
    6: ["return 与 print", "形参实参", "局部作用域", "函数拆分"],
    7: ["with 文件读写", "相对路径", "异常范围", "行尾换行处理"],
}

WEB_WORKSHOP_INPUTS = [
    {
        "input_type": "HANDOUT",
        "namespace": "make",
        "message": "帮我把这个 KC 做成课后讲义，短一点，要能直接放进工坊学习抽屉。",
    },
    {
        "input_type": "PRACTICE",
        "namespace": "practice",
        "message": "给我出三道练习，别给完整答案，要有样例和提示。",
    },
    {
        "input_type": "MINDMAP",
        "namespace": "make",
        "message": "生成知识点关系图，我想知道它和前置概念怎么连。",
    },
    {
        "input_type": "ASK",
        "namespace": "ask",
        "message": "我刚在小镇里问百晓，这个概念卡住了，先问我一个问题确认误区。",
    },
    {
        "input_type": "DEBUG",
        "namespace": "practice",
        "message": "我提交失败了，红字没看懂，只要下一条调试探针。",
    },
    {
        "input_type": "PATH",
        "namespace": "plan",
        "message": "我看完资源但微测错了，帮我决定通过、补救还是换资源。",
    },
]


def build_eval_pack_5000(target_count: int = 5000, *, seed: int = 20260706) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    if target_count < len(SLICE_RATIOS):
        raise ValueError("target_count must be at least the number of eval slices")

    rng = random.Random(seed)
    counts = _slice_counts(target_count)
    source_cases = generate_domain_cases(target_count + max(counts.values()))
    cursor = 0
    cases: list[dict[str, Any]] = []

    new_question_sources = source_cases[cursor : cursor + counts["new_question"]]
    cursor += counts["new_question"]
    cases.extend(
        _new_question_case(source, index, rng=rng)
        for index, source in enumerate(new_question_sources)
    )

    chapter_sources = source_cases[cursor : cursor + counts["new_chapter_slice"]]
    cursor += counts["new_chapter_slice"]
    cases.extend(
        _new_chapter_slice_case(source, index, rng=rng)
        for index, source in enumerate(chapter_sources)
    )

    student_sources = source_cases[cursor : cursor + counts["new_student_expression"]]
    cursor += counts["new_student_expression"]
    noisy_cases = augment_cases_with_beginner_noise(student_sources, seed=seed)
    cases.extend(
        _student_expression_case(source, index)
        for index, source in enumerate(noisy_cases)
    )

    web_sources = source_cases[cursor : cursor + counts["web_workshop_input"]]
    cases.extend(
        _web_workshop_case(source, index, rng=rng)
        for index, source in enumerate(web_sources)
    )

    rng.shuffle(cases)
    manifest = profile_eval_pack(cases, target_count=target_count, seed=seed)
    return cases, manifest


def write_eval_pack_jsonl(cases: list[dict[str, Any]], output: str | Path) -> int:
    target = Path(output)
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8") as handle:
        for case in cases:
            handle.write(json.dumps(case, ensure_ascii=False, sort_keys=True) + "\n")
    return len(cases)


def write_manifest(manifest: dict[str, Any], output: str | Path) -> None:
    target = Path(output)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")


def select_stratified_eval_subset(
    cases: list[dict[str, Any]],
    target_count: int,
    *,
    seed: int = 20260709,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    if target_count <= 0:
        raise ValueError("target_count must be positive")
    if target_count > len(cases):
        raise ValueError("target_count cannot exceed source case count")

    rng = random.Random(seed)
    indexed_cases = list(enumerate(cases))
    group_counts = Counter(_hard_group_key(case) for case in cases)
    group_quotas = _largest_remainder_quotas(group_counts, target_count)
    axis_targets = _soft_axis_targets(cases, target_count)
    axis_counts: dict[str, Counter[str]] = {
        axis: Counter() for axis in axis_targets
    }

    selected_indexes: set[int] = set()
    selected: list[dict[str, Any]] = []
    random_tiebreakers = {index: rng.random() for index, _case in indexed_cases}

    for group_key in sorted(group_quotas):
        quota = group_quotas[group_key]
        if quota <= 0:
            continue
        candidates = [
            (index, case)
            for index, case in indexed_cases
            if index not in selected_indexes and _hard_group_key(case) == group_key
        ]
        for _ in range(quota):
            if not candidates:
                raise ValueError(f"not enough cases for group {group_key!r}")
            best_index, best_case = max(
                candidates,
                key=lambda item: (
                    _soft_axis_deficit_score(item[1], axis_targets, axis_counts),
                    -item[0],
                    random_tiebreakers[item[0]],
                ),
            )
            candidates = [(index, case) for index, case in candidates if index != best_index]
            selected_indexes.add(best_index)
            selected.append(copy.deepcopy(best_case))
            _record_soft_axes(best_case, axis_counts)

    rng.shuffle(selected)
    manifest = profile_eval_pack(selected, target_count=target_count, seed=seed)
    manifest["source_count"] = len(cases)
    manifest["selection_strategy"] = "agent_eval_slice_quota_with_chapter_noise_web_evidence_greedy_balance"
    return selected, manifest


def profile_eval_pack(cases: list[dict[str, Any]], *, target_count: int, seed: int) -> dict[str, Any]:
    return {
        "target_count": target_count,
        "actual_count": len(cases),
        "seed": seed,
        "official_course_boundary": "Python语言基础官方 7 章；new_chapter_slice 表示官方章节内的新场景/新任务切片，不新增虚构章节。",
        "unique_case_ids": len({case["id"] for case in cases}),
        "unique_user_prompts": len({case["user"] for case in cases}),
        "slice_counts": dict(sorted(Counter(str(case.get("eval_slice", "unknown")) for case in cases).items())),
        "agent_counts": dict(sorted(Counter(str(case["agent"]) for case in cases).items())),
        "chapter_counts": dict(sorted(Counter(str(case["chapter"]) for case in cases).items(), key=lambda item: int(item[0]))),
        "source_kind_counts": dict(sorted(Counter(str(case.get("source_kind", "unknown")) for case in cases).items())),
        "noise_profile_counts": dict(sorted(Counter(str(case.get("beginner_noise_profile", "")) for case in cases if case.get("beginner_noise_profile")).items())),
        "web_input_type_counts": dict(sorted(Counter(str(case.get("web_input_type", "")) for case in cases if case.get("web_input_type")).items())),
        "question_family_counts": dict(sorted(Counter(str(case.get("question_family", "")) for case in cases if case.get("question_family")).items())),
    }


def _slice_counts(target_count: int) -> dict[str, int]:
    counts = {name: int(target_count * ratio) for name, ratio in SLICE_RATIOS.items()}
    remainder = target_count - sum(counts.values())
    counts["web_workshop_input"] += remainder
    for name in SLICE_RATIOS:
        if counts[name] == 0:
            donor = max(counts, key=counts.get)
            counts[donor] -= 1
            counts[name] = 1
    return counts


def _new_question_case(source: dict[str, Any], index: int, *, rng: random.Random) -> dict[str, Any]:
    case = _base_case(source, "new_question", index)
    family = _pick(QUESTION_FAMILIES, index + rng.randrange(len(QUESTION_FAMILIES)))
    case["source_kind"] = "deterministic_new_question"
    case["question_family"] = family
    case["holdout_tag"] = f"new-question-{index % 10}"
    case["user"] = (
        f"【新题样本 {index + 1}｜{family}】请不要沿用模板回答，要根据当前画像和证据重新判断。"
        f"题目变化：同一 KC 改成新问题情境，要求先判断误区，再给可验证下一步。"
        f"{source['user']}"
    )
    return case


def _new_chapter_slice_case(source: dict[str, Any], index: int, *, rng: random.Random) -> dict[str, Any]:
    case = _base_case(source, "new_chapter_slice", index)
    chapter = int(case["chapter"])
    chapter_name = CHAPTERS[chapter][0]
    focus = _pick(CHAPTER_SLICE_FOCI[chapter], index + rng.randrange(len(CHAPTER_SLICE_FOCI[chapter])))
    case["source_kind"] = "official_chapter_new_slice"
    case["official_chapter_name"] = chapter_name
    case["chapter_slice_focus"] = focus
    case["holdout_tag"] = f"chapter-{chapter}-slice-{index % 12}"
    case["user"] = (
        f"【官方第 {chapter} 章新切片｜{chapter_name}｜{focus}】"
        f"这是《Python语言基础》官方 7 章内的新任务切片，不新增教材外章节。"
        f"请把章节语境、误区线索和证据边界一起考虑。"
        f"{source['user']}"
    )
    return case


def _student_expression_case(source: dict[str, Any], index: int) -> dict[str, Any]:
    case = _base_case(source, "new_student_expression", index)
    case["source_kind"] = "beginner_expression_rewrite"
    case["holdout_tag"] = f"student-expression-{index % 12}"
    case["id"] = f"eval5000-new_student_expression-{index:04d}-{source['id']}"
    case["source_case_id"] = source.get("source_case_id", source["id"])
    case["user"] = f"【新学生表达样本 {index + 1}】{source['user']}"
    return case


def _web_workshop_case(source: dict[str, Any], index: int, *, rng: random.Random) -> dict[str, Any]:
    case = _base_case(source, "web_workshop_input", index)
    web_input = _pick(WEB_WORKSHOP_INPUTS, index + rng.randrange(len(WEB_WORKSHOP_INPUTS)))
    evidence_text = _evidence_text(case)
    case["source_kind"] = "web_workshop_product_input_shape"
    case["web_input_type"] = web_input["input_type"]
    case["web_namespace"] = web_input["namespace"]
    case["holdout_tag"] = f"web-workshop-{web_input['input_type'].lower()}-{index % 20}"
    case["user"] = (
        f"【真实 Web 工坊输入形态 {index + 1}】route=/workshop；"
        f"capabilityNamespace={web_input['namespace']}；resourceType={web_input['input_type']}；"
        f"kcCodes=[{case['kc']}]；studentMessage={web_input['message']}。"
        f"{evidence_text}"
        f"画像摘要：{case.get('learner_profile', '画像缺失')}。"
        f"学习信号：{case.get('learning_signal', '学习信号缺失')}。"
        f"请按 {case['agent']} 职责返回 JSON，并保留证据或 common_knowledge 边界。"
    )
    return case


def _base_case(source: dict[str, Any], eval_slice: str, index: int) -> dict[str, Any]:
    case = copy.deepcopy(source)
    case["id"] = f"eval5000-{eval_slice}-{index:04d}-{source['id']}"
    case["source_case_id"] = source["id"]
    case["eval_slice"] = eval_slice
    case["eval_pack_version"] = "5000-v1"
    return case


def _evidence_text(case: dict[str, Any]) -> str:
    evidence_ids = [str(item) for item in case.get("evidence_ids", [])]
    if evidence_ids:
        return "可用课件证据=" + "、".join(evidence_ids) + "。"
    return "本轮没有课件页证据，需要事实依据时请标注 common_knowledge 或通识。"


def _pick(items: list[Any], index: int) -> Any:
    return items[index % len(items)]


def _hard_group_key(case: dict[str, Any]) -> tuple[str, str]:
    return str(case.get("agent", "")), str(case.get("eval_slice", ""))


def _largest_remainder_quotas(counts: Counter[Any], target_count: int) -> dict[Any, int]:
    total = sum(counts.values())
    if total <= 0:
        raise ValueError("cannot compute quotas from empty counts")
    raw = {
        key: (value * target_count) / total
        for key, value in counts.items()
    }
    quotas = {key: int(value) for key, value in raw.items()}
    remainder = target_count - sum(quotas.values())
    ranked = sorted(
        raw,
        key=lambda key: (raw[key] - quotas[key], str(key)),
        reverse=True,
    )
    for key in ranked[:remainder]:
        quotas[key] += 1
    return quotas


def _soft_axis_targets(cases: list[dict[str, Any]], target_count: int) -> dict[str, dict[str, int]]:
    return {
        "chapter": _axis_quotas(cases, "chapter", target_count, include_empty=False),
        "beginner_noise_profile": _axis_quotas(
            cases,
            "beginner_noise_profile",
            _scaled_nonempty_target(cases, "beginner_noise_profile", target_count),
            include_empty=False,
        ),
        "web_input_type": _axis_quotas(
            cases,
            "web_input_type",
            _scaled_nonempty_target(cases, "web_input_type", target_count),
            include_empty=False,
        ),
        "evidence_bucket": _largest_remainder_quotas(
            Counter(_evidence_bucket(case) for case in cases),
            target_count,
        ),
    }


def _axis_quotas(
    cases: list[dict[str, Any]],
    field: str,
    target_count: int,
    *,
    include_empty: bool,
) -> dict[str, int]:
    values = Counter(
        str(case.get(field, ""))
        for case in cases
        if include_empty or case.get(field)
    )
    if not values or target_count <= 0:
        return {}
    return _largest_remainder_quotas(values, target_count)


def _scaled_nonempty_target(cases: list[dict[str, Any]], field: str, target_count: int) -> int:
    nonempty = sum(1 for case in cases if case.get(field))
    return round(target_count * nonempty / len(cases))


def _soft_axis_deficit_score(
    case: dict[str, Any],
    axis_targets: dict[str, dict[str, int]],
    axis_counts: dict[str, Counter[str]],
) -> int:
    weights = {
        "chapter": 3,
        "beginner_noise_profile": 5,
        "web_input_type": 5,
        "evidence_bucket": 2,
    }
    score = 0
    for axis, value in _soft_axis_values(case).items():
        target = axis_targets.get(axis, {}).get(value, 0)
        current = axis_counts.get(axis, Counter()).get(value, 0)
        if current < target:
            score += weights.get(axis, 1) * (target - current)
    return score


def _record_soft_axes(case: dict[str, Any], axis_counts: dict[str, Counter[str]]) -> None:
    for axis, value in _soft_axis_values(case).items():
        if axis in axis_counts:
            axis_counts[axis][value] += 1


def _soft_axis_values(case: dict[str, Any]) -> dict[str, str]:
    values = {
        "chapter": str(case.get("chapter", "")),
        "evidence_bucket": _evidence_bucket(case),
    }
    if case.get("beginner_noise_profile"):
        values["beginner_noise_profile"] = str(case["beginner_noise_profile"])
    if case.get("web_input_type"):
        values["web_input_type"] = str(case["web_input_type"])
    return values


def _evidence_bucket(case: dict[str, Any]) -> str:
    return "with_evidence" if case.get("evidence_ids") else "without_evidence"
