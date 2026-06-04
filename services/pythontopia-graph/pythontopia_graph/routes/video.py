"""B站视频检索路由（方案 A · 取数层）。

* ``GET /agents/video/bilibili/search?keyword=...&limit=8`` → :class:`BilibiliSearchResponse`

取数库 ``bilibili-api-python`` 是 GPL-3.0-or-later，**惰性 import** 在
:func:`_bilibili_search_videos` 内部完成——这样应用启动、``main`` 导入、单元测试都不需要
真实安装该包，也不会发起网络请求（测试 monkeypatch 这个函数即可）。后端（Spring）只做 HTTP
代理，GPL 隔离在本微服务。

清洗规则（fail-fast，不静默降级成空聊天）：
- 标题去掉 ``<em class="keyword">…</em>`` 高亮标签并 unescape HTML 实体；
- 封面统一成 ``https:`` 开头（B站返回 ``//i*.hdslb.com/...``）；
- ``duration`` 形如 ``"MM:SS"`` / ``"HH:MM:SS"`` 解析成秒；
- 丢弃付费课程（``cheese`` / 课程付费）与缺 ``bvid`` 的卡片；
- ``url = https://www.bilibili.com/video/{bvid}``。

空结果 → HTTP 200 且 ``items=[]``；检索/风控异常 → HTTP 502 结构化错误
``{"code": "VIDEO_SEARCH_FAILED", "message": ...}``，后端转 ``VIDEO_SEARCH_UNAVAILABLE``。
"""

from __future__ import annotations

import html
import re
from typing import Any

from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, Field

router = APIRouter()

# 任意 HTML 标签（覆盖 B站标题里的 <em class="keyword"> / </em> 高亮）。
_HTML_TAG_PATTERN = re.compile(r"<[^>]+>")

# 付费/课程类卡片：不属于普通视频，过滤掉（B站搜索偶尔混入）。
_PAID_TYPES = frozenset({"cheese", "ketang", "course", "course_ep"})


class BilibiliVideoItem(BaseModel):
    """单条已清洗的 B站视频卡片。字段名与后端 ``BilibiliVideoDto`` 的 snake_case 对齐。"""

    bvid: str
    title: str
    author: str
    cover: str
    duration_sec: int = Field(ge=0)
    play: int = Field(ge=0)
    danmaku: int = Field(ge=0)
    pubdate: int = Field(ge=0)
    url: str


class BilibiliSearchResponse(BaseModel):
    items: list[BilibiliVideoItem] = Field(default_factory=list)


async def _bilibili_search_videos(keyword: str, page: int) -> dict[str, Any]:
    """惰性 import + 调用 ``bilibili_api.search.search_by_type``（库自带 WBI 签名 + 反爬）。

    单独抽成模块级函数，是为了给测试一个**不触发真实 import / 网络**的 monkeypatch 接缝。
    """
    from bilibili_api import search  # noqa: PLC0415 - 惰性 import：缺包/无网时不影响应用导入与单测

    result: dict[str, Any] = await search.search_by_type(
        keyword=keyword,
        search_type=search.SearchObjectType.VIDEO,
        page=page,
    )
    return result


def _clean_title(raw: str) -> str:
    return html.unescape(_HTML_TAG_PATTERN.sub("", raw or "")).strip()


def _https_cover(raw: str) -> str:
    cover = (raw or "").strip()
    if not cover:
        return ""
    if cover.startswith("//"):
        return "https:" + cover
    if cover.startswith("http://"):
        return "https://" + cover[len("http://") :]
    return cover


def _duration_to_seconds(raw: object) -> int:
    """``"MM:SS"`` / ``"HH:MM:SS"`` → 秒；已是数字则直接取整；无法解析回 0。"""
    if isinstance(raw, bool):
        return 0
    if isinstance(raw, (int, float)):
        return max(0, int(raw))
    if not isinstance(raw, str):
        return 0
    text = raw.strip()
    if not text:
        return 0
    parts = text.split(":")
    seconds = 0
    for part in parts:
        chunk = part.strip()
        if not chunk.isdigit():
            return 0
        seconds = seconds * 60 + int(chunk)
    return max(0, seconds)


def _to_int(value: object) -> int:
    """B站计数字段可能是 int / float / "--"（隐藏）等，统一成非负 int。"""
    if isinstance(value, bool):
        return 0
    if isinstance(value, (int, float)):
        return max(0, int(value))
    if isinstance(value, str):
        text = value.strip()
        if text.isdigit():
            return int(text)
    return 0


def _map_items(raw: dict[str, Any] | None, limit: int) -> list[BilibiliVideoItem]:
    if not isinstance(raw, dict):
        return []
    results = raw.get("result")
    if not isinstance(results, list):
        return []
    items: list[BilibiliVideoItem] = []
    for entry in results:
        if not isinstance(entry, dict):
            continue
        if str(entry.get("type", "")).lower() in _PAID_TYPES:
            continue
        bvid = str(entry.get("bvid", "") or "").strip()
        if not bvid:
            continue
        items.append(
            BilibiliVideoItem(
                bvid=bvid,
                title=_clean_title(str(entry.get("title", "") or "")),
                author=str(entry.get("author", "") or "").strip(),
                cover=_https_cover(str(entry.get("pic", "") or "")),
                duration_sec=_duration_to_seconds(entry.get("duration")),
                play=_to_int(entry.get("play")),
                danmaku=_to_int(entry.get("video_review", entry.get("danmaku"))),
                pubdate=_to_int(entry.get("pubdate")),
                url=f"https://www.bilibili.com/video/{bvid}",
            )
        )
        if len(items) >= limit:
            break
    return items


@router.get("/agents/video/bilibili/search", response_model=BilibiliSearchResponse)
async def search_bilibili(
    keyword: str = Query(..., min_length=1, max_length=200),
    limit: int = Query(8, ge=1, le=50),
) -> BilibiliSearchResponse:
    try:
        raw = await _bilibili_search_videos(keyword, page=1)
    except Exception as exc:  # noqa: BLE001 - fail-fast：结构化 502，不静默降级成空聊天
        raise HTTPException(
            status_code=502,
            detail={"code": "VIDEO_SEARCH_FAILED", "message": str(exc)},
        ) from exc
    return BilibiliSearchResponse(items=_map_items(raw, limit))
