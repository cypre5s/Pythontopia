from __future__ import annotations

import json
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable

from .experiment_logging import ExperimentLogger
from .prompt_variants import apply_prompt_variant
from .scoring import CaseScore, score_case_response


@dataclass(frozen=True)
class BaselineResult:
    case: dict[str, Any]
    response: str
    latency_ms: int
    score: CaseScore


class OpenAICompatClient:
    def __init__(
        self,
        base_url: str,
        api_key: str,
        model: str,
        timeout: int = 180,
        retries: int = 2,
        retry_sleep_seconds: float = 1.0,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.model = model
        self.timeout = timeout
        self.retries = retries
        self.retry_sleep_seconds = retry_sleep_seconds
        self._opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def chat(self, messages: list[dict[str, str]], temperature: float = 0.0, max_tokens: int = 512) -> str:
        payload = build_chat_payload(self.model, messages, temperature=temperature, max_tokens=max_tokens)
        req = urllib.request.Request(
            f"{self.base_url}/chat/completions",
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json", "Authorization": f"Bearer {self.api_key}"},
            method="POST",
        )
        for attempt in range(self.retries + 1):
            try:
                with self._opener.open(req, timeout=self.timeout) as resp:
                    data = json.loads(resp.read().decode("utf-8"))
                break
            except urllib.error.HTTPError as exc:
                body = exc.read().decode("utf-8", errors="replace")
                raise RuntimeError(f"chat completion failed: {exc.code} {body}") from exc
            except urllib.error.URLError:
                if attempt >= self.retries:
                    raise
                time.sleep(self.retry_sleep_seconds)
        return extract_message_text(data["choices"][0]["message"])


def build_chat_payload(
    model: str,
    messages: list[dict[str, str]],
    temperature: float = 0.0,
    max_tokens: int = 512,
) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "model": model,
        "messages": messages,
        "temperature": temperature,
        "max_tokens": max_tokens,
    }
    # Qwen-specific: disable thinking/reasoning tokens
    if "qwen" in model.lower():
        payload["chat_template_kwargs"] = {"enable_thinking": False}
    return payload


def extract_message_text(message: dict[str, Any]) -> str:
    content = message.get("content")
    if content:
        return str(content)
    # Some providers (e.g. DeepSeek) use reasoning_content
    for key in ("reasoning", "reasoning_content"):
        reasoning = message.get(key)
        if reasoning:
            return str(reasoning)
    return ""


def load_cases(path: str | Path) -> list[dict[str, Any]]:
    cases = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if line.strip():
            cases.append(json.loads(line))
    return cases


def build_case_messages(case: dict[str, Any], prompt_variant: str | None = None) -> list[dict[str, str]]:
    patched = apply_prompt_variant(case, prompt_variant)
    return [
        {"role": "system", "content": str(patched["system"])},
        {"role": "user", "content": str(patched["user"])},
    ]


def run_cases(
    cases: list[dict[str, Any]],
    client: OpenAICompatClient,
    logger: ExperimentLogger,
    fold: int | None = None,
    prompt_variant: str | None = None,
    concurrency: int = 1,
    on_result: Callable[[BaselineResult], None] | None = None,
) -> list[BaselineResult]:
    if concurrency <= 1:
        results = []
        for case in cases:
            result = _run_one_case(case, client, logger, fold=fold, prompt_variant=prompt_variant)
            if on_result:
                on_result(result)
            results.append(result)
        return results

    results_by_index: dict[int, BaselineResult] = {}
    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = {
            executor.submit(_run_one_case, case, client, logger, fold=fold, prompt_variant=prompt_variant): index
            for index, case in enumerate(cases)
        }
        for future in as_completed(futures):
            result = future.result()
            if on_result:
                on_result(result)
            results_by_index[futures[future]] = result
    return [results_by_index[index] for index in range(len(cases))]


def _run_one_case(
    case: dict[str, Any],
    client: OpenAICompatClient,
    logger: ExperimentLogger,
    fold: int | None = None,
    prompt_variant: str | None = None,
) -> BaselineResult:
    logger.event("case_started", {"case_id": case["id"], "agent": case["agent"], "fold": fold})
    started = time.perf_counter()
    response = client.chat(
        build_case_messages(case, prompt_variant=prompt_variant),
        max_tokens=int(case.get("max_tokens", 512)),
    )
    latency_ms = int((time.perf_counter() - started) * 1000)
    score = score_case_response(case, response)
    row = {
        **score.as_row(fold=fold),
        **_case_metric_metadata(case),
        "latency_ms": latency_ms,
        "response_chars": len(response),
    }
    logger.metric(row)
    logger.event("case_finished", {"case_id": case["id"], "agent": case["agent"], "fold": fold, "score": score.score})
    return BaselineResult(case, response, latency_ms, score)


def _case_metric_metadata(case: dict[str, Any]) -> dict[str, Any]:
    keys = [
        "chapter",
        "kc",
        "task_type",
        "eval_slice",
        "source_kind",
        "beginner_noise_profile",
        "web_input_type",
        "web_namespace",
        "question_family",
        "chapter_slice_focus",
        "holdout_tag",
    ]
    return {key: case.get(key, "") for key in keys}


def write_results(results: list[BaselineResult], path: str | Path) -> None:
    with Path(path).open("w", encoding="utf-8") as handle:
        for result in results:
            handle.write(result_to_jsonl(result))


class ResponseJsonlWriter:
    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.Lock()

    def append(self, result: BaselineResult) -> None:
        with self._lock:
            with self.path.open("a", encoding="utf-8") as handle:
                handle.write(result_to_jsonl(result))


def load_completed_response_case_ids(path: str | Path) -> set[str]:
    source = Path(path)
    if not source.exists():
        return set()
    completed: set[str] = set()
    for line in source.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        try:
            record = json.loads(line)
        except json.JSONDecodeError:
            continue
        case_id = record.get("case", {}).get("id")
        if case_id:
            completed.add(str(case_id))
    return completed


def result_to_jsonl(result: BaselineResult) -> str:
    return (
        json.dumps(
            {
                "case": result.case,
                "response": result.response,
                "latency_ms": result.latency_ms,
                "score": result.score.as_row(),
            },
            ensure_ascii=False,
            sort_keys=True,
        )
        + "\n"
    )
