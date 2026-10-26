"""Graph stateful sessions must be scoped by the authenticated learner identity.

These tests intentionally exercise the storage/checkpoint boundary directly.  HTTP
authentication is covered separately in ``test_internal_auth.py``; keeping both layers
under test prevents a future route refactor from accidentally returning to a bare
``session_id`` key.
"""

from __future__ import annotations

import asyncio
from collections import OrderedDict

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
async def test_tutor_cache_evicts_old_inactive_sessions_but_keeps_active_run(monkeypatch) -> None:
    store = tutor_workflow_session
    monkeypatch.setattr(store, "_STORE", OrderedDict())
    monkeypatch.setattr(store, "_ACTIVE_RUNS", set())
    monkeypatch.setattr(store, "MAX_CACHED_SESSIONS", 2, raising=False)

    await store.restore_phase(303, "older", "CODING", 1)
    await store.restore_phase(303, "active", "IDEATING", 2)
    assert await store.claim_run(303, "active")
    await store.restore_phase(303, "newer", "TRANSFER", 3)

    assert len(store._STORE) == 2
    assert (303, "active") in store._STORE
    assert (303, "older") not in store._STORE
    assert (await store.get_state(303, "active")).phase == "IDEATING"

    await store.release_run(303, "active")


@pytest.mark.asyncio
async def test_orchestrator_session_cache_is_bounded_and_keeps_in_flight_session(monkeypatch) -> None:
    store = orchestrator_session
    monkeypatch.setattr(store, "_STORE", OrderedDict())
    monkeypatch.setattr(store, "_ACTIVE_RUNS", set(), raising=False)
    monkeypatch.setattr(store, "MAX_CACHED_SESSIONS", 2, raising=False)

    await store.append_turns(404, "older", [OrchestratorTurn(role="user", content="old")])
    await store.append_turns(404, "active", [OrchestratorTurn(role="user", content="active")])
    await store.begin_session_run(404, "active")
    await store.append_turns(404, "newer", [OrchestratorTurn(role="user", content="new")])

    assert len(store._STORE) == 2
    assert (404, "older") not in store._STORE
    assert (404, "active") in store._STORE
    assert [turn.content for turn in await store.get_history(404, "active")] == ["active"]

    await store.end_session_run(404, "active")


@pytest.mark.asyncio
async def test_orchestrator_session_cache_evicts_least_recently_used(monkeypatch) -> None:
    store = orchestrator_session
    monkeypatch.setattr(store, "_STORE", OrderedDict())
    monkeypatch.setattr(store, "_ACTIVE_RUNS", set(), raising=False)
    monkeypatch.setattr(store, "MAX_CACHED_SESSIONS", 2, raising=False)

    await store.append_turns(406, "older", [OrchestratorTurn(role="user", content="old")])
    await store.append_turns(406, "newer", [OrchestratorTurn(role="user", content="new")])
    await store.get_history(406, "older")
    await store.append_turns(406, "latest", [OrchestratorTurn(role="user", content="latest")])

    assert len(store._STORE) == 2
    assert (406, "older") in store._STORE
    assert (406, "newer") not in store._STORE


@pytest.mark.asyncio
async def test_orchestrator_runs_serialize_by_session_without_blocking_other_sessions(
    monkeypatch,
) -> None:
    store = orchestrator_session
    monkeypatch.setattr(store, "_STORE", OrderedDict())
    monkeypatch.setattr(store, "_ACTIVE_RUNS", set(), raising=False)
    monkeypatch.setattr(store, "_LOCK", asyncio.Lock())

    await store.begin_session_run(407, "shared")
    same_session = asyncio.create_task(store.begin_session_run(407, "shared"))
    other_session = asyncio.create_task(store.begin_session_run(407, "other"))
    await asyncio.sleep(0)
    same_session_waited = not same_session.done()
    other_session_did_not_wait = other_session.done()

    await store.end_session_run(407, "shared")
    await asyncio.gather(same_session, other_session)
    await store.end_session_run(407, "shared")
    await store.end_session_run(407, "other")

    assert same_session_waited
    assert other_session_did_not_wait


@pytest.mark.asyncio
async def test_cancelled_waiting_orchestrator_run_does_not_leak_session_pin(monkeypatch) -> None:
    store = orchestrator_session
    monkeypatch.setattr(store, "_STORE", OrderedDict())
    monkeypatch.setattr(store, "_ACTIVE_RUNS", set(), raising=False)
    monkeypatch.setattr(store, "_LOCK", asyncio.Lock())

    await store.begin_session_run(408, "shared")
    waiting_run = asyncio.create_task(store.begin_session_run(408, "shared"))
    await asyncio.sleep(0)
    waiting_run.cancel()

    with pytest.raises(asyncio.CancelledError):
        await waiting_run

    state = store._STORE[(408, "shared")]
    assert state.active_runs == 1
    assert (408, "shared") in store._ACTIVE_RUNS

    await store.end_session_run(408, "shared")
    assert (408, "shared") not in store._ACTIVE_RUNS
    assert (408, "shared") not in store._STORE


@pytest.mark.asyncio
async def test_orchestrator_cache_rehydrates_client_history_after_cold_start() -> None:
    prior = [
        OrchestratorTurn(role="user", content="earlier question"),
        OrchestratorTurn(role="assistant", content="earlier answer"),
    ]
    current = OrchestratorTurn(role="user", content="current question")

    await orchestrator_session.append_turns(
        405, "cold-start", [current], history_fallback=prior
    )

    assert [
        (turn.role, turn.content)
        for turn in await orchestrator_session.get_history(405, "cold-start")
    ] == [
        ("user", "earlier question"),
        ("assistant", "earlier answer"),
        ("user", "current question"),
    ]


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
