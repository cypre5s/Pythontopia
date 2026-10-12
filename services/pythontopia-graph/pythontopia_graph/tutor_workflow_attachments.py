"""Fetch private Tutor images on demand without placing bytes in workflow state."""

from __future__ import annotations

import base64
import os
from uuid import UUID

import httpx

from pythontopia_graph.schemas.tutor import normalize_image_data

DEFAULT_BACKEND_URL = "http://localhost:18080"
MAX_IMAGE_BYTES = 5 * 1024 * 1024
_SUPPORTED_TYPES = frozenset({"image/png", "image/jpeg", "image/gif", "image/webp"})


async def fetch_tutor_workflow_attachment(
    user_id: int,
    session_id: str,
    attachment_id: str,
    *,
    base_url: str | None = None,
    internal_token: str | None = None,
    transport: httpx.AsyncBaseTransport | None = None,
) -> str:
    """Return a validated data URL, holding bytes only in this card-stage call."""
    if user_id < 1:
        raise ValueError("Tutor attachment requires an authenticated user")
    normalized_id = str(UUID(attachment_id))
    token = (
        internal_token
        or os.environ.get("PYT_BACKEND_INTERNAL_TOKEN")
        or os.environ.get("PYT_RAG_ADMIN_TOKEN", "")
    ).strip()
    if not token:
        raise RuntimeError("Tutor attachment service authentication is not configured")
    resolved_base = (base_url or os.environ.get("PYT_BACKEND_URL", DEFAULT_BACKEND_URL)).rstrip("/")
    url = (
        f"{resolved_base}/api/internal/tutor/workflow/sessions/"
        f"{session_id}/attachments/{normalized_id}"
    )
    headers = {
        "X-Internal-Token": token,
        "X-Internal-Subject": f"user:{user_id}",
    }
    async with httpx.AsyncClient(
        timeout=httpx.Timeout(20, connect=3), trust_env=False, transport=transport
    ) as client:
        async with client.stream("GET", url, headers=headers) as response:
            if response.status_code != httpx.codes.OK:
                raise RuntimeError("Tutor attachment is unavailable")
            content_type = response.headers.get("content-type", "").split(";", 1)[0].lower()
            if content_type not in _SUPPORTED_TYPES:
                raise RuntimeError("Tutor attachment has an unsupported media type")
            declared_length = response.headers.get("content-length")
            if declared_length is not None:
                try:
                    if int(declared_length) > MAX_IMAGE_BYTES:
                        raise RuntimeError("Tutor attachment exceeds the size limit")
                except ValueError as exc:
                    raise RuntimeError("Tutor attachment has an invalid size") from exc
            chunks: list[bytes] = []
            total = 0
            async for chunk in response.aiter_bytes():
                total += len(chunk)
                if total > MAX_IMAGE_BYTES:
                    raise RuntimeError("Tutor attachment exceeds the size limit")
                chunks.append(chunk)

    image_bytes = b"".join(chunks)
    if not image_bytes:
        raise RuntimeError("Tutor attachment is empty")
    data_url = f"data:{content_type};base64,{base64.b64encode(image_bytes).decode('ascii')}"
    try:
        return normalize_image_data([data_url])[0]
    except ValueError as exc:
        raise RuntimeError("Tutor attachment content is not a valid image") from exc
