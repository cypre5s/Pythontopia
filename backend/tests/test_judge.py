import asyncio
import json
import httpx
import pytest
from app.judge import JudgeAdapter, JudgeSubmissionUnknown, JudgeUnavailable


def test_submit_uses_only_whitelisted_runtime_and_records_tokens():
    def handler(request):
        assert request.url.path == "/submissions/batch"
        data = json.loads(request.content)
        assert len(data["submissions"]) == 2
        for item in data["submissions"]:
            assert item["enable_network"] is False
            assert item["language_id"] == 71
            assert "callback_url" not in item and "additional_files" not in item
        return httpx.Response(201, json=[{"token": "a"}, {"token": "b"}])

    adapter = JudgeAdapter("http://judge.test", httpx.MockTransport(handler))
    assert asyncio.run(adapter.submit("print(input())", [{"input": "x", "output": "x"}, {"input": "secret", "expected_output": "secret"}])) == [
        "a",
        "b",
    ]


@pytest.mark.parametrize(
    ("remote_status", "expected"),
    [
        (1, "queued"),
        (2, "running"),
        (3, "accepted"),
        (4, "wrong_answer"),
        (5, "time_limit"),
        (11, "runtime_error"),
        (12, "runtime_error"),
        (13, "infrastructure_error"),
        (14, "runtime_error"),
    ],
)
def test_status_mapping_and_hidden_output_privacy(remote_status, expected):
    def handler(request):
        assert request.method == "GET"
        return httpx.Response(
            200,
            json={
                "submissions": [
                    {"status": {"id": 3}, "stdout": "public"},
                    {"status": {"id": remote_status}, "stdout": "SECRET-INPUT", "stderr": "SECRET-INPUT"},
                ]
            },
        )

    result = asyncio.run(JudgeAdapter("http://judge.test", httpx.MockTransport(handler)).poll(["a", "b"], public_count=1))
    assert result.status == expected
    assert "SECRET" not in json.dumps(result.feedback)
    assert len(result.feedback["samples"]) == 1


def test_memory_failure_is_distinct():
    transport = httpx.MockTransport(lambda _: httpx.Response(200, json={"submissions": [{"status": {"id": 11}, "stderr": "MemoryError"}]}))
    assert asyncio.run(JudgeAdapter("http://judge.test", transport).poll(["x"])).status == "memory_limit"


def test_post_timeout_never_claims_no_submission():
    def handler(request):
        raise httpx.ReadTimeout("response lost", request=request)

    with pytest.raises(JudgeSubmissionUnknown):
        asyncio.run(JudgeAdapter("http://judge.test", httpx.MockTransport(handler)).submit("pass", [{"input": ""}]))


def test_unconfigured_is_explicitly_unavailable():
    with pytest.raises(JudgeUnavailable):
        asyncio.run(JudgeAdapter("").submit("pass", []))


def test_partial_token_batch_requires_reconciliation():
    adapter = JudgeAdapter("http://judge.test", httpx.MockTransport(lambda _: httpx.Response(201, json=[{"token": "a"}, {"error": "invalid"}])))
    with pytest.raises(JudgeSubmissionUnknown):
        asyncio.run(adapter.submit("pass", [{}, {}]))


@pytest.mark.parametrize(
    ("code", "expected_output", "expected_status"),
    [
        ("print('ok')", "ok", "accepted"),
        ("print('wrong')", "ok", "wrong_answer"),
        ("raise RuntimeError('test-runtime')", "ok", "runtime_error"),
        ("while True: pass", "ok", "time_limit"),
        ("x = 'x' * (1024 ** 3)", "ok", "memory_limit"),
    ],
)
def test_live_judge0_acceptance(code, expected_output, expected_status):
    import os

    url = os.getenv("REBORN_LIVE_JUDGE0_URL")
    if not url:
        pytest.skip("Requires explicitly configured independent live Judge0; contract tests are separate")

    async def scenario():
        adapter = JudgeAdapter(url)
        tokens = await adapter.submit(code, [{"input": "", "output": expected_output}])
        for _ in range(35):
            result = await adapter.poll(tokens, public_count=1)
            if result.status not in {"queued", "running"}:
                assert result.status == expected_status, result.feedback
                return
            await asyncio.sleep(1)
        pytest.fail("Live Judge0 did not complete within 35 seconds")

    asyncio.run(scenario())
