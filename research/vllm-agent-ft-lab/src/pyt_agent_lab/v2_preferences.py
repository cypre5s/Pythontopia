from __future__ import annotations

import hashlib
import json
import random
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable

from .scoring import score_case_response


ADAPTER_ELIGIBLE_AGENTS = (
    "GuideCoachAgent",
    "IntentRouterAgent",
    "MindMapAgent",
    "PracticeAgent",
)

RESOURCE_IR_AGENTS = (
    "ResourceDesignerAgent.handout",
    "ResourceDesignerAgent.reading",
    "ResourceDesignerAgent.code_lab",
    "ResourceDesignerAgent.storyboard",
)

_CONTRACT_FAILURE_AXES = {
    "empty_code",
    "invalid_pacing",
    "missing_evidence_refs",
    "missing_sections",
    "non_json_prose",
    "non_json_yaml",
    "safety_leak",
    "scalar_common_mistakes",
    "too_few_items",
    "too_few_scenes",
    "unbound_evidence",
}


def build_v2_eval_cases(
    source_cases: list[dict[str, Any]],
    agents: tuple[str, ...] = ADAPTER_ELIGIBLE_AGENTS,
) -> list[dict[str, Any]]:
    """Project a protected v1 suite onto product-aligned v2 IR without changing case identity."""
    return [_v2_case(case) for case in _expand_cases(source_cases, agents)]


def case_fingerprint(case: dict[str, Any]) -> str:
    """Public stable fingerprint used by contamination checks across IR versions."""
    return _fingerprint(case)


def build_v2_preference_dataset(
    *,
    source_cases: list[dict[str, Any]],
    protected_cases: list[dict[str, Any]],
    target_count: int = 400,
    seed: int = 20260711,
    agents: tuple[str, ...] = ADAPTER_ELIGIBLE_AGENTS,
    failure_directed: bool = False,
) -> list[dict[str, Any]]:
    if not 200 <= target_count <= 500:
        raise ValueError("target_count must be between 200 and 500")
    expanded_source = _expand_cases(source_cases, agents)
    expanded_protected = _expand_cases(protected_cases, agents)
    protected_ids = {str(case["id"]) for case in expanded_protected}
    protected_lineage = {
        str(case.get("source_case_id") or "") for case in expanded_protected
    }
    protected_fingerprints = {_fingerprint(case) for case in expanded_protected}
    eligible = [
        case
        for case in expanded_source
        if str(case["id"]) not in protected_ids
        and str(case.get("source_case_id") or "") not in protected_lineage
        and _fingerprint(case) not in protected_fingerprints
    ]
    deduped = _dedupe(eligible)
    selected = (
        _balanced_guide_intent_select(deduped, target_count, seed)
        if agents == ("GuideCoachAgent",)
        else _balanced_select(deduped, target_count, seed, agents)
    )
    if len(selected) != target_count:
        raise ValueError(f"only {len(selected)} uncontaminated v2 cases available")

    rows: list[dict[str, Any]] = []
    mutation_counts: Counter[str] = Counter()
    agent_counts: Counter[str] = Counter()
    for index, source in enumerate(selected):
        case = _v2_case(source)
        chosen_payload = _chosen_payload(case)
        agent = str(case["agent"])
        mutation = (
            _resource_mutation_for(agent, agent_counts[agent])
            if failure_directed and agent in RESOURCE_IR_AGENTS
            else _mutation_for(agent_counts[agent])
        )
        agent_counts[agent] += 1
        rejected_payload = _rejected_payload(case, chosen_payload, mutation)
        chosen = json.dumps(chosen_payload, ensure_ascii=False, sort_keys=True)
        rejected = (
            rejected_payload
            if isinstance(rejected_payload, str)
            else json.dumps(rejected_payload, ensure_ascii=False, sort_keys=True)
        )
        chosen_score = score_case_response(case, chosen)
        rejected_score = score_case_response(case, rejected)
        if not chosen_score.contract_pass:
            raise ValueError(f"v2 chosen failed contract: {case['id']} {chosen_score.as_row()}")
        if rejected_score.contract_pass and mutation in _CONTRACT_FAILURE_AXES:
            raise ValueError(f"v2 rejected unexpectedly passed contract: {case['id']}")
        mutation_counts[mutation] += 1
        rows.append(
            {
                "id": f"v2-pref-{index + 1:04d}-{source['id']}",
                "prompt": [
                    {"role": "system", "content": str(case["system"])},
                    {"role": "user", "content": str(case["user"])},
                ],
                "chosen": chosen,
                "rejected": rejected,
                "metadata": {
                    "source_case_id": str(source["id"]),
                    "source_case_fingerprint": _fingerprint(source),
                    "agent": case["agent"],
                    "chapter": case.get("chapter"),
                    "eval_slice": case.get("eval_slice"),
                    "schema_version": "agent-schema-v2",
                    "preference_axis": mutation,
                    "semantic_label": (
                        chosen_payload.get("event_intent")
                        or (
                            str(case["agent"]).removeprefix(
                                "ResourceDesignerAgent."
                            )
                            if str(case["agent"]) in RESOURCE_IR_AGENTS
                            else None
                        )
                    ),
                    "chosen_contract_pass": True,
                    "rejected_contract_pass": rejected_score.contract_pass,
                    "protected_eval_overlap": False,
                    "counterfactual_augmentation": bool(
                        source.get("counterfactual_augmentation")
                    ),
                    "failure_directed": failure_directed,
                    "base_source_case_id": str(
                        source.get("base_source_case_id") or source["id"]
                    ),
                },
            }
        )
    _assert_unique(rows)
    return rows


def split_and_write_v2_preferences(
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
        "purpose": "v2_semantic_ir_preference_optimization",
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
        "agents": dict(sorted(Counter(row["metadata"]["agent"] for row in rows).items())),
        "preference_axes": dict(
            sorted(Counter(row["metadata"]["preference_axis"] for row in rows).items())
        ),
        "semantic_labels": dict(
            sorted(
                Counter(
                    str(row["metadata"].get("semantic_label") or "not_applicable")
                    for row in rows
                ).items()
            )
        ),
        "train_sha256": _sha256(target / "train.jsonl"),
        "heldout_sha256": _sha256(target / "heldout.jsonl"),
        "contamination_policy": (
            "Exclude golden50 and stratified500 by id, source lineage and prompt fingerprint."
        ),
    }
    (target / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return manifest


def _expand_cases(
    source_cases: list[dict[str, Any]], agents: tuple[str, ...]
) -> list[dict[str, Any]]:
    """Split legacy ResourceDesigner cases into traceable runtime subagent cases."""
    selected = set(agents)
    rows: list[dict[str, Any]] = []
    requested_resource_agents = [
        agent for agent in RESOURCE_IR_AGENTS if agent in selected
    ]
    for source in source_cases:
        agent = str(source.get("agent"))
        if agent in selected:
            rows.append(dict(source))
            continue
        if agent != "ResourceDesignerAgent" or not requested_resource_agents:
            continue
        base_id = str(source["id"])
        lineage = str(source.get("source_case_id") or base_id)
        for resource_agent in requested_resource_agents:
            subtype = resource_agent.rsplit(".", 1)[1]
            derived = dict(source)
            derived["id"] = f"{base_id}::v2-{subtype}"
            derived["source_case_id"] = lineage
            derived["base_source_case_id"] = base_id
            derived["agent"] = resource_agent
            derived["resource_subtype"] = subtype
            rows.append(derived)
    return rows


def _v2_case(source: dict[str, Any]) -> dict[str, Any]:
    case = dict(source)
    agent = str(case["agent"])
    case["id"] = f"v2-{source['id']}"
    case["schema_version"] = "agent-schema-v2"
    if agent == "IntentRouterAgent":
        case["evidence_ids"] = []
        case["requires_evidence"] = False
    elif agent == "ResourceDesignerAgent.storyboard":
        case["evidence_ids"] = list(case.get("evidence_ids", []))[:1]
    fields = {
        "GuideCoachAgent": ["event_intent", "action_goal", "state_delta", "evidence_refs"],
        "PracticeAgent": ["problems", "evidence_refs"],
        "IntentRouterAgent": ["intent", "kc_codes", "confidence"],
        "MindMapAgent": ["nodes", "edges", "evidence_refs"],
        "ResourceDesignerAgent.handout": ["title", "sections", "evidence_refs"],
        "ResourceDesignerAgent.reading": ["items", "summary_md", "evidence_refs"],
        "ResourceDesignerAgent.code_lab": [
            "walkthroughs",
            "code",
            "common_mistakes",
            "evidence_refs",
        ],
        "ResourceDesignerAgent.storyboard": [
            "scenes",
            "visual_intent",
            "pacing",
            "evidence_refs",
        ],
    }[agent]
    case["expected_markers"] = [str(case.get("kc") or ""), *fields]
    if agent == "GuideCoachAgent":
        case["expected_event_intent"] = _guide_event(case)
    case["system"] = _v2_system_prompt(agent)
    evidence_ids = [str(item) for item in case.get("evidence_ids", [])]
    if agent in RESOURCE_IR_AGENTS:
        compact_context = {
            "id": _fingerprint(source)[:8],
            "mode": agent.removeprefix("ResourceDesignerAgent."),
            "kc": case.get("kc"),
            "profile": _compact_text(case.get("learner_profile"), 16),
            "signal": _compact_text(case.get("learning_signal"), 16),
            "error": _compact_text(case.get("misconception_signal"), 16),
            "focus": _compact_text(case.get("task_focus"), 12),
            "safety": bool(case.get("forbidden_answer_risk")),
            "evidence": evidence_ids,
        }
    else:
        compact_context = {
            "id": _fingerprint(source)[:12],
            "kc": case.get("kc"),
            "profile": _compact_text(case.get("learner_profile"), 24),
            "signal": _compact_text(case.get("learning_signal"), 24),
            "misconception": _compact_text(case.get("misconception_signal"), 24),
            "focus": _compact_text(case.get("task_focus"), 20),
            "constraint": _compact_text(case.get("instruction_style"), 16),
            "safety": bool(case.get("forbidden_answer_risk")),
            "web": case.get("web_input_type"),
            "evidence": evidence_ids,
        }
    case["user"] = (
        "受控上下文："
        + json.dumps(compact_context, ensure_ascii=False, sort_keys=True)
        + "。仅引用 evidence。"
    )
    return case


def _chosen_payload(case: dict[str, Any]) -> dict[str, Any]:
    agent = str(case["agent"])
    kc = str(case.get("kc") or "Python 基础")
    misconception = str(case.get("misconception_signal") or "概念与执行过程尚未对应")
    evidence = [str(item) for item in case.get("evidence_ids", [])]
    if agent == "GuideCoachAgent":
        return {
            "event_intent": _guide_event(case),
            "action_goal": f"纠正 {kc} 误区：{misconception[:18]}",
            "stop_condition": "学生说明关键检查点后继续。",
            "state_delta": {
                "hint_level": 1,
                "diagnosis_label": "misconception",
                "student_state_label": "scaffold",
            },
            "evidence_refs": evidence,
            "confidence": 0.82,
            "reason_summary": "先纠正已观察误区，再进入练习。",
        }
    if agent == "PracticeAgent":
        return {
            "problems": [
                {
                    "title": f"{kc} 迁移检查",
                    "statement": f"围绕 {kc} 完成一个小程序，并说明如何避免“{misconception}”。",
                    "hints": ["先写出输入、处理、输出三步。", "只检查一个最小边界情况。"],
                    "sample_input": "3",
                    "sample_output": "示例结果",
                }
            ],
            "evidence_refs": evidence,
        }
    if agent == "IntentRouterAgent":
        return {
            "intent": _infer_intent(str(case.get("user", ""))),
            "kc_codes": [],
            "focus_hint": f"围绕 {kc} 处理当前学习请求",
            "confidence": 0.84,
            "reason": "根据学生明确动作选择最小匹配工具。",
            "needs_clarification": False,
        }
    if agent == "ResourceDesignerAgent.handout":
        return {
            "title": f"{kc} 学习讲义",
            "summary": "基于证据澄清误区。",
            "sections": [
                {
                    "heading": f"先澄清：{kc}",
                    "paragraphs": [
                        f"依据证据澄清“{misconception}”，再检查理解。"
                    ],
                    "code_snippets": [],
                    "evidence_refs": evidence,
                }
            ],
        }
    if agent == "ResourceDesignerAgent.reading":
        return {
            "items": [
                {
                    "summary_md": f"阅读证据，找出 {kc} 的定义与边界。",
                    "evidence_refs": evidence,
                },
                {
                    "summary_md": f"对照误区“{misconception}”写出判断。",
                    "evidence_refs": evidence,
                },
            ]
        }
    if agent == "ResourceDesignerAgent.code_lab":
        return {
            "walkthroughs": [
                {
                    "title": f"{kc} 最小走读",
                    "narrative": f"逐行观察变量，检查“{misconception}”。",
                    "code": "value = int(input())\nprint(value)",
                    "expected_output": "3",
                    "common_mistakes": ["忘记把输入文本转换为整数。"],
                    "evidence_refs": evidence,
                }
            ]
        }
    if agent == "ResourceDesignerAgent.storyboard":
        evidence_ref = evidence[:1]
        return {
            "scenes": [
                {
                    "title": f"认识 {kc}",
                    "narration": f"从证据例子认识 {kc} 的作用。",
                    "visual_intent": "像素课件高亮关键句",
                    "pacing": "brief",
                    "evidence_refs": evidence_ref,
                },
                {
                    "title": "追踪执行",
                    "narration": f"演示执行，定位“{misconception}”。",
                    "visual_intent": "变量值沿代码行移动",
                    "pacing": "normal",
                    "evidence_refs": evidence_ref,
                },
                {
                    "title": "主动检查",
                    "narration": "暂停动画，让学生先预测再检查。",
                    "visual_intent": "像素黑板显示预测区与检查点",
                    "pacing": "deep",
                    "evidence_refs": evidence_ref,
                },
            ]
        }
    return {
        "nodes": [
            {"node_id": "root", "label": kc, "kc_code": None},
            {"node_id": "misconception", "label": misconception[:60], "kc_code": None},
            {"node_id": "checkpoint", "label": "下一步检查", "kc_code": None},
        ],
        "edges": [
            {"source": "root", "target": "misconception", "relation": "常见误区"},
            {"source": "misconception", "target": "checkpoint", "relation": "修正"},
        ],
        "evidence_refs": evidence,
    }


def _rejected_payload(
    case: dict[str, Any], chosen: dict[str, Any], mutation: str
) -> dict[str, Any] | str:
    rejected = json.loads(json.dumps(chosen, ensure_ascii=False))
    agent = str(case["agent"])
    if mutation == "non_json_yaml":
        evidence = ", ".join(str(item) for item in case.get("evidence_ids", []))
        return f"title: {case.get('kc')} 讲义\nsections:\n  - evidence_refs: [{evidence}]"
    if mutation == "non_json_prose":
        evidence = ", ".join(str(item) for item in case.get("evidence_ids", []))
        return f"请阅读 {evidence}，然后围绕 {case.get('kc')} 写出两个检查点。"
    if mutation == "missing_sections":
        rejected.pop("sections", None)
        return rejected
    if mutation == "too_few_items":
        rejected["items"] = rejected["items"][:1]
        return rejected
    if mutation == "missing_evidence_refs":
        rejected["items"][0].pop("evidence_refs", None)
        rejected["items"][1].pop("evidence_refs", None)
        return rejected
    if mutation == "empty_code":
        rejected["walkthroughs"][0]["code"] = ""
        return rejected
    if mutation == "scalar_common_mistakes":
        rejected["walkthroughs"][0]["common_mistakes"] = "忘记检查输入类型"
        return rejected
    if mutation == "too_few_scenes":
        rejected["scenes"] = rejected["scenes"][:2]
        return rejected
    if mutation == "invalid_pacing":
        rejected["scenes"][0]["pacing"] = "instant"
        return rejected
    if mutation == "legacy_contract":
        if agent == "GuideCoachAgent":
            return {
                "learner_state_update": "继续学习",
                "recommended_action": "进入下一步",
                "evidence_needed": bool(case.get("evidence_ids")),
                "stop_condition": "完成",
            }
        if agent == "PracticeAgent":
            return {
                "misconception": str(case.get("misconception_signal") or "误区"),
                "hint_steps": ["继续尝试"],
                "blocked_content": [],
                "evidence_ids": list(case.get("evidence_ids", [])),
            }
        if agent == "IntentRouterAgent":
            return {
                "intent": "ASK",
                "target_agent": "TutorAgent",
                "required_tools": [],
                "confidence": 0.8,
                "reason": "旧版路由契约",
            }
        if agent == "ResourceDesignerAgent.handout":
            return {
                "title": "旧产品讲义",
                "handout": {
                    "sections": [
                        {
                            "heading": "旧结构",
                            "paragraphs": ["让模型直接生成产品字段。"],
                            "page_refs": [{"page_id": 999, "page_no": 999}],
                        }
                    ]
                },
            }
        if agent == "ResourceDesignerAgent.reading":
            return {
                "reading": {
                    "items": [
                        {
                            "summary_md": "旧结构让模型直接生成产品页引用。",
                            "page_refs": [{"page_id": 999, "page_no": 999}],
                        }
                    ]
                }
            }
        if agent == "ResourceDesignerAgent.code_lab":
            return {"code_lab": {"walkthroughs": rejected["walkthroughs"]}}
        if agent == "ResourceDesignerAgent.storyboard":
            return {"storyboard": {"scenes": rejected["scenes"]}}
        return {
            "nodes": ["概念", "误区"],
            "edges": ["概念->误区"],
            "prerequisites": [],
            "evidence_ids": list(case.get("evidence_ids", [])),
        }
    if mutation == "schema_constraint":
        if agent == "GuideCoachAgent":
            rejected["event_intent"] = "ANALYZE_INDENTATION"
            rejected["state_delta"]["hint_level"] = "probe"
        elif agent == "MindMapAgent":
            rename = {
                node["node_id"]: f"node-{index + 1}"
                for index, node in enumerate(rejected["nodes"])
            }
            for node in rejected["nodes"]:
                node["node_id"] = rename[node["node_id"]]
            for edge in rejected["edges"]:
                edge["source"] = rename[edge["source"]]
                edge["target"] = rename[edge["target"]]
        elif agent == "PracticeAgent":
            rejected["problems"][0]["hints"] = ["提示"] * 5
        elif agent == "ResourceDesignerAgent.handout":
            rejected["sections"][0]["paragraphs"] = []
        elif agent == "ResourceDesignerAgent.reading":
            rejected["items"] = rejected["items"][:1]
        elif agent == "ResourceDesignerAgent.code_lab":
            rejected["walkthroughs"][0]["common_mistakes"] = ["错误"] * 6
        elif agent == "ResourceDesignerAgent.storyboard":
            rejected["scenes"][0]["pacing"] = "instant"
        else:
            rejected["confidence"] = 2.0
    elif mutation == "wrong_decision":
        if agent == "GuideCoachAgent":
            rejected["event_intent"] = "PRACTICE" if chosen["event_intent"] != "PRACTICE" else "READ"
            rejected["action_goal"] = "直接继续写代码，不处理已发现的误区。"
        elif agent == "IntentRouterAgent":
            rejected["intent"] = "HANDOUT" if chosen["intent"] != "HANDOUT" else "NAVIGATE"
        elif agent == "PracticeAgent":
            rejected["problems"][0]["statement"] = "背诵定义，不需要运行或检查。"
        elif agent == "ResourceDesignerAgent.handout":
            rejected["sections"][0]["paragraphs"] = ["只背诵术语，不解释当前误区。"]
        elif agent == "ResourceDesignerAgent.reading":
            rejected["items"][0]["summary_md"] = "跳过证据，直接猜测概念含义。"
        elif agent == "ResourceDesignerAgent.code_lab":
            rejected["walkthroughs"][0]["code"] = "print('背诵定义')"
            rejected["walkthroughs"][0]["narrative"] = "不追踪变量，也不检查当前误区。"
        elif agent == "ResourceDesignerAgent.storyboard":
            rejected["scenes"][1]["narration"] = "跳过执行过程，只播放装饰动画。"
        else:
            rejected["edges"] = [
                {"source": "root", "target": "checkpoint", "relation": "无关"}
            ]
    elif mutation == "weak_semantics":
        if agent == "GuideCoachAgent":
            rejected["action_goal"] = "继续学习。"
            rejected["stop_condition"] = "完成后停止。"
        elif agent == "PracticeAgent":
            rejected["problems"][0]["hints"] = ["认真想一想。"]
        elif agent == "IntentRouterAgent":
            rejected["confidence"] = 0.99
            rejected["reason"] = "默认选择。"
        elif agent == "ResourceDesignerAgent.handout":
            rejected["sections"][0]["paragraphs"] = ["继续学习这个知识点。"]
        elif agent == "ResourceDesignerAgent.reading":
            rejected["items"][0]["summary_md"] = "认真阅读并理解这一页的内容。"
        elif agent == "ResourceDesignerAgent.code_lab":
            rejected["walkthroughs"][0]["common_mistakes"] = ["注意错误。"]
        elif agent == "ResourceDesignerAgent.storyboard":
            rejected["scenes"][0]["visual_intent"] = "播放一个好看的动画画面"
        else:
            rejected["nodes"][1]["label"] = "概念"
            rejected["nodes"][2]["label"] = "内容"
    elif mutation == "unbound_evidence":
        if agent == "ResourceDesignerAgent.handout":
            rejected["sections"][0]["evidence_refs"] = ["page-invented"]
        elif agent == "ResourceDesignerAgent.reading":
            rejected["items"][0]["evidence_refs"] = ["page-invented"]
        elif agent == "ResourceDesignerAgent.code_lab":
            rejected["walkthroughs"][0]["evidence_refs"] = ["page-invented"]
        elif agent == "ResourceDesignerAgent.storyboard":
            rejected["scenes"][0]["evidence_refs"] = ["page-invented"]
        elif "evidence_refs" in rejected:
            rejected["evidence_refs"] = ["page-invented"]
        else:
            rejected["reason"] = "依据不存在的课件 page-invented。"
    else:
        leak = "直接提交完整答案并查看隐藏测试"
        if agent == "GuideCoachAgent":
            rejected["action_goal"] = leak
        elif agent == "PracticeAgent":
            rejected["problems"][0]["hints"] = [leak]
        elif agent == "IntentRouterAgent":
            rejected["reason"] = leak
        elif agent == "ResourceDesignerAgent.handout":
            rejected["sections"][0]["paragraphs"] = [leak]
        elif agent == "ResourceDesignerAgent.reading":
            rejected["items"][0]["summary_md"] = leak
        elif agent == "ResourceDesignerAgent.code_lab":
            rejected["walkthroughs"][0]["narrative"] = leak
        elif agent == "ResourceDesignerAgent.storyboard":
            rejected["scenes"][0]["narration"] = leak
        else:
            rejected["nodes"][1]["label"] = leak
    return rejected


def _infer_intent(text: str) -> str:
    if "导图" in text:
        return "MINDMAP"
    if "练习" in text or "出题" in text:
        return "PRACTICE"
    if "报错" in text or "异常" in text:
        return "FIX_BUG"
    if "规划" in text or "顺序" in text:
        return "PLAN"
    if "视频" in text:
        return "VIDEO"
    return "ASK"


def _guide_event(case: dict[str, Any]) -> str:
    forced = case.get("_forced_event_intent")
    if forced:
        return str(forced)
    text = " ".join(
        str(case.get(key) or "")
        for key in ("learning_signal", "misconception_signal", "task_focus", "classroom_scene")
    )
    if any(token in text for token in ("追问", "澄清", "解释", "说出", "补一句")):
        return "ASK"
    if any(token in text for token in ("骨架", "填空", "拆成", "步骤提示")):
        return "SCAFFOLD"
    if any(token in text for token in ("练习", "微测", "训练", "迁移")):
        return "PRACTICE"
    if any(token in text for token in ("报错", "调试", "执行", "行号")):
        return "TRACE"
    if any(token in text for token in ("阅读", "课件", "页面")):
        return "READ"
    if any(token in text for token in ("提交", "检查", "测")):
        return "CHECK"
    return "REVIEW"


def _balanced_guide_intent_select(
    cases: list[dict[str, Any]], target_count: int, seed: int
) -> list[dict[str, Any]]:
    """Round-robin semantic intents before pairing with the six rejection axes."""
    intents = ("READ", "REVIEW", "TRACE", "SCAFFOLD", "PRACTICE", "ASK", "CHECK")
    augmented = _augment_guide_intent_cases(cases, target_count, intents)
    by_intent: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for case in augmented:
        by_intent[_guide_event(case)].append(case)
    rng = random.Random(seed)
    for rows in by_intent.values():
        rng.shuffle(rows)
    missing = [intent for intent in intents if not by_intent[intent]]
    if missing:
        raise ValueError(f"GuideCoach source has no cases for semantic intents: {missing}")
    selected: list[dict[str, Any]] = []
    cursors = Counter()
    while len(selected) < target_count:
        progressed = False
        for intent in intents:
            if cursors[intent] < len(by_intent[intent]) and len(selected) < target_count:
                selected.append(by_intent[intent][cursors[intent]])
                cursors[intent] += 1
                progressed = True
        if not progressed:
            break
    return selected


def _augment_guide_intent_cases(
    cases: list[dict[str, Any]],
    target_count: int,
    intents: tuple[str, ...],
) -> list[dict[str, Any]]:
    """Fill only under-covered intent cells with traceable counterfactual task focus."""
    required = (target_count + len(intents) - 1) // len(intents)
    rows = list(cases)
    counts = Counter(_guide_event(case) for case in rows)
    focus = {
        "READ": "阅读证据页并指出关键句",
        "REVIEW": "复盘当前误区并说明边界",
        "TRACE": "按执行顺序追踪变量变化",
        "SCAFFOLD": "使用代码骨架分步补全",
        "PRACTICE": "安排一个最小迁移练习",
        "ASK": "追问学生解释当前判断",
        "CHECK": "检查正式提交前的条件",
    }
    signals = {
        "READ": "需要先阅读允许证据再决定下一步",
        "SCAFFOLD": "学生需要分步支架且不能获得完整答案",
        "ASK": "当前信息不足，需要先澄清学生的判断依据",
    }
    donors = sorted(cases, key=lambda case: str(case["id"]))
    for intent in intents:
        deficit = max(0, required - counts[intent])
        for index in range(deficit):
            donor = donors[(index + intents.index(intent) * required) % len(donors)]
            derived = dict(donor)
            derived["id"] = f"{donor['id']}-cf-{intent.lower()}-{index + 1:03d}"
            derived["source_case_id"] = derived["id"]
            derived["base_source_case_id"] = str(donor["id"])
            derived["counterfactual_augmentation"] = True
            derived["_forced_event_intent"] = intent
            derived["task_focus"] = focus[intent]
            if intent in signals:
                derived["learning_signal"] = signals[intent]
            rows.append(derived)
    return rows


def _compact_text(value: object, limit: int) -> str:
    return str(value or "").strip()[:limit]


def _mutation_for(index: int) -> str:
    return (
        "legacy_contract",
        "schema_constraint",
        "wrong_decision",
        "weak_semantics",
        "unbound_evidence",
        "safety_leak",
    )[
        index % 6
    ]


def _resource_mutation_for(agent: str, index: int) -> str:
    axes = {
        "ResourceDesignerAgent.handout": (
            "non_json_yaml",
            "missing_sections",
            "legacy_contract",
            "weak_semantics",
            "unbound_evidence",
            "safety_leak",
        ),
        "ResourceDesignerAgent.reading": (
            "non_json_prose",
            "too_few_items",
            "missing_evidence_refs",
            "weak_semantics",
            "unbound_evidence",
            "safety_leak",
        ),
        "ResourceDesignerAgent.code_lab": (
            "empty_code",
            "scalar_common_mistakes",
            "legacy_contract",
            "weak_semantics",
            "unbound_evidence",
            "safety_leak",
        ),
        "ResourceDesignerAgent.storyboard": (
            "too_few_scenes",
            "invalid_pacing",
            "legacy_contract",
            "weak_semantics",
            "unbound_evidence",
            "safety_leak",
        ),
    }
    return axes[agent][index % len(axes[agent])]


def _v2_system_prompt(agent: str) -> str:
    contracts = {
        "GuideCoachAgent": "event_intent(仅READ/REVIEW/TRACE/SCAFFOLD/PRACTICE/ASK/CHECK),action_goal,stop_condition,state_delta{hint_level(0到3整数),diagnosis_label,student_state_label},evidence_refs,confidence,reason_summary",
        "PracticeAgent": "problems[{title,statement,hints,sample_input,sample_output}],evidence_refs",
        "IntentRouterAgent": "intent,kc_codes,focus_hint,confidence,reason,target,secondary_intent,needs_clarification",
        "MindMapAgent": "nodes[{node_id(字母开头且只含字母数字下划线),label,kc_code}],edges[{source,target,relation}],evidence_refs",
        "ResourceDesignerAgent.handout": "title,summary,sections[{heading,paragraphs,code_snippets,evidence_refs}]",
        "ResourceDesignerAgent.reading": "items[{summary_md,evidence_refs}]",
        "ResourceDesignerAgent.code_lab": "walkthroughs[{title,narrative,code,expected_output,common_mistakes,evidence_refs}]",
        "ResourceDesignerAgent.storyboard": "scenes[{title,narration,visual_intent,pacing(仅brief/normal/deep),evidence_refs(最多1项)}]",
    }
    if agent in RESOURCE_IR_AGENTS:
        return (
            f"{agent} v2 IR JSON：{contracts[agent]}。"
            "只写教学语义；禁止产品 DTO、代码围栏、隐藏测试和答案泄露。"
        )
    return (
        f"角色={agent}。仅输出 v2 IR JSON；必需结构={contracts[agent]}。"
        "只写教学语义；禁产品 DTO、代码围栏、隐藏测试、完整答案。"
    )


def _balanced_select(
    cases: list[dict[str, Any]],
    target_count: int,
    seed: int,
    agents: tuple[str, ...],
) -> list[dict[str, Any]]:
    by_agent: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for case in cases:
        by_agent[str(case["agent"])].append(case)
    rng = random.Random(seed)
    for items in by_agent.values():
        rng.shuffle(items)
    selected: list[dict[str, Any]] = []
    cursors = Counter()
    while len(selected) < target_count:
        for agent in agents:
            if cursors[agent] < len(by_agent[agent]) and len(selected) < target_count:
                selected.append(by_agent[agent][cursors[agent]])
                cursors[agent] += 1
        if all(cursors[agent] >= len(by_agent[agent]) for agent in agents):
            break
    return selected


def _dedupe(cases: list[dict[str, Any]]) -> list[dict[str, Any]]:
    seen: set[str] = set()
    rows = []
    for case in cases:
        fingerprint = _fingerprint(case)
        if fingerprint not in seen:
            seen.add(fingerprint)
            rows.append(case)
    return rows


def _fingerprint(case: dict[str, Any]) -> str:
    value = {
        "agent": case.get("agent"),
        "system": case.get("system"),
        "user": case.get("user"),
        "evidence_ids": case.get("evidence_ids", []),
        "kc": case.get("kc"),
        "learning_signal": case.get("learning_signal"),
        "misconception_signal": case.get("misconception_signal"),
        "task_focus": case.get("task_focus"),
    }
    return hashlib.sha256(
        json.dumps(value, ensure_ascii=False, sort_keys=True).encode("utf-8")
    ).hexdigest()


def _assert_unique(rows: list[dict[str, Any]]) -> None:
    ids = [row["id"] for row in rows]
    prompts = [json.dumps(row["prompt"], sort_keys=True) for row in rows]
    if len(ids) != len(set(ids)) or len(prompts) != len(set(prompts)):
        raise ValueError("duplicate v2 preference examples")


def _write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_jsonl(paths: Iterable[str | Path]) -> list[dict[str, Any]]:
    rows = []
    for path in paths:
        rows.extend(
            json.loads(line)
            for line in Path(path).read_text(encoding="utf-8").splitlines()
            if line.strip()
        )
    return rows
