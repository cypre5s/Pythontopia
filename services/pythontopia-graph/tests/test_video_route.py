"""B站视频检索路由测试（FastAPI TestClient）。

通过 monkeypatch ``video._bilibili_search_videos`` 注入样例载荷——惰性 import 的真实
``bilibili_api`` 永不执行，因此既不需要安装 ``bilibili-api-python`` 也不发起任何网络请求。
与 tests/test_intent_route.py 一致，不用 ``with`` 上下文（避免 lifespan 干扰）。
"""

from __future__ import annotations

import asyncio
import sys
from types import SimpleNamespace
from typing import Any

import pytest
from fastapi.testclient import TestClient

import pythontopia_graph.routes.video as video_route
from pythontopia_graph.llm.provider import LlmResponse
from pythontopia_graph.main import app

# 一份混合样例：1 条正常视频 + 1 条付费课程（应过滤）+ 1 条缺 bvid（应丢弃）。
SAMPLE_PAYLOAD: dict[str, Any] = {
    "result": [
        {
            "type": "video",
            "bvid": "BV1xx411c7XD",
            "title": '<em class="keyword">for</em> 循环 &amp; 遍历',
            "author": "UP主小明",
            "description": '本视频讲解 <em class="keyword">for</em> 循环与 range 的\n用法 &amp; 常见易错',
            "pic": "//i2.hdslb.com/bfs/archive/abc.jpg",
            "duration": "08:34",
            "play": 12345,
            "video_review": 678,
            "pubdate": 1600000000,
        },
        {
            "type": "cheese",  # 付费课程
            "bvid": "BV1paidpaid0",
            "title": "付费课程不展示",
            "pic": "//i0.hdslb.com/x.jpg",
            "duration": "1:02:03",
            "play": 999,
        },
        {
            "type": "video",  # 缺 bvid
            "title": "无 bvid 的脏数据",
            "pic": "//i0.hdslb.com/y.jpg",
        },
    ]
}


def test_search_maps_and_cleans_sample_payload(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fake_search(keyword: str, page: int) -> dict[str, Any]:
        assert keyword == "for循环 Python"
        assert page == 1
        return SAMPLE_PAYLOAD

    monkeypatch.setattr(video_route, "_bilibili_search_videos", fake_search)

    response = TestClient(app).get(
        "/agents/video/bilibili/search",
        params={"keyword": "for循环 Python", "limit": 8},
    )

    assert response.status_code == 200
    items = response.json()["items"]
    # cheese 过滤 + 缺 bvid 丢弃 → 仅剩 1 条
    assert len(items) == 1
    item = items[0]
    assert item["bvid"] == "BV1xx411c7XD"
    assert item["title"] == "for 循环 & 遍历"  # <em> 去除 + &amp; 反转义
    assert item["author"] == "UP主小明"
    # 简介：去 <em> 高亮 + &amp; 反转义 + 折叠换行/空白为单空格
    assert item["description"] == "本视频讲解 for 循环与 range 的 用法 & 常见易错"
    assert item["cover"] == "https://i2.hdslb.com/bfs/archive/abc.jpg"  # // → https:
    assert item["duration_sec"] == 8 * 60 + 34  # "MM:SS" → 秒（≤10 分钟，未被过滤）
    assert item["play"] == 12345
    assert item["danmaku"] == 678  # video_review → danmaku
    assert item["pubdate"] == 1600000000
    assert item["url"] == "https://www.bilibili.com/video/BV1xx411c7XD"


def test_bilibili_retry_log_does_not_include_search_error(monkeypatch, caplog) -> None:
    async def fail_search(**kwargs):
        raise RuntimeError("video-query-secret-sentinel")

    fake_search = SimpleNamespace(
        SearchObjectType=SimpleNamespace(VIDEO="video"), search_by_type=fail_search
    )

    async def no_sleep(_seconds: float) -> None:
        return None

    monkeypatch.setitem(sys.modules, "bilibili_api", SimpleNamespace(search=fake_search))
    monkeypatch.setattr(video_route, "_configure_bilibili_proxy", lambda: None)
    monkeypatch.setattr(video_route.asyncio, "sleep", no_sleep)

    with pytest.raises(RuntimeError, match="video-query-secret-sentinel"):
        asyncio.run(video_route._bilibili_search_videos("private query", page=1))

    assert "RuntimeError" in caplog.text
    assert "video-query-secret-sentinel" not in caplog.text


def test_search_failure_response_does_not_include_upstream_error(monkeypatch) -> None:
    async def fail_search(keyword: str, page: int) -> dict[str, Any]:
        raise RuntimeError("video-query-secret-sentinel")

    monkeypatch.setattr(video_route, "_bilibili_search_videos", fail_search)
    response = TestClient(app).get(
        "/agents/video/bilibili/search",
        params={"keyword": "private query", "with_summary": "false"},
    )

    assert response.status_code == 502
    assert response.json()["detail"]["code"] == "VIDEO_SEARCH_FAILED"
    assert "video-query-secret-sentinel" not in response.text


def test_limit_is_respected(monkeypatch: pytest.MonkeyPatch) -> None:
    payload: dict[str, Any] = {
        "result": [
            {"type": "video", "bvid": f"BV{i:010d}", "title": f"t{i}", "pic": "//h/p.jpg"}
            for i in range(20)
        ]
    }

    async def fake_search(keyword: str, page: int) -> dict[str, Any]:
        return payload

    monkeypatch.setattr(video_route, "_bilibili_search_videos", fake_search)

    response = TestClient(app).get(
        "/agents/video/bilibili/search", params={"keyword": "列表", "limit": 3}
    )

    assert response.status_code == 200
    items = response.json()["items"]
    assert len(items) == 3
    # 缺 description 字段时降级为空串（前端给中性占位，不伪造简介）。
    assert all(item["description"] == "" for item in items)


def test_filters_videos_over_ten_minutes_by_default(monkeypatch: pytest.MonkeyPatch) -> None:
    """默认 max_duration_sec=600：丢弃超 10 分钟的「课程合集」，保留短视频与未知时长。"""
    payload: dict[str, Any] = {
        "result": [
            {"type": "video", "bvid": "BVshort01", "title": "5 分钟讲清 input", "duration": "05:00"},
            {"type": "video", "bvid": "BVlong001", "title": "全748集 Python 合集", "duration": "39:58:14"},
            {"type": "video", "bvid": "BVnodur01", "title": "未知时长", "pic": "//h/p.jpg"},
            {"type": "video", "bvid": "BVexact10", "title": "刚好 10 分钟", "duration": "10:00"},
        ]
    }

    async def fake_search(keyword: str, page: int) -> dict[str, Any]:
        return payload

    monkeypatch.setattr(video_route, "_bilibili_search_videos", fake_search)

    response = TestClient(app).get(
        "/agents/video/bilibili/search", params={"keyword": "input Python"}
    )

    assert response.status_code == 200
    bvids = {item["bvid"] for item in response.json()["items"]}
    # 5 分钟、未知时长、刚好 10 分钟保留；39 小时合集被过滤。
    assert bvids == {"BVshort01", "BVnodur01", "BVexact10"}


def test_max_duration_zero_disables_filter(monkeypatch: pytest.MonkeyPatch) -> None:
    """显式 max_duration_sec=0 关闭过滤：长合集也返回。"""
    payload: dict[str, Any] = {
        "result": [
            {"type": "video", "bvid": "BVlong001", "title": "全748集 Python 合集", "duration": "39:58:14"},
        ]
    }

    async def fake_search(keyword: str, page: int) -> dict[str, Any]:
        return payload

    monkeypatch.setattr(video_route, "_bilibili_search_videos", fake_search)

    response = TestClient(app).get(
        "/agents/video/bilibili/search",
        params={"keyword": "Python", "max_duration_sec": 0},
    )

    assert response.status_code == 200
    items = response.json()["items"]
    assert len(items) == 1
    assert items[0]["bvid"] == "BVlong001"


def test_ai_summary_attached_when_provider_available(monkeypatch: pytest.MonkeyPatch) -> None:
    """provider 返回结构化 summaries 时，按下标就地填到每个视频的 ai_summary。"""
    payload: dict[str, Any] = {
        "result": [
            {"type": "video", "bvid": "BVa", "title": "5分钟讲清 input", "duration": "05:00"},
            {"type": "video", "bvid": "BVb", "title": "8分钟讲 for 循环", "duration": "08:00"},
        ]
    }

    async def fake_search(keyword: str, page: int) -> dict[str, Any]:
        return payload

    monkeypatch.setattr(video_route, "_bilibili_search_videos", fake_search)

    class FakeProvider:
        async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
            return LlmResponse(
                content_json={
                    "summaries": [
                        {"i": 0, "summary": "5 分钟速览 input 输入用法"},
                        {"i": 1, "summary": "8 分钟讲清 for 循环与 range"},
                    ]
                }
            )

    monkeypatch.setattr(video_route, "provider_from_env", lambda: FakeProvider())

    response = TestClient(app).get(
        "/agents/video/bilibili/search", params={"keyword": "input Python"}
    )

    assert response.status_code == 200
    by_bvid = {it["bvid"]: it["ai_summary"] for it in response.json()["items"]}
    assert by_bvid["BVa"] == "5 分钟速览 input 输入用法"
    assert by_bvid["BVb"] == "8 分钟讲清 for 循环与 range"


def test_ai_summary_disabled_by_flag(monkeypatch: pytest.MonkeyPatch) -> None:
    """with_summary=false 时跳过 LLM，ai_summary 为空（provider 不应被调用）。"""
    payload: dict[str, Any] = {
        "result": [{"type": "video", "bvid": "BVa", "title": "5分钟讲 input", "duration": "05:00"}]
    }

    async def fake_search(keyword: str, page: int) -> dict[str, Any]:
        return payload

    monkeypatch.setattr(video_route, "_bilibili_search_videos", fake_search)

    def boom() -> object:
        raise AssertionError("provider_from_env 不应在 with_summary=false 时被调用")

    monkeypatch.setattr(video_route, "provider_from_env", boom)

    response = TestClient(app).get(
        "/agents/video/bilibili/search",
        params={"keyword": "input", "with_summary": "false"},
    )

    assert response.status_code == 200
    assert response.json()["items"][0]["ai_summary"] == ""


def test_ai_summary_empty_when_provider_unavailable(monkeypatch: pytest.MonkeyPatch) -> None:
    """provider 构造失败时 best-effort 降级：ai_summary 空串、检索仍 200。"""
    payload: dict[str, Any] = {
        "result": [{"type": "video", "bvid": "BVa", "title": "x", "duration": "05:00"}]
    }

    async def fake_search(keyword: str, page: int) -> dict[str, Any]:
        return payload

    monkeypatch.setattr(video_route, "_bilibili_search_videos", fake_search)

    def boom() -> object:
        raise RuntimeError("no llm configured")

    monkeypatch.setattr(video_route, "provider_from_env", boom)

    response = TestClient(app).get(
        "/agents/video/bilibili/search", params={"keyword": "x"}
    )

    assert response.status_code == 200
    assert response.json()["items"][0]["ai_summary"] == ""


def test_empty_results_returns_200_empty_items(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fake_search(keyword: str, page: int) -> dict[str, Any]:
        return {"result": []}

    monkeypatch.setattr(video_route, "_bilibili_search_videos", fake_search)

    response = TestClient(app).get(
        "/agents/video/bilibili/search", params={"keyword": "没有结果的关键词zzz"}
    )

    assert response.status_code == 200
    assert response.json() == {"items": []}


def test_duration_filter_relaxed_when_strict_result_empty(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """渐进放宽①：默认 ≤10 分钟过滤把结果清空时，自动放开时长上限兜底（长讲解好过没有）。"""
    payload: dict[str, Any] = {
        "result": [
            {"type": "video", "bvid": "BVlong001", "title": "Python 列表 45 分钟精讲", "duration": "45:00"},
        ]
    }

    async def fake_search(keyword: str, page: int) -> dict[str, Any]:
        return payload

    monkeypatch.setattr(video_route, "_bilibili_search_videos", fake_search)

    response = TestClient(app).get(
        "/agents/video/bilibili/search", params={"keyword": "python 列表"}
    )

    assert response.status_code == 200
    items = response.json()["items"]
    assert [it["bvid"] for it in items] == ["BVlong001"]


def test_keyword_without_python_gets_python_prefix(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """课程为纯 Python：检索词没提 python 时统一补前缀，避免短中文词检索到全站泛结果。"""
    calls: list[str] = []

    async def fake_search(keyword: str, page: int) -> dict[str, Any]:
        calls.append(keyword)
        return {
            "result": [
                {"type": "video", "bvid": "BVgood0001", "title": "Python 循环 7 分钟入门", "duration": "07:00"},
            ]
        }

    monkeypatch.setattr(video_route, "_bilibili_search_videos", fake_search)

    response = TestClient(app).get(
        "/agents/video/bilibili/search", params={"keyword": "循环怎么用"}
    )

    assert response.status_code == 200
    assert [it["bvid"] for it in response.json()["items"]] == ["BVgood0001"]
    assert calls == ["python 循环怎么用"]


def test_keyword_with_python_not_double_prefixed(monkeypatch: pytest.MonkeyPatch) -> None:
    """前端工坊本就发「Python … 教程」：已含 python 的关键词原样透传（幂等，不重复加前缀）。"""
    calls: list[str] = []

    async def fake_search(keyword: str, page: int) -> dict[str, Any]:
        calls.append(keyword)
        return {"result": []}

    monkeypatch.setattr(video_route, "_bilibili_search_videos", fake_search)

    response = TestClient(app).get(
        "/agents/video/bilibili/search", params={"keyword": "Python 循环怎么用 教程"}
    )

    assert response.status_code == 200
    assert calls == ["Python 循环怎么用 教程"]


def test_missing_result_key_returns_200_empty_items(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fake_search(keyword: str, page: int) -> dict[str, Any]:
        return {"numResults": 0}  # B站无结果时可能不带 result 键

    monkeypatch.setattr(video_route, "_bilibili_search_videos", fake_search)

    response = TestClient(app).get(
        "/agents/video/bilibili/search", params={"keyword": "x"}
    )

    assert response.status_code == 200
    assert response.json() == {"items": []}


def test_missing_bilibili_api_dependency_returns_502(monkeypatch: pytest.MonkeyPatch) -> None:
    async def missing_dependency(keyword: str, page: int) -> dict[str, Any]:
        raise ModuleNotFoundError("No module named 'bilibili_api'")

    monkeypatch.setattr(video_route, "_bilibili_search_videos", missing_dependency)

    response = TestClient(app).get(
        "/agents/video/bilibili/search",
        params={"keyword": "for循环 Python", "limit": 8},
    )

    assert response.status_code == 502
    detail = response.json()["detail"]
    assert detail["code"] == "VIDEO_SEARCH_FAILED"
    assert "bilibili-api-python" in detail["message"]


def test_search_failure_returns_502_structured_error(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fake_search(keyword: str, page: int) -> dict[str, Any]:
        raise RuntimeError("风控拦截")

    monkeypatch.setattr(video_route, "_bilibili_search_videos", fake_search)

    response = TestClient(app).get("/agents/video/bilibili/search", params={"keyword": "x"})

    assert response.status_code == 502
    detail = response.json()["detail"]
    assert detail["code"] == "VIDEO_SEARCH_FAILED"
    assert detail["message"] == "视频检索暂时不可用，请稍后重试。"
