"""ReflectionAgent（学情反思器）。

读 topK 记忆条目 → 单次有界 LLM 综合 1-3 条 grounded 学情洞察；每条强制引用输入里
出现过的 event_id（防幻觉），dimension_key 限画像白名单子集。记忆为空时零 LLM 调用；
provider 失败向上传播（fail-fast），绝不退化为自由对话。非 ReAct：一次调用、无自治回环。
"""

from __future__ import annotations

import json
import re
from typing import Any

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.schemas.reflection import (
    Insight,
    ReflectionInput,
    ReflectionOutput,
)

AGENT_NAME = "ReflectionAgent"

# dimension_key 白名单（与 Insight schema 同源；显式列出供 system prompt 约束）。
ALLOWED_DIMENSIONS: tuple[str, ...] = (
    "error_pattern",
    "cognitive_style",
    "emotion_persistence",
    "practical_ability",
    "knowledge_base",
)

MAX_INSIGHTS = 3


def _strip_json_fence(text: str) -> str:
    """剥离可选的 ```json … ``` 围栏后再 json.loads。"""

    fence = re.match(r"^\s*```(?:json)?\s*(.+?)\s*```\s*$", text, re.DOTALL)
    return fence.group(1) if fence else text


class ReflectionAgent:
    """记忆流 → 学情洞察的 grounded 反思器；输出结构化 ReflectionOutput。"""

    AGENT_NAME = AGENT_NAME

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def reflect(self, payload: ReflectionInput) -> ReflectionOutput:
        # 空记忆：直接返回空洞察，绝不触发 LLM（成本红线 + 无证据不臆测）。
        if not payload.memories:
            return ReflectionOutput(insights=[])
        data = await self._chat_json(self._messages(payload))
        allowed_ids = {mem.event_id for mem in payload.memories}
        insights = self._parse_insights(data.get("insights"), allowed_ids)
        return ReflectionOutput(insights=insights)

    async def _chat_json(self, messages: list[dict[str, str]]) -> dict[str, Any]:
        # 仅吞 TypeError（chat 不支持 response_format/temperature 形参时回退）；
        # provider 真正失败（限流/欠费/网络）抛出的异常不捕获，向上传播 fail-fast。
        try:
            response = await self.provider.chat(
                messages, response_format={"type": "json_object"}, temperature=0.2
            )
        except TypeError:
            try:
                response = await self.provider.chat(messages, temperature=0.2)
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

    def _parse_insights(self, raw: Any, allowed_ids: set[int]) -> list[Insight]:
        """逐条校验：schema（白名单 + 非空证据）+ 证据 id ⊆ 输入记忆；最多保留 3 条。"""

        if not isinstance(raw, list):
            return []
        insights: list[Insight] = []
        for item in raw:
            if not isinstance(item, dict):
                continue
            try:
                insight = Insight.model_validate(item)
            except Exception:  # noqa: BLE001 - 非法维度/空证据由 schema 拦下，直接丢弃
                continue
            if not set(insight.evidence_event_ids).issubset(allowed_ids):
                continue
            insights.append(insight)
            if len(insights) >= MAX_INSIGHTS:
                break
        return insights

    def _messages(self, payload: ReflectionInput) -> list[dict[str, str]]:
        allowed = ", ".join(ALLOWED_DIMENSIONS)
        schema_hint = (
            '{"insights":[{"text":"一句话学情洞察","dimension_key":"error_pattern",'
            '"tags":["可选标签"],"evidence_event_ids":[整数],"confidence":0~1}]}'
        )
        system = (
            "你是 Pythontopia 学情反思器，只输出一个 JSON 对象，不要输出多余文字。"
            "基于下方真实学习记忆条目，综合 1-3 条关于该生学习模式的洞察。"
            f"严格遵循结构：{schema_hint}。"
            "每条洞察的 evidence_event_ids 必须引用下方记忆里出现过的 event_id，"
            "不得编造或引用不存在的 event_id；"
            f"dimension_key 只能取其一：{allowed}；"
            '若证据不足或无法得出可靠洞察，返回 {"insights":[]}；'
            "严禁输出题目答案、可直接提交的完整代码答案或课件页码。"
        )
        memory_lines = "\n".join(
            f"event_id={mem.event_id} type={mem.event_type} "
            f"importance={mem.importance} kc={','.join(mem.kc_codes)} "
            f"time={mem.created_at} summary={mem.summary}"
            for mem in payload.memories
        )
        return [
            {"role": "system", "content": system},
            {
                "role": "system",
                "content": f"学习记忆条目（只能引用这些 event_id）：\n{memory_lines}",
            },
            {
                "role": "user",
                "content": "请综合上述记忆，输出引用了 event_id 的学情洞察 JSON。",
            },
        ]
