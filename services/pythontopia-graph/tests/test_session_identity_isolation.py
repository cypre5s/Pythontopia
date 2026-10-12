"""Graph stateful sessions must be scoped by the authenticated learner identity.

These tests intentionally exercise the storage/checkpoint boundary directly.  HTTP
authentication is covered separately in ``test_internal_auth.py``; keeping both layers
under test prevents a future route refactor from accidentally returning to a bare
``session_id`` key.
"""

from __future__ import annotations

import asyncio

import pytest

from pythontopia_graph.graph import orchestrator_session
from pythontopia_graph.graph import tutor_workflow_session
from pythontopia_graph.graph.orchestrator_graph import OrchestratorGraph
from pythontopia_graph.schemas.orchestrator import OrchestratorTurn


@pytest.mark.asyncio
async def test_orchestrator_same_session_id_is_isolated_by_user() -> None:
    session_id = "shared-browser-session"
    await orchestrator_session.reset_session(101, session_id)
    await orchestrator_session.reset_session(202, session_id)

    await orchestrator_session.append_turns(
        101,
        session_id,
        [OrchestratorTurn(role="user", content="user-101-private")],
    )

    assert [turn.content for turn in await orchestrator_session.get_history(101, session_id)] == [
        "user-101-private"
    ]
    assert await orchestrator_session.get_history(202, session_id) == []


@pytest.mark.asyncio
async def test_tutor_workflow_same_session_id_is_isolated_by_user() -> None:
    session_id = "shared-workflow-session"
    await tutor_workflow_session.reset_session(101, session_id)
    await tutor_workflow_session.reset_session(202, session_id)

    await tutor_workflow_session.commit_run(
        101,
        session_id,
        phase="IDEATING",
        card_key="analysis",
        card_id="u101-card",
        short_text="user-101-private",
    )

    owner = await tutor_workflow_session.get_state(101, session_id)
    other = await tutor_workflow_session.get_state(202, session_id)
    assert owner.phase == "IDEATING"
    assert owner.cards[0]["short_text"] == "user-101-private"
    assert other.phase == "READING"
    assert other.cards == []


@pytest.mark.asyncio
async def test_scoped_reset_does_not_delete_another_users_session() -> None:
    session_id = "shared-reset-session"
    for user_id in (101, 202):
        await orchestrator_session.reset_session(user_id, session_id)
        await orchestrator_session.append_turns(
            user_id,
            session_id,
            [OrchestratorTurn(role="user", content=f"private-{user_id}")],
        )

    await orchestrator_session.reset_session(101, session_id)

    assert await orchestrator_session.get_history(101, session_id) == []
    assert [turn.content for turn in await orchestrator_session.get_history(202, session_id)] == [
        "private-202"
    ]


@pytest.mark.asyncio
async def test_concurrent_writes_remain_inside_each_identity_scope() -> None:
    session_id = "shared-concurrent-session"
    await orchestrator_session.reset_session(101, session_id)
    await orchestrator_session.reset_session(202, session_id)

    async def append(user_id: int, index: int) -> None:
        await orchestrator_session.append_turns(
            user_id,
            session_id,
            [OrchestratorTurn(role="user", content=f"u{user_id}-{index}")],
        )

    await asyncio.gather(
        *(append(user_id, index) for user_id in (101, 202) for index in range(10))
    )

    for user_id in (101, 202):
        contents = {
            turn.content
            for turn in await orchestrator_session.get_history(user_id, session_id)
        }
        assert contents == {f"u{user_id}-{index}" for index in range(10)}


def test_checkpoint_thread_id_includes_identity_and_does_not_adopt_legacy_key() -> None:
    user_101 = OrchestratorGraph.turn_thread_id(101, "legacy-session", 0)
    user_202 = OrchestratorGraph.turn_thread_id(202, "legacy-session", 0)

    assert user_101 != user_202
    assert user_101 != "legacy-session:turn-0"
    assert user_202 != "legacy-session:turn-0"
