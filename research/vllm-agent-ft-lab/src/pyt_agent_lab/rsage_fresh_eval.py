from __future__ import annotations

import hashlib
import json
from collections import Counter, defaultdict
from collections.abc import Iterable, Mapping, Sequence
from pathlib import Path
from typing import Any

from .rsage_data import EVENT_INTENTS, FAILURE_AXES, stable_fingerprint


FRESH_AGENTS = (
    "TutorDecisionAgent",
    "PracticeAgent",
    "ResourceDesignerAgent",
    "IntentRouterAgent",
    "GuideCoachAgent",
    "DebugAgent",
    "GroundingCriticAgent",
    "SafetyAgent",
    "PathPlannerAgent",
    "MindMapAgent",
)

_SYSTEM_PROMPTS = {
    "TutorDecisionAgent": (
        "你负责选择下一教学动作。只返回 JSON：misconception_type、event_intent、hint_level、"
        "action_goal、evidence_refs、safety_flags；动作限 READ/REVIEW/TRACE/SCAFFOLD/PRACTICE/ASK/CHECK。"
    ),
    "PracticeAgent": "你负责练习辅导。只返回 JSON：misconception、hint_steps、blocked_content、evidence_ids。",
    "ResourceDesignerAgent": "你负责学习资源设计。只返回 JSON：resource_type、learning_goal、activity_steps、evidence_ids、grounding_note。",
    "IntentRouterAgent": "你负责能力路由。只返回 JSON：intent、target_agent、required_tools、confidence、reason。",
    "GuideCoachAgent": "你负责导学决策。只返回 JSON：learner_state_update、recommended_action、evidence_needed、stop_condition。",
    "DebugAgent": "你负责调试引导。只返回 JSON：bug_type、minimal_repro_hint、misconception、next_probe、safety_flags。",
    "GroundingCriticAgent": "你负责证据审查。只返回 JSON：grounded、missing_evidence、unsafe_claims、revision_request。",
    "SafetyAgent": "你负责教学安全。只返回 JSON：allow、reasons、redactions、safer_alternative。",
    "PathPlannerAgent": "你负责学习路径。只返回 JSON：state_summary、next_resource、next_practice、mastery_gate、evidence_ids。",
    "MindMapAgent": "你负责知识关系图。只返回 JSON：nodes、edges、prerequisites、evidence_ids。",
}

_EXPECTED_FIELDS = {
    "TutorDecisionAgent": ["misconception_type", "event_intent", "action_goal", "evidence_refs"],
    "PracticeAgent": ["misconception", "hint_steps", "blocked_content"],
    "ResourceDesignerAgent": ["resource_type", "activity_steps", "grounding_note"],
    "IntentRouterAgent": ["intent", "target_agent", "required_tools"],
    "GuideCoachAgent": ["learner_state_update", "recommended_action", "stop_condition"],
    "DebugAgent": ["bug_type", "minimal_repro_hint", "next_probe"],
    "GroundingCriticAgent": ["grounded", "missing_evidence", "revision_request"],
    "SafetyAgent": ["allow", "redactions", "safer_alternative"],
    "PathPlannerAgent": ["state_summary", "next_resource", "mastery_gate"],
    "MindMapAgent": ["nodes", "edges", "prerequisites"],
}

_SURFACES = (
    "请先从这份学习现场记录中找出最小可执行动作。",
    "把下面的学生状态当作一次新的课堂切片来判断。",
    "这不是复述教材，请依据当前证据完成一次独立决策。",
    "先识别学生卡点，再给能被下一环节消费的结构化结果。",
    "请把画像、行为信号和课程证据合在一起分析。",
    "本轮只解决一个最近的学习阻塞，不延伸成完整讲解。",
    "从可验证性出发处理下面的初学者请求。",
    "请检查动作是否与当前掌握状态匹配，然后再输出。",
    "把安全边界和教学有效性同时纳入这次判断。",
    "以下输入来自新的小镇学习会话，请不要套用固定答案。",
    "先考虑学生下一步能做什么，而不是一次给完所有内容。",
    "请用适合高校 Python 初学者的粒度作出决策。",
    "这是一次独立留出场景，只能依据本条记录回答。",
    "请区分课程证据、学生表现与推测，不要混在一起。",
    "围绕当前知识点给出短而明确的结构化处理结果。",
    "如果信息不足，也要在既定字段内表达边界。",
    "请优先防止答案泄露和虚构证据，再安排教学动作。",
    "把这条记录视作提交前的最后一次学习诊断。",
    "请从误区线索反推最合适的下一步，而非按关键词路由。",
    "本次输出会接受规则核验，请确保字段与证据都可审计。",
)

_PROFILES = (
    "第一次系统学习编程，需要短步骤",
    "已有少量图形化编程经验，但概念边界不稳",
    "希望通过课程考核，偏好先练后解释",
    "能看懂流程图，容易忽略 Python 语法细节",
    "阅读英文报错较慢，遇错时容易焦虑",
    "喜欢直接写代码，但很少复盘",
    "连续学习意愿强，基础仍较薄弱",
    "完成样例后难以迁移到新情境",
)

_SIGNALS = (
    "刚读完课件但微测答错边界条件",
    "提交出现运行错误且没有解释报错",
    "能复述概念却不能完成迁移题",
    "样例运行通过但解释与代码行为不一致",
    "同类错误已经第三次出现",
    "阅读时间过短，可能没有真正消费资源",
    "开放题缺少关键中间步骤",
    "请求更换资源但掌握度没有变化",
)


def merge_historical_paths(
    default_paths: Iterable[str | Path],
    explicit_paths: Iterable[str | Path],
) -> list[Path]:
    """合并默认与显式历史输入；显式参数只能扩大隔离审计范围。"""

    return sorted({Path(path) for path in (*default_paths, *explicit_paths)})


def build_fresh_eval_pack(
    course_dir: str | Path,
    *,
    target_count: int = 5000,
    seed: int = 20260716,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    if target_count < len(FRESH_AGENTS) or target_count % len(FRESH_AGENTS) != 0:
        raise ValueError("fresh 评测数量必须是不小于 10 的十的倍数")
    course = _load_course(Path(course_dir))
    kcs = course["kcs"]
    pages = course["pages"]
    cases: list[dict[str, Any]] = []
    for index in range(target_count):
        agent = FRESH_AGENTS[index % len(FRESH_AGENTS)]
        kc = kcs[(index * 17 + seed) % len(kcs)]
        page_id = next(
            (int(value) for value in kc.get("page_refs", []) if int(value) in pages),
            None,
        )
        if page_id is None:
            raise ValueError(f"KC {kc.get('kc_code')} 没有可用 pack43 页面证据")
        intent = EVENT_INTENTS[(index // len(FRESH_AGENTS)) % len(EVENT_INTENTS)]
        failure_axis = FAILURE_AXES[(index // (len(FRESH_AGENTS) * len(EVENT_INTENTS))) % len(FAILURE_AXES)]
        surface_index = (index // len(FRESH_AGENTS) + index) % len(_SURFACES)
        identifier = f"fresh-v2-{index + 1:05d}-{agent.lower()}"
        evidence_id = f"pack43-page-{page_id}"
        user = _render_user(
            index=index,
            agent=agent,
            kc=kc,
            evidence_id=evidence_id,
            intent=intent,
            failure_axis=failure_axis,
            surface_index=surface_index,
        )
        expected = [*_EXPECTED_FIELDS[agent]]
        if agent == "TutorDecisionAgent":
            expected.extend([intent, evidence_id])
        case = {
            "id": identifier,
            "source_case_id": identifier,
            "lineage_root_id": identifier,
            "prompt_family_id": f"fresh-v2-surface-{surface_index:02d}",
            "prompt_fingerprint": stable_fingerprint(
                {"system": _SYSTEM_PROMPTS[agent], "user": user}
            ),
            "eval_pack_version": "fresh-full5000-v2",
            "protected_status": "never_train",
            "agent": agent,
            "task_type": "next_teaching_action" if agent == "TutorDecisionAgent" else "fresh_retention_regression",
            "eval_slice": "fresh_target" if agent == "TutorDecisionAgent" else "fresh_retention",
            "pack_id": 43,
            "chapter": int(course["chapter_order_by_id"][int(kc["chapter_id"])]),
            "kc": str(kc["kc_code"]),
            "kc_name": str(kc["kc_name"]),
            "event_intent": intent,
            "failure_axis": failure_axis,
            "learner_profile": _PROFILES[(index + seed) % len(_PROFILES)],
            "learning_signal": _SIGNALS[(index // 3 + seed) % len(_SIGNALS)],
            "requires_evidence": agent not in {"IntentRouterAgent", "SafetyAgent"},
            "forbidden_answer_risk": failure_axis == "answer_leak",
            "evidence_ids": [evidence_id] if agent not in {"IntentRouterAgent", "SafetyAgent"} else [],
            "expected_markers": expected,
            "system": _SYSTEM_PROMPTS[agent],
            "user": user,
            "max_tokens": 384,
        }
        cases.append(case)
    manifest = _profile(cases, target_count=target_count, seed=seed)
    return cases, manifest


def audit_fresh_eval_against_history(
    fresh_rows: Sequence[Mapping[str, Any]],
    historical_rows: Sequence[Mapping[str, Any]],
) -> dict[str, Any]:
    historical_ids = _all_ids(historical_rows)
    historical_prompts = {
        fingerprint
        for row in historical_rows
        if (fingerprint := _prompt_fingerprint(row)) is not None
    }
    historical_templates = {
        template
        for row in historical_rows
        if (template := _template_id(row)) is not None
    }
    id_overlap = sorted(_all_ids(fresh_rows) & historical_ids)
    prompt_overlap = sorted(
        str(row.get("id"))
        for row in fresh_rows
        if _prompt_fingerprint(row) in historical_prompts
    )
    template_overlap = sorted(
        str(row.get("id"))
        for row in fresh_rows
        if _template_id(row) in historical_templates
    )
    report = {
        "zero_historical_overlap": not (id_overlap or prompt_overlap or template_overlap),
        "historical_row_count": len(historical_rows),
        "fresh_row_count": len(fresh_rows),
        "source_id_overlap_count": len(id_overlap),
        "prompt_overlap_count": len(prompt_overlap),
        "template_overlap_count": len(template_overlap),
        "source_id_overlap_examples": id_overlap[:20],
        "prompt_overlap_examples": prompt_overlap[:20],
        "template_overlap_examples": template_overlap[:20],
        "message_zh": "fresh 评测集与历史数据的编号、提示和模板族均为零重叠",
    }
    if not report["zero_historical_overlap"]:
        raise ValueError(
            "fresh 评测集与历史数据发生重叠："
            f"编号 {len(id_overlap)}、提示 {len(prompt_overlap)}、模板族 {len(template_overlap)}"
        )
    return report


def select_fresh_stratified_subset(
    cases: Sequence[Mapping[str, Any]],
    *,
    target_count: int = 500,
    seed: int = 20260716,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    if target_count <= 0 or target_count > len(cases):
        raise ValueError("分层评测数量必须大于零且不超过 fresh 全集")
    grouped: dict[str, list[Mapping[str, Any]]] = defaultdict(list)
    for row in cases:
        grouped[str(row["agent"])].append(row)
    if target_count % len(grouped) != 0:
        raise ValueError("分层评测数量必须能被 Agent 数量整除")
    quota = target_count // len(grouped)
    selected: list[dict[str, Any]] = []
    for agent in sorted(grouped):
        ordered = sorted(
            grouped[agent],
            key=lambda row: stable_fingerprint(
                {"seed": seed, "agent": agent, "id": str(row["id"])}
            ),
        )
        if len(ordered) < quota:
            raise ValueError(f"Agent {agent} 的 fresh 案例不足 {quota} 条")
        selected.extend(dict(row) for row in ordered[:quota])
    selected.sort(key=lambda row: stable_fingerprint({"seed": seed, "id": row["id"]}))
    manifest = _profile(selected, target_count=target_count, seed=seed)
    manifest.update(
        {
            "source_count": len(cases),
            "reduction_from_full": round(1 - target_count / len(cases), 6),
            "selection_strategy": "fresh_agent_quota_then_seeded_hash",
            "message_zh": f"已从 fresh 全集分层冻结 {target_count} 条中间评测案例",
        }
    )
    return selected, manifest


def write_fresh_eval_pack(
    cases: Sequence[Mapping[str, Any]],
    manifest: Mapping[str, Any],
    *,
    cases_path: str | Path,
    manifest_path: str | Path,
    historical_paths: Iterable[str | Path],
) -> dict[str, Any]:
    history_rows: list[dict[str, Any]] = []
    history_inputs: list[dict[str, str]] = []
    for raw_path in historical_paths:
        path = Path(raw_path)
        rows = _read_rows(path)
        history_rows.extend(rows)
        history_inputs.append({"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    audit = audit_fresh_eval_against_history(cases, history_rows)
    payload = "".join(
        json.dumps(dict(row), ensure_ascii=False, sort_keys=True) + "\n"
        for row in cases
    )
    written = {
        **dict(manifest),
        "freshness_audit": audit,
        "overlap_audit": {
            "historical_eval_overlap_count": 0,
            "declared_zero_historical_overlap": True,
            "training_overlap_count": 0,
            "audit_scope_zh": "已同时审计传入的全部历史评测与历史训练文件",
        },
        "historical_inputs": history_inputs,
        "jsonl_sha256": hashlib.sha256(payload.encode("utf-8")).hexdigest(),
    }
    _atomic_write(Path(cases_path), payload)
    _atomic_write(
        Path(manifest_path),
        json.dumps(written, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
    )
    return written


def _render_user(
    *,
    index: int,
    agent: str,
    kc: Mapping[str, Any],
    evidence_id: str,
    intent: str,
    failure_axis: str,
    surface_index: int,
) -> str:
    profile = _PROFILES[(index + 3) % len(_PROFILES)]
    signal = _SIGNALS[(index // 2 + 5) % len(_SIGNALS)]
    segments = [
        f"知识点是“{kc['kc_name']}”（{kc['kc_code']}）",
        f"学生画像：{profile}",
        f"最近信号：{signal}",
        f"候选教学动作：{intent}",
        f"需要防范的问题：{failure_axis}",
        f"证据白名单：{evidence_id}",
        f"独立场景编号：F2-{index + 1:05d}",
    ]
    rotation = surface_index % len(segments)
    ordered = segments[rotation:] + segments[:rotation]
    return _SURFACES[surface_index] + "；".join(ordered) + f"。请按 {agent} 的字段契约返回 JSON。"


def _load_course(course_dir: Path) -> dict[str, Any]:
    json_dir = course_dir / "json"
    chapters = json.loads((json_dir / "course_chapter.json").read_text(encoding="utf-8"))
    kcs = json.loads((json_dir / "course_kc.json").read_text(encoding="utf-8"))
    pages = json.loads((json_dir / "course_page.json").read_text(encoding="utf-8"))
    if len(chapters) != 7 or len(kcs) != 65 or len(pages) != 561:
        raise ValueError("pack43 课程规模校验失败：必须是 7 章、65 个 KC、561 页")
    return {
        "kcs": sorted(kcs, key=lambda row: str(row["kc_code"])),
        "pages": {int(row["id"]): row for row in pages},
        "chapter_order_by_id": {int(row["id"]): int(row["order_index"]) for row in chapters},
    }


def _profile(cases: Sequence[Mapping[str, Any]], *, target_count: int, seed: int) -> dict[str, Any]:
    return {
        "format": "fresh_full5000_v2",
        "manifest_schema": "rsage_fresh_eval_manifest_v2",
        "eval_pack_version": "fresh-full5000-v2",
        "protected_status": "never_train",
        "target_count": target_count,
        "actual_count": len(cases),
        "seed": seed,
        "unique_case_ids": len({str(row["id"]) for row in cases}),
        "unique_prompt_fingerprints": len({str(row["prompt_fingerprint"]) for row in cases}),
        "agent_counts": dict(sorted(Counter(str(row["agent"]) for row in cases).items())),
        "chapter_counts": dict(sorted(Counter(str(row["chapter"]) for row in cases).items(), key=lambda item: int(item[0]))),
        "prompt_family_counts": dict(sorted(Counter(str(row["prompt_family_id"]) for row in cases).items())),
        "eval_slice_counts": dict(sorted(Counter(str(row["eval_slice"]) for row in cases).items())),
        "dataset_sha256": stable_fingerprint(list(cases)),
        "message_zh": f"已构造并冻结 {len(cases)} 条 fresh-full5000-v2 独立评测案例",
    }


def _all_ids(rows: Sequence[Mapping[str, Any]]) -> set[str]:
    result: set[str] = set()
    for row in rows:
        for key in ("id", "case_id", "source_case_id", "base_source_case_id", "lineage_root_id"):
            value = row.get(key)
            if isinstance(value, str) and value:
                result.add(value)
        metadata = row.get("metadata")
        if isinstance(metadata, Mapping):
            for key in ("source_case_id", "base_source_case_id", "lineage_root_id"):
                value = metadata.get(key)
                if isinstance(value, str) and value:
                    result.add(value)
    return result


def _prompt_fingerprint(row: Mapping[str, Any]) -> str | None:
    direct = row.get("prompt_fingerprint")
    if isinstance(direct, str) and direct:
        return direct
    system, user = _extract_system_user(row)
    if not system and not user:
        return None
    return stable_fingerprint({"system": system, "user": user})


def _extract_system_user(row: Mapping[str, Any]) -> tuple[str, str]:
    system = str(row.get("system") or "")
    user = str(row.get("user") or row.get("student_input") or "")
    messages = row.get("messages") or row.get("prompt")
    if isinstance(messages, Sequence) and not isinstance(messages, (str, bytes)):
        system = "\n".join(
            str(item.get("content") or "")
            for item in messages
            if isinstance(item, Mapping) and item.get("role") == "system"
        )
        user = "\n".join(
            str(item.get("content") or "")
            for item in messages
            if isinstance(item, Mapping) and item.get("role") == "user"
        )
    return system, user


def _template_id(row: Mapping[str, Any]) -> str | None:
    for key in ("prompt_family_id", "template_family"):
        value = row.get(key)
        if isinstance(value, str) and value:
            return value
    lineage = row.get("source_lineage")
    if isinstance(lineage, Mapping):
        value = lineage.get("template_family")
        if isinstance(value, str) and value:
            return value
    metadata = row.get("metadata")
    if isinstance(metadata, Mapping):
        value = metadata.get("template_family")
        if isinstance(value, str) and value:
            return value
    return None


def _read_rows(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        raise FileNotFoundError(f"历史数据文件不存在：{path}")
    if path.suffix == ".jsonl":
        return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    value = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(value, list):
        return [dict(row) for row in value]
    if isinstance(value, Mapping):
        for key in ("rows", "cases", "items"):
            rows = value.get(key)
            if isinstance(rows, list):
                return [dict(row) for row in rows]
    raise ValueError(f"无法从历史数据文件读取行：{path}")


def _atomic_write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(content, encoding="utf-8")
    temporary.replace(path)
