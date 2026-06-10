"""B站视频检索路由（取数层）。

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
- **面向初学者：默认只保留 ≤ ``max_duration_sec``（默认 600 秒 = 10 分钟）的短视频**，
  把动辄几十小时的「课程合集」过滤掉（``max_duration_sec=0`` 关闭过滤）；时长解析失败
  （``duration_sec==0``）的条目不参与过滤，避免误杀（真实数据 B 站均返回时长）；
- ``url = https://www.bilibili.com/video/{bvid}``。

「AI 速览」（``ai_summary``，``with_summary=true`` 默认开）：对最终卡片**批量一次** LLM 调用，
基于每个视频真实的 标题 + 简介 + 时长 概括一句话「讲了啥 / 为什么值得看」，**只概括已给信息、
禁止臆造**；LLM 不可用 / 超时 / dry-run → 留空串（前端给中性占位并标注 AI），best-effort 不阻断检索。

空结果 → HTTP 200 且 ``items=[]``；检索/风控异常 → HTTP 502 结构化错误
``{"code": "VIDEO_SEARCH_FAILED", "message": ...}``，后端转 ``VIDEO_SEARCH_UNAVAILABLE``。
"""

from __future__ import annotations

import asyncio
import html
import json as _json
import os
import re
from typing import Any
from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, Field

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse, provider_from_env

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

# 推荐时长上限（秒）：面向初学者只推 ≤10 分钟的短视频，过滤掉几十小时的「课程合集」。
# 0 表示不过滤；路由 query 可覆盖（默认即此值）。
_DEFAULT_MAX_DURATION_SEC = 600

# 「AI 速览」一句话上限与生成超时：一句话定位，超时/失败一律降级为空串（best-effort，
# 不阻塞、不报错、不伪造）。前端对空串给中性占位并标注 AI。
_SUMMARY_MAX_CHARS = 48
_SUMMARY_TIMEOUT_SEC = 12.0

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
    # UP 主填写的视频简介（B站 search 的 ``description``）：去标签/反转义并截断。
    # 仅作为生成「AI 速览」的输入素材，**不再直接展示**给学生（前端改显示 ai_summary）。
    description: str = ""
    # 「AI 速览」一句话：基于本视频真实 标题 + 简介 + 时长 由 LLM 概括「讲了啥/为什么值得看」，
    # 防幻觉（只概括已给信息）。LLM 不可用/超时/dry-run → 空串（前端给中性占位，不伪造）。
    ai_summary: str = ""
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


def _within_duration(duration_sec: int, max_duration_sec: int) -> bool:
    """是否满足时长上限：上限 ≤0 不过滤；时长 0（解析失败）一律保留，避免误杀。"""
    if max_duration_sec <= 0:
        return True
    if duration_sec <= 0:
        return True
    return duration_sec <= max_duration_sec


def _map_items(
    raw: dict[str, Any] | None,
    limit: int,
    keyword: str = "",
    max_duration_sec: int = 0,
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
        duration_sec = _duration_to_seconds(entry.get("duration"))
        # ≤10 分钟过滤：丢弃明显超长的「课程合集」，只给初学者推短视频。
        if not _within_duration(duration_sec, max_duration_sec):
            continue
        candidates.append(
            BilibiliVideoItem(
                bvid=bvid,
                title=title,
                author=str(entry.get("author", "") or "").strip(),
                description=_clean_description(str(entry.get("description", "") or "")),
                cover=_https_cover(str(entry.get("pic", "") or "")),
                duration_sec=duration_sec,
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


# 「AI 速览」system 提示：强约束只概括已给信息、禁止臆造，输出结构化 JSON。
_SUMMARY_SYSTEM = (
    "你是 Pythontopia 的视频速览助手，面向零基础 Python 初学者。"
    "我会给你若干 B 站视频的【标题】【时长(秒)】【UP主简介】。"
    f"请仅依据这些信息，为每个视频写一句话（不超过 {_SUMMARY_MAX_CHARS} 字）的"
    "『这视频讲了啥 / 为什么值得初学者看』。硬性要求："
    "1) 只能概括标题/简介里出现过的主题，严禁臆造视频里没提到的具体内容、章节、数字、结论；"
    "2) 可结合时长给出定位（如『N 分钟速览』）；"
    "3) 信息不足时给中性定位（如『Python 入门讲解，适合快速了解』），不要编造；"
    "4) 简体中文，口语、好懂、不夸张。"
    '只输出 JSON：{"summaries":[{"i":<下标整数>,"summary":"…"}]}'
)


def _coerce_summary_map(resp: LlmResponse) -> dict[int, str]:
    """把 LLM 返回解析成 {下标: 一句话}；任何异常返回空 map（不报错）。"""
    data: Any = resp.content_json
    if data is None and resp.content_text:
        try:
            data = _json.loads(resp.content_text)
        except (ValueError, TypeError):
            data = None
    if not isinstance(data, dict):
        return {}
    raw = data.get("summaries")
    out: dict[int, str] = {}
    if isinstance(raw, list):
        for entry in raw:
            if not isinstance(entry, dict):
                continue
            try:
                idx = int(entry.get("i"))
            except (TypeError, ValueError):
                continue
            text = entry.get("summary")
            if isinstance(text, str):
                cleaned = re.sub(r"\s+", " ", text).strip()
                if cleaned:
                    out[idx] = cleaned[:_SUMMARY_MAX_CHARS]
    return out


async def _summarize_videos(
    items: list[BilibiliVideoItem], *, provider: LlmProvider | None = None
) -> None:
    """就地给每个视频填 ``ai_summary``（best-effort，1 次批量 LLM 调用）。

    LLM 不可用 / 超时 / dry-run / 解析失败 → 原样不动（空串），绝不阻断检索、不伪造。
    """
    if not items:
        return
    try:
        prov = provider if provider is not None else provider_from_env()
    except Exception:  # noqa: BLE001 - provider 构造失败：不加速览，不阻断检索
        return
    payload = [
        {"i": idx, "title": v.title, "duration_sec": v.duration_sec, "intro": v.description}
        for idx, v in enumerate(items)
    ]
    messages = [
        {"role": "system", "content": _SUMMARY_SYSTEM},
        {"role": "user", "content": _json.dumps(payload, ensure_ascii=False)},
    ]
    try:
        resp = await asyncio.wait_for(
            prov.chat(
                messages, response_format={"type": "json_object"}, temperature=0.3
            ),
            timeout=_SUMMARY_TIMEOUT_SEC,
        )
    except TypeError:
        # 个别 provider 不接受 response_format/temperature kwargs：退化为最简调用。
        try:
            resp = await asyncio.wait_for(
                prov.chat(messages), timeout=_SUMMARY_TIMEOUT_SEC
            )
        except Exception:  # noqa: BLE001
            return
    except Exception:  # noqa: BLE001 - 超时/网络/限流：best-effort 降空
        return
    summary_map = _coerce_summary_map(resp)
    for idx, v in enumerate(items):
        text = summary_map.get(idx)
        if text:
            v.ai_summary = text


@router.get("/agents/video/bilibili/search", response_model=BilibiliSearchResponse)
async def search_bilibili(
    keyword: str = Query(..., min_length=1, max_length=200),
    limit: int = Query(8, ge=1, le=50),
    # 默认只回 ≤10 分钟短视频；传 0 关闭过滤（需要长课程合集时显式覆盖）。
    max_duration_sec: int = Query(_DEFAULT_MAX_DURATION_SEC, ge=0, le=86400),
    # 是否生成每张卡的「AI 速览」一句话（默认开；为 false 跳过 LLM 调用，省时）。
    with_summary: bool = Query(True),
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
    items = _map_items(raw, limit, keyword, max_duration_sec)
    if with_summary:
        await _summarize_videos(items)
    return BilibiliSearchResponse(items=items)
