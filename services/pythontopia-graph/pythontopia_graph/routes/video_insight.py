"""B站视频「基于学生问题的概述 + 关键时间点」路由（Track A）。

``POST /agents/video/bilibili/insight`` — body=:class:`VideoInsightRequest`，
resp=:class:`VideoInsightResponse`。链路：

1. 取数（best-effort）：复用 ``routes/video.py`` 的 ``bilibili-api`` **惰性 import** 模式——
   在 :func:`_fetch_bilibili_metadata` 内部 ``import bilibili_api``，给单测留 monkeypatch 接缝
   （测试替换该函数即可，永不触发真实 import / 网络）。用 ``video.Video(bvid)`` 取 ``get_info``
   （标题 / 简介）和 ``get_pages``（分 P）；不调用 B 站 AI 总结接口。
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
from typing import cast

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
    """已归一化的视频元数据：标题 / 简介 + B 站真实分 P 时间轴。"""

    title: str = ""
    desc: str = ""
    outline_segments: list[TimelineSegment] = field(default_factory=list)
    page_segments: list[TimelineSegment] = field(default_factory=list)


def _video_title_desc(info: object) -> tuple[str, str]:
    if not isinstance(info, dict):
        return "", ""
    return _clean(str(info.get("title") or "")), _clean(str(info.get("desc") or ""))


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
    return VideoMeta(
        title=title,
        desc=desc,
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
    except Exception as exc:  # noqa: BLE001 - 取数失败：best-effort，仍给降级概述
        _LOG.warning("bilibili insight fetch failed error_type=%s", type(exc).__name__)
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
    except Exception as exc:  # noqa: BLE001 - 纵深兜底：Agent 内部已 fail-soft，这里再保一层
        _LOG.warning("video insight agent failed error_type=%s", type(exc).__name__)
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
