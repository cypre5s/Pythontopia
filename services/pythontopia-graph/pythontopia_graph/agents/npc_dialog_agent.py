from __future__ import annotations

import json
import re
from typing import Any

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.schemas.npc_dialog import NpcDialogInput, NpcDialogOutput

AGENT_NAME = "NpcDialogAgent"

FORBIDDEN_TOKENS = (
    "answer_key",
    "hidden_test",
    "secret_test",
    "完整答案",
    "完整代码",
    "直接给我代码",
    "标准答案",
)


def _strip_json_fence(text: str) -> str:
    fence = re.match(r"^\s*```(?:json)?\s*(.+?)\s*```\s*$", text, re.DOTALL)
    return fence.group(1) if fence else text


def _has_forbidden_token(text: str) -> bool:
    lowered = text.lower()
    return any(token in text or token in lowered for token in FORBIDDEN_TOKENS)


class NpcDialogAgent:
    """NPC 个性化对话：注入真实记忆，要求引用 event_id，失败时规则兜底。"""

    AGENT_NAME = AGENT_NAME

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def reply(self, inp: NpcDialogInput) -> NpcDialogOutput:
        if _has_forbidden_token(inp.user_message):
            return self._safety_refusal()
        if not inp.memories:
            return NpcDialogOutput(
                reply_md="我这边还没有可引用的长期记忆。先告诉我你现在卡在哪个知识点，我会用通用提示帮你推进。",
                cited_event_ids=[],
                grounded=False,
            )
        try:
            data = await self._chat_json(self._messages(inp))
        except Exception:  # noqa: BLE001 - graph 对话演示链路必须离线兜底
            return self._fallback_with_memory()
        return self._parse_output(data, inp)

    async def _chat_json(self, messages: list[dict[str, str]]) -> dict[str, Any]:
        # model_tier="fast"：寒暄/对话走快档模型（如 deepseek-v4-flash），低延迟优先；
        # 答疑/工坊等其余 agent 不传该参数，沿用默认档（如 deepseek-v4-pro）。
        try:
            response = await self.provider.chat(
                messages,
                response_format={"type": "json_object"},
                temperature=0.3,
                model_tier="fast",
            )
        except TypeError:
            try:
                response = await self.provider.chat(messages, temperature=0.3)
            except TypeError:
                response = await self.provider.chat(messages)
        return self._coerce_json(response)

    @staticmethod
    def _coerce_json(response: LlmResponse) -> dict[str, Any]:
        if response.content_json is not None:
            return response.content_json
        if response.content_text:
            try:
                parsed = json.loads(_strip_json_fence(response.content_text))
            except json.JSONDecodeError:
                return {}
            if isinstance(parsed, dict):
                return parsed
        return {}

    def _parse_output(self, data: dict[str, Any], inp: NpcDialogInput) -> NpcDialogOutput:
        reply_md = str(data.get("reply_md") or "").strip()
        if not reply_md:
            return self._fallback_with_memory()
        if _has_forbidden_token(reply_md):
            return self._safety_refusal()
        allowed_ids = {memory.event_id for memory in inp.memories}
        cited_event_ids = [
            int(event_id)
            for event_id in data.get("cited_event_ids", [])
            if isinstance(event_id, int) and event_id in allowed_ids
        ]
        return NpcDialogOutput(
            reply_md=reply_md,
            cited_event_ids=cited_event_ids,
            grounded=bool(data.get("grounded")) and bool(cited_event_ids),
        )

    @staticmethod
    def _safety_refusal() -> NpcDialogOutput:
        return NpcDialogOutput(
            reply_md="我不能提供完整答案或隐藏答案，但可以根据你的思路给分层提示，帮你自己推出来。",
            cited_event_ids=[],
            grounded=False,
        )

    @staticmethod
    def _fallback_with_memory() -> NpcDialogOutput:
        return NpcDialogOutput(
            reply_md="先按当前目标继续：复述你刚才的思路，再指出一个最小可验证步骤。我暂时无法安全引用长期记忆。",
            cited_event_ids=[],
            grounded=False,
        )

    def _messages(self, inp: NpcDialogInput) -> list[dict[str, str]]:
        schema_hint = '{"reply_md":"NPC Markdown 回复","cited_event_ids":[整数],"grounded":true}'
        system = (
            "你是 Pythontopia 的 NPC 对话 agent，只输出一个 JSON 对象，不要输出多余文字。"
            f"当前 NPC={inp.npc_key}，人格核心：{inp.persona_core}。"
            f"严格遵循结构：{schema_hint}。"
            "回复必须使用 NPC 口吻，但只能依据给定 memories 说“我记得”；"
            "cited_event_ids 只能引用 memories 中出现过的 event_id。"
            "严禁编造课件页码、题目答案、answer_key、hidden_test、完整答案或完整代码。"
        )
        memory_lines = "\n".join(
            f"event_id={memory.event_id} kc={','.join(memory.kc_codes)} summary={memory.summary}"
            for memory in inp.memories
        )
        messages: list[dict[str, str]] = [
            {"role": "system", "content": system},
            {"role": "system", "content": f"可引用长期记忆：\n{memory_lines}"},
        ]
        messages.extend(turn.model_dump() for turn in inp.history)
        messages.append({"role": "user", "content": inp.user_message})
        return messages
