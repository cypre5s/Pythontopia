"""kc_map 单一事实源测试：与课程包 course_kc.json 逐条核对，确保映射不漂移。"""

from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path

import pytest

from pythontopia_graph.agents.boss import kc_map


def _find_course_kc_json() -> Path | None:
    """从测试文件向上找仓库内的 data/course-pack-43/json/course_kc.json。"""

    rel = Path("data") / "course-pack-43" / "json" / "course_kc.json"
    for parent in Path(__file__).resolve().parents:
        candidate = parent / rel
        if candidate.is_file():
            return candidate
    return None


def test_chapter_to_kcs_matches_course_pack() -> None:
    """kc_map.CHAPTER_TO_KCS 必须与课程包 chapter_order → kc_code 完全一致。"""

    course_kc = _find_course_kc_json()
    if course_kc is None:
        pytest.skip("course-pack-43/json/course_kc.json 不可用（环境未带 data 目录）")

    rows = json.loads(course_kc.read_text(encoding="utf-8"))
    expected: dict[int, list[str]] = defaultdict(list)
    for row in rows:
        assert row["pack_id"] == kc_map.PACK_ID
        expected[int(row["chapter_order"])].append(row["kc_code"])
    expected_sorted = {
        ch: tuple(sorted(codes, key=lambda c: int(c.rsplit("-", 1)[1])))
        for ch, codes in expected.items()
    }

    assert kc_map.CHAPTER_TO_KCS == expected_sorted
    # 课程包硬约束：7 章、65 KC。
    assert len(kc_map.CHAPTER_TO_KCS) == 7
    assert len(kc_map.all_kc_codes()) == 65


def test_chapter_default_kc_is_first_kc_of_each_chapter() -> None:
    expected_defaults = {
        1: "PY43-KC-001",
        2: "PY43-KC-008",
        3: "PY43-KC-017",
        4: "PY43-KC-031",
        5: "PY43-KC-039",
        6: "PY43-KC-048",
        7: "PY43-KC-054",
    }
    for chapter, kc in expected_defaults.items():
        assert kc_map.chapter_default_kc(chapter) == kc
        assert kc_map.kc_codes_for_chapter(chapter)[0] == kc


def test_chapter_1_default_kept_backward_compatible() -> None:
    """旧映射里唯一正确的 章1 → KC-001 必须保持不变。"""

    assert kc_map.chapter_default_kc(1) == "PY43-KC-001"


def test_chapter_default_kc_clamps_out_of_range() -> None:
    assert kc_map.chapter_default_kc(0) == kc_map.chapter_default_kc(1)
    assert kc_map.chapter_default_kc(99) == kc_map.chapter_default_kc(7)


def test_old_buggy_defaults_are_now_corrected() -> None:
    """回归：旧的「每章 10 KC」缺省值（KC-006/016/026/036/046/056）已被纠正。"""

    buggy = {2: "PY43-KC-006", 3: "PY43-KC-016", 4: "PY43-KC-026", 5: "PY43-KC-036"}
    for chapter, old_wrong in buggy.items():
        assert kc_map.chapter_default_kc(chapter) != old_wrong


def test_chapter_for_kc_round_trips() -> None:
    for chapter, codes in kc_map.CHAPTER_TO_KCS.items():
        for code in codes:
            assert kc_map.chapter_for_kc(code) == chapter


def test_chapter_for_kc_boundaries_match_frontend_ranges() -> None:
    """与前端 useEndlessRun.ts 的 kcRange 区间一致：1-7/8-16/17-30/31-38/39-47/48-53/54-65。"""

    assert kc_map.chapter_for_kc("PY43-KC-007") == 1
    assert kc_map.chapter_for_kc("PY43-KC-008") == 2
    assert kc_map.chapter_for_kc("PY43-KC-016") == 2
    assert kc_map.chapter_for_kc("PY43-KC-017") == 3
    assert kc_map.chapter_for_kc("PY43-KC-030") == 3
    assert kc_map.chapter_for_kc("PY43-KC-031") == 4
    assert kc_map.chapter_for_kc("PY43-KC-038") == 4
    assert kc_map.chapter_for_kc("PY43-KC-039") == 5
    assert kc_map.chapter_for_kc("PY43-KC-047") == 5
    assert kc_map.chapter_for_kc("PY43-KC-048") == 6
    assert kc_map.chapter_for_kc("PY43-KC-053") == 6
    assert kc_map.chapter_for_kc("PY43-KC-054") == 7
    assert kc_map.chapter_for_kc("PY43-KC-065") == 7


def test_unknown_kc_returns_none_and_not_known() -> None:
    assert kc_map.chapter_for_kc("PY43-KC-999") is None
    assert kc_map.chapter_for_kc("UNKNOWN-CODE") is None
    assert kc_map.chapter_for_kc("") is None
    assert not kc_map.is_known_kc("PY43-KC-999")
    assert kc_map.is_known_kc("PY43-KC-001")


def test_resolve_kc_route_helper_uses_course_pack() -> None:
    """routes/boss._resolve_kc 必须复用 kc_map，章号缺省解析出课程包正确 KC。"""

    from pythontopia_graph.routes.boss import _resolve_kc

    assert _resolve_kc(1, "") == "PY43-KC-001"
    assert _resolve_kc(4, "") == "PY43-KC-031"
    assert _resolve_kc(7, "") == "PY43-KC-054"
    # 覆盖值优先且去空白
    assert _resolve_kc(4, "PY43-KC-033") == "PY43-KC-033"
    assert _resolve_kc(4, "  PY43-KC-033  ") == "PY43-KC-033"
