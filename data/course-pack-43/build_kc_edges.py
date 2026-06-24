#!/usr/bin/env python3
"""Build auditable typed KC edge seed data for course pack 43."""

from __future__ import annotations

import hashlib
import json
from collections import defaultdict
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent
JSON_DIR = ROOT / "json"
EDGE_JSON = JSON_DIR / "course_kc_edge.json"
SEED_MIGRATION = (
    ROOT.parents[1]
    / "backend"
    / "src"
    / "main"
    / "resources"
    / "db"
    / "migration"
    / "V178__course_kc_edge_seed.sql"
)

PACK_ID = 43

RELATION_REQUIRES = "requires"
RELATION_CO_OCCURS = "co_occurs_with"
RELATION_EXPLAINED_BY_PAGE = "explained_by_page"
RELATION_EXEMPLIFIED_BY_EXAMPLE = "exemplified_by_example"
RELATION_ASSESSED_BY_PROBLEM = "assessed_by_problem"
RELATION_SAME_CHAPTER = "same_chapter"
RELATION_COMMON_MISTAKE = "has_common_mistake"


def load_json(name: str) -> list[dict[str, Any]]:
    return json.loads((JSON_DIR / f"{name}.json").read_text(encoding="utf-8"))


def sql_string(value: Any) -> str:
    return "'" + str(value).replace("'", "''") + "'"


def sql_json(value: Any) -> str:
    return sql_string(json.dumps(value, ensure_ascii=False, sort_keys=True))


class EdgeBuilder:
    def __init__(self) -> None:
        self._edges: dict[tuple[str, str, str, str, str], dict[str, Any]] = {}

    def add(
        self,
        *,
        subject_type: str,
        subject_id: str,
        relation: str,
        object_type: str,
        object_id: str,
        source: str,
        confidence: float,
        evidence_refs: list[dict[str, Any]],
        metadata: dict[str, Any] | None = None,
    ) -> None:
        key = (subject_type, subject_id, relation, object_type, object_id)
        if key in self._edges:
            existing = self._edges[key]
            existing["confidence"] = max(float(existing["confidence"]), confidence)
            existing["evidence_refs"] = _merge_refs(existing["evidence_refs"], evidence_refs)
            return
        self._edges[key] = {
            "pack_id": PACK_ID,
            "subject_type": subject_type,
            "subject_id": subject_id,
            "relation": relation,
            "object_type": object_type,
            "object_id": object_id,
            "source": source,
            "confidence": round(confidence, 4),
            "evidence_refs": evidence_refs,
            "metadata": metadata or {},
        }

    def edges(self) -> list[dict[str, Any]]:
        return sorted(
            self._edges.values(),
            key=lambda edge: (
                edge["subject_type"],
                edge["subject_id"],
                edge["relation"],
                edge["object_type"],
                edge["object_id"],
            ),
        )


def _merge_refs(
    left: list[dict[str, Any]], right: list[dict[str, Any]]
) -> list[dict[str, Any]]:
    keyed = {json.dumps(item, ensure_ascii=False, sort_keys=True): item for item in left}
    for item in right:
        keyed.setdefault(json.dumps(item, ensure_ascii=False, sort_keys=True), item)
    return [keyed[key] for key in sorted(keyed)]


def _json_list(value: Any) -> list[Any]:
    if not value:
        return []
    if isinstance(value, list):
        return value
    if isinstance(value, str):
        try:
            parsed = json.loads(value)
        except json.JSONDecodeError:
            return []
        return parsed if isinstance(parsed, list) else []
    return []


def _mistake_id(problem_id: int, index: int, mistake: str) -> str:
    digest = hashlib.sha1(mistake.encode("utf-8")).hexdigest()[:12]
    return f"problem-{problem_id}-mistake-{index + 1}-{digest}"


def build_edges() -> list[dict[str, Any]]:
    kcs = load_json("course_kc")
    examples = load_json("course_example")
    problems = load_json("course_problem")
    builder = EdgeBuilder()
    kc_by_code = {str(kc["kc_code"]): kc for kc in kcs}
    kc_code_by_id = {int(kc["id"]): str(kc["kc_code"]) for kc in kcs}

    for kc in kcs:
        code = str(kc["kc_code"])
        for prereq in kc.get("prerequisite_codes") or []:
            if prereq not in kc_by_code:
                continue
            builder.add(
                subject_type="kc",
                subject_id=code,
                relation=RELATION_REQUIRES,
                object_type="kc",
                object_id=str(prereq),
                source="alethicode_prerequisite",
                confidence=0.95,
                evidence_refs=[
                    {"type": "kc", "id": code},
                    {"type": "kc", "id": str(prereq)},
                ],
            )
        for page_id in kc.get("page_refs") or []:
            builder.add(
                subject_type="kc",
                subject_id=code,
                relation=RELATION_EXPLAINED_BY_PAGE,
                object_type="page",
                object_id=str(page_id),
                source="course_kc.page_refs",
                confidence=1.0,
                evidence_refs=[{"type": "page", "id": int(page_id)}],
            )

    _add_co_occurrence_edges(kcs, builder)
    _add_same_chapter_edges(kcs, builder)
    _add_example_edges(examples, builder)
    _add_problem_edges(problems, kc_code_by_id, builder)
    return builder.edges()


def _add_co_occurrence_edges(kcs: list[dict[str, Any]], builder: EdgeBuilder) -> None:
    page_to_kcs: dict[int, list[str]] = defaultdict(list)
    for kc in kcs:
        for page_id in kc.get("page_refs") or []:
            page_to_kcs[int(page_id)].append(str(kc["kc_code"]))
    pair_pages: dict[tuple[str, str], list[int]] = defaultdict(list)
    for page_id, codes in page_to_kcs.items():
        unique = sorted(set(codes))
        for index, left in enumerate(unique):
            for right in unique[index + 1 :]:
                pair_pages[(left, right)].append(page_id)
    for (left, right), pages in pair_pages.items():
        confidence = min(0.85, 0.55 + len(pages) * 0.05)
        builder.add(
            subject_type="kc",
            subject_id=left,
            relation=RELATION_CO_OCCURS,
            object_type="kc",
            object_id=right,
            source="course_page.kc_co_occurrence",
            confidence=confidence,
            evidence_refs=[{"type": "page", "id": page_id} for page_id in sorted(pages)],
            metadata={"symmetric": True},
        )


def _add_same_chapter_edges(kcs: list[dict[str, Any]], builder: EdgeBuilder) -> None:
    by_chapter: dict[int, list[dict[str, Any]]] = defaultdict(list)
    for kc in kcs:
        by_chapter[int(kc["chapter_order"])].append(kc)
    for chapter, rows in by_chapter.items():
        ordered = sorted(rows, key=lambda row: str(row["kc_code"]))
        for left, right in zip(ordered, ordered[1:], strict=False):
            builder.add(
                subject_type="kc",
                subject_id=str(left["kc_code"]),
                relation=RELATION_SAME_CHAPTER,
                object_type="kc",
                object_id=str(right["kc_code"]),
                source="course_kc.chapter_order",
                confidence=0.35,
                evidence_refs=[{"type": "chapter", "id": chapter}],
                metadata={"symmetric": True},
            )


def _add_example_edges(examples: list[dict[str, Any]], builder: EdgeBuilder) -> None:
    for example in examples:
        kc_code = str(example.get("kc_code") or "")
        if not kc_code:
            continue
        example_id = int(example["id"])
        builder.add(
            subject_type="kc",
            subject_id=kc_code,
            relation=RELATION_EXEMPLIFIED_BY_EXAMPLE,
            object_type="example",
            object_id=str(example_id),
            source="course_example.kc_code",
            confidence=0.95,
            evidence_refs=[
                {"type": "example", "id": example_id},
                *[
                    {"type": "page", "id": int(page_id)}
                    for page_id in example.get("page_refs") or []
                ],
            ],
        )


def _add_problem_edges(
    problems: list[dict[str, Any]], kc_code_by_id: dict[int, str], builder: EdgeBuilder
) -> None:
    for problem in problems:
        problem_id = int(problem["id"])
        primary = str(problem.get("kc_code") or "")
        if primary:
            _add_problem_assessment(builder, primary, problem_id, "course_problem.kc_code", 0.95)
        related_ids = [int(value) for value in _json_list(problem.get("related_kc_ids_json"))]
        for related_id in related_ids:
            related_code = kc_code_by_id.get(related_id)
            if related_code:
                _add_problem_assessment(
                    builder,
                    related_code,
                    problem_id,
                    "course_problem.related_kc_ids_json",
                    0.85,
                )
        for index, mistake in enumerate(_json_list(problem.get("common_mistakes_json"))):
            if primary and isinstance(mistake, str) and mistake.strip():
                builder.add(
                    subject_type="kc",
                    subject_id=primary,
                    relation=RELATION_COMMON_MISTAKE,
                    object_type="mistake",
                    object_id=_mistake_id(problem_id, index, mistake),
                    source="course_problem.common_mistakes_json",
                    confidence=0.75,
                    evidence_refs=[{"type": "problem", "id": problem_id}],
                    metadata={"text": mistake},
                )


def _add_problem_assessment(
    builder: EdgeBuilder, kc_code: str, problem_id: int, source: str, confidence: float
) -> None:
    builder.add(
        subject_type="kc",
        subject_id=kc_code,
        relation=RELATION_ASSESSED_BY_PROBLEM,
        object_type="problem",
        object_id=str(problem_id),
        source=source,
        confidence=confidence,
        evidence_refs=[{"type": "problem", "id": problem_id}],
    )


def validate_edges(edges: list[dict[str, Any]]) -> None:
    requires = [
        (str(edge["subject_id"]), str(edge["object_id"]))
        for edge in edges
        if edge["relation"] == RELATION_REQUIRES
    ]
    if _has_cycle(requires):
        raise SystemExit("requires graph contains a cycle")


def _has_cycle(edges: list[tuple[str, str]]) -> bool:
    graph: dict[str, list[str]] = defaultdict(list)
    indegree: dict[str, int] = defaultdict(int)
    nodes: set[str] = set()
    for left, right in edges:
        graph[left].append(right)
        indegree[right] += 1
        indegree.setdefault(left, indegree[left])
        nodes.add(left)
        nodes.add(right)
    ready = [node for node in nodes if indegree[node] == 0]
    visited = 0
    while ready:
        node = ready.pop()
        visited += 1
        for target in graph[node]:
            indegree[target] -= 1
            if indegree[target] == 0:
                ready.append(target)
    return visited != len(nodes)


def write_json(edges: list[dict[str, Any]]) -> None:
    EDGE_JSON.write_text(
        json.dumps(edges, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def write_seed_migration(edges: list[dict[str, Any]]) -> None:
    lines = [
        "-- Purpose: seed auditable typed KC knowledge edges for packId=43.",
        "-- Tables affected: course_kc_edge.",
        "-- Irreversible risk: re-running this migration overwrites generated pack 43 edge rows.",
        "",
        "DELETE FROM course_kc_edge WHERE pack_id = 43;",
        "",
    ]
    for edge in edges:
        lines.append(
            "INSERT INTO course_kc_edge (pack_id, subject_type, subject_id, relation, "
            "object_type, object_id, source, confidence, evidence_refs, metadata) VALUES "
            f"({PACK_ID}, {sql_string(edge['subject_type'])}, {sql_string(edge['subject_id'])}, "
            f"{sql_string(edge['relation'])}, {sql_string(edge['object_type'])}, "
            f"{sql_string(edge['object_id'])}, {sql_string(edge['source'])}, "
            f"{edge['confidence']:.4f}, {sql_json(edge['evidence_refs'])}::jsonb, "
            f"{sql_json(edge['metadata'])}::jsonb) "
            "ON CONFLICT (pack_id, subject_type, subject_id, relation, object_type, object_id) "
            "DO UPDATE SET source = EXCLUDED.source, confidence = EXCLUDED.confidence, "
            "evidence_refs = EXCLUDED.evidence_refs, metadata = EXCLUDED.metadata;"
        )
    SEED_MIGRATION.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    edges = build_edges()
    validate_edges(edges)
    write_json(edges)
    write_seed_migration(edges)
    print(f"{EDGE_JSON} edges={len(edges)}")
    print(SEED_MIGRATION)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
