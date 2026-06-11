"""B站视频「基于学生问题的概述 + 关键时间点」路由（Track A）。

``POST /agents/video/bilibili/insight`` — body=:class:`VideoInsightRequest`，
resp=:class:`VideoInsightResponse`。链路：

1. 取数（best-effort）：复用 ``routes/video.py`` 的 ``bilibili-api`` **惰性 import** 模式——
   在 :func:`_fetch_bilibili_metadata` 内部 ``import bilibili_api``，给单测留 monkeypatch 接缝
   （测试替换该函数即可，永不触发真实 import / 网络）。用 ``video.Video(bvid)`` 取 ``get_info``
   （标题 / 简介）、``get_pages``（分 P），并**尝试**官方 AI 总结 ``get_ai_conclusion``
   （bilibili-api 17.x：``page_index=0`` 内部解析 cid 与 up_mid；可能需登录态 / 触发风控 /
   该视频本就没有总结 → 失败即跳过，不报错）。
2. 概述：把元数据交给 :class:`VideoInsightAgent` 产出 overview + 候选 key_points。
3. 防幻觉：候选 key_points 过 :func:`timeline_grounding`，剔除 ``t_sec`` 不在真实时间轴里的点，
   并据可用数据裁定 ``based_on``。

全程 fail-soft：取不到 outline → ``based_on=metadata_only``、``key_points=[]``、仍给 overview；
LLM / 整体异常 → ``degraded=True`` 且 **HTTP 200**，绝不阻断（概述是补充功能）。
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass, field
from typing import Any, cast

from fastapi import APIRouter, Depends, Request

from pythontopia_graph.agents.video_insight_agent import (
    VideoInsightAgent,
    build_degraded_response,
)
from pythontopia_graph.llm.provider import LlmProvider, provider_from_env
from pythontopia_graph.nodes.timeline_grounding import (
    TimelineSegment,
    coerce_t_sec,
    timeline_grounding,
)
from pythontopia_graph.schemas.video_insight import (
    VideoInsightRequest,
    VideoInsightResponse,
)

router = APIRouter()

_LOG = logging.getLogger(__name__)

# 时间轴段文本上限：仅用于喂 LLM 概括 label，截断保证 prompt 不膨胀。
_SEG_TEXT_CHARS = 120


def _clean(text: str) -> str:
    return re.sub(r"\s+", " ", text or "").strip()


@dataclass(frozen=True)
class VideoMeta:
    """已归一化的视频元数据：标题 / 简介 + 两类真实时间轴（outline / 分 P）。"""

    title: str = ""
    desc: str = ""
    outline_segments: list[TimelineSegment] = field(default_factory=list)
    page_segments: list[TimelineSegment] = field(default_factory=list)


def _video_title_desc(info: object) -> tuple[str, str]:
    if not isinstance(info, dict):
        return "", ""
    return _clean(str(info.get("title") or "")), _clean(str(info.get("desc") or ""))


def _extract_outline_segments(ai_conclusion: object) -> list[TimelineSegment]:
    """官方 AI 总结 → 时间轴段。优先细粒度 ``part_outline``，缺失时回退段标题 + 段起始秒。

    结构（bilibili-api 17.x 已解包至 data）：
    ``{"model_result":{"outline":[{"title","timestamp","part_outline":[{"timestamp","content"}]}]}}``。
    任意层级缺失 / 非预期类型 → 返回空（best-effort，不报错）。
    """
    if not isinstance(ai_conclusion, dict):
        return []
    model_result = ai_conclusion.get("model_result")
    if not isinstance(model_result, dict):
        return []
    outline = model_result.get("outline")
    if not isinstance(outline, list):
        return []
    segments: list[TimelineSegment] = []
    for section in outline:
        if not isinstance(section, dict):
            continue
        parts = section.get("part_outline")
        collected = False
        if isinstance(parts, list):
            for part in parts:
                if not isinstance(part, dict):
                    continue
                t_sec = coerce_t_sec(part.get("timestamp"))
                text = _clean(str(part.get("content") or ""))
                if t_sec is not None and text:
                    segments.append(
                        TimelineSegment(t_sec=t_sec, text=text[:_SEG_TEXT_CHARS])
                    )
                    collected = True
        if not collected:
            t_sec = coerce_t_sec(section.get("timestamp"))
            text = _clean(str(section.get("title") or ""))
            if t_sec is not None and text:
                segments.append(
                    TimelineSegment(t_sec=t_sec, text=text[:_SEG_TEXT_CHARS])
                )
    return segments


def _extract_page_segments(pages: object) -> list[TimelineSegment]:
    """分 P → 各 P 起始秒（累加各 P 时长）。

    单 P 视频没有有意义的「分 P 时间点」（整段即全片），返回空 → ``based_on`` 落到
    ``metadata_only``。多 P 时按 ``duration`` 累加得各 P 起始秒（粗粒度章节式定位，best-effort）。
    """
    if not isinstance(pages, list) or len(pages) < 2:
        return []
    segments: list[TimelineSegment] = []
    offset = 0
    for index, page in enumerate(pages):
        if not isinstance(page, dict):
            continue
        title = _clean(str(page.get("part") or "")) or f"P{index + 1}"
        segments.append(TimelineSegment(t_sec=offset, text=title[:_SEG_TEXT_CHARS]))
        offset += coerce_t_sec(page.get("duration")) or 0
    return segments


async def _try_ai_conclusion(video_obj: Any) -> dict[str, Any] | None:
    """尝试官方 AI 总结（best-effort）：失败 / 不可用 / 需登录 → ``None``，绝不抛。"""
    try:
        result = await video_obj.get_ai_conclusion(page_index=0)
    except Exception:  # noqa: BLE001 - 风控/未登录/该视频无总结：跳过，不阻断
        return None
    return result if isinstance(result, dict) else None


async def _fetch_bilibili_metadata(bvid: str) -> VideoMeta:
    """惰性 import + 调用 ``bilibili_api.video.Video`` 取真实元数据并归一化。

    单独抽成模块级函数，给测试一个**不触发真实 import / 网络**的 monkeypatch 接缝
    （与 ``routes/video.py._bilibili_search_videos`` 同款）。
    """
    from bilibili_api import video  # noqa: PLC0415 - 惰性 import：缺包/无网时不影响导入与单测

    from pythontopia_graph.routes.video import _configure_bilibili_proxy

    _configure_bilibili_proxy()
    video_obj = video.Video(bvid=bvid)
    info = await video_obj.get_info()
    title, desc = _video_title_desc(info)
    pages_raw = await video_obj.get_pages()
    outline_raw = await _try_ai_conclusion(video_obj)
    return VideoMeta(
        title=title,
        desc=desc,
        outline_segments=_extract_outline_segments(outline_raw),
        page_segments=_extract_page_segments(pages_raw),
    )


def _provider(request: Request) -> LlmProvider | None:
    """复用启动期共享 provider；缺失则现取。构造失败返回 ``None``（降级而非阻断）。"""
    provider = getattr(request.app.state, "llm_provider", None)
    if provider is not None:
        return cast(LlmProvider, provider)
    try:
        return provider_from_env()
    except Exception:  # noqa: BLE001 - 概述是补充功能，provider 不可用走降级
        return None


async def _safe_fetch(bvid: str) -> VideoMeta:
    try:
        return await _fetch_bilibili_metadata(bvid)
    except Exception:  # noqa: BLE001 - 取数失败：best-effort，仍给降级概述
        _LOG.warning("bilibili insight fetch failed for %s", bvid, exc_info=True)
        return VideoMeta()


async def _safe_insight(
    provider: LlmProvider | None, body: VideoInsightRequest, meta: VideoMeta
) -> VideoInsightResponse:
    if provider is None:
        return build_degraded_response(body.bvid, meta.title)
    try:
        return await VideoInsightAgent(provider).insight(
            body,
            title=meta.title,
            desc=meta.desc,
            pages=meta.page_segments,
            outline=meta.outline_segments,
        )
    except Exception:  # noqa: BLE001 - 纵深兜底：Agent 内部已 fail-soft，这里再保一层
        _LOG.warning("video insight agent failed for %s", body.bvid, exc_info=True)
        return build_degraded_response(body.bvid, meta.title)


@router.post("/agents/video/bilibili/insight", response_model=VideoInsightResponse)
async def video_insight(
    body: VideoInsightRequest,
    provider: LlmProvider | None = Depends(_provider),
) -> VideoInsightResponse:
    meta = await _safe_fetch(body.bvid)
    agent_resp = await _safe_insight(provider, body, meta)
    # 防幻觉硬约束：候选 key_points 只保留 t_sec 命中真实时间轴的点，并据可用数据定 based_on。
    grounded = timeline_grounding(
        agent_resp.key_points,
        outline_timestamps={seg.t_sec for seg in meta.outline_segments},
        pages_timestamps={seg.t_sec for seg in meta.page_segments},
    )
    return VideoInsightResponse(
        bvid=body.bvid,
        overview=agent_resp.overview,
        key_points=grounded.key_points,
        based_on=grounded.based_on,
        degraded=agent_resp.degraded,
    )
