"""B站视频检索路由（方案 A · 取数层）。

* ``GET /agents/video/bilibili/search?keyword=...&limit=8`` → :class:`BilibiliSearchResponse`

取数库 ``bilibili-api-python``（GitHub: Nemo2011/bilibili-api）是 GPL-3.0-or-later，
**惰性 import** 在 :func:`_bilibili_search_videos` 内部完成——库自带 WBI 签名与反爬，
**禁止**用裸 HTTP 兜底（会 412）。应用启动/单测 monkeypatch 该函数即可，无需真实安装。
后端（Spring）只做 HTTP 代理，GPL 隔离在本微服务。

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
import os
import re
from typing import Any
from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, Field

router = APIRouter()

# 代理只配一次（幂等）。B站在部分网络环境需经代理；优先 PYT_BILI_PROXY，回退 HTTPS_PROXY。
_proxy_configured = False


def _configure_bilibili_proxy() -> None:
    """若配置了代理则设给 bilibili-api（其 httpx 客户端走该代理）。

    优先 ``PYT_BILI_PROXY``，回退标准 ``HTTPS_PROXY`` / ``https_proxy``。未配置则直连；
    老版本 bilibili-api 无 ``request_settings`` 时静默跳过（靠 httpx ``trust_env`` 兜底）。
    """
    global _proxy_configured
    if _proxy_configured:
        return
    proxy = (
        os.environ.get("PYT_BILI_PROXY")
        or os.environ.get("HTTPS_PROXY")
        or os.environ.get("https_proxy")
        or ""
    ).strip()
    if proxy:
        try:
            from bilibili_api import request_settings  # noqa: PLC0415

            request_settings.set_proxy(proxy)
        except Exception:  # noqa: BLE001 - 老版本无该 API 时忽略，不阻塞检索
            pass
    _proxy_configured = True

# 任意 HTML 标签（覆盖 B站标题里的 <em class="keyword"> / </em> 高亮）。
_HTML_TAG_PATTERN = re.compile(r"<[^>]+>")

# 简介展示上限：B站 description 可能很长，截断保证卡片版式整齐、payload 不膨胀。
_DESC_MAX_CHARS = 160

# 付费/课程类卡片：不属于普通视频，过滤掉（B站搜索偶尔混入）。
_PAID_TYPES = frozenset({"cheese", "ketang", "course", "course_ep"})

# 面向「非科班 Python 初学者」：默认 Python。若关键词没提别的语言/领域，则丢弃明显
# 跑题的结果（机器人/工控/其它编程语言）。这些词若出现在关键词里（用户主动要），则不过滤。
_OFFTOPIC_TERMS = (
    "机器人", "abb", "fanuc", "kuka", "plc", "单片机", "数控", "嵌入式", "汇编",
    "labview", "wincc", "组态", "stm32", "arduino", "树莓派", "matlab", "simulink",
    "c语言", "c++", "c#", "java", "javascript", "php", "go语言", "golang", "rust",
    "易语言", "vba", "汇编语言", "verilog", "fpga", "西门子", "三菱", "码垛",
)

# 标题里出现这些 = 强 Python 相关，排序时优先。
_PY_AFFINITY = ("python", "py", "派森", "蟒蛇")


def _is_offtopic(title_lower: str, keyword_lower: str) -> bool:
    """标题含跑题词、且关键词里没提该词 → 视为跑题（默认 Python 语境）。"""
    return any(
        term in title_lower and term not in keyword_lower for term in _OFFTOPIC_TERMS
    )


def _python_affinity(title_lower: str) -> int:
    return 1 if any(term in title_lower for term in _PY_AFFINITY) else 0


class BilibiliVideoItem(BaseModel):
    """单条已清洗的 B站视频卡片。字段名与后端 ``BilibiliVideoDto`` 的 snake_case 对齐。"""

    bvid: str
    title: str
    author: str
    # UP 主填写的视频简介（B站 search 的 ``description``）：作为「这个视频讲什么」的真实讲解，
    # 去标签/反转义并截断。空串表示 UP 主未填写（前端给中性占位，不伪造内容）。
    description: str = ""
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

    _configure_bilibili_proxy()
    result: dict[str, Any] = await search.search_by_type(
        keyword=keyword,
        search_type=search.SearchObjectType.VIDEO,
        page=page,
    )
    return result


def _clean_title(raw: str) -> str:
    return html.unescape(_HTML_TAG_PATTERN.sub("", raw or "")).strip()


def _clean_description(raw: str) -> str:
    """清洗 B站简介：去 HTML 高亮标签 + 反转义 + 折叠空白 + 截断到展示上限。"""
    text = re.sub(r"\s+", " ", _clean_title(raw)).strip()
    if len(text) > _DESC_MAX_CHARS:
        text = text[:_DESC_MAX_CHARS].rstrip() + "…"
    return text


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


def _map_items(
    raw: dict[str, Any] | None, limit: int, keyword: str = ""
) -> list[BilibiliVideoItem]:
    if not isinstance(raw, dict):
        return []
    results = raw.get("result")
    if not isinstance(results, list):
        return []
    keyword_lower = (keyword or "").lower()
    # 先收全部候选（不在此处截断），再按「跑题过滤 + Python 优先」清洗排序后取前 limit。
    candidates: list[BilibiliVideoItem] = []
    for entry in results:
        if not isinstance(entry, dict):
            continue
        if str(entry.get("type", "")).lower() in _PAID_TYPES:
            continue
        bvid = str(entry.get("bvid", "") or "").strip()
        if not bvid:
            continue
        title = _clean_title(str(entry.get("title", "") or ""))
        if _is_offtopic(title.lower(), keyword_lower):
            continue
        candidates.append(
            BilibiliVideoItem(
                bvid=bvid,
                title=title,
                author=str(entry.get("author", "") or "").strip(),
                description=_clean_description(str(entry.get("description", "") or "")),
                cover=_https_cover(str(entry.get("pic", "") or "")),
                duration_sec=_duration_to_seconds(entry.get("duration")),
                play=_to_int(entry.get("play")),
                danmaku=_to_int(entry.get("video_review", entry.get("danmaku"))),
                pubdate=_to_int(entry.get("pubdate")),
                url=f"https://www.bilibili.com/video/{bvid}",
            )
        )
    # Python 相关优先；同档按播放量降序。稳定排序保留 B站原始相关度作为次序兜底。
    candidates.sort(
        key=lambda v: (_python_affinity(v.title.lower()), v.play), reverse=True
    )
    return candidates[:limit]


@router.get("/agents/video/bilibili/search", response_model=BilibiliSearchResponse)
async def search_bilibili(
    keyword: str = Query(..., min_length=1, max_length=200),
    limit: int = Query(8, ge=1, le=50),
) -> BilibiliSearchResponse:
    try:
        raw = await _bilibili_search_videos(keyword, page=1)
    except ModuleNotFoundError as exc:
        if "bilibili_api" not in str(exc):
            raise
        raise HTTPException(
            status_code=502,
            detail={
                "code": "VIDEO_SEARCH_FAILED",
                "message": (
                    "bilibili-api-python 未安装。"
                    "请重建 pythontopia-graph 镜像或在宿主执行 "
                    "pip install -e .（依赖见 pyproject.toml）。"
                ),
            },
        ) from exc
    except Exception as exc:  # noqa: BLE001 - fail-fast：结构化 502，不静默降级成空聊天
        raise HTTPException(
            status_code=502,
            detail={"code": "VIDEO_SEARCH_FAILED", "message": str(exc)},
        ) from exc
    return BilibiliSearchResponse(items=_map_items(raw, limit, keyword))
