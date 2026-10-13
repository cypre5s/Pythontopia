"""ReflectionAgent（学情反思器）。

读 topK 记忆条目 → 单次有界 LLM 综合 1-3 条 grounded 学情洞察；每条强制引用输入里
出现过的 event_id（防幻觉），dimension_key 限画像白名单子集。记忆为空时零 LLM 调用；
provider 失败向上传播（fail-fast），绝不退化为自由对话。非 ReAct：一次调用、无自治回环。
"""

from __future__ import annotations

from typing import Any

from pydantic import ValidationError

from pythontopia_graph.contracts.semantic_ir import (
    ContractCompiler,
    ReflectionObservationIR,
    ReflectionSemanticIR,
)
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.schemas.reflection import (
    ReflectionInput,
    ReflectionOutput,
)

AGENT_NAME = "ReflectionAgent"

# dimension_key 白名单（与 Insight schema 同源；显式列出供 system prompt 约束）。
ALLOWED_DIMENSIONS: tuple[str, ...] = (
    "error_pattern",
)

MAX_INSIGHTS = 3


class ReflectionAgent:
    """记忆流 → 学情洞察的 grounded 反思器；输出结构化 ReflectionOutput。"""

    AGENT_NAME = AGENT_NAME

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def reflect(self, payload: ReflectionInput) -> ReflectionOutput:
        # 空记忆：直接返回空洞察，绝不触发 LLM（成本红线 + 无证据不臆测）。
        if not payload.memories:
            return ReflectionOutput(insights=[])
        allowed_ids = [mem.event_id for mem in payload.memories]
        data = await self._chat_json(
            self._messages(payload),
            guard_context={
                "agent": self.AGENT_NAME,
                "json_schema": ReflectionSemanticIR.model_json_schema(),
                "evidence_ids": [f"event-{event_id}" for event_id in allowed_ids],
                "evidence_fields": ["observations.*.evidence_refs"],
                "allow_empty_evidence_collection": True,
            },
        )
        return self._parse_observations(data.get("observations"), allowed_ids)

    async def _chat_json(
        self,
        messages: list[dict[str, str]],
        *,
        guard_context: dict[str, Any],
    ) -> dict[str, Any]:
        # 仅吞 TypeError（chat 不支持 response_format/temperature 形参时回退）；
        # provider 真正失败（限流/欠费/网络）抛出的异常不捕获，向上传播 fail-fast。
        try:
            response = await self.provider.chat(
                messages,
                response_format={"type": "json_object"},
                temperature=0.2,
                guard_context=guard_context,
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
        return {}

    def _parse_observations(
        self, raw: Any, allowed_ids: list[int]
    ) -> ReflectionOutput:
        """Validate each semantic observation and compile opaque event refs."""
        if not isinstance(raw, list):
            return ReflectionOutput(insights=[])
        observations: list[ReflectionObservationIR] = []
        for item in raw:
            if not isinstance(item, dict):
                continue
            try:
                observation = ReflectionObservationIR.model_validate(item)
                ContractCompiler.compile_reflection(
                    ReflectionSemanticIR(observations=[observation]),
                    allowed_event_ids=allowed_ids,
                )
            except (ValidationError, ValueError):
                continue
            observations.append(observation)
            if len(observations) >= MAX_INSIGHTS:
                break
        return ContractCompiler.compile_reflection(
            ReflectionSemanticIR(observations=observations),
            allowed_event_ids=allowed_ids,
        )

    def _messages(self, payload: ReflectionInput) -> list[dict[str, str]]:
        allowed = ", ".join(ALLOWED_DIMENSIONS)
        system = (
            "你是 Pythontopia 学情反思器，只输出一个 JSON 对象，不要输出多余文字。"
            "基于下方真实学习记忆条目，综合 1-3 条关于该生学习模式的洞察。"
            "输出 ReflectionSemanticIR：observations 中每项只含 claim、dimension_intent、"
            "tags、evidence_refs、confidence，不要生成产品 DTO 或真实 event_id。"
            "每条观察的 evidence_refs 必须使用下方提供的 event-N 引用，不能编造；"
            f"dimension_intent 对应维度只能取其一：{allowed.upper()}；"
            '若证据不足或无法得出可靠洞察，返回 {"observations":[]}；'
            "严禁输出题目答案、可直接提交的完整代码答案或课件页码。"
        )
        memory_lines = "\n".join(
            f"evidence_ref=event-{mem.event_id} type={mem.event_type} "
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
                "content": "请综合上述记忆，输出引用 event-N 的学情观察 IR。",
            },
        ]
