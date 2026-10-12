from __future__ import annotations

import httpx
import pytest

from pythontopia_graph.tutor_workflow_attachments import (
    MAX_IMAGE_BYTES,
    fetch_tutor_workflow_attachment,
)

ATTACHMENT_ID = "e780cb26-4b20-4e69-a043-750eeddf4051"
PNG = b"\x89PNG\r\n\x1a\n\x00"


@pytest.mark.asyncio
async def test_fetch_uses_authenticated_owner_session_endpoint_and_validates_image() -> None:
    seen: list[httpx.Request] = []

    def handle(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        return httpx.Response(200, headers={"content-type": "image/png"}, content=PNG)

    image = await fetch_tutor_workflow_attachment(
        7,
        "session-key",
        ATTACHMENT_ID,
        base_url="http://backend",
        internal_token="test-only-token",
        transport=httpx.MockTransport(handle),
    )

    request = seen[0]
    assert request.method == "GET"
    assert request.url.path == (
        "/api/internal/tutor/workflow/sessions/session-key/attachments/" + ATTACHMENT_ID
    )
    assert request.headers["X-Internal-Token"] == "test-only-token"
    assert request.headers["X-Internal-Subject"] == "user:7"
    assert image.startswith("data:image/png;base64,")


@pytest.mark.asyncio
async def test_fetch_rejects_fake_mime_and_oversized_response() -> None:
    async def fake_mime(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, headers={"content-type": "image/png"}, content=b"not png")

    with pytest.raises(RuntimeError, match="not a valid image"):
        await fetch_tutor_workflow_attachment(
            7,
            "session-key",
            ATTACHMENT_ID,
            internal_token="test-only-token",
            transport=httpx.MockTransport(fake_mime),
        )

    async def oversized(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            headers={"content-type": "image/png", "content-length": str(MAX_IMAGE_BYTES + 1)},
            content=PNG,
        )

    with pytest.raises(RuntimeError, match="exceeds the size limit"):
        await fetch_tutor_workflow_attachment(
            7,
            "session-key",
            ATTACHMENT_ID,
            internal_token="test-only-token",
            transport=httpx.MockTransport(oversized),
        )


@pytest.mark.asyncio
async def test_fetch_fails_closed_without_internal_token(monkeypatch) -> None:
    monkeypatch.delenv("PYT_BACKEND_INTERNAL_TOKEN", raising=False)
    monkeypatch.delenv("PYT_RAG_ADMIN_TOKEN", raising=False)

    with pytest.raises(RuntimeError, match="authentication is not configured"):
        await fetch_tutor_workflow_attachment(7, "session-key", ATTACHMENT_ID)
