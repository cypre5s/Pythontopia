import logging

import httpx
import pytest

from pythontopia_graph import main  # noqa: F401


def test_autogen_message_loggers_do_not_emit_info_content() -> None:
    assert logging.getLogger("autogen_core").level >= logging.WARNING
    assert logging.getLogger("autogen_core.events").level >= logging.WARNING


@pytest.mark.asyncio
async def test_httpx_request_logs_do_not_include_student_query(
    caplog: pytest.LogCaptureFixture,
) -> None:
    query = "PRIVATE_STUDENT_QUERY_HTTPX_16e4"
    caplog.set_level(logging.INFO)
    transport = httpx.MockTransport(lambda _: httpx.Response(200))

    async with httpx.AsyncClient(transport=transport) as client:
        await client.get(
            "http://memory.local/api/learner/memory/brief",
            params={"query": query, "user_id": "7"},
        )

    assert query not in caplog.text
