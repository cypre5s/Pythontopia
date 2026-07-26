"""Deterministic Supervisor compiler for bounded multi-agent TaskPlan DAGs."""

from __future__ import annotations

import hashlib
from collections import Counter
from dataclasses import dataclass

from pydantic import ValidationError

from pythontopia_graph.schemas.orchestrator import PlanStep, TaskPlan
from pythontopia_graph.schemas.workshop_route import RouteDecision
from pythontopia_graph.tools.registry import TOOLS


class TaskPlanPolicyError(ValueError):
    """TaskPlan violates route authority, dependency, or hard-budget policy."""


_PHASE_BY_TOOL = {
    "ASK": "learn",
    "ASKME": "practice",
    "EXPLAIN_CODE": "learn",
    "FIX_BUG": "practice",
    "HANDOUT": "learn",
    "MINDMAP": "learn",
    "PRACTICE": "practice",
    "CODE_LAB": "practice",
    "READING": "learn",
    "STORYBOARD": "watch",
    "PLAN": "assess",
}
_PANEL_BY_TOOL = {
    "ASK": "tutor",
    "ASKME": "training",
    "EXPLAIN_CODE": "tutor",
    "FIX_BUG": "workshop",
    "HANDOUT": "workshop",
    "MINDMAP": "workshop",
    "PRACTICE": "training",
    "CODE_LAB": "workshop",
    "READING": "workshop",
    "STORYBOARD": "lecture",
    "PLAN": "problems",
}
_TITLE_BY_TOOL = {
    "ASK": "课件答疑",
    "ASKME": "微测检验",
    "EXPLAIN_CODE": "代码讲解",
    "FIX_BUG": "诊断失败提交",
    "HANDOUT": "生成章节总结",
    "MINDMAP": "生成知识图谱",
    "PRACTICE": "生成针对性练习",
    "CODE_LAB": "生成代码案例",
    "READING": "生成阅读材料",
    "STORYBOARD": "生成微课脚本",
    "PLAN": "安排学习路径",
}
_RESOURCE_TOOLS = {"HANDOUT", "MINDMAP", "PRACTICE", "CODE_LAB", "READING", "STORYBOARD"}


def compile_task_plan(
    decision: RouteDecision,
    *,
    message: str,
    kc_codes: list[str],
) -> TaskPlan:
    """Compile a RouteDecision into a stable, auditable DAG without model autonomy."""
    if decision.task.family != "MULTI_TASK":
        raise TaskPlanPolicyError("TaskPlan requires MULTI_TASK route")
    candidate_id: str | None = None
    tool_agents: list[tuple[str, str]]
    planner_executor = decision.orchestration.topology == "PLANNER_EXECUTOR"
    if decision.task.complexity == "C3":
        search = decision.search
        if search is None:
            raise TaskPlanPolicyError("C3 TaskPlan requires route search")
        selected = next(
            (
                candidate
                for candidate in search.candidates
                if candidate.candidate_id == search.selected_candidate_id
            ),
            None,
        )
        if selected is None:
            raise TaskPlanPolicyError("selected C3 candidate is missing")
        candidate_id = selected.candidate_id
        tool_agents = list(zip(selected.tool_path, selected.agent_path, strict=True))
    else:
        tools = list(decision.tools.required)
        if planner_executor:
            # C2 保持原合同：规划器先给出受约束路径，失败时只能原步重试一次。
            tools = [name for name in tools if name == "PLAN"] + [
                name for name in tools if name != "PLAN"
            ]
        tool_agents = []
        for tool_name in tools:
            if tool_name not in TOOLS:
                raise TaskPlanPolicyError(f"unknown tool: {tool_name}")
            spec = TOOLS[tool_name]
            agent = spec.allowed_agents[0] if spec.allowed_agents else "OrchestratorAgent"
            tool_agents.append((tool_name, agent))
    steps: list[PlanStep] = []
    sequential = decision.orchestration.topology in {"SEQUENTIAL", "PLANNER_EXECUTOR"}
    for index, (tool_name, agent) in enumerate(tool_agents):
        if tool_name not in TOOLS:
            raise TaskPlanPolicyError(f"unknown tool: {tool_name}")
        spec = TOOLS[tool_name]
        step_id = f"task-{index + 1}-{tool_name.lower().replace('_', '-')}"
        dependency = [steps[-1].id] if sequential and steps else []
        steps.append(
            PlanStep(
                id=step_id,
                phase=_PHASE_BY_TOOL.get(tool_name, "learn"),  # type: ignore[arg-type]
                title=_TITLE_BY_TOOL.get(tool_name, tool_name),
                detail=message[:400],
                agent=agent,
                kc_codes=kc_codes,
                panel=_PANEL_BY_TOOL.get(tool_name, "workshop"),
                resource_type=tool_name if tool_name in _RESOURCE_TOOLS else None,
                tool_name=tool_name,
                depends_on=dependency,
                input_refs=[f"{dependency[0]}.artifact"] if dependency else [],
                output_schema=spec.result_schema,
                parallel_group="ready-0" if not sequential else f"ready-{index}",
                idempotency_key=f"{decision.route_id}:{step_id}",
                on_failure="REPLAN_ONCE" if planner_executor else "BLOCK_DEPENDENTS",
            )
        )
    plan = TaskPlan(
        plan_id=_plan_id(decision.route_id),
        route_id=decision.route_id,
        initial_candidate_id=candidate_id,
        active_candidate_id=candidate_id,
        topology=decision.orchestration.topology,
        steps=steps,
        max_tool_calls=decision.policies.max_tool_calls,
        max_calls_per_tool=decision.policies.max_calls_per_tool,
        max_parallel_agents=decision.policies.max_parallel_agents,
        max_planning_depth=decision.policies.max_planning_depth,
        max_replans=decision.policies.max_replans,
    )
    validate_task_plan(plan, decision)
    return plan


def validate_task_plan(plan: TaskPlan, decision: RouteDecision) -> None:
    try:
        plan = TaskPlan.model_validate(plan.model_dump(mode="python"))
    except (AttributeError, ValidationError) as exc:
        raise TaskPlanPolicyError(f"TaskPlan schema validation failed: {exc}") from exc
    if plan.route_id != decision.route_id or plan.topology != decision.orchestration.topology:
        raise TaskPlanPolicyError("TaskPlan does not match RouteDecision")
    for budget_name in (
        "max_tool_calls",
        "max_calls_per_tool",
        "max_parallel_agents",
        "max_planning_depth",
        "max_replans",
    ):
        if getattr(plan, budget_name) != getattr(decision.policies, budget_name):
            raise TaskPlanPolicyError(f"{budget_name} budget does not match RouteDecision")
    if plan.replans_used > plan.max_replans:
        raise TaskPlanPolicyError("replans_used exceeds max_replans budget")

    required_tools = list(decision.tools.required)
    plan_tools = [step.tool_name or "" for step in plan.steps]
    if (
        len(plan_tools) != len(required_tools)
        or Counter(plan_tools) != Counter(required_tools)
        or any(count != 1 for count in Counter(plan_tools).values())
    ):
        raise TaskPlanPolicyError("every required tool must appear exactly once in TaskPlan")
    if decision.task.complexity == "C3":
        search = decision.search
        if search is None:
            raise TaskPlanPolicyError("C3 TaskPlan requires route search")
        candidate_ids = {candidate.candidate_id for candidate in search.candidates}
        if plan.initial_candidate_id != search.selected_candidate_id:
            raise TaskPlanPolicyError(
                "C3 TaskPlan initial candidate does not match search selection"
            )
        if plan.active_candidate_id not in candidate_ids:
            raise TaskPlanPolicyError("C3 TaskPlan active candidate is missing from search")
        if (
            plan.replanned_candidate_id is not None
            and plan.replanned_candidate_id not in candidate_ids
        ):
            raise TaskPlanPolicyError("C3 TaskPlan replanned candidate is missing from search")
        if plan.replans_used == 0 and (
            plan.active_candidate_id != plan.initial_candidate_id
            or plan.replanned_candidate_id is not None
        ):
            raise TaskPlanPolicyError("unreplanned C3 TaskPlan changed candidate ids")
        if plan.replans_used == 1 and (
            plan.replanned_candidate_id != plan.active_candidate_id
            or plan.active_candidate_id == plan.initial_candidate_id
        ):
            raise TaskPlanPolicyError("replanned C3 TaskPlan has inconsistent candidate ids")
        initial_candidate = next(
            candidate
            for candidate in search.candidates
            if candidate.candidate_id == search.selected_candidate_id
        )
        if plan.replans_used == 0:
            if plan_tools != initial_candidate.tool_path:
                raise TaskPlanPolicyError("C3 TaskPlan must follow selected candidate tool order")
            if [step.agent for step in plan.steps] != initial_candidate.agent_path:
                raise TaskPlanPolicyError("C3 TaskPlan must follow selected candidate agent order")
        else:
            active_candidate = next(
                candidate
                for candidate in search.candidates
                if candidate.candidate_id == plan.active_candidate_id
            )
            plan_agents = [step.agent for step in plan.steps]
            valid_orders: list[tuple[list[str], list[str]]] = []
            for prefix_length in range(len(initial_candidate.tool_path)):
                prefix_tools = initial_candidate.tool_path[:prefix_length]
                prefix_agents = initial_candidate.agent_path[:prefix_length]
                successful_tools = set(prefix_tools)
                active_remaining = [
                    (tool, agent)
                    for tool, agent in zip(
                        active_candidate.tool_path,
                        active_candidate.agent_path,
                        strict=True,
                    )
                    if tool not in successful_tools
                ]
                valid_orders.append(
                    (
                        [*prefix_tools, *(tool for tool, _ in active_remaining)],
                        [*prefix_agents, *(agent for _, agent in active_remaining)],
                    )
                )
            if not any(
                plan_tools == expected_tools and plan_agents == expected_agents
                for expected_tools, expected_agents in valid_orders
            ):
                raise TaskPlanPolicyError("replanned C3 TaskPlan violates candidate order")
        seen_unfinished = False
        for step in plan.steps:
            if step.status == "done":
                if seen_unfinished:
                    raise TaskPlanPolicyError("completed C3 steps must form a successful prefix")
            else:
                seen_unfinished = True
    elif any(
        candidate_id is not None
        for candidate_id in (
            plan.initial_candidate_id,
            plan.active_candidate_id,
            plan.replanned_candidate_id,
        )
    ):
        raise TaskPlanPolicyError("candidate ids are only allowed for C3 TaskPlan")
    allowed_tools = set(decision.tools.required) | set(decision.tools.optional)
    step_ids = {step.id for step in plan.steps}
    if len(step_ids) != len(plan.steps):
        raise TaskPlanPolicyError("duplicate step id")
    if len(plan.steps) > decision.policies.max_agent_calls:
        raise TaskPlanPolicyError("agent plan exceeds max_agent_calls")
    counts: dict[str, int] = {}
    graph: dict[str, list[str]] = {}
    for step in plan.steps:
        tool_name = step.tool_name or ""
        if tool_name not in allowed_tools:
            raise TaskPlanPolicyError(f"tool outside RouteDecision: {tool_name}")
        if step.idempotency_key != f"{plan.route_id}:{step.id}":
            raise TaskPlanPolicyError(f"invalid idempotency_key for TaskPlan step: {step.id}")
        counts[tool_name] = counts.get(tool_name, 0) + 1
        if counts[tool_name] > decision.policies.max_calls_per_tool:
            raise TaskPlanPolicyError(f"tool call budget exceeded: {tool_name}")
        if step.agent not in TOOLS[tool_name].allowed_agents:
            raise TaskPlanPolicyError(f"agent {step.agent} not allowed for tool {tool_name}")
        unknown = [dependency for dependency in step.depends_on if dependency not in step_ids]
        if unknown:
            raise TaskPlanPolicyError(f"unknown dependency: {unknown[0]}")
        graph[step.id] = list(step.depends_on)
    _assert_acyclic(graph)
    depth = _max_depth(graph)
    if depth > decision.policies.max_planning_depth:
        raise TaskPlanPolicyError("planning depth exceeds max_planning_depth")
    max_width = max(
        (
            sum(1 for step in plan.steps if _depth(step.id, graph, {}) == level)
            for level in range(depth)
        ),
        default=0,
    )
    if max_width > decision.policies.max_parallel_agents:
        raise TaskPlanPolicyError("parallel width exceeds max_parallel_agents")


def switch_task_plan_candidate_once(
    plan: TaskPlan,
    decision: RouteDecision,
    *,
    successful_step_ids: set[str],
) -> TaskPlan:
    """Choose one lower-ranked C3 alternative and reorder only unfinished steps."""
    validate_task_plan(plan, decision)
    search = decision.search
    if decision.task.complexity != "C3" or search is None:
        raise TaskPlanPolicyError("candidate switch requires C3 route search")
    if (
        plan.replans_used >= plan.max_replans
        or plan.replans_used >= search.replan_policy.max_candidate_switches
    ):
        raise TaskPlanPolicyError("C3 candidate switch already used")

    known_step_ids = {step.id for step in plan.steps}
    unknown_successes = successful_step_ids - known_step_ids
    if unknown_successes:
        raise TaskPlanPolicyError(f"unknown successful step: {min(unknown_successes)}")
    alternatives = [
        candidate
        for candidate in search.candidates
        if candidate.candidate_id != plan.active_candidate_id
    ]
    if not alternatives:
        raise TaskPlanPolicyError("C3 route has no alternative candidate")
    next_candidate = min(
        alternatives,
        key=lambda candidate: (candidate.total_cost, candidate.candidate_id),
    )

    successful_steps = [step for step in plan.steps if step.id in successful_step_ids]
    successful_tools = {step.tool_name for step in successful_steps}
    step_by_tool = {step.tool_name: step for step in plan.steps}
    candidate_agents = dict(zip(next_candidate.tool_path, next_candidate.agent_path, strict=True))
    remaining_steps: list[PlanStep] = []
    for tool_name in next_candidate.tool_path:
        if tool_name in successful_tools:
            continue
        original = step_by_tool.get(tool_name)
        if original is None:
            raise TaskPlanPolicyError(f"candidate introduced unknown tool: {tool_name}")
        payload = original.model_dump(mode="python")
        payload["agent"] = candidate_agents[tool_name]
        remaining_steps.append(PlanStep.model_validate(payload))

    reordered: list[PlanStep] = []
    for index, original in enumerate([*successful_steps, *remaining_steps]):
        dependency = [reordered[-1].id] if reordered else []
        payload = original.model_dump(mode="python")
        payload.update(
            {
                "status": "done" if original.id in successful_step_ids else "pending",
                "depends_on": dependency,
                "input_refs": [f"{dependency[0]}.artifact"] if dependency else [],
                "parallel_group": f"ready-{index}",
            }
        )
        reordered.append(PlanStep.model_validate(payload))

    payload = plan.model_dump(mode="python")
    payload.update(
        {
            "steps": [step.model_dump(mode="python") for step in reordered],
            "active_candidate_id": next_candidate.candidate_id,
            "replanned_candidate_id": next_candidate.candidate_id,
            "replans_used": plan.replans_used + 1,
            "status": "RUNNING",
        }
    )
    switched = TaskPlan.model_validate(payload)
    validate_task_plan(switched, decision)
    return switched


@dataclass(frozen=True)
class SwitchedRuntimeState:
    """候选切换后 dispatch 必须整体一致重建的全部运行时集合。

    plan/completed/failed/pending/step_status/replans_used 六份状态互为镜像，
    任何一份单独维护都可能漂移；统一由 :func:`rebuild_runtime_after_switch`
    一次性产出。
    """

    plan: list[PlanStep]
    completed: set[str]
    failed: set[str]
    pending: dict[str, PlanStep]
    step_status: dict[str, str]
    replans_used: int


def rebuild_runtime_after_switch(
    plan: TaskPlan,
    *,
    successful_step_ids: set[str],
) -> SwitchedRuntimeState:
    """Rebuild every dispatch runtime collection from a switched TaskPlan."""
    steps = list(plan.steps)
    known_step_ids = {step.id for step in steps}
    unknown_successes = successful_step_ids - known_step_ids
    if unknown_successes:
        raise TaskPlanPolicyError(f"unknown successful step: {min(unknown_successes)}")
    return SwitchedRuntimeState(
        plan=steps,
        completed=set(successful_step_ids),
        failed=set(),
        pending={step.id: step for step in steps if step.id not in successful_step_ids},
        step_status={
            step.id: "done" if step.id in successful_step_ids else "pending" for step in steps
        },
        replans_used=plan.replans_used,
    )


def _assert_acyclic(graph: dict[str, list[str]]) -> None:
    visiting: set[str] = set()
    visited: set[str] = set()

    def visit(node: str) -> None:
        if node in visiting:
            raise TaskPlanPolicyError("dependency cycle detected")
        if node in visited:
            return
        visiting.add(node)
        for dependency in graph[node]:
            visit(dependency)
        visiting.remove(node)
        visited.add(node)

    for node in graph:
        visit(node)


def _depth(node: str, graph: dict[str, list[str]], cache: dict[str, int]) -> int:
    if node in cache:
        return cache[node]
    dependencies = graph[node]
    cache[node] = (
        0 if not dependencies else max(_depth(dep, graph, cache) for dep in dependencies) + 1
    )
    return cache[node]


def _max_depth(graph: dict[str, list[str]]) -> int:
    cache: dict[str, int] = {}
    return max((_depth(node, graph, cache) for node in graph), default=-1) + 1


def _plan_id(route_id: str) -> str:
    digest = hashlib.sha256(f"task-plan:{route_id}".encode()).hexdigest()[:16]
    return f"plan_{digest}"
