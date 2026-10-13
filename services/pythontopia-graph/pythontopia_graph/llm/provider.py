from __future__ import annotations

import json as _json
import os
import re
from urllib.parse import urlsplit
from copy import deepcopy
from collections.abc import AsyncIterator
from pathlib import Path
from typing import Any, Protocol

import httpx
from dotenv import dotenv_values
from pydantic import BaseModel

from pythontopia_graph.llm.guard_pipeline import GuardMetrics, GuardPipeline
from pythontopia_graph.optimize.loader import CompiledPrompt, load_compiled
from pythontopia_graph.schemas.profile import ProfileDraft


DEEPSEEK_CHAT_MODELS = frozenset({"deepseek-flash", "deepseek-v4-pro"})
DEEPSEEK_VISION_MODELS = frozenset({"deepseek-flash"})


def _require_deepseek_model(model: str, *, vision: bool = False) -> str:
    supported = DEEPSEEK_VISION_MODELS if vision else DEEPSEEK_CHAT_MODELS
    if model not in supported:
        purpose = "vision" if vision else "chat"
        raise RuntimeError(f"Unsupported DeepSeek model for {purpose}: {model}")
    return model


class LlmResponse(BaseModel):
    content_json: dict[str, Any] | None = None
    content_text: str | None = None
    # 原生工具调用（OpenAI 兼容 function calling）：模型请求调用的工具列表，无则 None。
    # 每项形如 {"id","type":"function","function":{"name","arguments"(JSON 串)}}。
    tool_calls: list[dict[str, Any]] | None = None


_FORBIDDEN_FINE_TUNED_PATTERNS = (
    re.compile(r"完整答案"),
    re.compile(r"直接提交"),
    re.compile(r"隐藏测试"),
    re.compile(r"submission_id\s*[:=]\s*(fake|mock|test)", re.IGNORECASE),
)


def _message_content_to_text(content: object) -> str | None:
    if isinstance(content, str):
        return content
    if not isinstance(content, list):
        return None
    parts: list[str] = []
    for item in content:
        if isinstance(item, str):
            parts.append(item)
            continue
        if not isinstance(item, dict):
            continue
        text = item.get("text")
        if isinstance(text, str):
            parts.append(text)
            continue
        nested = item.get("content")
        if isinstance(nested, str):
            parts.append(nested)
    return "".join(parts) if parts else None


def _contains_image_input(messages: list[dict[str, Any]]) -> bool:
    return any(
        isinstance(part, dict) and part.get("type") in {"image_url", "input_image"}
        for message in messages
        if isinstance(message.get("content"), list)
        for part in message["content"]
    )


def _parse_chat_message(data: dict[str, Any]) -> LlmResponse:
    """把 OpenAI 兼容 chat.completions 响应解析为 LlmResponse（含原生 tool_calls）。

    工具调用轮 content 可能为 null（仅 tool_calls）；content 为字符串时尽力解析为
    content_json；响应结构异常一律降级为空 LlmResponse（绝不抛断主链路）。
    """
    try:
        message = data["choices"][0]["message"]
    except (KeyError, IndexError, TypeError):
        return LlmResponse()
    content = _message_content_to_text(message.get("content"))
    raw_tool_calls = message.get("tool_calls")
    tool_calls = (
        raw_tool_calls if isinstance(raw_tool_calls, list) and raw_tool_calls else None
    )
    content_json: dict[str, Any] | None = None
    if isinstance(content, str):
        try:
            parsed = _json.loads(content)
        except (ValueError, TypeError):
            parsed = None
        if isinstance(parsed, dict):
            content_json = parsed
    return LlmResponse(
        content_json=content_json,
        content_text=content if isinstance(content, str) else None,
        tool_calls=tool_calls,
    )


def _system_messages_first(messages: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Merge all system turns into one leading turn for stricter chat templates.

    Qwen-family HF templates used by local vLLM reject conversations where a
    system message appears after a user/assistant turn. Several graph agents
    compose small system fragments, so normalize at the provider boundary.
    """
    system_parts: list[str] = []
    non_system: list[dict[str, Any]] = []
    for message in messages:
        if message.get("role") == "system":
            content = _message_content_to_text(message.get("content"))
            if content:
                system_parts.append(content)
            continue
        non_system.append(dict(message))
    if not system_parts:
        return messages
    return [{"role": "system", "content": "\n\n".join(system_parts)}, *non_system]


def provider_supports_tool_calling(provider: object) -> bool:
    """provider 是否支持原生工具调用；无能力位者视为不支持 → 调用方回落意图分类器（零回归）。"""
    return bool(getattr(provider, "supports_tool_calling", False))


# 仅供 provider 包装层消费、绝不能进入上游请求体的 kwargs。
# - guard_context：含 semantic_validator 等可调用对象，随 body 一起 json.dumps 会直接
#   抛 TypeError（默认部署 PYT_FINE_TUNED_LLM_ENABLED=false 时没有 FineTunedFallbackProvider
#   帮忙 pop，CodeLab/Handout 等 Agent 会 100% 崩在序列化上）。
# - agent_route：RoutedFineTunedProvider 的内部路由信号，上游不认。
_NON_WIRE_KWARGS = ("guard_context", "agent_route")


def _drop_non_wire_kwargs(kwargs: dict[str, object]) -> None:
    """就地移除只属于本进程 provider 包装层的 kwargs（不影响任何真实上游参数）。"""
    for name in _NON_WIRE_KWARGS:
        kwargs.pop(name, None)


class LlmProvider(Protocol):
    async def chat(
        self, messages: list[dict[str, Any]], *, json_mode: bool = True, **kwargs: object
    ) -> LlmResponse:
        raise NotImplementedError

    async def chat_stream(
        self, messages: list[dict[str, Any]], **kwargs: object
    ) -> AsyncIterator[str]:
        raise NotImplementedError
        yield  # noqa: RET503 – makes this a proper async generator in Protocol


class EnvLoadResult(BaseModel):
    path: str
    exists: bool
    loaded_keys: int


def _default_deploy_env_path() -> Path:
    # 沿父目录向上查找 deploy/.env（避免写死 parents[n] 在容器内越界 IndexError）；未命中返回占位路径，由 load_pythontopia_env fail-soft。
    here = Path(__file__).resolve()
    for parent in here.parents:
        candidate = parent / "deploy" / ".env"
        if candidate.exists():
            return candidate
    return here.parent / "deploy" / ".env"


def load_pythontopia_env(path: str | None = None) -> EnvLoadResult:
    """加载 <repo>/deploy/.env（PYT_LLM_PROVIDER / PYT_OPENAI_* 等）。

    供 graph 直跑 uvicorn、未经 compose 注入时读取 DeepSeek 凭证；容器内找不到则 exists=False 无副作用。
    """
    env_path = Path(path) if path else _default_deploy_env_path()
    if not env_path.exists():
        return EnvLoadResult(path=str(env_path), exists=False, loaded_keys=0)
    values = dotenv_values(env_path)
    loaded = 0
    for key, value in values.items():
        if key and value is not None and key not in os.environ:
            os.environ[key] = value
            loaded += 1
    return EnvLoadResult(path=str(env_path), exists=True, loaded_keys=loaded)


def _openai_compat_env() -> tuple[str | None, str | None, str | None]:
    """解析 DeepSeek 三件套；项目专用变量优先，通用名仅作 Compose 兼容别名。
    """
    base_url = (
        os.environ.get("PYT_OPENAI_BASE_URL")
        or os.environ.get("OPENAI_BASE_URL")
        or os.environ.get("LLM_BASE_URL")
    )
    api_key = (
        os.environ.get("PYT_OPENAI_API_KEY")
        or os.environ.get("OPENAI_API_KEY")
        or os.environ.get("LLM_API_KEY")
    )
    model = (
        os.environ.get("PYT_OPENAI_MODEL")
        or os.environ.get("OPENAI_MODEL")
        or os.environ.get("LLM_MODEL")
    )
    return base_url, api_key, model


def _openai_fast_model() -> str | None:
    """快档模型名（NPC 寒暄/对话用，低延迟、少推理 token）。

    项目专用变量优先；未配置则返回 None，由调用方回落默认档。
    """
    return (
        os.environ.get("PYT_OPENAI_MODEL_FAST")
        or os.environ.get("OPENAI_MODEL_FAST")
        or os.environ.get("LLM_MODEL_FAST")
    )


def _openai_vision_model() -> str | None:
    return (
        os.environ.get("PYT_OPENAI_MODEL_VISION")
        or os.environ.get("OPENAI_MODEL_VISION")
        or os.environ.get("LLM_MODEL_VISION")
    )


def _fine_tuned_env() -> tuple[str | None, str | None, str | None]:
    base_url = os.environ.get("PYT_FINE_TUNED_BASE_URL")
    api_key = os.environ.get("PYT_FINE_TUNED_API_KEY")
    model = os.environ.get("PYT_FINE_TUNED_MODEL")
    return base_url, api_key, model


def _specialized_adapter_models() -> tuple[str | None, str | None]:
    return (
        os.environ.get("PYT_FINE_TUNED_CONTRACT_MODEL"),
        os.environ.get("PYT_FINE_TUNED_GROUNDING_MODEL"),
    )


def _fine_tuned_enabled() -> bool:
    value = (os.environ.get("PYT_FINE_TUNED_LLM_ENABLED") or "").strip().lower()
    return value in {"1", "true", "yes", "on"}


class DryRunProvider:
    # dry-run 也声明支持工具调用：离线确定性地回一次 tool_call → 收到 tool 结果后作答，
    # 让「有界工具调用循环」在无网络/无密钥时也可被单测覆盖（确定性、可终止）。
    supports_tool_calling = True

    async def chat(self, messages: list[dict[str, Any]], **kwargs: object) -> LlmResponse:
        if _contains_image_input(messages):
            raise RuntimeError("dry-run provider does not support image input")
        tools = kwargs.get("tools")
        if tools:
            return self._dryrun_tool_turn(messages, tools)
        text = "\n".join(
            _message_content_to_text(message.get("content")) or "" for message in messages
        )
        if "敏感" in text:
            return LlmResponse(content_json={"safety_failed": True, "reason": "PII_OR_SENSITIVE"})
        if "PY43-KC-999" in text or "虚构" in text or "不存在" in text:
            draft = ProfileDraft.example_complete(target_kc_codes=["PY43-KC-999"])
            return LlmResponse(content_json={"profile_draft": draft.model_dump(mode="json")})
        draft = ProfileDraft.example_complete(target_kc_codes=["PY43-KC-001", "PY43-KC-003"])
        return LlmResponse(content_json={"profile_draft": draft.model_dump(mode="json")})

    async def chat_stream(
        self, messages: list[dict[str, Any]], **kwargs: object
    ) -> AsyncIterator[str]:
        if _contains_image_input(messages):
            raise RuntimeError("dry-run provider does not support image input")
        # dry-run 禁止回显 system/证据消息，否则 SYSTEM_PROMPT 会泄露到答疑气泡；仅依据最后一条 user 问题生成占位答复。
        user_turns = [
            str(m.get("content", "")) for m in messages if m.get("role") == "user"
        ]
        question = user_turns[-1].strip() if user_turns else ""
        answer = (
            "基于通识知识（本地 dry-run，未接入真实大模型）：\n\n"
            f"已收到你的问题：「{question}」。\n\n"
            "当前为离线占位答复。配置 DeepSeek 密钥后即可获得真实课件答疑。"
        )
        for i in range(0, len(answer), 12):
            yield answer[i : i + 12]

    @staticmethod
    def _dryrun_tool_turn(messages: list[dict[str, Any]], tools: object) -> LlmResponse:
        """收到 tool 结果前回一次确定性 tool_call；收到后给最终答复（保证循环可终止）。"""
        last_role = messages[-1].get("role") if messages else None
        if last_role == "tool":
            return LlmResponse(content_text="（dry-run）已根据工具结果作答。")
        name = "unknown_tool"
        if isinstance(tools, list) and tools and isinstance(tools[0], dict):
            fn = tools[0].get("function")
            if isinstance(fn, dict) and isinstance(fn.get("name"), str):
                name = fn["name"]
        return LlmResponse(
            tool_calls=[
                {
                    "id": "drycall_0",
                    "type": "function",
                    "function": {"name": name, "arguments": "{}"},
                }
            ]
        )


class OpenAiCompatProvider:
    # DeepSeek 兼容接口原生支持 function calling（已实测 v4-pro / v4-flash 单轮+回喂双轮）。
    supports_tool_calling = True

    def __init__(
        self,
        *,
        base_url: str | None = None,
        api_key: str | None = None,
        model: str | None = None,
        fast_model: str | None = None,
        vision_model: str | None = None,
        enable_thinking: bool | None = None,
    ) -> None:
        if base_url is None or api_key is None or model is None:
            load_pythontopia_env()
            env_base_url, env_api_key, env_model = _openai_compat_env()
            base_url = base_url or env_base_url
            api_key = api_key or env_api_key
            model = model or env_model
        if not base_url or not api_key or not model:
            raise RuntimeError("OpenAI-compatible LLM env is missing")
        endpoint = urlsplit(base_url)
        if (
            endpoint.scheme != "https"
            or endpoint.hostname != "api.deepseek.com"
            or endpoint.port not in (None, 443)
            or endpoint.username is not None
            or endpoint.password is not None
        ):
            raise RuntimeError("DeepSeek base URL must use https://api.deepseek.com")
        # 经上面非空校验后窄化为 str（mypy 不跨实例属性传递 narrowing，故经局部变量回填）。
        self.base_url: str = base_url
        self.api_key: str = api_key
        self.model: str = _require_deepseek_model(model)
        # 快档模型（NPC 寒暄/对话）未配置时回落默认档，行为零回归。
        self.fast_model: str = _require_deepseek_model(
            fast_model or _openai_fast_model() or self.model
        )
        selected_vision_model = vision_model or _openai_vision_model()
        self.vision_model = (
            _require_deepseek_model(selected_vision_model, vision=True)
            if selected_vision_model
            else None
        )
        self.enable_thinking = enable_thinking

    def _resolve_model(self, kwargs: dict[str, object]) -> str:
        """按调用方声明的 model_tier 选模型：fast→快档、vision→视觉档，其余→默认档。

        从 kwargs 弹出 model_tier，避免它被透传进 OpenAI 兼容请求体。
        """
        if "model" in kwargs:
            raise RuntimeError("DeepSeek request model override is disabled; use model_tier")
        tier = kwargs.pop("model_tier", None)
        if tier == "vision":
            if not self.vision_model:
                raise RuntimeError("OpenAI-compatible vision model is not configured")
            return self.vision_model
        return self.fast_model if tier == "fast" else self.model

    async def chat(
        self, messages: list[dict[str, Any]], *, json_mode: bool = True, **kwargs: object
    ) -> LlmResponse:
        model = self._resolve_model(kwargs)
        _drop_non_wire_kwargs(kwargs)
        # 按需结构化输出（json_mode）：仅当 json_mode=True（默认）才强制
        # response_format=json_object——资源/画像/图解/证据注/辩论等依赖 JSON 的调用沿用默认；
        # 纯文本答疑（导师作答 / 修订 / 考考我点评）显式传 json_mode=False，避免回答被压成
        # JSON 串而变短、夹结构噪声。调用方显式传入的 response_format 始终优先（保持既有
        # *_agent 行为不变）。默认保持 True 是为不波及本次改动范围外、未显式声明 JSON 的调用方
        # （6 个资源 Agent + profile_graph 等），它们靠该默认拿到结构化输出。
        body: dict[str, object] = {"model": model, "messages": messages}
        body["messages"] = _system_messages_first(messages)
        # 原生工具调用与强制 json_object 互斥：传 tools 时绝不再强制 response_format
        # （上游会冲突/失能），由模型自行决定输出 tool_calls 还是文本。
        has_tools = bool(kwargs.get("tools"))
        if json_mode and not has_tools and "response_format" not in kwargs:
            body["response_format"] = {"type": "json_object"}
        if self.enable_thinking is not None and "chat_template_kwargs" not in kwargs:
            body["chat_template_kwargs"] = {"enable_thinking": self.enable_thinking}
        body.update(kwargs)
        # trust_env=True：有 HTTP(S)_PROXY 则走代理，否则直连；NO_PROXY 保护 localhost/RAG。
        # 读超时 90s：讲义/练习生成属长输出任务，30s 会让 ResourceDesigner 等 agent 高频
        # 超时（实测 DeepSeek 长文常超 30s）；连接超时仍保持 10s 快速失败。
        async with httpx.AsyncClient(
            timeout=httpx.Timeout(90, connect=10), trust_env=True
        ) as client:
            response = await client.post(
                f"{self.base_url.rstrip('/')}/chat/completions",
                headers={"Authorization": f"Bearer {self.api_key}"},
                json=body,
            )
            response.raise_for_status()
        # 解析含原生 tool_calls（function calling）：工具调用轮 content 可能为 null。
        return _parse_chat_message(response.json())

    async def chat_stream(
        self, messages: list[dict[str, Any]], **kwargs: object
    ) -> AsyncIterator[str]:
        model = self._resolve_model(kwargs)
        _drop_non_wire_kwargs(kwargs)
        url = f"{self.base_url.rstrip('/')}/chat/completions"
        payload: dict[str, object] = {
            "model": model,
            "messages": _system_messages_first(messages),
            "stream": True,
            **kwargs,
        }
        # 同 chat()：trust_env 代理策略。
        async with httpx.AsyncClient(timeout=60, trust_env=True) as client:
            async with client.stream(
                "POST",
                url,
                headers={"Authorization": f"Bearer {self.api_key}"},
                json=payload,
            ) as response:
                response.raise_for_status()
                async for line in response.aiter_lines():
                    if not line.startswith("data: "):
                        continue
                    data_str = line[len("data: "):]
                    if data_str.strip() == "[DONE]":
                        break
                    chunk = _json.loads(data_str)
                    delta = chunk.get("choices", [{}])[0].get("delta", {})
                    content = delta.get("content")
                    if content:
                        yield content


class FineTunedFallbackProvider:
    """Use the local fine-tuned adapter for structured Agent JSON, with hard fallback.

    The adapter is a specialization layer for Pythontopia Agent contracts. It is not
    used for streaming prose, fast NPC turns, or native tool-calling turns, so the
    Web experience remains available when local vLLM is stopped.
    """

    supports_tool_calling = True

    def __init__(self, *, fine_tuned: LlmProvider, fallback: LlmProvider) -> None:
        self.fine_tuned = fine_tuned
        self.fallback = fallback
        self.guard_pipeline = GuardPipeline()
        self.guard_metrics = GuardMetrics()

    async def chat(
        self, messages: list[dict[str, Any]], *, json_mode: bool = True, **kwargs: object
    ) -> LlmResponse:
        guard_context = kwargs.pop("guard_context", None)
        if not self._should_use_adapter(json_mode=json_mode, kwargs=kwargs):
            return await self.fallback.chat(messages, json_mode=json_mode, **kwargs)
        self.guard_metrics.record_attempt()
        try:
            adapter_kwargs = dict(kwargs)
            adapter_kwargs.setdefault("temperature", 0.0)
            adapter_kwargs.setdefault("top_p", 1.0)
            context = dict(guard_context) if isinstance(guard_context, dict) else None
            if context is not None and isinstance(context.get("json_schema"), dict):
                adapter_kwargs["response_format"] = _bound_json_schema_response_format(
                    context
                )
                self.guard_metrics.record_constrained_attempt(
                    evidence_bound=bool(context.get("evidence_ids"))
                )
            if getattr(self.fine_tuned, "supports_agent_routing", False) and context:
                adapter_kwargs["agent_route"] = str(context.get("agent") or "")
            response = await self.fine_tuned.chat(
                messages, json_mode=json_mode, **adapter_kwargs
            )
            parsed = _response_json(response)
            raw_parsed = dict(parsed) if parsed is not None else None
            if context is not None and not context.get("json_schema"):
                if _needs_safety_refusal(context, parsed):
                    parsed = _safety_refusal_payload(context)
                if parsed is not None:
                    parsed = _normalize_fine_tuned_grounding(context, parsed)
                    context["_contract_repaired"] = parsed != raw_parsed
            result = self.guard_pipeline.evaluate(parsed, context)
            self.guard_metrics.record(result)
            if result.passed and result.payload is not None:
                text = _json.dumps(result.payload, ensure_ascii=False, sort_keys=True)
                return LlmResponse(content_json=result.payload, content_text=text)
            self.guard_metrics.record_fallback()
            return await self.fallback.chat(messages, json_mode=json_mode, **kwargs)
        except Exception:  # noqa: BLE001 - adapter is optional; fallback keeps Agent alive.
            self.guard_metrics.record_fallback()
            return await self.fallback.chat(messages, json_mode=json_mode, **kwargs)

    async def chat_stream(
        self, messages: list[dict[str, Any]], **kwargs: object
    ) -> AsyncIterator[str]:
        async for chunk in self.fallback.chat_stream(messages, **kwargs):
            yield chunk

    @staticmethod
    def _should_use_adapter(*, json_mode: bool, kwargs: dict[str, object]) -> bool:
        if not json_mode:
            return False
        if kwargs.get("tools"):
            return False
        if kwargs.get("model_tier") == "fast":
            return False
        return True


def _bound_json_schema_response_format(context: dict[str, Any]) -> dict[str, Any]:
    schema = deepcopy(context["json_schema"])
    evidence_ids = [
        value
        for value in context.get("evidence_ids", []) or []
        if isinstance(value, (str, int)) and not isinstance(value, bool)
    ]
    if evidence_ids:
        _bind_evidence_schema(schema, evidence_ids)
    agent = re.sub(r"[^A-Za-z0-9_]", "_", str(context.get("agent") or "Agent"))
    return {
        "type": "json_schema",
        "json_schema": {
            "name": f"{agent}SemanticIR",
            "schema": schema,
            "strict": True,
        },
    }


def _bind_evidence_schema(node: object, evidence_ids: list[str | int]) -> None:
    if isinstance(node, dict):
        properties = node.get("properties")
        if isinstance(properties, dict):
            for field in ("evidence_ids", "evidence_refs", "evidence_page_ids"):
                value = properties.get(field)
                if not isinstance(value, dict) or not isinstance(value.get("items"), dict):
                    continue
                value["items"]["enum"] = list(dict.fromkeys(evidence_ids))
                value["minItems"] = max(1, int(value.get("minItems", 0)))
                required = node.setdefault("required", [])
                if isinstance(required, list) and field not in required:
                    required.append(field)
        for value in node.values():
            _bind_evidence_schema(value, evidence_ids)
    elif isinstance(node, list):
        for value in node:
            _bind_evidence_schema(value, evidence_ids)


class RoutedFineTunedProvider:
    """Routes agent families to independently promotable adapters."""

    supports_tool_calling = False
    supports_agent_routing = True
    CONTRACT_AGENTS = frozenset({"GuideCoachAgent", "PracticeAgent", "DebugAgent"})

    def __init__(
        self,
        *,
        default: LlmProvider,
        contract: LlmProvider | None = None,
        grounding: LlmProvider | None = None,
    ) -> None:
        self.default = default
        self.contract = contract or default
        self.grounding = grounding or default

    async def chat(
        self, messages: list[dict[str, Any]], *, json_mode: bool = True, **kwargs: object
    ) -> LlmResponse:
        agent = str(kwargs.pop("agent_route", ""))
        provider = self.contract if agent in self.CONTRACT_AGENTS else self.grounding
        return await provider.chat(messages, json_mode=json_mode, **kwargs)

    async def chat_stream(
        self, messages: list[dict[str, Any]], **kwargs: object
    ) -> AsyncIterator[str]:
        async for chunk in self.default.chat_stream(messages, **kwargs):
            yield chunk


def _response_json(response: LlmResponse) -> dict[str, Any] | None:
    if isinstance(response.content_json, dict):
        return dict(response.content_json)
    if not response.content_text:
        return None
    try:
        parsed = _json.loads(response.content_text)
    except (TypeError, ValueError):
        return None
    return parsed if isinstance(parsed, dict) else None


def _needs_safety_refusal(context: dict[str, Any], parsed: dict[str, Any] | None) -> bool:
    if str(context.get("agent", "")) != "SafetyAgent":
        return False
    if parsed is None:
        return True
    if _fine_tuned_forbidden_leak(parsed):
        return True
    return not {"redactions", "safer_alternative"}.issubset(parsed.keys())


def _safety_refusal_payload(context: dict[str, Any]) -> dict[str, Any]:
    kc = str(context.get("kc") or "当前知识点")
    return {
        "allow": False,
        "reasons": [
            f"该请求围绕 {kc}，需要保护练习评估与教师视角边界。",
            "不能提供完整答案、隐藏测试或可直接提交的内容。",
        ],
        "redactions": ["完整答案", "直接提交", "隐藏测试"],
        "blocked_content": ["完整答案", "直接提交", "隐藏测试"],
        "safer_alternative": f"围绕 {kc} 给一个最小下一步提示：先说出你卡住的概念、当前现象和你已尝试的检查点。",
        "safety_flags": ["answer_leak_guard"],
    }


def _normalize_fine_tuned_grounding(
    context: dict[str, Any], parsed: dict[str, Any]
) -> dict[str, Any]:
    normalized = dict(parsed)
    kc = context.get("kc")
    if kc and "kc" not in normalized:
        normalized["kc"] = str(kc)
    evidence_ids = [str(item) for item in context.get("evidence_ids", []) or []]
    normalized["evidence_ids"] = evidence_ids
    if evidence_ids:
        normalized.setdefault("grounding_note", "courseware_evidence")
    else:
        normalized["grounding_note"] = "common_knowledge"
    return normalized


def _fine_tuned_forbidden_leak(parsed: dict[str, Any]) -> bool:
    text = _json.dumps(parsed, ensure_ascii=False)
    if not any(pattern.search(text) for pattern in _FORBIDDEN_FINE_TUNED_PATTERNS):
        return False
    if _looks_like_safe_refusal(parsed):
        return False
    return True


def _looks_like_safe_refusal(parsed: dict[str, Any]) -> bool:
    allow = parsed.get("allow")
    if allow is False or str(allow).lower() == "false":
        return True
    redaction_keys = {"redactions", "blocked_content", "safety_flags", "safer_alternative"}
    if not (redaction_keys & set(parsed.keys())):
        return False
    text = _json.dumps(parsed, ensure_ascii=False)
    code_signals = ("```python", "def ", "input(", "print(", "return ")
    return not any(signal in text for signal in code_signals)


class LlmProviderError(RuntimeError):
    """上游 Provider 硬性失败（限流、余额不足等）时抛出。"""

    def __init__(self, message: str, *, kind: str, retryable: bool = False) -> None:
        super().__init__(message)
        self.kind = kind
        self.retryable = retryable


class LlmProviderNotConfigured(RuntimeError):
    """No real provider is configured; API routes must not return demo answers."""


def provider_from_env() -> LlmProvider:
    if _fine_tuned_enabled():
        raise RuntimeError("Fine-tuned LLM providers are disabled; configure DeepSeek")
    load_pythontopia_env()
    provider_name = (os.environ.get("PYT_LLM_PROVIDER") or "").strip().lower()
    if not provider_name:
        # 未指定 provider 时仅在真实 DeepSeek 凭证存在时启动在线 provider。
        _, api_key, _ = _openai_compat_env()
        provider_name = "openai_compat" if api_key else ""
    if not provider_name or provider_name == "dryrun":
        raise LlmProviderNotConfigured("DeepSeek provider is not configured")
    if provider_name in {"openai_compat", "openai-compatible", "openaicompat"}:
        return OpenAiCompatProvider()
    if provider_name == "spark":
        raise RuntimeError("Spark LLM is disabled; configure DeepSeek")
    raise RuntimeError(f"Unsupported PYT_LLM_PROVIDER: {provider_name}")


def _with_optional_fine_tuned_provider(fallback: LlmProvider) -> LlmProvider:
    if not _fine_tuned_enabled():
        return fallback
    base_url, api_key, model = _fine_tuned_env()
    if not base_url or not api_key or not model:
        return fallback
    fine_tuned: LlmProvider = OpenAiCompatProvider(
        base_url=base_url,
        api_key=api_key,
        model=model,
        fast_model=model,
        enable_thinking=False,
    )
    contract_model, grounding_model = _specialized_adapter_models()
    if contract_model or grounding_model:
        def adapter(candidate_model: str | None) -> LlmProvider | None:
            if not candidate_model:
                return None
            return OpenAiCompatProvider(
                base_url=base_url,
                api_key=api_key,
                model=candidate_model,
                fast_model=candidate_model,
                enable_thinking=False,
            )

        fine_tuned = RoutedFineTunedProvider(
            default=fine_tuned,
            contract=adapter(contract_model),
            grounding=adapter(grounding_model),
        )
    return FineTunedFallbackProvider(fine_tuned=fine_tuned, fallback=fallback)


def provider_label() -> str:
    fine_tuned_suffix = ""
    if _fine_tuned_enabled():
        _, _, fine_tuned_model = _fine_tuned_env()
        if fine_tuned_model:
            fine_tuned_suffix = f" + fine_tuned:{fine_tuned_model}"
    name = (os.environ.get("PYT_LLM_PROVIDER") or "").strip().lower()
    if not name:
        _, api_key, model = _openai_compat_env()
        if api_key:
            return f"openai_compat:{model or 'unknown'}{fine_tuned_suffix}"
        return f"dryrun{fine_tuned_suffix}"
    if name in {"openai_compat", "openai-compatible", "openaicompat"}:
        _, _, model = _openai_compat_env()
        fast = _openai_fast_model()
        suffix = f" (fast={fast})" if fast and fast != model else ""
        return f"openai_compat:{model or 'unknown'}{suffix}{fine_tuned_suffix}"
    return f"{name}{fine_tuned_suffix}"


# ─────────────────────────────────────────────────────────────────────────────
# §3.5 DSPy 冻结产物 loader hook（P2-dspy-loader）
#
# 可选、fail-soft：命中离线 GEPA 编译产物时，用冻结 instruction 替换系统提示并注入
# demos；未命中（缺/损坏产物）时**原样返回**传入 messages，完全不改变现有调用语义。
# 运行时绝不调用 dspy.compile / GEPA（仅离线 scripts/optimize_*.py 跑）。
#
# 接线方式（Phase 2，不在本单元改 agents/*）：调用方在 provider.chat 前包一层——
#   messages = apply_compiled_prompt(self._messages(...), "guide_coach_decision")
#   await provider.chat(messages, ...)
# ─────────────────────────────────────────────────────────────────────────────

_COMPILED_DEMO_ROLES: frozenset[str] = frozenset({"system", "user", "assistant"})


def apply_compiled_prompt(
    messages: list[dict[str, str]],
    name: str,
    *,
    artifacts_dir: Path | None = None,
) -> list[dict[str, str]]:
    """命中 DSPy 冻结产物则套用冻结 prompt，否则原样返回（§3.5 provider 侧 hook）。

    Args:
        messages: 调用方原始消息（手写系统提示 + 真实对话轮次）。
        name: 产物名（``CompiledPrompt`` 白名单内才会命中）。
        artifacts_dir: 可选目录覆盖（测试用）；缺省读默认 ``optimize/artifacts/``。

    Returns:
        命中 → 新 list：``[system=冻结 instruction] + demo 消息 + 原非系统轮次``；
        未命中 → **传入的同一对象**（保证现有调用零回归）。
    """
    compiled = load_compiled(name, artifacts_dir=artifacts_dir)
    if compiled is None:
        return messages
    return _compose_compiled_messages(messages, compiled)


def _compose_compiled_messages(
    messages: list[dict[str, str]], compiled: CompiledPrompt
) -> list[dict[str, str]]:
    composed: list[dict[str, str]] = [{"role": "system", "content": compiled.instruction}]
    for demo in compiled.demos:
        composed.extend(_demo_to_messages(demo))
    # 冻结 instruction 取代手写系统提示；保留真实 user/assistant 轮次（动态上下文）。
    composed.extend(message for message in messages if message.get("role") != "system")
    return composed


def _demo_to_messages(demo: dict[str, Any]) -> list[dict[str, str]]:
    """把一个 demo 渲染为 few-shot 聊天消息；无法识别的形状静默跳过（fail-soft）。"""
    role = demo.get("role")
    content = demo.get("content")
    if isinstance(role, str) and role in _COMPILED_DEMO_ROLES and isinstance(content, str):
        return [{"role": role, "content": content}]
    rendered: list[dict[str, str]] = []
    if "inputs" in demo or "input" in demo:
        rendered.append({"role": "user", "content": _stringify_demo(demo.get("inputs", demo.get("input")))})
    if "outputs" in demo or "output" in demo:
        rendered.append({"role": "assistant", "content": _stringify_demo(demo.get("outputs", demo.get("output")))})
    return rendered


def _stringify_demo(value: Any) -> str:
    if isinstance(value, str):
        return value
    try:
        return _json.dumps(value, ensure_ascii=False)
    except (TypeError, ValueError):
        return str(value)
