"""B站视频检索路由测试（FastAPI TestClient）。

通过 monkeypatch ``video._bilibili_search_videos`` 注入样例载荷——惰性 import 的真实
``bilibili_api`` 永不执行，因此既不需要安装 ``bilibili-api-python`` 也不发起任何网络请求。
与 tests/test_intent_route.py 一致，不用 ``with`` 上下文（避免 lifespan 干扰）。
"""

from __future__ import annotations

from typing import Any

import pytest
from fastapi.testclient import TestClient

import pythontopia_graph.routes.video as video_route
from pythontopia_graph.main import app

# 一份混合样例：1 条正常视频 + 1 条付费课程（应过滤）+ 1 条缺 bvid（应丢弃）。
SAMPLE_PAYLOAD: dict[str, Any] = {
    "result": [
        {
            "type": "video",
            "bvid": "BV1xx411c7XD",
            "title": '<em class="keyword">for</em> 循环 &amp; 遍历',
            "author": "UP主小明",
            "pic": "//i2.hdslb.com/bfs/archive/abc.jpg",
            "duration": "12:34",
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
    assert item["cover"] == "https://i2.hdslb.com/bfs/archive/abc.jpg"  # // → https:
    assert item["duration_sec"] == 12 * 60 + 34  # "MM:SS" → 秒
    assert item["play"] == 12345
    assert item["danmaku"] == 678  # video_review → danmaku
    assert item["pubdate"] == 1600000000
    assert item["url"] == "https://www.bilibili.com/video/BV1xx411c7XD"


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
    assert len(response.json()["items"]) == 3


def test_empty_results_returns_200_empty_items(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fake_search(keyword: str, page: int) -> dict[str, Any]:
        return {"result": []}

    monkeypatch.setattr(video_route, "_bilibili_search_videos", fake_search)

    response = TestClient(app).get(
        "/agents/video/bilibili/search", params={"keyword": "没有结果的关键词zzz"}
    )

    assert response.status_code == 200
    assert response.json() == {"items": []}


def test_missing_result_key_returns_200_empty_items(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fake_search(keyword: str, page: int) -> dict[str, Any]:
        return {"numResults": 0}  # B站无结果时可能不带 result 键

    monkeypatch.setattr(video_route, "_bilibili_search_videos", fake_search)

    response = TestClient(app).get(
        "/agents/video/bilibili/search", params={"keyword": "x"}
    )

    assert response.status_code == 200
    assert response.json() == {"items": []}


def test_search_failure_returns_502_structured_error(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fake_search(keyword: str, page: int) -> dict[str, Any]:
        raise RuntimeError("风控拦截")

    monkeypatch.setattr(video_route, "_bilibili_search_videos", fake_search)

    response = TestClient(app).get("/agents/video/bilibili/search", params={"keyword": "x"})

    assert response.status_code == 502
    detail = response.json()["detail"]
    assert detail["code"] == "VIDEO_SEARCH_FAILED"
    assert "风控拦截" in detail["message"]
