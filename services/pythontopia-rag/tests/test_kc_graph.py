"""KcGraph（KC 前置关系图）单元测试：扩展/去重/防环/空图退化。"""

from __future__ import annotations

from pythontopia_rag.index import kc_graph
from pythontopia_rag.index.kc_graph import KcGraph, fetch_kc_graph


def graph_of(adjacency: dict[str, list[str]]) -> KcGraph:
    return KcGraph(prerequisites=adjacency)


def test_one_hop_prerequisites_exclude_self() -> None:
    graph = graph_of({"KC-003": ["KC-002"], "KC-002": ["KC-001"]})

    assert graph.prerequisites_of(["KC-003"]) == ["KC-002"]


def test_multi_seed_dedup_and_order_stable() -> None:
    graph = graph_of({"KC-003": ["KC-001"], "KC-004": ["KC-001", "KC-002"]})

    assert graph.prerequisites_of(["KC-003", "KC-004"]) == ["KC-001", "KC-002"]


def test_depth_two_walks_ancestors() -> None:
    graph = graph_of({"KC-003": ["KC-002"], "KC-002": ["KC-001"]})

    assert graph.prerequisites_of(["KC-003"], depth=2) == ["KC-002", "KC-001"]


def test_cycle_does_not_loop_forever() -> None:
    # A→B→A 环：visited 防护下各节点只发现一次。
    graph = graph_of({"KC-A": ["KC-B"], "KC-B": ["KC-A"]})

    assert graph.prerequisites_of(["KC-A"], depth=5) == ["KC-B"]


def test_seed_already_in_input_not_repeated() -> None:
    # 输入同时含 KC 及其前置：前置已在输入集合内则不重复输出。
    graph = graph_of({"KC-002": ["KC-001"]})

    assert graph.prerequisites_of(["KC-002", "KC-001"]) == []


def test_empty_graph_returns_empty() -> None:
    assert KcGraph().prerequisites_of(["KC-001"]) == []


def test_fetch_kc_graph_fail_soft_on_bad_db() -> None:
    graph = fetch_kc_graph("postgresql://nobody:nope@127.0.0.1:1/none")

    assert graph.size == 0
    assert graph.prerequisites_of(["KC-001"]) == []


def test_fetch_kc_graph_does_not_log_database_url(monkeypatch, caplog) -> None:
    secret = "postgresql://student:private-db-password@db.internal/course"
    connect_options: dict[str, object] = {}

    def fail_connect(*args, **kwargs):
        connect_options.update(kwargs)
        raise RuntimeError(f"connection refused for {secret}")

    monkeypatch.setattr(kc_graph.psycopg, "connect", fail_connect)

    graph = fetch_kc_graph(secret)

    assert graph.size == 0
    assert connect_options["connect_timeout"] == kc_graph.DB_CONNECT_TIMEOUT_SECONDS
    assert secret not in caplog.text
    assert "RuntimeError" in caplog.text
