"""记忆 brief HTTP 客户端（M2-mem-brief 落地）。

graph 侧从后端 ``GET /api/learner/memory/brief`` 取回时序压缩的 :class:`MemoryBrief`
（后端 M1-mem-backend 按 ``MEMORY_PROVIDER`` 路由 local/mem0/zep 聚合产出），供 S1 在
``understand`` 前注入 ``OrchestratorRequest.context['memory_brief']``（真正接线见 Phase 2）。

红线（§0 / §8.3 / §8.5）——**外部依赖 fail-soft**：后端缺失 / 超时 / 连接失败 / 非 200 /
响应体损坏，一律回退空 ``MemoryBrief()``，绝不抛异常拖垮闭环主链路。记忆是「锦上添花」
的上下文增强，缺省空 brief 时编排向后兼容（契约 §3.2）。

设计沿用 :mod:`pythontopia_graph.nodes.evidence_fetcher`（内部服务调用 ``trust_env=False``）
与 :mod:`pythontopia_graph.observability.otel`（缺依赖即降级、绝不上抛）两处既有范式。
"""

from __future__ import annotations

import logging
import os

import httpx

from pythontopia_graph.memory.contract import MemoryBrief

__all__ = [
    "DEFAULT_BACKEND_URL",
    "DEFAULT_BRIEF_TIMEOUT",
    "fetch_memory_brief",
]

LOG = logging.getLogger(__name__)

# 后端默认地址（4 端共用 backend，:18080，前缀 /api）；经 env PYT_BACKEND_URL 覆盖。
DEFAULT_BACKEND_URL = "http://localhost:18080"
# brief 是非关键增强上下文，超时取较短值，避免拖慢 understand 首响应。
DEFAULT_BRIEF_TIMEOUT = 3.0

_BRIEF_PATH = "/api/learner/memory/brief"
# 服务间内部鉴权头（与 internal_auth.py 同名约定）；仅当 env 提供令牌时附带，
# 缺令牌则不附带——后端若拒绝（非 200）仍走 fail-soft 空 brief。Phase 2 与 M1 对齐。
_INTERNAL_TOKEN_HEADER = "X-Internal-Token"
_INTERNAL_TOKEN_ENV = "PYT_BACKEND_INTERNAL_TOKEN"


def _resolve_base_url(base_url: str | None) -> str:
    """显式参数 > env(PYT_BACKEND_URL) > 默认 :18080；规整末尾斜杠避免双斜杠。"""
    resolved = base_url or os.environ.get("PYT_BACKEND_URL", DEFAULT_BACKEND_URL)
    return resolved.rstrip("/")


def _brief_headers() -> dict[str, str]:
    token = os.environ.get(_INTERNAL_TOKEN_ENV, "").strip()
    return {_INTERNAL_TOKEN_HEADER: token} if token else {}


def _parse_brief(body: object) -> MemoryBrief:
    """把响应体解析为 MemoryBrief；非对象 / 字段类型错 → fail-soft 空 brief。

    缺字段 / 空字段交由契约默认值兜底（``model_validate`` 对额外键默认忽略，向后兼容）。
    """
    if not isinstance(body, dict):
        return MemoryBrief()
    try:
        return MemoryBrief.model_validate(body)
    except Exception:  # noqa: BLE001 - 任何 schema 偏差都回退空 brief，绝不注入半成品
        LOG.warning("memory_brief.parse_failed", exc_info=True)
        return MemoryBrief()


async def fetch_memory_brief(
    user_id: int,
    query: str,
    *,
    timeout: float = DEFAULT_BRIEF_TIMEOUT,
    base_url: str | None = None,
) -> MemoryBrief:
    """取回学习者记忆 brief；任何失败一律 fail-soft 回退空 :class:`MemoryBrief`。

    参数:
        user_id: 学习者 id（随查询串透传，供后端按 user 隔离召回）。
        query: 本轮会话/任务查询，用于时序记忆与画像的相关性召回。
        timeout: HTTP 总超时秒数（默认 :data:`DEFAULT_BRIEF_TIMEOUT`）。
        base_url: 覆盖后端基址；缺省取 env ``PYT_BACKEND_URL`` 再退默认 :18080。

    返回:
        解析成功的 :class:`MemoryBrief`；否则空 brief（不抛异常）。
    """
    url = f"{_resolve_base_url(base_url)}{_BRIEF_PATH}"
    params = {"query": query, "user_id": str(user_id)}
    try:
        # trust_env=False：内部服务直连，不经宿主代理（沿用 HttpRagEvidenceFetcher 范式）。
        async with httpx.AsyncClient(timeout=timeout, trust_env=False) as client:
            response = await client.get(url, params=params, headers=_brief_headers())
        if response.status_code != httpx.codes.OK:
            LOG.info(
                "memory_brief.non_ok status=%s user_id=%s",
                response.status_code,
                user_id,
            )
            return MemoryBrief()
        return _parse_brief(response.json())
    except Exception as exc:  # noqa: BLE001 - 外部依赖 fail-soft：超时/断连/损坏统统回退空 brief
        LOG.warning("memory_brief.fetch_failed user_id=%s error=%s", user_id, exc)
        return MemoryBrief()
