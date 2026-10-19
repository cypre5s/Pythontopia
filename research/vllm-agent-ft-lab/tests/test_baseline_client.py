from pathlib import Path
import subprocess
import sys
import urllib.error
import urllib.request

import pytest

from pyt_agent_lab.baseline import (
    OpenAICompatClient,
    ResponseJsonlWriter,
    build_chat_payload,
    extract_message_text,
    load_completed_response_case_ids,
    run_cases,
)
from pyt_agent_lab.experiment_logging import ExperimentLogger


def test_build_chat_payload_disables_qwen_thinking_by_default() -> None:
    payload = build_chat_payload("qwen3", [{"role": "user", "content": "hi"}], max_tokens=32)

    assert payload["chat_template_kwargs"] == {"enable_thinking": False}


def test_build_chat_payload_can_disable_thinking_for_teacher_model() -> None:
    payload = build_chat_payload(
        "deepseek-v4-pro",
        [{"role": "user", "content": "只输出 JSON"}],
        enable_thinking=False,
    )

    assert payload["chat_template_kwargs"] == {"enable_thinking": False}


def test_build_chat_payload_can_request_strict_json_schema() -> None:
    response_format = {
        "type": "json_schema",
        "json_schema": {"name": "IR", "schema": {"type": "object"}, "strict": True},
    }

    payload = build_chat_payload(
        "qwen3", [{"role": "user", "content": "hi"}], response_format=response_format
    )

    assert payload["response_format"] == response_format


def test_extract_message_text_falls_back_to_reasoning() -> None:
    assert extract_message_text({"content": None, "reasoning": "Thinking only"}) == "Thinking only"
    assert extract_message_text({"content": "Final", "reasoning": "Thinking"}) == "Final"


@pytest.mark.parametrize(
    "base_url",
    [
        "http://api.deepseek.com/v1",
        "https://example.test/v1",
        "https://api.deepseek.com:8443/v1",
        "https://user:password@api.deepseek.com/v1",
    ],
)
def test_openai_compat_client_rejects_non_deepseek_endpoints(base_url: str) -> None:
    with pytest.raises(RuntimeError, match="DeepSeek base URL"):
        OpenAICompatClient(base_url, "token", "deepseek-flash")


def test_openai_compat_client_rejects_non_allowlisted_models() -> None:
    with pytest.raises(RuntimeError, match="Unsupported DeepSeek model"):
        OpenAICompatClient("https://api.deepseek.com/v1", "token", "qwen3")


def test_openai_compat_client_does_not_follow_redirects() -> None:
    client = OpenAICompatClient("https://api.deepseek.com/v1", "token", "deepseek-flash")
    redirect_handler = next(
        handler
        for handler in client._opener.handlers
        if isinstance(handler, urllib.request.HTTPRedirectHandler)
    )

    redirected_request = redirect_handler.redirect_request(
        urllib.request.Request("https://api.deepseek.com/v1/chat/completions"),
        None,
        307,
        "Temporary Redirect",
        {"Location": "https://unexpected.example"},
        "https://unexpected.example",
    )

    assert redirected_request is None


@pytest.mark.parametrize(
    ("script", "secret_flags"),
    [
        ("run_baseline.py", ("--api-key",)),
        ("run_abc_eval.py", ("--a-api-key", "--b-api-key")),
        ("label_rsage_teacher_votes.py", ("--api-key",)),
        ("run_graph_route_ab.py", ("--api-key", "--internal-token")),
        ("diagnose_graph_agent_prompts.py", ("--api-key",)),
    ],
)
def test_live_research_clis_do_not_accept_api_keys_in_argv(
    script: str, secret_flags: tuple[str, ...]
) -> None:
    script_path = Path(__file__).parents[1] / "scripts" / script
    command = [sys.executable]
    if script in {"run_graph_route_ab.py", "diagnose_graph_agent_prompts.py"}:
        # Help must remain usable without installing the live Graph runtime dependencies.
        command.append("-S")
    result = subprocess.run(
        [*command, str(script_path), "--help"],
        check=True,
        capture_output=True,
        text=True,
    )

    for secret_flag in secret_flags:
        assert secret_flag not in result.stdout


def test_openai_compat_client_retries_transient_url_errors(monkeypatch) -> None:
    calls = 0

    class _Response:
        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, tb) -> None:
            return None

        def read(self) -> bytes:
            return b'{"choices":[{"message":{"content":"{\\"ok\\": true}"}}]}'

    class _Opener:
        def open(self, req, timeout):
            nonlocal calls
            calls += 1
            if calls == 1:
                raise urllib.error.URLError(ConnectionRefusedError("refused once"))
            return _Response()

    def fake_build_opener(*input_handlers):
        return _Opener()

    monkeypatch.setattr("urllib.request.build_opener", fake_build_opener)
    client = OpenAICompatClient(
        "https://api.deepseek.com/v1", "token", "deepseek-flash", retries=2, retry_sleep_seconds=0
    )

    assert client.chat([{"role": "user", "content": "hi"}]) == '{"ok": true}'
    assert calls == 2


def test_openai_compat_client_uses_proxy_free_opener(monkeypatch) -> None:
    handlers = []

    class _Opener:
        def open(self, req, data=None, timeout=None):
            class _Response:
                def __enter__(self):
                    return self

                def __exit__(self, exc_type, exc, tb) -> None:
                    return None

                def read(self) -> bytes:
                    return b'{"choices":[{"message":{"content":"{\\"ok\\": true}"}}]}'

            return _Response()

    def fake_build_opener(*input_handlers):
        handlers.extend(input_handlers)
        return _Opener()

    monkeypatch.setattr("urllib.request.build_opener", fake_build_opener)

    client = OpenAICompatClient("https://api.deepseek.com/v1", "token", "deepseek-flash")

    assert client.chat([{"role": "user", "content": "hi"}]) == '{"ok": true}'
    assert any(getattr(handler, "proxies", None) == {} for handler in handlers)


def test_openai_compat_client_can_use_explicit_https_proxy(monkeypatch) -> None:
    handlers = []

    class _Opener:
        def open(self, req, data=None, timeout=None):
            class _Response:
                def __enter__(self):
                    return self

                def __exit__(self, exc_type, exc, tb) -> None:
                    return None

                def read(self) -> bytes:
                    return b'{"choices":[{"message":{"content":"{\\"ok\\": true}"}}]}'

            return _Response()

    def fake_build_opener(*input_handlers):
        handlers.extend(input_handlers)
        return _Opener()

    monkeypatch.setattr("urllib.request.build_opener", fake_build_opener)

    client = OpenAICompatClient(
        "https://api.deepseek.com/v1",
        "token",
        "deepseek-v4-pro",
        proxy_url="http://127.0.0.1:7892",
    )

    assert client.chat([{"role": "user", "content": "hi"}]) == '{"ok": true}'
    assert any(
        getattr(handler, "proxies", None)
        == {"http": "http://127.0.0.1:7892", "https": "http://127.0.0.1:7892"}
        for handler in handlers
    )


def test_run_cases_can_use_bounded_concurrency(tmp_path: Path) -> None:
    cases = [
        {
            "id": f"case-{index}",
            "agent": "TutorAgent",
            "system": "输出 JSON：diagnosis,next_hint,evidence_ids,safety_flags。",
            "user": "围绕变量，必须引用课件证据 page-1-006。",
            "evidence_ids": ["page-1-006"],
            "expected_markers": ["变量", "page-1-006", "next_hint", "evidence_ids"],
            "max_tokens": 64,
        }
        for index in range(6)
    ]
    client = _FakeClient()

    results = run_cases(cases, client, ExperimentLogger(tmp_path), fold=2, concurrency=3)

    assert [result.case["id"] for result in results] == [case["id"] for case in cases]
    assert client.max_in_flight == 3
    assert len((tmp_path / "events.jsonl").read_text(encoding="utf-8").splitlines()) == 12


def test_run_cases_streams_results_to_callback(tmp_path: Path) -> None:
    cases = [_case(index) for index in range(3)]
    client = _FakeClient()
    writer = ResponseJsonlWriter(tmp_path / "responses.jsonl")

    results = run_cases(
        cases,
        client,
        ExperimentLogger(tmp_path),
        fold=0,
        concurrency=2,
        on_result=writer.append,
    )

    completed_ids = load_completed_response_case_ids(tmp_path / "responses.jsonl")
    assert completed_ids == {case["id"] for case in cases}
    assert [result.case["id"] for result in results] == [case["id"] for case in cases]


def test_load_completed_response_case_ids_ignores_partial_bad_lines(tmp_path: Path) -> None:
    path = tmp_path / "responses.jsonl"
    path.write_text(
        '{"case":{"id":"done-1"},"response":"{}"}\n'
        '{"case":{"id":"done-2"},"response":"{}"}\n'
        '{"case":\n',
        encoding="utf-8",
    )

    assert load_completed_response_case_ids(path) == {"done-1", "done-2"}


class _FakeClient:
    def __init__(self) -> None:
        import threading
        import time

        self._lock = threading.Lock()
        self._time = time
        self.in_flight = 0
        self.max_in_flight = 0

    def chat(
        self,
        messages: list[dict[str, str]],
        temperature: float = 0.0,
        max_tokens: int = 512,
        response_format=None,  # noqa: ANN001
    ) -> str:
        with self._lock:
            self.in_flight += 1
            self.max_in_flight = max(self.max_in_flight, self.in_flight)
        self._time.sleep(0.01)
        with self._lock:
            self.in_flight -= 1
        return '{"diagnosis":"变量","next_hint":"看 page-1-006","evidence_ids":["page-1-006"],"safety_flags":[]}'


def _case(index: int) -> dict[str, object]:
    return {
        "id": f"case-{index}",
        "agent": "TutorAgent",
        "system": "输出 JSON：diagnosis,next_hint,evidence_ids,safety_flags。",
        "user": "围绕变量，必须引用课件证据 page-1-006。",
        "evidence_ids": ["page-1-006"],
        "expected_markers": ["变量", "page-1-006", "next_hint", "evidence_ids"],
        "max_tokens": 64,
    }
