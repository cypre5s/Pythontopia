from pathlib import Path
import urllib.error

from pyt_agent_lab.baseline import OpenAICompatClient, build_chat_payload, extract_message_text, run_cases
from pyt_agent_lab.experiment_logging import ExperimentLogger


def test_build_chat_payload_disables_qwen_thinking_by_default() -> None:
    payload = build_chat_payload("m", [{"role": "user", "content": "hi"}], max_tokens=32)

    assert payload["chat_template_kwargs"] == {"enable_thinking": False}


def test_extract_message_text_falls_back_to_reasoning() -> None:
    assert extract_message_text({"content": None, "reasoning": "Thinking only"}) == "Thinking only"
    assert extract_message_text({"content": "Final", "reasoning": "Thinking"}) == "Final"


def test_openai_compat_client_retries_transient_url_errors(monkeypatch) -> None:
    calls = 0

    class _Response:
        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, tb) -> None:
            return None

        def read(self) -> bytes:
            return b'{"choices":[{"message":{"content":"{\\"ok\\": true}"}}]}'

    def fake_urlopen(req, timeout):
        nonlocal calls
        calls += 1
        if calls == 1:
            raise urllib.error.URLError(ConnectionRefusedError("refused once"))
        return _Response()

    monkeypatch.setattr("urllib.request.urlopen", fake_urlopen)
    client = OpenAICompatClient("http://local/v1", "token", "model", retries=2, retry_sleep_seconds=0)

    assert client.chat([{"role": "user", "content": "hi"}]) == '{"ok": true}'
    assert calls == 2


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


class _FakeClient:
    def __init__(self) -> None:
        import threading
        import time

        self._lock = threading.Lock()
        self._time = time
        self.in_flight = 0
        self.max_in_flight = 0

    def chat(self, messages: list[dict[str, str]], temperature: float = 0.0, max_tokens: int = 512) -> str:
        with self._lock:
            self.in_flight += 1
            self.max_in_flight = max(self.max_in_flight, self.in_flight)
        self._time.sleep(0.01)
        with self._lock:
            self.in_flight -= 1
        return '{"diagnosis":"变量","next_hint":"看 page-1-006","evidence_ids":["page-1-006"],"safety_flags":[]}'
