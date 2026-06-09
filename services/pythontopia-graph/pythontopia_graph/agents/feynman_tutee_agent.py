from __future__ import annotations

import json
import re
from typing import Any

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.schemas.feynman import (
    FeynmanTuteeInput,
    FeynmanTuteeOutput,
    Misconception,
)

AGENT_NAME = "FeynmanTuteeAgent"


def _strip_json_fence(text: str) -> str:
    fence = re.match(r"^\s*```(?:json)?\s*(.+?)\s*```\s*$", text, re.DOTALL)
    return fence.group(1) if fence else text


class FeynmanTuteeAgent:
    """费曼反演 AI 学弟：按真实误解犯错，被正确教导才更新状态。"""

    AGENT_NAME = AGENT_NAME

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def step(self, inp: FeynmanTuteeInput) -> FeynmanTuteeOutput:
        if inp.student_teaching is not None:
            return self._rule_step(inp)
        try:
            data = await self._chat_json(self._messages(inp))
        except Exception:  # noqa: BLE001 - 无网/无 key 时必须规则兜底
            return self._rule_step(inp)
        parsed = self._parse_output(data, inp)
        return parsed if parsed is not None else self._rule_step(inp)

    async def _chat_json(self, messages: list[dict[str, str]]) -> dict[str, Any]:
        try:
            response = await self.provider.chat(
                messages, response_format={"type": "json_object"}, temperature=0.35
            )
        except TypeError:
            try:
                response = await self.provider.chat(messages, temperature=0.35)
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

    def _parse_output(
        self, data: dict[str, Any], inp: FeynmanTuteeInput
    ) -> FeynmanTuteeOutput | None:
        reply_md = str(data.get("reply_md") or "").strip()
        if not reply_md:
            return None
        allowed = {mis.tag: mis for mis in inp.misconceptions}
        raw_updated = data.get("updated_misconceptions")
        if not isinstance(raw_updated, list):
            return None
        updated: list[Misconception] = []
        for item in raw_updated:
            if not isinstance(item, dict):
                continue
            try:
                misconception = Misconception.model_validate(item)
            except Exception:  # noqa: BLE001 - 非法状态直接丢弃
                continue
            if misconception.tag not in allowed:
                continue
            if not set(misconception.evidence_event_ids).issubset(
                set(allowed[misconception.tag].evidence_event_ids)
            ):
                continue
            updated.append(misconception)
        if not updated:
            return None
        return FeynmanTuteeOutput(
            reply_md=reply_md,
            updated_misconceptions=updated,
            produced_code=data.get("produced_code")
            if isinstance(data.get("produced_code"), str)
            else None,
        )

    def _rule_step(self, inp: FeynmanTuteeInput) -> FeynmanTuteeOutput:
        if not inp.misconceptions:
            return FeynmanTuteeOutput(
                reply_md="我暂时没有带着具体误解来练习。你可以先给我一个要讲的知识点。",
                updated_misconceptions=[],
                produced_code=None,
            )
        if inp.student_teaching is None:
            first = next((mis for mis in inp.misconceptions if not mis.resolved), inp.misconceptions[0])
            reply, code = self._mistake_for(first.tag)
            return FeynmanTuteeOutput(
                reply_md=reply,
                updated_misconceptions=inp.misconceptions,
                produced_code=code,
            )

        teaching = inp.student_teaching
        updated: list[Misconception] = []
        resolved_any = False
        for mis in inp.misconceptions:
            resolved = mis.resolved or self._teaching_resolves(mis.tag, teaching)
            resolved_any = resolved_any or (resolved and not mis.resolved)
            updated.append(
                Misconception(
                    tag=mis.tag,
                    evidence_event_ids=mis.evidence_event_ids,
                    resolved=resolved,
                )
            )
        if resolved_any:
            return FeynmanTuteeOutput(
                reply_md="明白了，我会按你讲的规则改：先说出边界，再写一个小例子验证。",
                updated_misconceptions=updated,
                produced_code=self._fixed_code_for(updated[0].tag),
            )
        return FeynmanTuteeOutput(
            reply_md="我还是有点困惑：你的解释没有指出我原来的错误规则，能再用反例讲一次吗？",
            updated_misconceptions=updated,
            produced_code=None,
        )

    @staticmethod
    def _teaching_resolves(tag: str, teaching: str) -> bool:
        text = teaching.lower()
        if tag == "off_by_one_range":
            return (
                ("不包含" in teaching or "不会包含" in teaching or "不包括" in teaching)
                and ("右端" in teaching or "结束" in teaching or "最后" in teaching)
            ) or "range(1, 6)" in text
        if tag == "input_type_string":
            return ("int(" in text or "转换" in teaching or "转成" in teaching) and (
                "数字" in teaching or "整数" in teaching or "int" in text
            )
        if tag == "list_alias_mutation":
            return (
                "copy" in text or "复制" in teaching or "切片" in teaching or "list(" in text
            ) and ("别名" in teaching or "同一个列表" in teaching or "共享" in teaching)
        return False

    @staticmethod
    def _mistake_for(tag: str) -> tuple[str, str | None]:
        if tag == "off_by_one_range":
            return (
                "我以为 range 的结束值会被包含，所以 range(1, 5) 应该能打印到 5。",
                "for i in range(1, 5):\n    print(i)",
            )
        if tag == "input_type_string":
            return (
                "我以为 input() 得到的就是数字，所以可以直接拿它和 1 相加。",
                "age = input('age: ')\nprint(age + 1)",
            )
        if tag == "list_alias_mutation":
            return (
                "我以为 b = a 会复制一个新列表，所以改 b 不会影响 a。",
                "a = [1, 2]\nb = a\nb.append(3)\nprint(a)",
            )
        return ("我会带着这个误解先试一遍，但还说不清哪里错了。", None)

    @staticmethod
    def _fixed_code_for(tag: str) -> str | None:
        if tag == "off_by_one_range":
            return "for i in range(1, 6):\n    print(i)"
        if tag == "input_type_string":
            return "age = int(input('age: '))\nprint(age + 1)"
        if tag == "list_alias_mutation":
            return "a = [1, 2]\nb = a.copy()\nb.append(3)\nprint(a)"
        return None

    def _messages(self, inp: FeynmanTuteeInput) -> list[dict[str, str]]:
        schema_hint = (
            '{"reply_md":"AI 学弟回复","updated_misconceptions":'
            '[{"tag":"误解标签","evidence_event_ids":[整数],"resolved":false}],'
            '"produced_code":null}'
        )
        system = (
            "你是 Pythontopia 费曼反演里的 AI 学弟，只输出一个 JSON 对象，不要输出多余文字。"
            f"严格遵循结构：{schema_hint}。"
            "你必须持续体现输入 misconceptions 中未解决的误解，并引用原 evidence_event_ids；"
            "只有当 student_teaching 明确纠正该误解时，才能把 resolved 置为 true。"
            "不要编造新的误解、event_id、隐藏测试或标准答案。"
        )
        misconceptions = json.dumps(
            [mis.model_dump(mode="json") for mis in inp.misconceptions],
            ensure_ascii=False,
        )
        messages: list[dict[str, str]] = [
            {"role": "system", "content": system},
            {"role": "system", "content": f"kc_code={inp.kc_code}\nmisconceptions={misconceptions}"},
        ]
        messages.extend(turn.model_dump() for turn in inp.history)
        user = inp.student_teaching or "请作为 AI 学弟先暴露你当前的理解或错误尝试。"
        messages.append({"role": "user", "content": user})
        return messages
