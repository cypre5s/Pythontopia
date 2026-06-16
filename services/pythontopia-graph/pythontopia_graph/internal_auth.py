"""服务间内部鉴权：校验 X-Internal-Token。

安全默认 **fail-closed**：未配置 ``PYT_RAG_ADMIN_TOKEN`` 时，除 ``PUBLIC_PATHS`` 外一律拒绝（503），
避免生产误配下 graph 服务（含全部 LLM Agent）「裸奔」被白嫖/注入/当内网跳板。本地开发或测试如需在
无令牌下放行，必须显式设置 ``PYT_INTERNAL_AUTH_OPTIONAL=1``（仅限非生产）。令牌比较使用常量时间，避免时序侧信道。
"""
from __future__ import annotations

import hmac
import logging
import os

from fastapi import Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.responses import Response

logger = logging.getLogger(__name__)

INTERNAL_TOKEN_HEADER = "X-Internal-Token"
PUBLIC_PATHS = frozenset({"/healthz", "/docs", "/openapi.json", "/redoc"})

_TRUTHY = frozenset({"1", "true", "yes", "on"})


def expected_internal_token() -> str:
    return os.environ.get("PYT_RAG_ADMIN_TOKEN", "").strip()


def _auth_optional() -> bool:
    """仅当显式开启时才允许在无令牌下放行（开发/测试逃生舱，绝不用于生产）。"""
    return os.environ.get("PYT_INTERNAL_AUTH_OPTIONAL", "").strip().lower() in _TRUTHY


class InternalTokenMiddleware(BaseHTTPMiddleware):
    async def dispatch(
        self, request: Request, call_next: RequestResponseEndpoint
    ) -> Response:
        if request.url.path in PUBLIC_PATHS:
            return await call_next(request)
        expected = expected_internal_token()
        if not expected:
            # fail-closed：未配置令牌时除非显式声明 dev 放行，否则拒绝服务，绝不裸奔。
            if _auth_optional():
                logger.warning(
                    "PYT_RAG_ADMIN_TOKEN 未配置且 PYT_INTERNAL_AUTH_OPTIONAL 已开启："
                    "服务间鉴权放行（仅限本地开发，切勿用于生产）。"
                )
                return await call_next(request)
            logger.error(
                "拒绝 %s：PYT_RAG_ADMIN_TOKEN 未配置。生产必须配置内部令牌；"
                "本地开发如需放行请设 PYT_INTERNAL_AUTH_OPTIONAL=1。",
                request.url.path,
            )
            return JSONResponse(
                status_code=503,
                content={"detail": "internal auth not configured"},
            )
        provided = request.headers.get(INTERNAL_TOKEN_HEADER, "")
        if not hmac.compare_digest(provided, expected):
            return JSONResponse(
                status_code=401,
                content={"detail": "invalid token"},
            )
        return await call_next(request)
