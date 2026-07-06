"""B站视频「基于学生问题的概述 + 关键时间点」生成 Agent（Track A）。

输入学生问题 + 视频真实元数据（标题 / 简介 / 分 P pages / 官方 AI 总结 outline）+ 画像，
由 LLM 产出 ``overview``（这视频讲了啥 / 是否对口 / 建议看哪段）与**候选** ``key_points``。

两条硬约束（与项目红线一致）：
1. ``key_points.t_sec`` 只能取自传入的真实时间轴；prompt 显式列出可选秒数并禁止编造，
   最终再由 ``nodes.timeline_grounding`` 强校验剔除（本 Agent 不是唯一防线）。
2. **fail-soft**：LLM 不可用 / 超时 / 解析失败 → ``degraded=True``，``overview`` 给中性占位、
   ``key_points=[]``；绝不抛异常、不退化成大段自然语言闲聊（结构化输出）。

最终 ``based_on`` 由 route 侧 ``timeline_grounding`` 据真实时间集合裁定，本 Agent 返回占位值。
"""

from __future__ import annotations

import asyncio
import json
import re
from typing import Any, Literal

from pythontopia_graph.agents.personalization import personalization_messages
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.timeline_grounding import TimelineSegment, coerce_t_sec
from pythontopia_graph.schemas.video_insight import (
    VideoInsightRequest,
    VideoInsightResponse,
    VideoKeyPoint,
)

AGENT_NAME = "VideoInsightAgent"

# 概述生成超时（秒）：超时按 LLM 不可用降级，绝不挂起请求（概述是补充功能）。
_INSIGHT_TIMEOUT_SEC = 20.0
# 概述 / 看点上限：概述是简要定位、看点是精选，避免「大段自然语言」与 payload 膨胀。
_OVERVIEW_MAX_CHARS = 400
_LABEL_MAX_CHARS = 120  # 与 schema VideoKeyPoint.label 上限对齐。
_MAX_KEY_POINTS = 6
# 喂给 LLM 的简介裁剪上限：足够定位主题即可。
_DESC_PROMPT_CHARS = 200

_JSON_SCHEMA_HINT = (
    '{"overview":"针对学生问题的简要概述（这视频讲了啥/是否对口/建议看哪段）",'
    '"key_points":[{"t_sec":整数秒,"label":"该时间点讲了什么","source":"ai_conclusion|pages"}]}'
)


def _strip_json_fence(text: str) -> str:
    fence = re.match(r"^\s*```(?:json)?\s*(.+?)\s*```\s*$", text, re.DOTALL)
    return fence.group(1) if fence else text


def _fold_ws(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def _neutral_overview(title: str) -> str:
    """LLM 不可用 / 解析失败时的中性占位：只引用真实标题，绝不编造视频内容。"""
    name = title.strip()
    if name:
        return (
            f"《{name}》：AI 概述暂时不可用（概述为补充功能，可稍后重试），"
            "你可直接观看视频自行判断是否对口。"
        )
    return "AI 概述暂时不可用（概述为补充功能，可稍后重试），你可直接观看视频自行判断是否对口。"


def build_degraded_response(bvid: str, title: str) -> VideoInsightResponse:
    """统一的降级响应（fail-soft）：中性占位 overview + 空看点 + ``degraded=True``。

    Agent 内部失败与 route 侧（provider 缺失 / 整体异常）共用此构造，保证降级文案单一来源。
    """
    return VideoInsightResponse(
        bvid=bvid,
        overview=_neutral_overview(title),
        key_points=[],
        based_on="metadata_only",
        degraded=True,
    )


class VideoInsightAgent:
    """针对学生问题概述 B 站视频并给出候选关键时间点；故障 fail-soft 成 degraded。"""

    AGENT_NAME = AGENT_NAME

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def insight(
        self,
        request: VideoInsightRequest,
        *,
        title: str,
        desc: str,
        pages: list[TimelineSegment],
        outline: list[TimelineSegment],
    ) -> VideoInsightResponse:
        try:
            data = await self._chat_json(
                self._messages(
                    request, title=title, desc=desc, pages=pages, outline=outline
                )
            )
        except Exception:  # noqa: BLE001 - LLM 不可用/超时：fail-soft 成 degraded，不阻断
            return build_degraded_response(request.bvid, title)
        overview = _fold_ws(str(data.get("overview") or ""))[:_OVERVIEW_MAX_CHARS]
        if not overview:
            # 解析失败 / 模型未给可用概述 → degraded（占位 overview、空 key_points）。
            return build_degraded_response(request.bvid, title)
        return VideoInsightResponse(
            bvid=request.bvid,
            overview=overview,
            key_points=self._parse_key_points(data.get("key_points")),
            # 占位：真正的 based_on 由 route 的 timeline_grounding 据真实时间集合裁定。
            based_on="metadata_only",
            degraded=False,
        )

    async def _chat_json(self, messages: list[dict[str, str]]) -> dict[str, Any]:
        try:
            response = await asyncio.wait_for(
                self.provider.chat(
                    messages, response_format={"type": "json_object"}, temperature=0.2
                ),
                timeout=_INSIGHT_TIMEOUT_SEC,
            )
        except TypeError:
            # 个别 provider 不接受 response_format/temperature kwargs：退化为最简调用。
            response = await asyncio.wait_for(
                self.provider.chat(messages), timeout=_INSIGHT_TIMEOUT_SEC
            )
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

    def _parse_key_points(self, raw: object) -> list[VideoKeyPoint]:
        """把 LLM 候选点解析成 ``VideoKeyPoint``；跳过非法/空 label/重复秒数的脏数据。

        这里的 ``source`` 只是候选标注，真实来源由 ``timeline_grounding`` 据命中集合最终判定。
        """
        if not isinstance(raw, list):
            return []
        out: list[VideoKeyPoint] = []
        seen: set[int] = set()
        for entry in raw:
            if not isinstance(entry, dict):
                continue
            t_sec = coerce_t_sec(entry.get("t_sec"))
            if t_sec is None or t_sec in seen:
                continue
            label = _fold_ws(str(entry.get("label") or ""))
            if not label:
                continue
            source: Literal["ai_conclusion", "pages"] = (
                "pages" if entry.get("source") == "pages" else "ai_conclusion"
            )
            out.append(
                VideoKeyPoint(t_sec=t_sec, label=label[:_LABEL_MAX_CHARS], source=source)
            )
            seen.add(t_sec)
            if len(out) >= _MAX_KEY_POINTS:
                break
        return out

    def _messages(
        self,
        request: VideoInsightRequest,
        *,
        title: str,
        desc: str,
        pages: list[TimelineSegment],
        outline: list[TimelineSegment],
    ) -> list[dict[str, str]]:
        question = request.question.strip() or "（学生未给出具体问题，请给中性概述）"
        kc_hint = "、".join(request.kc_codes) if request.kc_codes else "（无）"
        timeline_block = self._timeline_block(outline, pages)
        has_timeline = bool(outline or pages)
        key_point_rule = (
            "key_points 每项的 t_sec 必须**精确等于**上面列出的某个 t_sec，禁止编造其它秒数；"
            "label 用对应内容/标题概括该处讲什么；source 用对应来源；"
            "按与【学生问题】的相关度从高到低排序，最多 6 个。"
            if has_timeline
            else "本视频没有可用时间轴，key_points 必须是空数组 []，不要编造任何秒数。"
        )
        system = (
            "你是 Pythontopia 的 VideoInsightAgent，面向零基础 Python 初学者。"
            "我会给你一个 B 站视频的真实元数据与（可能存在的）时间轴，请只输出一个 JSON 对象，"
            "不要输出多余文字、不要大段抒情。结构严格如下："
            f"{_JSON_SCHEMA_HINT}。"
            "overview 用 1-3 句中文：这视频大概讲了啥、是否切合【学生问题】、建议重点看哪段；"
            "只能依据我给的标题/简介/时间轴信息概括，严禁臆造视频里没出现的章节、数字、结论。"
            f"{key_point_rule}"
        )
        user = (
            f"【学生问题】{question}\n"
            f"【相关知识点】{kc_hint}\n"
            f"【视频标题】{title or '（未知）'}\n"
            f"【视频简介】{desc[:_DESC_PROMPT_CHARS] or '（无）'}\n"
            f"【可用时间轴】\n{timeline_block}"
        )
        return [
            {"role": "system", "content": system},
            *personalization_messages(request.profile_snapshot),
            {"role": "user", "content": user},
        ]

    @staticmethod
    def _timeline_block(
        outline: list[TimelineSegment], pages: list[TimelineSegment]
    ) -> str:
        lines: list[str] = []
        for seg in outline:
            lines.append(f"[ai_conclusion] t_sec={seg.t_sec} 内容={seg.text}")
        for seg in pages:
            lines.append(f"[pages] t_sec={seg.t_sec} 标题={seg.text}")
        if not lines:
            return "（本视频暂无可用时间轴：key_points 必须为空数组）"
        return "\n".join(lines)
