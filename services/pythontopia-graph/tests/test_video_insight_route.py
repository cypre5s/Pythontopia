"""B站视频概述路由测试（FastAPI TestClient）。

通过 monkeypatch ``video_insight._fetch_bilibili_metadata``（取数）与
``video_insight.provider_from_env``（LLM）双接缝，惰性 import 的真实 ``bilibili_api`` 与真实
大模型**都不会执行**：既不需要安装依赖也不发任何网络请求。与 tests/test_video_route.py 一致，
不用 ``with`` 上下文（避免 lifespan 注入 app.state.llm_provider，确保走 provider_from_env 接缝）。

覆盖四条：①有 outline→真实时间点且按问题相关度排序 ②无 outline 仅元数据→看点空但概述非空、
based_on=metadata_only ③LLM 不可用→degraded ④LLM 编造的非法 t_sec 被 timeline_grounding 剔除。
"""

from __future__ import annotations

import pytest
import logging
from fastapi.testclient import TestClient
from types import SimpleNamespace

import pythontopia_graph.routes.video_insight as video_insight_route
import pythontopia_graph.routes.video as video_route
from pythontopia_graph.llm.provider import LlmResponse
from pythontopia_graph.main import app
from pythontopia_graph.nodes.timeline_grounding import TimelineSegment
from pythontopia_graph.routes.video_insight import VideoMeta


def test_bilibili_proxy_configuration_does_not_log_proxy_credentials(
    monkeypatch: pytest.MonkeyPatch, caplog: pytest.LogCaptureFixture
) -> None:
    from types import ModuleType

    proxy = "http://proxy-user:proxy-secret@proxy.example:8080/?token=hidden"
    calls: list[str] = []
    request_settings = SimpleNamespace(
        set_proxy=lambda value: calls.append(value),
        set_timeout=lambda _value: None,
    )
    fake_bilibili = ModuleType("bilibili_api")
    fake_bilibili.request_settings = request_settings  # type: ignore[attr-defined]
    monkeypatch.setitem(__import__("sys").modules, "bilibili_api", fake_bilibili)
    monkeypatch.setattr(video_route, "_proxy_configured", False)
    monkeypatch.setenv("PYT_BILI_PROXY", proxy)
    monkeypatch.delenv("HTTP_PROXY", raising=False)
    monkeypatch.delenv("HTTPS_PROXY", raising=False)
    monkeypatch.delenv("https_proxy", raising=False)
    caplog.set_level(logging.INFO, logger="pythontopia_graph.routes.video")

    video_route._configure_bilibili_proxy()

    assert calls == [proxy]
    assert proxy not in caplog.text
    assert "proxy-secret" not in caplog.text
    assert "token=hidden" not in caplog.text


@pytest.mark.asyncio
async def test_metadata_fetch_does_not_call_external_ai_summary(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class FakeVideo:
        ai_summary_called = False

        async def get_info(self) -> dict[str, str]:
            return {"title": "Python 入门", "desc": "基础语法"}

        async def get_pages(self) -> list[dict[str, object]]:
            return [{"part": "第一章", "duration": 60}, {"part": "第二章", "duration": 45}]

        async def get_ai_conclusion(self, **_kwargs: object) -> None:
            self.ai_summary_called = True

    fake_video = FakeVideo()
    fake_module = SimpleNamespace(video=SimpleNamespace(Video=lambda **_kwargs: fake_video))
    monkeypatch.setitem(__import__("sys").modules, "bilibili_api", fake_module)
    monkeypatch.setattr(
        "pythontopia_graph.routes.video._configure_bilibili_proxy", lambda: None
    )

    meta = await video_insight_route._fetch_bilibili_metadata("BV1test00000")

    assert fake_video.ai_summary_called is False
    assert meta.outline_segments == []
    assert [segment.t_sec for segment in meta.page_segments] == [0, 60]


def _patch_fetch(monkeypatch: pytest.MonkeyPatch, meta: VideoMeta) -> None:
    async def fake_fetch(bvid: str) -> VideoMeta:
        return meta

    monkeypatch.setattr(video_insight_route, "_fetch_bilibili_metadata", fake_fetch)


def _patch_provider(monkeypatch: pytest.MonkeyPatch, provider: object) -> None:
    # app.state 无 provider（未跑 lifespan）→ _provider 回落到 provider_from_env()。
    monkeypatch.setattr(video_insight_route, "provider_from_env", lambda: provider)


class _JsonProvider:
    """固定返回一份结构化 JSON 的假 provider（key_points 的顺序模拟「按问题相关度排序」）。"""

    def __init__(self, payload: dict[str, object]) -> None:
        self._payload = payload

    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        return LlmResponse(content_json=self._payload)


class _RaisingProvider:
    """模拟 LLM 不可用：chat 直接抛错，Agent 应 fail-soft 成 degraded。"""

    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        raise RuntimeError("llm upstream down")


def test_insight_with_outline_returns_real_points_in_question_order(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """①有 outline：key_points 全部落在真实时间轴上、source=ai_conclusion、based_on=ai_conclusion，
    且**保留 LLM 给定的相关度顺序**（不是按时间升序）。"""
    meta = VideoMeta(
        title="Python for 循环 8 分钟入门",
        desc="讲 for 循环与 range 的用法",
        outline_segments=[
            TimelineSegment(t_sec=12, text="for 循环基本语法"),
            TimelineSegment(t_sec=45, text="range 的用法"),
            TimelineSegment(t_sec=90, text="常见易错点"),
        ],
        page_segments=[],
    )
    _patch_fetch(monkeypatch, meta)
    # 学生问 range，故 LLM 把 t_sec=45 排在最前（相关度优先），而非时间最小的 12。
    _patch_provider(
        monkeypatch,
        _JsonProvider(
            {
                "overview": "这视频讲 for 循环与 range，和你的问题对口，建议从 range 用法看起。",
                "key_points": [
                    {"t_sec": 45, "label": "range 的用法", "source": "ai_conclusion"},
                    {"t_sec": 12, "label": "for 循环基本语法", "source": "ai_conclusion"},
                    {"t_sec": 90, "label": "常见易错点", "source": "ai_conclusion"},
                ],
            }
        ),
    )

    response = TestClient(app).post(
        "/agents/video/bilibili/insight",
        json={
            "bvid": "BV1xx411c7XD",
            "question": "range 怎么用？",
            "kc_codes": ["PY43-KC-010"],
            "profile_snapshot": {"cognitive_style": "visual", "error_patterns": ["缩进"]},
        },
    )

    assert response.status_code == 200
    data = response.json()
    assert data["bvid"] == "BV1xx411c7XD"
    assert data["degraded"] is False
    assert data["based_on"] == "ai_conclusion"
    assert data["overview"]  # 非空概述
    # 顺序按问题相关度（LLM 给定），不被重排成时间升序 [12,45,90]。
    assert [kp["t_sec"] for kp in data["key_points"]] == [45, 12, 90]
    assert all(kp["source"] == "ai_conclusion" for kp in data["key_points"])


def test_insight_metadata_only_has_overview_but_no_points(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """②无 outline、单 P（无分 P 时间轴）：based_on=metadata_only、key_points 为空、overview 仍非空。

    即便 LLM 不守规矩给了候选点，无真实时间轴时 timeline_grounding 也会全部剔除。"""
    meta = VideoMeta(title="Python 杂谈", desc="随便聊聊", outline_segments=[], page_segments=[])
    _patch_fetch(monkeypatch, meta)
    _patch_provider(
        monkeypatch,
        _JsonProvider(
            {
                "overview": "这视频比较泛，和你的具体问题关系不大，可作为背景了解。",
                # 故意塞一个候选点：无真实时间轴 → 应被剔除。
                "key_points": [{"t_sec": 30, "label": "瞎编的点", "source": "ai_conclusion"}],
            }
        ),
    )

    response = TestClient(app).post(
        "/agents/video/bilibili/insight",
        json={"bvid": "BV1meta00000", "question": "装饰器怎么写？"},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["degraded"] is False
    assert data["based_on"] == "metadata_only"
    assert data["key_points"] == []
    assert data["overview"]  # 仍给概述


def test_insight_degrades_when_llm_unavailable(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """③LLM 不可用：degraded=True、key_points 空、overview 给中性占位（引用真实标题），HTTP 200。"""
    meta = VideoMeta(title="Python 列表推导式讲解", desc="x", outline_segments=[], page_segments=[])
    _patch_fetch(monkeypatch, meta)
    _patch_provider(monkeypatch, _RaisingProvider())

    response = TestClient(app).post(
        "/agents/video/bilibili/insight",
        json={"bvid": "BV1down00000", "question": "列表推导式？"},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["degraded"] is True
    assert data["key_points"] == []
    assert data["overview"]
    # 中性占位只引用真实标题，不编造内容。
    assert "Python 列表推导式讲解" in data["overview"]


def test_insight_drops_hallucinated_timestamps(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """④LLM 编造的非法 t_sec（不在真实集合里）被 timeline_grounding 硬剔除，只留真实点。"""
    meta = VideoMeta(
        title="Python 入门",
        desc="基础",
        outline_segments=[
            TimelineSegment(t_sec=12, text="for 循环基本语法"),
            TimelineSegment(t_sec=45, text="range 的用法"),
        ],
        page_segments=[],
    )
    _patch_fetch(monkeypatch, meta)
    _patch_provider(
        monkeypatch,
        _JsonProvider(
            {
                "overview": "讲 for 与 range。",
                "key_points": [
                    {"t_sec": 45, "label": "range 的用法", "source": "ai_conclusion"},
                    {"t_sec": 9999, "label": "编造的不存在的点", "source": "ai_conclusion"},
                    {"t_sec": 12, "label": "for 循环基本语法", "source": "ai_conclusion"},
                    {"t_sec": 7, "label": "另一个编造点", "source": "pages"},
                ],
            }
        ),
    )

    response = TestClient(app).post(
        "/agents/video/bilibili/insight",
        json={"bvid": "BV1hallu0000", "question": "range？"},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["degraded"] is False
    assert data["based_on"] == "ai_conclusion"
    # 9999、7 不在真实时间集合 {12,45} → 剔除；仅留 45、12（保留相关度顺序）。
    assert [kp["t_sec"] for kp in data["key_points"]] == [45, 12]
    assert all(kp["source"] == "ai_conclusion" for kp in data["key_points"])
