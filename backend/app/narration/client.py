"""OpenAI-compatible chat call used only for course authoring.

Validation is deliberately strict: a thinking-mode budget can burn `max_tokens`
entirely on reasoning and still return an empty message, and a JSON-mode reply can be
replaced with spaces. Neither may be stored as narration.
"""

import json
from typing import Any

import httpx

from .contracts import AuthoringSettings, ChatReply


class ModelUnavailable(RuntimeError):
    """No credentials configured; the feature is unavailable rather than degraded."""


class ModelFailed(RuntimeError):
    """The provider answered but the result cannot be used."""


def chat(messages: list[dict[str, Any]], *, settings: AuthoringSettings, model: str,
         max_tokens: int | None = None, effort: str | None = None,
         require_text: bool = True) -> ChatReply:
    if not settings["api_key"]:
        raise ModelUnavailable("未配置作者期模型凭据")
    body: dict[str, Any] = {
        "model": model,
        "messages": messages,
        "max_tokens": max_tokens or settings["max_tokens"],
        "response_format": {"type": "json_object"},
        "reasoning_effort": effort or settings["effort"],
    }
    try:
        with httpx.Client(base_url=settings["base_url"], timeout=settings["timeout"]) as client:
            response = client.post("/chat/completions",
                                   headers={"Authorization": f"Bearer {settings['api_key']}"}, json=body)
    except httpx.HTTPError as exc:
        raise ModelFailed("作者期模型请求失败，请稍后重试") from exc
    if response.status_code in {401, 403}:
        raise ModelUnavailable("作者期模型鉴权失败")
    if response.status_code != 200:
        raise ModelFailed(f"作者期模型返回 {response.status_code}")
    data = response.json()
    choice = (data.get("choices") or [{}])[0]
    message = choice.get("message") or {}
    content = message.get("content") or ""
    if choice.get("finish_reason") == "length":
        raise ModelFailed("模型输出被截断，请重试或调高上限")
    if not content.strip():
        raise ModelFailed("模型返回空内容，请重试")
    try:
        parsed = json.loads(content)
    except ValueError as exc:
        raise ModelFailed("模型返回的不是有效 JSON，请重试") from exc
    if not isinstance(parsed, dict):
        raise ModelFailed("模型返回的 JSON 结构不可用，请重试")
    if require_text and not str(parsed.get("text") or "").strip():
        raise ModelFailed("模型返回的讲解为空，请重试")
    anchors = [str(item)[:200] for item in (parsed.get("anchors") or []) if str(item).strip()][:6]
    return ChatReply(
        raw=parsed,
        text=str(parsed.get("text") or "").strip(),
        bridge_in=str(parsed.get("bridge_in") or "")[:300],
        anchors=anchors,
        usage=data.get("usage") or {},
        model=str(data.get("model") or model),
    )
