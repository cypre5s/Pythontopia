"""Persist versioned AutoGen Tutor stage snapshots through the owner-scoped Java API."""

from __future__ import annotations

import os
import re
from collections.abc import Mapping, Sequence
from uuid import UUID

import httpx

DEFAULT_BACKEND_URL = "http://localhost:18080"
_FINGERPRINT = re.compile(r"[0-9a-f]{64}\Z")
_IMAGE_DATA_URI = re.compile(r"data:image/(?:png|jpeg|gif|webp);base64,", re.IGNORECASE)


async def persist_tutor_workflow_checkpoint(
    user_id: int,
    session_id: str,
    run_id: str,
    request_fingerprint: str,
    checkpoint: dict[str, object],
    *,
    base_url: str | None = None,
    internal_token: str | None = None,
    transport: httpx.AsyncBaseTransport | None = None,
) -> None:
    """Persist one completed stage before AutoGen dispatches the next one."""
    if user_id < 1 or not session_id:
        raise ValueError("Tutor checkpoint requires an authenticated session")
    normalized_run_id = str(UUID(run_id))
    if _FINGERPRINT.fullmatch(request_fingerprint) is None:
        raise ValueError("Tutor checkpoint request fingerprint is invalid")
    _validate_checkpoint(checkpoint)

    token = (
        internal_token
        or os.environ.get("PYT_BACKEND_INTERNAL_TOKEN")
        or os.environ.get("PYT_RAG_ADMIN_TOKEN", "")
    ).strip()
    if not token:
        raise RuntimeError("Tutor checkpoint service authentication is not configured")
    resolved_base = (base_url or os.environ.get("PYT_BACKEND_URL", DEFAULT_BACKEND_URL)).rstrip("/")
    url = (
        f"{resolved_base}/api/internal/tutor/workflow/sessions/{session_id}"
        f"/autogen-checkpoints/{normalized_run_id}"
    )
    headers = {
        "X-Internal-Token": token,
        "X-Internal-Subject": f"user:{user_id}",
    }
    async with httpx.AsyncClient(
        timeout=httpx.Timeout(20, connect=3), trust_env=False, transport=transport
    ) as client:
        response = await client.put(
            url,
            headers=headers,
            json={
                "request_fingerprint": request_fingerprint,
                "checkpoint": checkpoint,
            },
        )
    if response.status_code != httpx.codes.NO_CONTENT:
        raise RuntimeError("Tutor AutoGen checkpoint could not be persisted")


def _validate_checkpoint(checkpoint: dict[str, object]) -> None:
    if (
        not isinstance(checkpoint.get("stage_index"), int)
        or isinstance(checkpoint.get("stage_index"), bool)
        or checkpoint["stage_index"] < 0
        or not isinstance(checkpoint.get("stage_name"), str)
        or not isinstance(checkpoint.get("team_state"), dict)
        or not isinstance(checkpoint.get("workflow_state"), str)
    ):
        raise ValueError("Tutor AutoGen checkpoint has an invalid stage state")

    def visit(value: object) -> None:
        if isinstance(value, (bytes, bytearray, memoryview)):
            raise ValueError("Tutor AutoGen checkpoint cannot contain binary image data")
        if isinstance(value, Mapping):
            for key, item in value.items():
                normalized_key = str(key).lower()
                if any(part in normalized_key for part in ("image", "base64", "bytes")):
                    raise ValueError("Tutor AutoGen checkpoint cannot contain image payloads")
                visit(item)
        elif isinstance(value, Sequence) and not isinstance(value, (str, bytes, bytearray)):
            for item in value:
                visit(item)
        elif isinstance(value, str) and _IMAGE_DATA_URI.search(value):
            raise ValueError("Tutor AutoGen checkpoint cannot contain image data URIs")

    visit(checkpoint)
