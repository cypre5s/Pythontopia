"""服务间内部鉴权：当 PYT_RAG_ADMIN_TOKEN 已配置时校验 X-Internal-Token。"""
from __future__ import annotations

import os

from fastapi import Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.responses import Response

INTERNAL_TOKEN_HEADER = "X-Internal-Token"
PUBLIC_PATHS = frozenset({"/healthz", "/docs", "/openapi.json", "/redoc"})


def expected_internal_token() -> str:
    return os.environ.get("PYT_RAG_ADMIN_TOKEN", "").strip()


class InternalTokenMiddleware(BaseHTTPMiddleware):
    async def dispatch(
        self, request: Request, call_next: RequestResponseEndpoint
    ) -> Response:
        expected = expected_internal_token()
        if not expected or request.url.path in PUBLIC_PATHS:
            return await call_next(request)
        provided = request.headers.get(INTERNAL_TOKEN_HEADER)
        if provided != expected:
            return JSONResponse(
                status_code=401,
                content={"detail": "invalid token"},
            )
        return await call_next(request)
