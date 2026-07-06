from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from pythontopia_graph.agents.curriculum_mapper_agent import CurriculumMapperAgent
from pythontopia_graph.main import app
from pythontopia_graph.schemas.curriculum import (
    MapperChapter,
    MapperInput,
    MapperKc,
    MapperProfileSummary,
)


def _sample_input(level: int = 2, target: list[str] | None = None) -> MapperInput:
    chapters = [
        MapperChapter(chapter_id=i, order_index=i, title=f"第{i}章")
        for i in range(1, 6)
    ]
    kcs = [
        MapperKc(
            kc_code=f"PY43-KC-{i:03d}",
            kc_name=f"知识点 {i}",
            chapter_id=((i - 1) // 4) + 1,
            chapter_order=((i - 1) // 4) + 1,
            difficulty=(i % 3) + 1,
        )
        for i in range(1, 21)
    ]
    return MapperInput(
        user_id=1001,
        profile_summary=MapperProfileSummary(
            major="CS",
            python_self_rated_level=level,
            target_kc_codes=target or [],
            media_priority=["code", "text"],
        ),
        chapters=chapters,
        kcs=kcs,
    )


def test_mapper_default_unlocks_eight() -> None:
    agent = CurriculumMapperAgent()
    output = agent.map(_sample_input())
    assert len(output.tile_assignments) == 20
    assert len(output.unlocked_kcs) == 8
    chapter_orders = [step.chapter_order for step in output.plan_steps[:8]]
    assert all(order <= 2 for order in chapter_orders)


def test_mapper_is_deterministic() -> None:
    agent = CurriculumMapperAgent()
    first = agent.map(_sample_input())
    second = agent.map(_sample_input())
    assert first.model_dump() == second.model_dump()
    assert first.deterministic_signature == second.deterministic_signature


def test_mapper_high_level_unlocks_more() -> None:
    agent = CurriculumMapperAgent()
    output = agent.map(_sample_input(level=5))
    assert len(output.unlocked_kcs) == 11


def test_mapper_grid_layout() -> None:
    agent = CurriculumMapperAgent()
    output = agent.map(_sample_input())
    assert output.tile_assignments[0].tile_x == 2
    assert output.tile_assignments[0].tile_y == 2
    assert output.tile_assignments[4].tile_x == 6
    assert output.tile_assignments[4].tile_y == 2
    assert output.tile_assignments[5].tile_x == 2
    assert output.tile_assignments[5].tile_y == 3


def test_mapper_route_returns_assignments() -> None:
    client = TestClient(app)
    payload = _sample_input().model_dump()
    response = client.post("/agents/curriculum/map", json=payload)
    assert response.status_code == 200
    body = response.json()
    assert len(body["tile_assignments"]) == 20
    assert len(body["unlocked_kcs"]) == 8


def test_mapper_rejects_invalid_input() -> None:
    with pytest.raises(Exception):
        MapperInput(
            user_id=1,
            profile_summary=MapperProfileSummary(
                major="CS",
                python_self_rated_level=10,
                target_kc_codes=[],
                media_priority=[],
            ),
            chapters=[],
            kcs=[],
        )
