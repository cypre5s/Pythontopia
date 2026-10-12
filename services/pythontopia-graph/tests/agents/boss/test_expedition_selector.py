"""无尽远征知识前沿选择器单测（纯逻辑 + 路由冒烟）。"""

from __future__ import annotations

from datetime import datetime, timezone

from fastapi.testclient import TestClient

from pythontopia_graph.agents.boss.expedition_selector import select_frontier
from pythontopia_graph.agents.boss.kc_map import kc_codes_for_chapter
from pythontopia_graph.main import app


def test_weak_kc_prioritized() -> None:
    picks = select_frontier(
        mastery={}, profile_signals={"weakKcCodes": ["PY43-KC-040"]}, count=1
    )
    assert picks[0].kc_code == "PY43-KC-040"
    assert "弱项" in picks[0].reason


def test_focus_chapter_restricts_pool() -> None:
    picks = select_frontier(mastery={}, focus_chapter=2, count=40)
    ch2 = set(kc_codes_for_chapter(2))
    assert {p.kc_code for p in picks} <= ch2
    assert all(p.chapter == 2 for p in picks)


def test_persisted_recent_practice_pushed_down() -> None:
    picks = select_frontier(
        mastery={},
        last_practiced={"PY43-KC-001": "2026-06-18T00:00:00Z"},
        now=datetime(2026, 6, 19, tzinfo=timezone.utc),
        focus_chapter=1,
        count=7,
    )
    order = [p.kc_code for p in picks]
    assert order[-1] == "PY43-KC-001"  # 正式练习时间显示刚练过 → 压制到末位


def test_persisted_last_practiced_prioritizes_stale_mastered_kc() -> None:
    others = [
        c for c in kc_codes_for_chapter(1) if c not in {"PY43-KC-001", "PY43-KC-002"}
    ]
    picks = select_frontier(
        mastery={"PY43-KC-001": 0.9, "PY43-KC-002": 0.9},
        last_practiced={
            "PY43-KC-001": "2026-06-18T00:00:00Z",
            "PY43-KC-002": "2026-04-01T00:00:00Z",
        },
        now=datetime(2026, 6, 19, tzinfo=timezone.utc),
        focus_chapter=1,
        exclude=others,
        count=2,
    )
    assert [p.kc_code for p in picks][:2] == ["PY43-KC-002", "PY43-KC-001"]
    assert "久未练" in picks[0].reason


def test_difficulty_scales_with_mastery() -> None:
    others = [c for c in kc_codes_for_chapter(1) if c != "PY43-KC-001"]
    low = select_frontier(mastery={"PY43-KC-001": 0.0}, focus_chapter=1, exclude=others, count=1)
    high = select_frontier(mastery={"PY43-KC-001": 1.0}, focus_chapter=1, exclude=others, count=1)
    assert low[0].difficulty < high[0].difficulty


def test_low_confidence_mastery_gets_a_small_exploration_boost() -> None:
    picks = select_frontier(
        mastery={"PY43-KC-001": 0.6, "PY43-KC-002": 0.6},
        confidence={"PY43-KC-001": 0.2, "PY43-KC-002": 0.9},
        focus_chapter=1,
        exclude=[c for c in kc_codes_for_chapter(1) if c not in {"PY43-KC-001", "PY43-KC-002"}],
        count=2,
    )
    assert [pick.kc_code for pick in picks] == ["PY43-KC-001", "PY43-KC-002"]
    assert "证据待确认" in picks[0].reason


def test_exclude_respected_and_count() -> None:
    picks = select_frontier(mastery={}, focus_chapter=1, exclude=["PY43-KC-001"], count=3)
    codes = [p.kc_code for p in picks]
    assert "PY43-KC-001" not in codes
    assert len(picks) == 3


def test_select_frontier_route_smoke() -> None:
    client = TestClient(app)
    resp = client.post(
        "/agents/boss/select-frontier",
        json={
            "mastery": [{"kcCode": "PY43-KC-001", "masteryScore": 0.5, "confidence": 0.3}],
            "profileSignals": {"weakKcCodes": ["PY43-KC-005"]},
            "recentKcs": ["PY43-KC-001"],  # 旧客户端信号不应覆盖服务端练习证据
            "lastPracticed": {"PY43-KC-002": "2026-04-01T00:00:00Z"},
            "focusChapter": 1,
            "count": 3,
        },
    )
    assert resp.status_code == 200
    body = resp.json()
    assert len(body["picks"]) == 3
    assert body["picks"][0]["kcCode"] == "PY43-KC-005"  # 弱项优先
    for pick in body["picks"]:
        assert pick["chapter"] == 1
        assert 1 <= pick["difficulty"] <= 5


def test_route_ignores_legacy_client_recent_kcs() -> None:
    response = TestClient(app).post(
        "/agents/boss/select-frontier",
        json={"recentKcs": ["PY43-KC-001"], "focusChapter": 1, "count": 2},
    )
    assert response.status_code == 200
    assert response.json()["picks"][0]["kcCode"] == "PY43-KC-001"
