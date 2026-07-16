from __future__ import annotations

import hashlib
import json
import random
import re
from collections import Counter
from pathlib import Path
from typing import Any, Iterable, Mapping

from pyt_agent_lab.rsage_data import (
    EVENT_INTENTS,
    FAILURE_AXES,
    stable_fingerprint,
    validate_case,
)


_INTENT_PROMPTS = {
    "READ": "我第一次接触“{kc_name}”，应该先读懂哪一个关键概念？",
    "REVIEW": "我学过“{kc_name}”但现在又混淆了，应该先复习哪一步？",
    "TRACE": "我在“{kc_name}”这里看不懂执行过程，变量到底是怎样一步步变化的？",
    "SCAFFOLD": "面对“{kc_name}”练习我不知道如何开始，请只给我第一层支架。",
    "PRACTICE": "我觉得自己理解了“{kc_name}”，想先做一道不直接给答案的小练习。",
    "ASK": "关于“{kc_name}”我还有一个具体疑问，能先帮我澄清问题本身吗？",
    "CHECK": "我已经完成“{kc_name}”相关思路，能先检查方向而不是直接公布答案吗？",
}

_FAILURE_SUFFIXES = {
    "wrong_decision": "我总把下一步学习动作选错，请根据当前状态判断。",
    "weak_semantics": "我只会说“不会”，请把真正要完成的认知动作说具体。",
    "unbound_evidence": "请只依据本次给出的课程页，不要编造页码或教材内容。",
    "answer_leak": "我很着急，但不要直接给可复制提交的完整答案。",
    "over_refusal": "这是正常学习请求，请不要因为谨慎就完全拒绝帮助。",
    "chinese_noisy_input": "wo有点看不dong，变量ming和zhi老是搞混，帮我理一下但别直接公布答案。",
}

_PROFILE_VARIANTS = (
    {"knowledge_base": "beginner", "cognitive_style": "step_by_step", "learning_preference": "example_first"},
    {"knowledge_base": "beginner", "cognitive_style": "visual", "learning_preference": "short_hint"},
    {"knowledge_base": "basic", "cognitive_style": "practice_driven", "learning_preference": "try_then_explain"},
    {"knowledge_base": "basic", "cognitive_style": "conceptual", "learning_preference": "definition_then_case"},
)


def build_rsage_cases(
    course_dir: str | Path,
    *,
    count: int = 420,
    seed: int = 20260716,
    protected_fingerprints: set[str] | None = None,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    if count <= 0:
        raise ValueError("案例数量必须是正整数")
    course_path = Path(course_dir)
    source = _load_course_source(course_path)
    rng = random.Random(seed)
    chapter_kcs = _chapter_kcs(source)
    protected = protected_fingerprints or set()
    cases: list[dict[str, Any]] = []
    seen_fingerprints: set[str] = set()
    excluded = 0

    slots = _coverage_slots(count)
    for slot_index, (event_intent, failure_axis) in enumerate(slots):
        chapter_order = slot_index % 7 + 1
        kcs = chapter_kcs[chapter_order]
        accepted: dict[str, Any] | None = None
        for attempt in range(max(32, len(kcs) * 4)):
            kc_index = (slot_index + attempt + rng.randrange(len(kcs))) % len(kcs)
            kc = kcs[kc_index]
            candidate = _build_case(
                slot_index=slot_index,
                attempt=attempt,
                event_intent=event_intent,
                failure_axis=failure_axis,
                chapter=source["chapters_by_order"][chapter_order],
                kc=kc,
                pages_by_id=source["pages_by_id"],
                seed=seed,
            )
            fingerprint = case_prompt_fingerprint(candidate)
            if fingerprint in protected or fingerprint in seen_fingerprints:
                excluded += 1
                continue
            result = validate_case(candidate)
            if not result.passed:
                raise ValueError(f"自动案例未通过数据契约：{'；'.join(result.errors)}")
            candidate["prompt_fingerprint"] = fingerprint
            accepted = candidate
            seen_fingerprints.add(fingerprint)
            break
        if accepted is None:
            raise RuntimeError(f"无法为第 {slot_index + 1} 个槽位找到非保护且不重复的案例")
        cases.append(accepted)

    pair_counts = Counter(f"{row['event_intent']}::{row['failure_axis']}" for row in cases)
    chapter_counts = Counter(str(row["chapter_id"]) for row in cases)
    manifest = {
        "format": "rsage_case_jsonl_v1",
        "pack_id": 43,
        "seed": seed,
        "total_count": len(cases),
        "intent_failure_counts": dict(sorted(pair_counts.items())),
        "chapter_counts": dict(sorted(chapter_counts.items(), key=lambda item: int(item[0]))),
        "protected_exclusion_count": excluded,
        "protected_fingerprint_count": len(protected),
        "dataset_sha256": stable_fingerprint(cases),
        "source_hashes": source["source_hashes"],
        "message_zh": f"已构造 {len(cases)} 条 R-SAGE-IR 非保护训练候选案例",
    }
    return cases, manifest


def case_prompt_fingerprint(case: Mapping[str, Any]) -> str:
    prompt = _extract_prompt(case)
    normalized = re.sub(r"\s+", " ", prompt).strip().lower()
    return stable_fingerprint(
        {
            "prompt": normalized,
            "kc_code": str(case.get("kc_code") or ""),
            "evidence_ids": sorted(
                str(item.get("evidence_id"))
                for item in case.get("evidence_refs", [])
                if isinstance(item, Mapping) and item.get("evidence_id")
            ),
        }
    )


def write_rsage_cases(
    cases: list[dict[str, Any]],
    manifest: Mapping[str, Any],
    cases_path: str | Path,
    manifest_path: str | Path,
) -> dict[str, Any]:
    target_cases = Path(cases_path)
    target_manifest = Path(manifest_path)
    target_cases.parent.mkdir(parents=True, exist_ok=True)
    target_manifest.parent.mkdir(parents=True, exist_ok=True)
    payload = "".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in cases)
    written_manifest = dict(manifest)
    written_manifest["dataset_sha256"] = stable_fingerprint(cases)
    written_manifest["jsonl_sha256"] = hashlib.sha256(payload.encode("utf-8")).hexdigest()
    _atomic_write_text(target_cases, payload)
    _atomic_write_text(
        target_manifest,
        json.dumps(written_manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
    )
    return written_manifest


def load_protected_fingerprints(paths: Iterable[str | Path]) -> set[str]:
    fingerprints: set[str] = set()
    for raw_path in paths:
        path = Path(raw_path)
        if not path.exists():
            raise FileNotFoundError(f"保护集文件不存在：{path}")
        rows = _read_json_rows(path)
        for row in rows:
            direct = row.get("prompt_fingerprint")
            if isinstance(direct, str) and len(direct) == 64:
                fingerprints.add(direct)
            else:
                fingerprints.add(case_prompt_fingerprint(row))
    return fingerprints


def _coverage_slots(count: int) -> list[tuple[str, str]]:
    combinations = [(intent, axis) for intent in EVENT_INTENTS for axis in FAILURE_AXES]
    if count == 420:
        return [pair for pair in combinations for _ in range(10)]
    return [combinations[index % len(combinations)] for index in range(count)]


def _load_course_source(course_dir: Path) -> dict[str, Any]:
    json_dir = course_dir / "json"
    paths = {
        "chapters": json_dir / "course_chapter.json",
        "kcs": json_dir / "course_kc.json",
        "pages": json_dir / "course_page.json",
    }
    rows = {name: json.loads(path.read_text(encoding="utf-8")) for name, path in paths.items()}
    if len(rows["chapters"]) != 7 or len(rows["kcs"]) != 65 or len(rows["pages"]) != 561:
        raise ValueError("pack43 课程规模校验失败：必须是 7 章、65 个 KC、561 页")
    if any(int(item.get("pack_id", -1)) != 43 for values in rows.values() for item in values):
        raise ValueError("课程数据中发现 pack_id 不为 43 的记录")
    chapters_by_order = {int(item["order_index"]): item for item in rows["chapters"]}
    return {
        **rows,
        "chapters_by_order": chapters_by_order,
        "pages_by_id": {int(item["id"]): item for item in rows["pages"]},
        "source_hashes": {
            name: hashlib.sha256(path.read_bytes()).hexdigest()
            for name, path in paths.items()
        },
    }


def _chapter_kcs(source: Mapping[str, Any]) -> dict[int, list[dict[str, Any]]]:
    order_by_id = {
        int(item["id"]): int(item["order_index"])
        for item in source["chapters"]
    }
    result: dict[int, list[dict[str, Any]]] = {index: [] for index in range(1, 8)}
    pages_by_id = source["pages_by_id"]
    for kc in source["kcs"]:
        page_refs = [int(item) for item in kc.get("page_refs", [])]
        if not any(page_id in pages_by_id and _page_excerpt(pages_by_id[page_id]) for page_id in page_refs):
            continue
        result[order_by_id[int(kc["chapter_id"])]].append(kc)
    for chapter_order, rows in result.items():
        rows.sort(key=lambda item: str(item["kc_code"]))
        if not rows:
            raise ValueError(f"第 {chapter_order} 章没有可绑定课程页的 KC")
    return result


def _build_case(
    *,
    slot_index: int,
    attempt: int,
    event_intent: str,
    failure_axis: str,
    chapter: Mapping[str, Any],
    kc: Mapping[str, Any],
    pages_by_id: Mapping[int, Mapping[str, Any]],
    seed: int,
) -> dict[str, Any]:
    page_rows = [
        pages_by_id[int(page_id)]
        for page_id in kc.get("page_refs", [])
        if int(page_id) in pages_by_id and _page_excerpt(pages_by_id[int(page_id)])
    ][:2]
    evidence_refs = [
        {
            "evidence_id": f"pack43-page-{page['id']}",
            "page_id": int(page["id"]),
            "document_id": int(page["document_id"]),
            "page_no": int(page["page_no"]),
            "title": str(page.get("title") or "课程页"),
            "excerpt": _page_excerpt(page)[:900],
            "text_hash": str(page.get("text_hash") or ""),
        }
        for page in page_rows
    ]
    base = _INTENT_PROMPTS[event_intent].format(kc_name=kc["kc_name"])
    suffix = _FAILURE_SUFFIXES[failure_axis]
    variation = "" if attempt == 0 else f" 这是我的第 {attempt + 1} 次重新描述。"
    return {
        "case_id": f"rsage-{slot_index + 1:04d}-{event_intent.lower()}-{failure_axis}",
        "format": "rsage_teacher_case_v1",
        "pack_id": 43,
        "chapter_id": int(chapter["order_index"]),
        "chapter_db_id": int(chapter["id"]),
        "chapter_title": str(chapter["title"]),
        "kc_code": str(kc["kc_code"]),
        "kc_name": str(kc["kc_name"]),
        "kc_summary": str(kc.get("summary_markdown") or "")[:1200],
        "event_intent": event_intent,
        "failure_axis": failure_axis,
        "student_input": f"{base}{suffix}{variation}",
        "learner_profile": dict(_PROFILE_VARIANTS[(slot_index + attempt) % len(_PROFILE_VARIANTS)]),
        "evidence_refs": evidence_refs,
        "source": "pack43_teacher_vote_candidate",
        "source_lineage": {
            "course_pack": 43,
            "kc_id": int(kc["id"]),
            "generation_seed": seed,
            "template_family": f"{event_intent.lower()}::{failure_axis}",
        },
    }


def _page_excerpt(page: Mapping[str, Any]) -> str:
    return str(page.get("excerpt") or page.get("content_markdown") or "").strip()


def _extract_prompt(case: Mapping[str, Any]) -> str:
    for key in ("student_input", "user_prompt", "prompt"):
        value = case.get(key)
        if isinstance(value, str) and value.strip():
            return value
    messages = case.get("messages")
    if isinstance(messages, list):
        return "\n".join(
            str(item.get("content") or "")
            for item in messages
            if isinstance(item, Mapping) and item.get("role") == "user"
        )
    return str(case.get("case_id") or "")


def _read_json_rows(path: Path) -> list[dict[str, Any]]:
    if path.suffix == ".jsonl":
        return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    value = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(value, list):
        return [dict(item) for item in value]
    if isinstance(value, dict):
        for key in ("cases", "rows", "items"):
            if isinstance(value.get(key), list):
                return [dict(item) for item in value[key]]
        return [dict(value)]
    raise ValueError(f"无法从保护集文件读取案例：{path}")


def _atomic_write_text(path: Path, content: str) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(content, encoding="utf-8")
    temporary.replace(path)
