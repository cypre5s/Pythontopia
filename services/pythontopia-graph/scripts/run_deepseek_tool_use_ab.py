#!/usr/bin/env python3
"""Run a real DeepSeek tool-selection A/B for the workshop tool loop.

The comparison is intentionally narrow:

- pure LLM tool-loop selection from consolidated tool schemas
- hybrid router selection via IntentRouterAgent, mapped to consolidated tools

By default it uses ``DEFAULT_TASKS`` from ``pythontopia_graph.eval.tool_use_eval``
so the result stays comparable with the offline deterministic eval. Pass
``--task-set challenge`` for the larger anti-overfit prompt set. Credentials are
loaded by ``OpenAiCompatProvider`` from ``deploy/.env`` or normal OpenAI-compatible
environment variables. The script never prints secret values.
"""

from __future__ import annotations

import argparse
import asyncio
import os
import sys
import time
from collections import Counter
from collections.abc import Awaitable, Callable
from pathlib import Path
from typing import TypeVar

import httpx

# Let the script import pythontopia_graph when run from services/pythontopia-graph.
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from pythontopia_graph.agents.intent_router_agent import IntentRouterAgent
from pythontopia_graph.eval.tool_use_eval import (
    DEFAULT_SYSTEM_PROMPT,
    ToolUseTask,
    tool_use_tasks_for_set,
)
from pythontopia_graph.graph.hybrid_loop import IntentRouterSelector
from pythontopia_graph.graph.tool_executor import RegistryToolExecutor
from pythontopia_graph.graph.tool_loop import ToolCallingLoop
from pythontopia_graph.llm.provider import OpenAiCompatProvider, provider_label
from pythontopia_graph.schemas.orchestrator import DispatchResult, PlanStep
from pythontopia_graph.tools.consolidated import (
    CONSOLIDATED_TOOL_NAMES,
    consolidated_for_legacy,
    consolidated_tool_schemas,
)
from pythontopia_graph.tools.harness import reset_harness_state

TRANSIENT_ERRORS = (
    httpx.ConnectError,
    httpx.ConnectTimeout,
    httpx.ReadError,
    httpx.ReadTimeout,
    httpx.RemoteProtocolError,
    httpx.ProxyError,
)

T = TypeVar("T")


class FakeDispatcher:
    """Minimal dispatcher so tool execution can finish without side effects."""

    async def dispatch(
        self, step: PlanStep, user_id: int = 1, focus_hint_extra: str = ""
    ) -> DispatchResult:
        return DispatchResult(step_id=step.id, agent=step.agent, ok=True, summary="ok")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Real DeepSeek tool-use A/B")
    parser.add_argument(
        "--model",
        default="deepseek-v4-pro",
        help="OpenAI-compatible model to pin for the A/B run.",
    )
    parser.add_argument("--votes", type=int, default=3, help="Number of rounds for majority vote.")
    parser.add_argument(
        "--task-set",
        default="default",
        choices=["default", "challenge"],
        help="Prompt set to evaluate.",
    )
    parser.add_argument("--max-steps", type=int, default=4, help="Tool loop step budget.")
    parser.add_argument(
        "--retry-attempts",
        type=int,
        default=5,
        help="Retries per model call for transient proxy/network errors.",
    )
    return parser.parse_args(argv)


async def retry_call(coro_factory: Callable[[], Awaitable[T]], attempts: int) -> T:
    last: BaseException | None = None
    for attempt in range(attempts):
        try:
            return await coro_factory()
        except TRANSIENT_ERRORS as exc:
            last = exc
            await asyncio.sleep(min(2.0 * (attempt + 1), 8.0))
    if last is not None:
        raise last
    raise RuntimeError("retry failed")


async def loop_pick(
    provider: OpenAiCompatProvider,
    executor: RegistryToolExecutor,
    prompt: str,
    max_steps: int,
) -> str:
    loop = ToolCallingLoop(
        provider=provider,
        tools=consolidated_tool_schemas("default"),
        valid_names=set(CONSOLIDATED_TOOL_NAMES),
        max_steps=max_steps,
        temperature=0,
    )
    result = await loop.run(
        system_prompt=DEFAULT_SYSTEM_PROMPT,
        user_message=prompt,
        executor=executor,
    )
    return result.tool_trail[0] if result.tool_trail else "(none)"


async def router_pick(selector: IntentRouterSelector, prompt: str) -> str:
    result = await selector.select(prompt)
    if result.needs_clarification:
        return "(clarify)"
    mapped = consolidated_for_legacy(result.intent)
    return mapped[0] if mapped else "(none)"


def majority_accuracy(
    tasks: tuple[ToolUseTask, ...], votes: dict[str, list[str]]
) -> tuple[int, list[str]]:
    correct = 0
    failures: list[str] = []
    for task in tasks:
        winner = Counter(votes[task.id]).most_common(1)[0][0]
        if winner in task.expected_tools:
            correct += 1
        else:
            failures.append(f"{task.id}->{winner}(exp {task.expected_tools})")
    return correct, failures


async def run(args: argparse.Namespace) -> None:
    if args.votes < 1:
        raise ValueError("--votes must be >= 1")
    os.environ["OPENAI_MODEL"] = args.model

    tasks = tool_use_tasks_for_set(args.task_set)
    provider = OpenAiCompatProvider()
    selector = IntentRouterSelector(IntentRouterAgent(provider))
    task_count = len(tasks)
    print(
        f"PROVIDER {provider_label()} | model={provider.model} "
        f"| task_set={args.task_set} tasks={task_count} votes={args.votes}",
        flush=True,
    )

    loop_votes: dict[str, list[str]] = {task.id: [] for task in tasks}
    router_votes: dict[str, list[str]] = {task.id: [] for task in tasks}

    for round_index in range(1, args.votes + 1):
        reset_harness_state()
        executor = RegistryToolExecutor(FakeDispatcher())
        loop_correct = 0
        router_correct = 0
        started_at = time.time()
        print(f"\n----- ROUND {round_index}/{args.votes} -----", flush=True)

        for task in tasks:
            try:
                loop_choice = await retry_call(
                    lambda: loop_pick(provider, executor, task.prompt, args.max_steps),
                    args.retry_attempts,
                )
            except Exception as exc:  # noqa: BLE001 - keep the eval running per task.
                loop_choice = "(error)"
                print(f"  ! loop {task.id} failed: {type(exc).__name__}", flush=True)
            try:
                router_choice = await retry_call(
                    lambda: router_pick(selector, task.prompt),
                    args.retry_attempts,
                )
            except Exception as exc:  # noqa: BLE001 - keep the eval running per task.
                router_choice = "(error)"
                print(f"  ! router {task.id} failed: {type(exc).__name__}", flush=True)

            loop_votes[task.id].append(loop_choice)
            router_votes[task.id].append(router_choice)
            loop_ok = loop_choice in task.expected_tools
            router_ok = router_choice in task.expected_tools
            loop_correct += loop_ok
            router_correct += router_ok
            print(
                f"  {task.id:10} exp={str(task.expected_tools):20} "
                f"loop={loop_choice:14}{'OK' if loop_ok else 'XX'}  "
                f"router={router_choice:14}{'OK' if router_ok else 'XX'}",
                flush=True,
            )

        elapsed = time.time() - started_at
        print(
            f"  ROUND {round_index}: "
            f"loop={loop_correct}/{task_count}={loop_correct / task_count:.0%}  "
            f"router={router_correct}/{task_count}={router_correct / task_count:.0%}  "
            f"({elapsed:.0f}s)",
            flush=True,
        )

    loop_majority, loop_failures = majority_accuracy(tasks, loop_votes)
    router_majority, router_failures = majority_accuracy(tasks, router_votes)
    print("\n==================== MAJORITY SUMMARY ====================", flush=True)
    print(
        f"PURE-LLM TOOL-LOOP : {loop_majority}/{task_count} = "
        f"{loop_majority / task_count:.0%}  fails={loop_failures}",
        flush=True,
    )
    print(
        f"HYBRID ROUTER      : {router_majority}/{task_count} = "
        f"{router_majority / task_count:.0%}  fails={router_failures}",
        flush=True,
    )
    print(
        f"DELTA router-loop  : "
        f"{(router_majority - loop_majority) / task_count * 100:+.0f}pp",
        flush=True,
    )


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    asyncio.run(run(args))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
