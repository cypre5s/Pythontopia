from __future__ import annotations

import json
from collections import defaultdict, deque
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
EDGE_PATH = REPO_ROOT / "data" / "course-pack-43" / "json" / "course_kc_edge.json"
KC_PATH = REPO_ROOT / "data" / "course-pack-43" / "json" / "course_kc.json"
MIGRATION_PATH = (
    REPO_ROOT
    / "backend"
    / "src"
    / "main"
    / "resources"
    / "db"
    / "migration"
    / "V177__course_kc_edge.sql"
)
SEED_MIGRATION_PATH = (
    REPO_ROOT
    / "backend"
    / "src"
    / "main"
    / "resources"
    / "db"
    / "migration"
    / "V178__course_kc_edge_seed.sql"
)


def _load_edges() -> list[dict[str, object]]:
    return json.loads(EDGE_PATH.read_text(encoding="utf-8"))


def _kc_codes() -> set[str]:
    rows = json.loads(KC_PATH.read_text(encoding="utf-8"))
    return {str(row["kc_code"]) for row in rows}


def test_course_kc_edge_seed_is_typed_and_auditable() -> None:
    edges = _load_edges()
    codes = _kc_codes()

    assert len(edges) >= 180
    relations = {str(edge["relation"]) for edge in edges}
    assert {
        "requires",
        "co_occurs_with",
        "explained_by_page",
        "exemplified_by_example",
        "assessed_by_problem",
    } <= relations

    seen: set[tuple[str, str, str, str, str]] = set()
    for edge in edges:
        key = (
            str(edge["subject_type"]),
            str(edge["subject_id"]),
            str(edge["relation"]),
            str(edge["object_type"]),
            str(edge["object_id"]),
        )
        assert key not in seen
        seen.add(key)
        assert edge["source"]
        assert 0 < float(edge["confidence"]) <= 1
        assert isinstance(edge["evidence_refs"], list)
        if edge["subject_type"] == "kc":
            assert edge["subject_id"] in codes
        if edge["object_type"] == "kc":
            assert edge["object_id"] in codes


def test_requires_edges_are_acyclic_after_database_seed_rules() -> None:
    """The authoritative graph must not reintroduce the V125 linear-fill KC cycle."""
    edges = _load_edges()
    graph: dict[str, list[str]] = defaultdict(list)
    indegree: dict[str, int] = defaultdict(int)
    nodes: set[str] = set()
    for edge in edges:
        if edge["relation"] != "requires":
            continue
        subject = str(edge["subject_id"])
        target = str(edge["object_id"])
        nodes.add(subject)
        nodes.add(target)
        graph[subject].append(target)
        indegree[target] += 1
        indegree.setdefault(subject, indegree[subject])

    ready = deque(sorted(node for node in nodes if indegree[node] == 0))
    visited: list[str] = []
    while ready:
        node = ready.popleft()
        visited.append(node)
        for target in graph[node]:
            indegree[target] -= 1
            if indegree[target] == 0:
                ready.append(target)

    assert len(visited) == len(nodes)


def test_course_kc_edge_migration_creates_and_seeds_edge_table() -> None:
    sql = MIGRATION_PATH.read_text(encoding="utf-8")
    seed_sql = SEED_MIGRATION_PATH.read_text(encoding="utf-8")

    assert "CREATE TABLE IF NOT EXISTS course_kc_edge" in sql
    assert "source VARCHAR(64) NOT NULL" in sql
    assert "confidence NUMERIC" in sql
    assert "INSERT INTO course_kc_edge" not in sql
    assert "INSERT INTO course_kc_edge" in seed_sql
    assert "relation IN (" in sql
