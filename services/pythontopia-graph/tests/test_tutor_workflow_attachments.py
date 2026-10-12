from __future__ import annotations

import json

import httpx
import pytest

from pythontopia_graph.tutor_workflow_attachments import (
    MAX_IMAGE_BYTES,
    fetch_tutor_workflow_attachment,
)
from pythontopia_graph.tutor_workflow_checkpoints import persist_tutor_workflow_checkpoint

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


@pytest.mark.asyncio
async def test_checkpoint_write_uses_authenticated_owner_and_run_scope() -> None:
    seen: list[httpx.Request] = []
    run_id = "2d4c90d4-20f4-4a1b-90ee-e909e5e6d7b5"
    checkpoint = {
        "stage_index": 2,
        "stage_name": "generate_card",
        "team_state": {"agent_states": []},
        "workflow_state": "__pythontopia_tutor_state_v1__:{}",
    }

    def handle(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        return httpx.Response(204)

    await persist_tutor_workflow_checkpoint(
        7,
        "session-key",
        run_id,
        "a" * 64,
        checkpoint,
        base_url="http://backend",
        internal_token="test-only-token",
        transport=httpx.MockTransport(handle),
    )

    request = seen[0]
    assert request.method == "PUT"
    assert request.url.path == (
        "/api/internal/tutor/workflow/sessions/session-key/autogen-checkpoints/" + run_id
    )
    assert request.headers["X-Internal-Token"] == "test-only-token"
    assert request.headers["X-Internal-Subject"] == "user:7"
    assert json.loads(request.read()) == {
        "request_fingerprint": "a" * 64,
        "checkpoint": checkpoint,
    }


@pytest.mark.asyncio
async def test_checkpoint_write_rejects_private_image_state_before_network_io() -> None:
    async def fail_if_called(_request: httpx.Request) -> httpx.Response:
        pytest.fail("checkpoint validation must happen before network I/O")

    with pytest.raises(ValueError, match="cannot contain image payloads"):
        await persist_tutor_workflow_checkpoint(
            7,
            "session-key",
            "2d4c90d4-20f4-4a1b-90ee-e909e5e6d7b5",
            "a" * 64,
            {
                "stage_index": 3,
                "stage_name": "generate_card",
                "team_state": {"private_image_data": b"private bytes"},
                "workflow_state": "state",
            },
            internal_token="test-only-token",
            transport=httpx.MockTransport(fail_if_called),
        )
