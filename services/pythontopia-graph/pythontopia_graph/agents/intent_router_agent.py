"""IntentRouterAgent（百晓工坊 意图路由）.

Picks ONE tool for a single composer message and grounds suggested KC codes to
the course catalog. Mirrors ``OrchestratorAgent`` provider plumbing (content_json
→ fenced content_text fallback) and degrades to a *deterministic keyword*
baseline when the provider returns nothing usable — it never degrades to
free-form chat, so a structured intent is always returned.

Output contract (``IntentRouteResponse``):
  * ``intent``      – one of ASK / HANDOUT / MINDMAP / PRACTICE / CODE_LAB /
    READING / STORYBOARD;
  * ``kc_codes``    – manual ``@`` codes first, then grounded LLM suggestions,
    then a name-substring / recent fallback (all clamped to the catalog);
  * ``focus_hint``  – one-line learning focus extracted from the message;
  * ``confidence`` / ``reason`` – for the frontend preview / debug.
"""

from __future__ import annotations

import json
import re
from typing import Any

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.schemas.intent import IntentRouteRequest, IntentRouteResponse

AGENT_NAME = "IntentRouterAgent"

# ASK = 答疑；其余 6 个对应资源工坊 6 类资源。集中常量便于测试与前端对齐。
RESOURCE_INTENTS: tuple[str, ...] = (
    "HANDOUT",
    "MINDMAP",
    "PRACTICE",
    "CODE_LAB",
    "READING",
    "STORYBOARD",
)
ALL_INTENTS: tuple[str, ...] = ("ASK", *RESOURCE_INTENTS)

# 确定性关键词 → 工具映射（顺序敏感：靠前先命中）。仅 provider 不可用时兜底。
_INTENT_KEYWORDS: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("MINDMAP", ("思维导图", "导图", "脑图", "知识图谱", "mindmap", "mind map")),
    ("STORYBOARD", ("动画", "分镜", "故事板", "动画脚本", "视频脚本", "storyboard")),
    ("CODE_LAB", ("代码", "示例代码", "敲代码", "实操", "动手", "跑一下", "运行", "code", "demo")),
    ("PRACTICE", ("练习", "练一练", "做题", "习题", "题目", "出题", "练题", "practice", "quiz")),
    ("READING", ("拓展", "延伸", "扩展阅读", "延伸阅读", "课外", "拓展阅读", "reading")),
    ("HANDOUT", ("讲义", "笔记", "总结", "梳理", "讲解", "概念", "整理", "handout")),
)

# 问句信号：偏「提问/解释」而非「生成产物」时优先判 ASK。
_ASK_KEYWORDS: tuple[str, ...] = (
    "为什么",
    "什么是",
    "怎么",
    "如何",
    "是不是",
    "可以吗",
    "区别",
    "报错",
    "怎么办",
    "为啥",
    "啥意思",
    "解释",
    "?",
    "？",
)

_MAX_KC = 3


def _strip_json_fence(text: str) -> str:
    """Strip an optional ```json … ``` fence before json.loads (LLM habit)."""

    fence = re.match(r"^\s*```(?:json)?\s*(.+?)\s*```\s*$", text, re.DOTALL)
    return fence.group(1) if fence else text


class IntentRouterAgent:
    AGENT_NAME = AGENT_NAME

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def route(self, req: IntentRouteRequest) -> IntentRouteResponse:
        allowed = {kc.code for kc in req.course_kcs}
        data = await self._chat_json(self._messages(req))

        intent = self._coerce_intent(data.get("intent"))
        confidence = self._coerce_confidence(data.get("confidence"))
        reason = self._clean_text(data.get("reason"))
        suggested = self._ground(data.get("kc_codes"), allowed)

        if intent is None:
            intent = self._keyword_intent(req.message)
            confidence = min(confidence, 0.5)
            reason = reason or "关键词兜底判定"

        kc_codes = self._merge_kcs(req, allowed, suggested)
        focus = self._clean_text(data.get("focus_hint")) or req.message.strip()[:120]

        return IntentRouteResponse(
            intent=intent,
            kc_codes=kc_codes,
            focus_hint=focus,
            confidence=confidence,
            reason=reason,
        )

    # ── provider plumbing (mirrors OrchestratorAgent._chat_json) ───────────
    async def _chat_json(self, messages: list[dict[str, str]]) -> dict[str, Any]:
        try:
            response = await self.provider.chat(
                messages, response_format={"type": "json_object"}, temperature=0.1
            )
        except TypeError:
            try:
                response = await self.provider.chat(messages, temperature=0.1)
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

    # ── prompt builder ─────────────────────────────────────────────────────
    def _messages(self, req: IntentRouteRequest) -> list[dict[str, str]]:
        catalog = (
            "\n".join(f"{kc.code} = {kc.name}".strip() for kc in req.course_kcs[:80])
            or "（空，请勿编造 KC）"
        )
        system = (
            "你是 Pythontopia「百晓工坊」的意图路由器，只输出 JSON 对象。"
            "从学生这一句话判断该调用哪个工具，并从课程知识点目录里挑出最相关的 KC。schema:"
            '{"intent":"ASK|HANDOUT|MINDMAP|PRACTICE|CODE_LAB|READING|STORYBOARD",'
            '"kc_codes":["…"],"focus_hint":"一句话学习重点","confidence":0~1,"reason":"简短理由"}。'
            "工具语义：ASK=直接答疑/解释/回答提问；HANDOUT=讲义笔记；MINDMAP=思维导图；"
            "PRACTICE=练习题；CODE_LAB=可运行代码案例；READING=拓展阅读；STORYBOARD=动画分镜脚本。"
            f"kc_codes 只能取自下方目录里的 code，最多 {_MAX_KC} 个；目录找不到就留空数组，禁止编造。"
        )
        messages: list[dict[str, str]] = [
            {"role": "system", "content": system},
            {"role": "system", "content": f"课程知识点目录：\n{catalog}"},
        ]
        if req.current_chapter:
            messages.append(
                {"role": "system", "content": f"学生当前在第 {req.current_chapter} 章。"}
            )
        if req.manual_kc_codes:
            messages.append(
                {
                    "role": "system",
                    "content": "学生已手动 @ 指定 KC（最高优先级，必须保留）："
                    + ", ".join(req.manual_kc_codes),
                }
            )
        messages.append({"role": "user", "content": req.message})
        return messages

    # ── deterministic helpers ──────────────────────────────────────────────
    @staticmethod
    def _coerce_intent(raw: Any) -> str | None:
        if isinstance(raw, str):
            token = raw.strip().upper().replace("-", "_").replace(" ", "_")
            if token in ALL_INTENTS:
                return token
        return None

    @classmethod
    def _keyword_intent(cls, message: str) -> str:
        lowered = message.lower()
        for intent, keywords in _INTENT_KEYWORDS:
            if any(kw in message or kw in lowered for kw in keywords):
                return intent
        if any(kw in message or kw in lowered for kw in _ASK_KEYWORDS):
            return "ASK"
        return "HANDOUT"

    @staticmethod
    def _ground(codes: Any, allowed: set[str]) -> list[str]:
        if not allowed or not isinstance(codes, list):
            return []
        grounded: list[str] = []
        for code in codes:
            text = str(code).strip()
            if text in allowed and text not in grounded:
                grounded.append(text)
        return grounded

    @classmethod
    def _merge_kcs(
        cls, req: IntentRouteRequest, allowed: set[str], suggested: list[str]
    ) -> list[str]:
        out: list[str] = []
        # 手动 @ 优先：有目录时按目录过滤，无目录时原样信任。
        for code in req.manual_kc_codes:
            text = str(code).strip()
            if text and text not in out and (not allowed or text in allowed):
                out.append(text)
        for code in suggested:
            if code not in out:
                out.append(code)
        if not out:
            out = cls._match_by_name(req)
        if not out:
            for code in req.recent_kc_codes:
                text = str(code).strip()
                if text and text not in out and (not allowed or text in allowed):
                    out.append(text)
        return out[:_MAX_KC]

    @staticmethod
    def _match_by_name(req: IntentRouteRequest) -> list[str]:
        msg = req.message.lower()
        out: list[str] = []
        for kc in req.course_kcs:
            name = (kc.name or "").strip().lower()
            if len(name) >= 2 and name in msg and kc.code not in out:
                out.append(kc.code)
        return out[:_MAX_KC]

    @staticmethod
    def _clean_text(value: Any) -> str:
        return value.strip() if isinstance(value, str) and value.strip() else ""

    @staticmethod
    def _coerce_confidence(value: Any) -> float:
        try:
            confidence = float(value)
        except (TypeError, ValueError):
            return 0.5
        return max(0.0, min(1.0, confidence))
