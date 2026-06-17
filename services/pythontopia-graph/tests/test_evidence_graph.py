"""GraphRAG 感知证据抓取（Stream G2a-evidence）单元测试。

覆盖契约 §3.3：
- `HttpRagEvidenceFetcher.fetch` 透传 `graph_mode`/`prereq_hops` 进 `/rag/query`；
- `EvidenceBundle` 新增 `route`/`subgraph` 字段并从 RAG 响应解析；
- `StaticEvidenceFetcher` 同步支持新字段，便于兄弟单元（S1/G2b）注入；
- no_evidence 不编造、旧调用向后兼容、业务 fail-fast、参数边界裁剪。

RAG 端实际返回 route/subgraph 由兄弟单元 G1a/G1b 落地；本测试用 mock httpx 解耦。
"""

from __future__ import annotations

import json
from typing import Any, cast

import httpx
import pytest

from pythontopia_graph.nodes.evidence_fetcher import (
    EvidenceBundle,
    GraphMode,
    HttpRagEvidenceFetcher,
    KcEdge,
    RagHit,
    StaticEvidenceFetcher,
)


def _install_mock_rag(
    monkeypatch: pytest.MonkeyPatch,
    *,
    response_json: dict[str, Any] | None = None,
    status_code: int = 200,
) -> list[dict[str, Any]]:
    """把 httpx.AsyncClient 重定向到 MockTransport，并返回累积的请求体列表。

    沿用 test_openai_compat_provider.py 的注入范式（零真实联网）。
    """

    bodies: list[dict[str, Any]] = []
    payload: dict[str, Any] = (
        response_json
        if response_json is not None
        else {"hits": [], "no_evidence_found": True}
    )

    def handler(request: httpx.Request) -> httpx.Response:
        bodies.append(json.loads(request.content))
        return httpx.Response(status_code, json=payload)

    transport = httpx.MockTransport(handler)
    real_init = httpx.AsyncClient.__init__

    def patched_init(self: httpx.AsyncClient, *args: Any, **kwargs: Any) -> None:
        kwargs.setdefault("transport", transport)
        real_init(self, *args, **kwargs)

    monkeypatch.setattr(httpx.AsyncClient, "__init__", patched_init)
    return bodies


def _graph_response() -> dict[str, Any]:
    return {
        "hits": [
            {
                "chunk_id": "c1",
                "page_id": 101,
                "page_no": 47,
                "chapter_id": 7,
                "kc_codes": ["PY43-KC-030"],
                "text": "for 循环用于已知次数的遍历。",
                "score": 0.91,
                "via_prerequisite": False,
            },
            {
                "chunk_id": "c2",
                "page_id": 90,
                "page_no": 30,
                "chapter_id": 5,
                "kc_codes": ["PY43-KC-012"],
                "text": "变量与赋值是循环计数的前置。",
                "score": 0.62,
                "via_prerequisite": True,
            },
        ],
        "no_evidence_found": False,
        "route": "graph",
        "subgraph": [
            {"from_kc": "PY43-KC-030", "to_kc": "PY43-KC-012", "relation": "prerequisite"},
        ],
    }


# --- ① graph_mode / prereq_hops 透传到 RAG 请求 ---------------------------------


async def test_fetch_passes_graph_mode_and_prereq_hops(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    bodies = _install_mock_rag(monkeypatch)
    fetcher = HttpRagEvidenceFetcher(base_url="http://rag.test")

    await fetcher.fetch(
        kc_codes=["PY43-KC-030"],
        query="for 循环怎么写",
        top_k=4,
        graph_mode="force",
        prereq_hops=2,
    )

    assert len(bodies) == 1
    body = bodies[0]
    assert body["graph_mode"] == "force"
    assert body["prereq_hops"] == 2
    assert body["query"] == "for 循环怎么写"
    assert body["top_k"] == 4
    assert body["kc_codes"] == ["PY43-KC-030"]
    # 有 kc_codes 时 min_score=0（既有语义不被破坏）。
    assert body["min_score"] == 0.0


async def test_fetch_defaults_graph_mode_auto_and_hops_one(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    bodies = _install_mock_rag(monkeypatch)
    fetcher = HttpRagEvidenceFetcher(base_url="http://rag.test")

    # 不传新字段：默认 graph_mode=auto / prereq_hops=1，仍写进请求体。
    await fetcher.fetch(kc_codes=[], query="变量", top_k=6)

    body = bodies[0]
    assert body["graph_mode"] == "auto"
    assert body["prereq_hops"] == 1
    # 无 kc_codes 时 min_score=0.15（既有抑噪语义不被破坏）。
    assert body["min_score"] == 0.15


async def test_fetch_clamps_prereq_hops_upper_bound(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    bodies = _install_mock_rag(monkeypatch)
    fetcher = HttpRagEvidenceFetcher(base_url="http://rag.test")

    # 上界 2：超过即裁剪到 2（与 G1 端裁剪语义一致，防越界多跳）。
    await fetcher.fetch(kc_codes=["KC01"], query="q", prereq_hops=5)

    assert bodies[0]["prereq_hops"] == 2


async def test_fetch_clamps_prereq_hops_lower_bound(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    bodies = _install_mock_rag(monkeypatch)
    fetcher = HttpRagEvidenceFetcher(base_url="http://rag.test")

    await fetcher.fetch(kc_codes=["KC01"], query="q", prereq_hops=-3)

    assert bodies[0]["prereq_hops"] == 0


async def test_fetch_rejects_invalid_graph_mode_fail_fast(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    bodies = _install_mock_rag(monkeypatch)
    fetcher = HttpRagEvidenceFetcher(base_url="http://rag.test")

    # 业务 fail-fast：非法枚举值直接抛错，且不发起任何请求。
    with pytest.raises(ValueError, match="graph_mode"):
        await fetcher.fetch(
            kc_codes=["KC01"],
            query="q",
            graph_mode=cast("GraphMode", "bogus"),
        )

    assert bodies == []


# --- ② EvidenceBundle 新增 route/subgraph 字段解析 -----------------------------


async def test_fetch_parses_route_and_subgraph(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _install_mock_rag(monkeypatch, response_json=_graph_response())
    fetcher = HttpRagEvidenceFetcher(base_url="http://rag.test")

    bundle = await fetcher.fetch(
        kc_codes=["PY43-KC-030"], query="for 循环", graph_mode="auto", prereq_hops=2
    )

    assert bundle.route == "graph"
    assert bundle.subgraph == [
        KcEdge(from_kc="PY43-KC-030", to_kc="PY43-KC-012", relation="prerequisite")
    ]
    assert [c.page_id for c in bundle.chunks] == [101, 90]
    # 前置 KC 召回路标记被保留（可审计）。
    assert bundle.chunks[1].via_prerequisite is True
    assert bundle.no_evidence_found is False


async def test_fetch_defaults_route_and_subgraph_when_absent(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # 旧版 RAG 响应不含 route/subgraph：向后兼容，回退 route=vector / 空子图。
    _install_mock_rag(
        monkeypatch,
        response_json={
            "hits": [
                {
                    "chunk_id": "c1",
                    "page_id": 7,
                    "page_no": 7,
                    "kc_codes": ["KC01"],
                    "text": "x",
                    "score": 0.5,
                }
            ],
            "no_evidence_found": False,
        },
    )
    fetcher = HttpRagEvidenceFetcher(base_url="http://rag.test")

    bundle = await fetcher.fetch(kc_codes=["KC01"], query="q")

    assert bundle.route == "vector"
    assert bundle.subgraph == []
    assert len(bundle.chunks) == 1


async def test_fetch_null_route_falls_back_to_vector(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _install_mock_rag(
        monkeypatch,
        response_json={"hits": [], "no_evidence_found": True, "route": None, "subgraph": None},
    )
    fetcher = HttpRagEvidenceFetcher(base_url="http://rag.test")

    bundle = await fetcher.fetch(kc_codes=["KC01"], query="q")

    assert bundle.route == "vector"
    assert bundle.subgraph == []


# --- ④ no_evidence 不编造 ------------------------------------------------------


async def test_fetch_no_evidence_does_not_fabricate(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _install_mock_rag(
        monkeypatch,
        response_json={"hits": [], "no_evidence_found": True, "route": "graph"},
    )
    fetcher = HttpRagEvidenceFetcher(base_url="http://rag.test")

    bundle = await fetcher.fetch(kc_codes=["KC01"], query="q", graph_mode="force")

    assert bundle.no_evidence_found is True
    assert bundle.chunks == []
    # 即便走图路由，无证据时也不得编造子图/证据。
    assert bundle.subgraph == []
    assert bundle.page_ids == []


async def test_fetch_raises_on_http_error_fail_fast(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _install_mock_rag(monkeypatch, status_code=500)
    fetcher = HttpRagEvidenceFetcher(base_url="http://rag.test")

    with pytest.raises(httpx.HTTPStatusError):
        await fetcher.fetch(kc_codes=["KC01"], query="q")


# --- ③ StaticEvidenceFetcher 同步支持新字段（便于兄弟单元注入） ----------------


def _hit(kc: str = "KC01") -> RagHit:
    return RagHit(
        chunk_id="c1", page_id=1, page_no=1, kc_codes=[kc], text="t", score=0.9
    )


async def test_static_fetcher_returns_configured_route_and_subgraph() -> None:
    edge = KcEdge(from_kc="KC07", to_kc="KC12", relation="prerequisite")
    fetcher = StaticEvidenceFetcher([_hit("KC07")], route="graph", subgraph=[edge])

    bundle = await fetcher.fetch(
        kc_codes=["KC07"], query="q", graph_mode="force", prereq_hops=2
    )

    assert bundle.route == "graph"
    assert bundle.subgraph == [edge]
    assert [c.kc_codes for c in bundle.chunks] == [["KC07"]]


async def test_static_fetcher_records_calls_for_passthrough_assertions() -> None:
    fetcher = StaticEvidenceFetcher([_hit()])

    await fetcher.fetch(kc_codes=["KC01"], query="q", graph_mode="force", prereq_hops=2)

    assert len(fetcher.calls) == 1
    call = fetcher.calls[0]
    assert call["graph_mode"] == "force"
    assert call["prereq_hops"] == 2
    assert call["kc_codes"] == ["KC01"]
    assert call["query"] == "q"


async def test_static_fetcher_backward_compatible_defaults() -> None:
    # 旧式构造与旧式调用（不传任何新字段）必须照常工作。
    fetcher = StaticEvidenceFetcher([_hit("KC01")])

    matched = await fetcher.fetch(kc_codes=["KC01"], query="q")
    assert matched.route == "vector"
    assert matched.subgraph == []
    assert len(matched.chunks) == 1

    # ④ 不匹配 → no_evidence，不编造。
    miss = await fetcher.fetch(kc_codes=["NOPE"], query="q")
    assert miss.no_evidence_found is True
    assert miss.chunks == []
    assert miss.subgraph == []


# --- ⑤ 模型层向后兼容 / KcEdge 契约 -------------------------------------------


def test_evidence_bundle_defaults_are_backward_compatible() -> None:
    # 大量既有 agent 直接构造 EvidenceBundle(chunks=..., no_evidence_found=...)。
    bundle = EvidenceBundle(chunks=[_hit()], no_evidence_found=False)
    assert bundle.route == "vector"
    assert bundle.subgraph == []
    assert bundle.page_ids == [1]


def test_kc_edge_fields_and_default_relation() -> None:
    edge = KcEdge(from_kc="KC07", to_kc="KC12", relation="co_occur")
    assert edge.from_kc == "KC07"
    assert edge.to_kc == "KC12"
    assert edge.relation == "co_occur"

    # relation 缺省时取主导关系 prerequisite（不让次级审计字段拖垮主链路解析）。
    defaulted = KcEdge(from_kc="KC07", to_kc="KC12")
    assert defaulted.relation == "prerequisite"
