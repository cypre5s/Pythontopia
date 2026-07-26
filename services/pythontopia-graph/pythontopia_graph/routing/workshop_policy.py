"""把既有 IntentRouter 结果编译为受治理的统一 RouteDecision。

模型只负责现有 intent 语义分类；Agent、工具、拓扑、预算、输出合同与失败策略全部
由本模块确定性生成并在返回前校验。复合任务只生成有界 DAG，不启用自由 ReAct。
"""

from __future__ import annotations

import hashlib
import json
import re

from pythontopia_graph.schemas.intent import IntentRouteRequest
from pythontopia_graph.schemas.workshop_route import (
    RouteDecision,
    RouteFallback,
    RouteOrchestration,
    RouteOutput,
    RoutePersonalization,
    RoutePolicies,
    RouteSearch,
    RouteSearchCandidate,
    RouteTask,
    RouteTools,
)
from pythontopia_graph.tools.registry import ALL_TOOL_NAMES, TOOLS, tools_for_scope

_LOW_CONFIDENCE_THRESHOLD = 0.55
_C3_TOOL_SET: frozenset[str] = frozenset({"HANDOUT", "PRACTICE", "PLAN"})
_CLAUSE_DELIMITER = re.compile(r"[，,。；;！？!?\n：:]")
_ACTION_ONLY_CUES: dict[str, tuple[re.Pattern[str], ...]] = {
    "HANDOUT": (
        re.compile(r"总结(?:一下)?"),
        re.compile(r"(?:生成|整理|制作|补).{0,10}?" r"(?:讲义|复习资料|知识梳理|知识小结)"),
    ),
    "PRACTICE": (re.compile(r"(?:出|生成|设计|安排|来|做).{0,12}?(?:题|练习|微测|测验)"),),
    "PLAN": (
        re.compile(r"告诉我先做哪(?:一道|个)?"),
        re.compile(r"(?:安排|规划|制定|设计|生成).{0,10}?" r"(?:学习|复习)?(?:路径|顺序|计划)"),
    ),
}
_CONNECTOR_SEQUENCE = re.compile(r"^(?:(?:然后|接着|并且|最后|还要|也要|先|再|并)\s*)+")
_EXPLICIT_REQUEST_START = re.compile(
    r"^(?:(?:但(?:现在)?|这次|下一步|还是)\s*)?"
    r"(?:请(?!问|确认|注意)(?:你)?|帮我|给我|让我|告诉我|麻烦|能不能|"
    r"我要你|我想让你|我需要你|我准备请你|我打算请你)"
)
_STRONG_IMPERATIVE_START: dict[str, tuple[re.Pattern[str], ...]] = {
    "HANDOUT": (
        re.compile(r"^总结(?:一下)?"),
        re.compile(r"^(?:生成|整理|制作|补).{0,10}?" r"(?:讲义|复习资料|知识梳理|知识小结)"),
    ),
    "PRACTICE": (
        re.compile(
            r"^出(?:[一二两三四五六七八九十几]|\d+)(?:道|个)?.{0,8}?" r"(?:题|练习|微测|测验)"
        ),
        re.compile(r"^(?:生成|设计|安排|来|做).{0,12}?(?:题|练习|微测|测验)"),
    ),
    "PLAN": (
        re.compile(r"^安排(?:一下)?.{0,10}?(?:学习|复习)?(?:路径|顺序|计划)"),
        re.compile(r"^告诉我先做哪(?:一道|个)?"),
        re.compile(r"^(?:规划|制定|设计|生成).{0,10}?" r"(?:学习|复习)?(?:路径|顺序|计划)"),
    ),
}
_REQUEST_OBJECT_CUES: dict[str, tuple[re.Pattern[str], ...]] = {
    "HANDOUT": (
        re.compile(
            r"给我(?:一份|一篇|简短(?:一点)?的?|精简(?:一点)?的?)"
            r"(?:讲义|复习资料|知识梳理|知识小结)"
        ),
    ),
    "PRACTICE": (
        re.compile(
            r"给我(?:(?:[一二两三四五六七八九十几]+|\d+)(?:道|个)?|"
            r"(?:更)?(?:难|简单|容易)(?:一点)?的)"
            r".{0,8}?(?:题|练习|微测|测验)"
        ),
    ),
    "PLAN": (),
}
_COMPILED_ACTION_REFERENCES: dict[str, tuple[re.Pattern[str], ...]] = {
    "HANDOUT": (
        re.compile(
            r"(?:总结|生成|整理|制作|补)(?:过)?的.{0,10}" r"(?:讲义|复习资料|知识梳理|知识小结)"
        ),
    ),
    "PRACTICE": (
        re.compile(r"(?:出|生成|设计|安排|来|做)(?:过)?的.{0,10}" r"(?:题|练习|微测|测验)"),
    ),
    "PLAN": (
        re.compile(
            r"(?:安排|规划|制定|设计|生成)(?:过)?的.{0,10}" r"(?:学习|复习)?(?:路径|顺序|计划)"
        ),
    ),
}
_ACTION_BLOCKING_OPERATOR = (
    r"(?:不允许|不可以|不需要|不想要|不要|不用|不必|无需|(?<!能)不能|无法|没法|"
    r"不准|不得|不可|(?<!特)(?<!分)别|请勿|禁止|严禁|不应该|不想|"
    r"拒绝|停止|取消|避免|阻止)"
)
_BLOCKING_OPERATOR = re.compile(_ACTION_BLOCKING_OPERATOR)
_ACTION_SPANS_BY_TOOL: dict[str, re.Pattern[str]] = {
    tool: re.compile("|".join(f"(?:{cue.pattern})" for cue in cues))
    for tool, cues in _ACTION_ONLY_CUES.items()
}
_DECLARATIVE_ACTION_SUFFIX = re.compile(r"^\s*(?:是|由|属于)")
_RESOURCE_CONSTRAINT_SUFFIX = re.compile(
    r"^\s*的(?:难度|数量|范围|主题|题型|知识点|内容|长度|形式)"
)
_EMBEDDED_ACTION_PREFIX = re.compile(
    r"(?:为什么|会不会|是否|何时|什么时候|谁|怎么|怎样|"
    r"解释|说明|确认|分析|回答|判断|询问|预测|预计)"
)
_ACTION_CONNECTOR = re.compile(r"(?:然后|接着|并且|最后|还要|也要|先|再|并)")
_DIRECT_ACTION_GOVERNOR = re.compile(
    r"(?:(?:但(?:现在)?|这次|下一步|还是)\s*)?"
    r"(?:(?:请(?!问|确认|注意)(?:你)?|帮我|给我|让我|麻烦|"
    r"能不能|我要你|我想让你|我需要你|我准备请你|我打算请你)\s*)?"
    r"(?:给我|帮我|为我|替我|让我|来)?\s*"
)
_STANDALONE_CANCELLATION = re.compile(
    r"^(?:(?:算了|不用了|不要了|取消(?:刚才的)?|撤销(?:刚才的)?)[\s，,]*)+$"
)
_C3_DIAGNOSTIC_CUES: tuple[re.Pattern[str], ...] = (
    re.compile(r"以前.{0,12}(?:犯的错|错误|错题)"),
    re.compile(r"(?:反复|经常|总是).{0,10}(?:出错|做错|错)"),
    re.compile(r"(?:学得|学的).{0,8}(?:乱|不好|差|不扎实)"),
    re.compile(r"(?:薄弱|不会|没掌握|不理解|卡住|困难)"),
    re.compile(
        r"(?:之前|以前|上次|最近|刚才).{0,24}"
        r"(?:太难|很难|难住|做不出|看不懂)"
    ),
)
_NEGATED_DIAGNOSTIC = re.compile(
    r"(?:没有|并无|毫无|不存在|并不|并非|不是|不再|不|没)"
    r"(?:任何)?(?:明显)?(?:的)?\s*"
    r"(?:困难|薄弱|问题|错误|错题|卡住|不会|没掌握|不理解|出错|做错)"
)
_C3_TOOL_BASE_COST: dict[str, float] = {
    "PLAN": 0.7,
    "HANDOUT": 0.8,
    "PRACTICE": 0.9,
}
_C3_POSITION_MULTIPLIERS: tuple[float, float, float] = (1.0, 1.1, 1.2)
_C3_H_COST_STANDARD: dict[str, float] = {
    "PLAN": 0.35,
    "HANDOUT": 0.2,
    "PRACTICE": 0.15,
}
_C3_H_COST_WITH_ERROR: dict[str, float] = {
    "PLAN": 0.1,
    "HANDOUT": 0.15,
    "PRACTICE": 0.45,
}
_C3_HIGH_LOAD_PENALTY: dict[str, float] = {
    "PLAN": 0.0,
    "HANDOUT": 0.15,
    "PRACTICE": 0.75,
}
_C3_PRACTICE_FIRST_PENALTY = 0.25
_C3_RECENT_RESOURCE_PENALTY: dict[str, float] = {
    "PLAN": 0.1,
    "HANDOUT": 0.2,
    "PRACTICE": 0.1,
}
_C3_CANDIDATE_PATHS: tuple[tuple[str, str, tuple[str, str, str]], ...] = (
    (
        "candidate_plan_first",
        "规划诊断优先",
        ("PLAN", "HANDOUT", "PRACTICE"),
    ),
    (
        "candidate_rebuild_first",
        "知识重构优先",
        ("HANDOUT", "PRACTICE", "PLAN"),
    ),
    (
        "candidate_practice_first",
        "练习探测优先",
        ("PRACTICE", "HANDOUT", "PLAN"),
    ),
)
_KNOWN_AGENTS: frozenset[str] = frozenset(
    agent for spec in TOOLS.values() for agent in spec.allowed_agents
)

_FAMILY_BY_INTENT: dict[str, str] = {
    "EXPLAIN_CODE": "CONCEPT_QA",
    "FIX_BUG": "DEBUG",
    "NAVIGATE": "GUIDE",
    "HANDOUT": "RESOURCE",
    "READING": "RESOURCE",
    "STORYBOARD": "RESOURCE",
    "CODE_LAB": "RESOURCE",
    "PRACTICE": "PRACTICE",
    "ASKME": "PRACTICE",
    "MINDMAP": "MINDMAP",
    "PLAN": "PATH",
    "PROGRESS": "PATH",
    "MY_STUDY": "PATH",
    "REVIEW": "PATH",
    "VIDEO": "COURSEWARE_QA",
    "BROWSE": "COURSEWARE_QA",
}

_PRIMARY_AGENT_BY_INTENT: dict[str, str | None] = {
    name: (spec.allowed_agents[0] if spec.allowed_agents else None) for name, spec in TOOLS.items()
}


class RoutePolicyError(ValueError):
    """RouteDecision 违反确定性 Agent/工具/预算策略。"""


def compile_route_decision(
    request: IntentRouteRequest,
    *,
    intent: str,
    confidence: float,
    needs_clarification: bool,
    clarification_prompt: str,
    render_mode: str,
    resolved_kc_codes: list[str] | None = None,
    secondary_intent: str | None = None,
) -> RouteDecision:
    """将现有 intent 结果编译成单 Agent 或 Phase 2 复合合同并立即过策略门。"""
    if intent not in TOOLS:
        raise RoutePolicyError(f"unknown tool: {intent}")

    if secondary_intent == intent or secondary_intent not in TOOLS:
        secondary_intent = None
    family = _task_family(intent, request, resolved_kc_codes or [])
    primary_agent = _PRIMARY_AGENT_BY_INTENT[intent]
    allowed_in_scope = {spec.name for spec in tools_for_scope(request.scope)}
    planned_intents = [intent] + ([secondary_intent] if secondary_intent else [])
    planned_intents = _expand_explicit_c3_tools(request, planned_intents)
    requested_c3 = (
        len(planned_intents) == 3 and set(planned_intents) == _C3_TOOL_SET
    )
    missing_context = any(_missing_required_context(name, request) for name in planned_intents)
    out_of_scope = any(name not in allowed_in_scope for name in planned_intents)
    # 显式单工具 launcher/slash 保留 Phase 1 的确定性入口语义；所有复合
    # 路由都必须重新通过低置信门，不能借入口夹带第二个写工具。
    low_confidence = confidence < _LOW_CONFIDENCE_THRESHOLD and (
        request.source == "composer_text" or len(planned_intents) > 1
    )
    clarify = needs_clarification or low_confidence
    clarify = clarify or missing_context or out_of_scope
    question = ""
    if clarify:
        question = clarification_prompt or _clarification_question(
            intent, missing_context=missing_context, out_of_scope=out_of_scope
        )

    is_multi = len(planned_intents) > 1 and not clarify
    is_c3 = is_multi and requested_c3
    topology = (
        "DIRECT"
        if clarify or primary_agent is None
        else "PLANNER_EXECUTOR"
        if is_c3
        else _compound_topology(intent, secondary_intent)
        if is_multi
        else "SINGLE_AGENT_TOOL"
    )
    personalization, topology, max_parallel_agents = _personalize_route(
        request,
        intent=intent,
        topology=topology,
        resolved_kc_codes=resolved_kc_codes or [],
        clarify=clarify,
    )
    required = [] if clarify else planned_intents
    optional: list[str] = []
    fallback_tool = TOOLS[intent].fallback
    if (
        not clarify
        and not is_multi
        and fallback_tool
        and fallback_tool in allowed_in_scope
        and primary_agent in TOOLS[fallback_tool].allowed_agents
    ):
        optional.append(fallback_tool)
    permitted = set(required) | set(optional)
    forbidden = [name for name in ALL_TOOL_NAMES if name not in permitted]
    output_schema = (
        "WorkshopClarificationCard"
        if clarify
        else "CompoundLearningSupportCard"
        if is_multi
        else TOOLS[intent].result_schema
    )
    effective_render_mode = "clarification" if clarify else "plan" if is_multi else render_mode
    require_grounding = family in {
        "CONCEPT_QA",
        "COURSEWARE_QA",
        "DEBUG",
        "RESOURCE",
        "PRACTICE",
        "MINDMAP",
        "PATH",
    }

    decision = RouteDecision(
        route_id=_route_id(request, intent, confidence, secondary_intent),
        task=RouteTask(
            family="MULTI_TASK" if is_multi else family,  # type: ignore[arg-type]
            subtype=intent,
            sub_intents=planned_intents if is_multi else [intent],
            complexity=(
                "C0"
                if clarify or primary_agent is None
                else "C3"
                if is_c3
                else "C2"
                if is_multi
                else "C1"
            ),
            confidence=confidence,
            needs_clarification=clarify,
            clarification_question=question,
        ),
        orchestration=RouteOrchestration(
            topology=topology,  # type: ignore[arg-type] - deterministic closed mapping
            primary_agent=None if clarify else primary_agent,
            supporting_agents=[]
            if clarify or not is_multi
            else _supporting_agents(primary_agent, planned_intents[1:]),
        ),
        tools=RouteTools(required=required, optional=optional, forbidden=forbidden),
        policies=RoutePolicies(
            max_parallel_agents=max_parallel_agents,
            require_grounding=require_grounding,
            require_safety=True,
            allow_common_knowledge=family in {"CONCEPT_QA", "COURSEWARE_QA"},
            read_before_write=any(
                TOOLS[name].side_effects == "resource_write" for name in required
            ),
        ),
        output=RouteOutput(
            schema_name=output_schema,
            render_mode=effective_render_mode,
            require_evidence=require_grounding,
        ),
        fallback=RouteFallback(),
        personalization=personalization,
        search=_build_c3_search(request, required) if is_c3 else None,
    )
    validate_route_decision(decision, scope=request.scope, request=request)
    return decision


def validate_route_decision(
    decision: RouteDecision,
    *,
    scope: str,
    request: IntentRouteRequest | None = None,
) -> None:
    """在任何模型/工具执行前验证 Agent、工具、scope 与 Agent-tool 组合。"""
    if decision.task.complexity == "C3":
        if decision.search is None:
            raise RoutePolicyError("C3 route requires search")
        if decision.orchestration.topology != "PLANNER_EXECUTOR":
            raise RoutePolicyError("C3 route requires PLANNER_EXECUTOR topology")
    elif decision.search is not None:
        raise RoutePolicyError("search is only allowed for C3 routes")

    agent = decision.orchestration.primary_agent
    declared_agents = ([agent] if agent else []) + decision.orchestration.supporting_agents
    for declared in declared_agents:
        if declared not in _KNOWN_AGENTS:
            raise RoutePolicyError(f"unknown agent: {declared}")
    if len(declared_agents) != len(set(declared_agents)):
        raise RoutePolicyError("declared agents overlap")
    if len(declared_agents) > decision.policies.max_agent_calls:
        raise RoutePolicyError("agent plan exceeds max_agent_calls")

    required = decision.tools.required
    optional = decision.tools.optional
    if len(required) != len(set(required)):
        raise RoutePolicyError("required tools contain duplicates")
    if len(optional) != len(set(optional)):
        raise RoutePolicyError("optional tools contain duplicates")
    named_tools = required + optional + decision.tools.forbidden
    unknown_tools = sorted(set(named_tools) - set(ALL_TOOL_NAMES))
    if unknown_tools:
        raise RoutePolicyError(f"unknown tool: {unknown_tools[0]}")
    required_set = set(required)
    optional_set = set(optional)
    forbidden_set = set(decision.tools.forbidden)
    if required_set & optional_set or required_set & forbidden_set or optional_set & forbidden_set:
        raise RoutePolicyError("required, optional and forbidden tools overlap")
    expected_forbidden = set(ALL_TOOL_NAMES) - required_set - optional_set
    if forbidden_set != expected_forbidden:
        raise RoutePolicyError("forbidden tools must be the exact executable complement")
    if len(required) + len(optional) > decision.policies.max_tool_calls:
        raise RoutePolicyError("tool plan exceeds max_tool_calls")

    allowed_in_scope = {spec.name for spec in tools_for_scope(scope)}
    executable = required + optional
    out_of_scope = [name for name in executable if name not in allowed_in_scope]
    if out_of_scope:
        raise RoutePolicyError(f"tool outside scope: {out_of_scope[0]}")

    if declared_agents:
        for name in executable:
            if not any(declared in TOOLS[name].allowed_agents for declared in declared_agents):
                if len(declared_agents) == 1:
                    raise RoutePolicyError(
                        f"agent {declared_agents[0]} not allowed for tool {name}"
                    )
                raise RoutePolicyError(f"no declared agent allowed for tool {name}")
    elif any(TOOLS[name].allowed_agents for name in executable):
        raise RoutePolicyError("agent is required for executable tool")
    if (
        any(TOOLS[name].side_effects == "resource_write" for name in executable)
        and not decision.policies.read_before_write
    ):
        raise RoutePolicyError("resource_write tools require read_before_write policy")

    if decision.task.complexity == "C3":
        if request is None:
            raise RoutePolicyError("C3 route validation requires current request context")
        if not decision.policies.require_safety:
            raise RoutePolicyError("C3 route requires safety policy")
        if not decision.policies.require_grounding:
            raise RoutePolicyError("C3 route requires grounding policy")
        if decision.policies.allow_common_knowledge:
            raise RoutePolicyError("C3 route forbids ungrounded common knowledge")
        if not decision.output.require_evidence:
            raise RoutePolicyError("C3 route requires output evidence")
        _validate_c3_search(decision, request=request)

    if decision.orchestration.topology == "DIRECT" and agent is not None:
        raise RoutePolicyError("DIRECT route cannot declare a primary agent")

    if required and decision.task.family == "MULTI_TASK":
        if len(required) < 2:
            raise RoutePolicyError("MULTI_TASK requires at least two tools")
        if decision.output.schema_name != "CompoundLearningSupportCard":
            raise RoutePolicyError("multi-task output schema must be CompoundLearningSupportCard")
        if decision.orchestration.topology not in {
            "SEQUENTIAL",
            "PARALLEL",
            "PLANNER_EXECUTOR",
        }:
            raise RoutePolicyError("MULTI_TASK requires a multi-agent topology")
    elif required:
        expected_schema = TOOLS[required[0]].result_schema
        if decision.output.schema_name != expected_schema:
            raise RoutePolicyError(
                f"output schema {decision.output.schema_name} does not match {expected_schema}"
            )
    elif decision.task.needs_clarification:
        if decision.output.schema_name != "WorkshopClarificationCard":
            raise RoutePolicyError("output schema must be WorkshopClarificationCard")


def _validate_c3_search(
    decision: RouteDecision,
    *,
    request: IntentRouteRequest,
) -> None:
    """Validate all public C3 search fields against route authority and budgets."""
    search = decision.search
    if search is None:  # guarded by validate_route_decision; keeps this helper total
        raise RoutePolicyError("C3 route requires search")
    required = decision.tools.required
    if len(required) != 3 or set(required) != _C3_TOOL_SET:
        raise RoutePolicyError("C3 route requires HANDOUT, PRACTICE and PLAN authority")
    if decision.task.sub_intents != required:
        raise RoutePolicyError("C3 task sub_intents must match required tools")
    if len(search.candidates) != 3:
        raise RoutePolicyError("C3 search requires exactly three candidates")

    candidate_ids = [candidate.candidate_id for candidate in search.candidates]
    if len(candidate_ids) != len(set(candidate_ids)):
        raise RoutePolicyError("C3 candidate ids must be unique")
    candidate_tool_paths = [tuple(candidate.tool_path) for candidate in search.candidates]
    if len(candidate_tool_paths) != len(set(candidate_tool_paths)):
        raise RoutePolicyError("C3 candidate tool paths must be unique")
    expected_paths = {
        candidate_id: (label, list(tool_path))
        for candidate_id, label, tool_path in _C3_CANDIDATE_PATHS
    }
    if set(candidate_ids) != set(expected_paths):
        raise RoutePolicyError("C3 candidates must match the bounded templates")

    for candidate in search.candidates:
        if len(candidate.tool_path) != len(set(candidate.tool_path)):
            raise RoutePolicyError(f"candidate {candidate.candidate_id} contains duplicate tools")
        if set(candidate.tool_path) != set(required):
            raise RoutePolicyError(
                f"candidate {candidate.candidate_id} exceeds or misses required tool authority"
            )
        expected_label, expected_tool_path = expected_paths[candidate.candidate_id]
        if candidate.label != expected_label or candidate.tool_path != expected_tool_path:
            raise RoutePolicyError(
                f"candidate {candidate.candidate_id} diverges from bounded template"
            )
        if not (candidate.depth == len(candidate.tool_path) == len(candidate.agent_path)):
            raise RoutePolicyError(f"candidate {candidate.candidate_id} depth does not match paths")
        if candidate.depth > search.max_depth:
            raise RoutePolicyError(f"candidate {candidate.candidate_id} exceeds search depth")
        if candidate.depth > decision.policies.max_planning_depth:
            raise RoutePolicyError(
                f"candidate {candidate.candidate_id} exceeds route planning depth"
            )

        for tool, candidate_agent in zip(candidate.tool_path, candidate.agent_path, strict=True):
            if tool not in TOOLS:
                raise RoutePolicyError(f"unknown candidate tool: {tool}")
            if candidate_agent not in _KNOWN_AGENTS:
                raise RoutePolicyError(f"unknown candidate agent: {candidate_agent}")
            if candidate_agent not in TOOLS[tool].allowed_agents:
                raise RoutePolicyError(
                    f"candidate agent {candidate_agent} not allowed for candidate tool {tool}"
                )

        expected_total = round(
            candidate.g_cost + candidate.h_cost + candidate.penalty,
            6,
        )
        if candidate.total_cost != expected_total:
            raise RoutePolicyError(
                f"candidate {candidate.candidate_id} total_cost violates public formula"
            )

    selected = next(
        (
            candidate
            for candidate in search.candidates
            if candidate.candidate_id == search.selected_candidate_id
        ),
        None,
    )
    if selected is None:
        raise RoutePolicyError("selected candidate is missing from C3 candidates")
    expected_selected = min(
        search.candidates,
        key=lambda candidate: (candidate.total_cost, candidate.candidate_id),
    )
    if selected.candidate_id != expected_selected.candidate_id:
        raise RoutePolicyError("selected candidate is not the stable lowest-cost path")
    if search.replan_policy.max_candidate_switches > decision.policies.max_replans:
        raise RoutePolicyError("C3 candidate switch budget exceeds max_replans")
    if not all(
        _has_authorized_c3_action(request.message, missing_tool=tool)
        for tool in ("HANDOUT", "PRACTICE", "PLAN")
    ) or not _has_c3_diagnostic_signal(request):
        raise RoutePolicyError("C3 route does not match the current request")
    expected_search = _build_c3_search(request, required)
    if search.model_dump(mode="python") != expected_search.model_dump(mode="python"):
        raise RoutePolicyError("C3 search costs or selection diverge from current context")


def _task_family(intent: str, request: IntentRouteRequest, resolved_kc_codes: list[str]) -> str:
    if intent == "ASK":
        has_course_anchor = bool(
            request.manual_kc_codes
            or resolved_kc_codes
            or request.recent_kc_codes
            or (request.interaction and request.interaction.selected_page_ids)
        )
        return "COURSEWARE_QA" if has_course_anchor else "CONCEPT_QA"
    return _FAMILY_BY_INTENT.get(intent, "UNKNOWN")


def _expand_explicit_c3_tools(
    request: IntentRouteRequest,
    planned_intents: list[str],
) -> list[str]:
    """Complete only an explicitly requested HANDOUT/PRACTICE/PLAN triad."""
    if len(planned_intents) != 2 or not set(planned_intents) < _C3_TOOL_SET:
        return planned_intents
    missing = _C3_TOOL_SET - set(planned_intents)
    if len(missing) != 1:
        return planned_intents
    missing_tool = next(iter(missing))
    if not all(
        _has_authorized_c3_action(request.message, missing_tool=tool)
        for tool in ("HANDOUT", "PRACTICE", "PLAN")
    ):
        return planned_intents
    if not _has_c3_diagnostic_signal(request):
        return planned_intents
    return [*planned_intents, missing_tool]


def _has_c3_diagnostic_signal(request: IntentRouteRequest) -> bool:
    context = request.learner_context
    if context and (
        context.weak_kcs
        or context.recent_error_kcs
        or context.struggle_patterns
        or any(not item.passed for item in context.recent_submissions)
    ):
        return True
    for clause in (part.strip() for part in _CLAUSE_DELIMITER.split(request.message)):
        if not clause:
            continue
        matches = [
            match
            for pattern in _C3_DIAGNOSTIC_CUES
            for match in pattern.finditer(clause)
            if not _is_negated_diagnostic(clause, match)
        ]
        matches.sort(key=lambda match: match.start())
        if matches and not _has_action_bound_blocker(clause, matches):
            return True
    return False


def _is_negated_diagnostic(clause: str, match: re.Match[str]) -> bool:
    window_start = max(0, match.start() - 12)
    window_end = min(len(clause), match.end() + 4)
    return bool(_NEGATED_DIAGNOSTIC.search(clause[window_start:window_end]))


def _has_authorized_c3_action(
    message: str,
    *,
    missing_tool: str,
) -> bool:
    authorized = False
    last_action_clause_was_relevant = False
    clauses = (clause.strip() for clause in _CLAUSE_DELIMITER.split(message))
    for clause in clauses:
        if not clause:
            continue
        if _STANDALONE_CANCELLATION.fullmatch(clause):
            if last_action_clause_was_relevant:
                authorized = False
            continue
        relevant_matches = list(_ACTION_SPANS_BY_TOOL[missing_tool].finditer(clause))
        for cue in _REQUEST_OBJECT_CUES[missing_tool]:
            relevant_matches.extend(cue.finditer(clause))
        relevant_matches.sort(key=lambda match: match.start())
        request_facts = _request_clause_facts(clause, missing_tool=missing_tool)
        if _has_action_bound_blocker(clause, relevant_matches):
            authorized = False
            continue
        for action_match in relevant_matches:
            if _is_match_local_reference(action_match, missing_tool=missing_tool):
                continue
            if _is_embedded_action_statement(
                clause,
                action_start=action_match.start(),
                missing_tool=missing_tool,
            ):
                continue
            suffix = clause[action_match.end() :]
            if suffix.lstrip().startswith("的") and not (
                _is_direct_action_request(
                    clause,
                    action_start=action_match.start(),
                    missing_tool=missing_tool,
                )
                and _RESOURCE_CONSTRAINT_SUFFIX.match(suffix)
            ):
                continue
            if _DECLARATIVE_ACTION_SUFFIX.match(suffix):
                continue
            if _is_current_request_clause(
                request_facts,
                action_start=action_match.start(),
            ) or _is_compound_action_request(clause, action_start=action_match.start()):
                authorized = True
        if any(
            _ACTION_SPANS_BY_TOOL[tool].search(clause)
            or any(cue.search(clause) for cue in _REQUEST_OBJECT_CUES[tool])
            for tool in ("HANDOUT", "PRACTICE", "PLAN")
        ):
            last_action_clause_was_relevant = bool(relevant_matches)
    return authorized


def _is_embedded_action_statement(
    clause: str,
    *,
    action_start: int,
    missing_tool: str,
) -> bool:
    prefix = clause[:action_start]
    if _is_direct_action_request(
        clause,
        action_start=action_start,
        missing_tool=missing_tool,
    ):
        return False
    if _EMBEDDED_ACTION_PREFIX.search(prefix):
        return True
    tell_match = re.match(r"^(?:(?:然后|接着|并且|最后|还要|也要|先|再|并)\s*)*告诉我", clause)
    return bool(
        missing_tool != "PLAN"
        and tell_match is not None
        and action_start >= tell_match.end()
    )


def _is_direct_action_request(
    clause: str,
    *,
    action_start: int,
    missing_tool: str,
) -> bool:
    if any(
        pattern.match(clause)
        for pattern in _STRONG_IMPERATIVE_START[missing_tool]
    ):
        return True
    prefix = clause[:action_start]
    connectors = list(_ACTION_CONNECTOR.finditer(prefix))
    governor = prefix[connectors[-1].end() :] if connectors else prefix
    return bool(governor.strip() and _DIRECT_ACTION_GOVERNOR.fullmatch(governor.strip()))


def _is_compound_action_request(clause: str, *, action_start: int) -> bool:
    prefix = clause[:action_start]
    if not re.search(r"(?:然后|接着|并且|最后|还要|也要|再|并)\s*$", prefix):
        return False
    return bool(
        _EXPLICIT_REQUEST_START.match(clause)
        or any(
            pattern.match(clause)
            for patterns in _STRONG_IMPERATIVE_START.values()
            for pattern in patterns
        )
    )


def _has_action_bound_blocker(
    clause: str,
    action_matches: list[re.Match[str]],
) -> bool:
    action_index = 0
    for blocker in _BLOCKING_OPERATOR.finditer(clause):
        while (
            action_index < len(action_matches)
            and action_matches[action_index].start() < blocker.end()
        ):
            action_index += 1
        if action_index < len(action_matches):
            return True
    return False


def _is_match_local_reference(
    action_match: re.Match[str],
    *,
    missing_tool: str,
) -> bool:
    matched_text = action_match.group(0)
    return any(
        pattern.search(matched_text) for pattern in _COMPILED_ACTION_REFERENCES[missing_tool]
    )


def _is_current_request_clause(
    request_facts: tuple[bool, int | None, bool, bool],
    *,
    action_start: int,
) -> bool:
    explicit_request, connector_body_start, connector_body_request, strong_imperative = (
        request_facts
    )
    return bool(
        explicit_request
        or (
            connector_body_start is not None
            and (action_start == connector_body_start or connector_body_request)
        )
        or strong_imperative
    )


def _request_clause_facts(
    clause: str,
    *,
    missing_tool: str,
) -> tuple[bool, int | None, bool, bool]:
    explicit_request = bool(_EXPLICIT_REQUEST_START.match(clause))
    connector_body_start: int | None = None
    connector_body_request = False
    connector = _CONNECTOR_SEQUENCE.match(clause)
    if connector:
        connector_body_start = connector.end()
        while connector_body_start < len(clause) and clause[connector_body_start].isspace():
            connector_body_start += 1
        body = clause[connector_body_start:]
        connector_body_request = bool(
            body.startswith("根据") or _EXPLICIT_REQUEST_START.match(body)
        )
    strong_imperative = any(
        pattern.match(clause) for pattern in _STRONG_IMPERATIVE_START[missing_tool]
    )
    return (
        explicit_request,
        connector_body_start,
        connector_body_request,
        strong_imperative,
    )


def _build_c3_search(
    request: IntentRouteRequest,
    required: list[str],
) -> RouteSearch:
    """Build three bounded public paths and select via deterministic numeric cost."""
    if set(required) != _C3_TOOL_SET:
        raise RoutePolicyError("C3 search requires HANDOUT, PRACTICE and PLAN")
    candidates = [
        _score_c3_candidate(
            request,
            candidate_id=candidate_id,
            label=label,
            tool_path=list(tool_path),
        )
        for candidate_id, label, tool_path in _C3_CANDIDATE_PATHS
    ]
    selected = min(
        candidates,
        key=lambda candidate: (candidate.total_cost, candidate.candidate_id),
    )
    return RouteSearch(
        candidates=candidates,
        selected_candidate_id=selected.candidate_id,
    )


def _score_c3_candidate(
    request: IntentRouteRequest,
    *,
    candidate_id: str,
    label: str,
    tool_path: list[str],
) -> RouteSearchCandidate:
    context = request.learner_context
    has_weak_or_error = bool(
        context
        and (
            context.weak_kcs
            or context.recent_error_kcs
            or context.struggle_patterns
            or any(not item.passed for item in context.recent_submissions)
        )
    )
    has_recent_error_kcs = bool(context and context.recent_error_kcs)
    high_load = bool(
        context and (context.cognitive_load == "HIGH" or context.frustration_level == "HIGH")
    )
    candidate_kind = tool_path[0]
    use_error_h = (
        has_recent_error_kcs
        if candidate_kind == "PRACTICE"
        else has_weak_or_error
    )
    h_cost = (
        _C3_H_COST_WITH_ERROR[candidate_kind]
        if use_error_h
        else _C3_H_COST_STANDARD[candidate_kind]
    )

    penalty = 0.0
    if high_load:
        penalty += _C3_HIGH_LOAD_PENALTY[candidate_kind]
    if candidate_kind == "PRACTICE" and has_recent_error_kcs:
        penalty += _C3_PRACTICE_FIRST_PENALTY
    recent_types = {resource.type for resource in context.recent_resources} if context else set()
    first_resource_tool = next(tool for tool in tool_path if tool in {"HANDOUT", "PRACTICE"})
    if first_resource_tool in recent_types:
        penalty += _C3_RECENT_RESOURCE_PENALTY[candidate_kind]

    agent_path: list[str] = []
    for tool in tool_path:
        allowed_agents = TOOLS[tool].allowed_agents
        if not allowed_agents:
            raise RoutePolicyError(f"C3 tool has no registered agent: {tool}")
        agent_path.append(allowed_agents[0])
    g_cost = round(
        sum(
            _C3_TOOL_BASE_COST[tool] * _C3_POSITION_MULTIPLIERS[index]
            for index, tool in enumerate(tool_path)
        ),
        6,
    )
    h_cost = round(h_cost, 6)
    penalty = round(penalty, 6)
    return RouteSearchCandidate(
        candidate_id=candidate_id,
        label=label,
        agent_path=agent_path,
        tool_path=tool_path,
        depth=len(tool_path),
        g_cost=g_cost,
        h_cost=h_cost,
        penalty=penalty,
        total_cost=round(g_cost + h_cost + penalty, 6),
    )


def _personalize_route(
    request: IntentRouteRequest,
    *,
    intent: str,
    topology: str,
    resolved_kc_codes: list[str],
    clarify: bool,
) -> tuple[RoutePersonalization, str, int]:
    """Apply bounded strategy differences without adding unauthorized tools or agents."""
    context = request.learner_context
    if context is None or clarify:
        return RoutePersonalization(), topology, 3

    signals: list[str] = []
    explanations: list[str] = []
    weak = set(context.weak_kcs) | set(context.recent_error_kcs)
    focus_weak = [code for code in resolved_kc_codes if code in weak]
    if context.cognitive_load != "LOW":
        signals.append(f"cognitive_load:{context.cognitive_load}")
    if context.frustration_level != "LOW":
        signals.append(f"frustration:{context.frustration_level}")
    signals.extend(f"weak_kc:{code}" for code in focus_weak[:3])

    preference_text = " ".join([context.cognitive_style, *context.learning_preferences]).lower()
    is_visual = "visual" in preference_text or "图" in preference_text or "视觉" in preference_text
    if is_visual:
        signals.append("preference:visual")

    recent_types = {item.type for item in context.recent_resources}
    if intent in recent_types:
        signals.append(f"recent_resource:{intent}")
        explanations.append(f"近期已有 {intent} 资源；保留本次显式请求，但执行时避免重复内容。")

    high_load = context.cognitive_load == "HIGH" or context.frustration_level == "HIGH"
    if high_load:
        strategy = "SCAFFOLD_FIRST"
        explanations.insert(0, "检测到高认知负荷或挫败信号，改为短步骤串行执行并关闭并发。")
        if topology == "PARALLEL":
            topology = "SEQUENTIAL"
        max_parallel_agents = 1
    elif focus_weak:
        strategy = "REMEDIATION_FIRST"
        explanations.insert(0, f"当前任务命中薄弱知识点 {focus_weak[0]}，优先补救后再检验。")
        max_parallel_agents = 2
    elif is_visual:
        strategy = "VISUAL_FIRST"
        explanations.insert(0, "依据稳定图解偏好，要求产物先给结构图与可视化小结。")
        max_parallel_agents = 3
    else:
        strategy = "STANDARD"
        max_parallel_agents = 3

    return (
        RoutePersonalization(
            strategy=strategy,  # type: ignore[arg-type] - closed deterministic values above
            signals_used=list(dict.fromkeys(signals))[:12],
            explanations=explanations[:4],
        ),
        topology,
        max_parallel_agents,
    )


def _compound_topology(intent: str, secondary_intent: str | None) -> str:
    pair = {intent, secondary_intent}
    if "PLAN" in pair:
        return "PLANNER_EXECUTOR"
    if pair <= {"HANDOUT", "MINDMAP", "READING", "STORYBOARD", "CODE_LAB"}:
        return "PARALLEL"
    if "MINDMAP" in pair and pair & {"PRACTICE", "ASKME"}:
        return "PARALLEL"
    return "SEQUENTIAL"


def _supporting_agents(
    primary_agent: str | None,
    secondary_intents: list[str],
) -> list[str]:
    agents = [_PRIMARY_AGENT_BY_INTENT[secondary_intent] for secondary_intent in secondary_intents]
    return list(dict.fromkeys(agent for agent in agents if agent and agent != primary_agent))


def _missing_required_context(intent: str, request: IntentRouteRequest) -> bool:
    if intent not in {"FIX_BUG", "EXPLAIN_CODE"}:
        return False
    interaction = request.interaction
    current_code = interaction.current_code.strip() if interaction else ""
    error_text = interaction.error_text.strip() if interaction else ""
    fenced_code = bool(re.search(r"```(?:python)?\s+.+?```", request.message, re.DOTALL))
    if intent == "EXPLAIN_CODE":
        return not (current_code or fenced_code)
    # DebugAgent 的真实分析需要失败提交和代码/错误上下文；缺失时澄清，绝不伪造。
    submission_id = interaction.latest_submission_id if interaction else None
    return not (submission_id and (current_code or fenced_code) and (error_text or fenced_code))


def _clarification_question(intent: str, *, missing_context: bool, out_of_scope: bool) -> str:
    if out_of_scope:
        return "当前考核模式不允许使用这个能力；请先退出考核，或继续回答当前问题。"
    if missing_context and intent == "FIX_BUG":
        return "请提供代码、报错信息并选择最近一次失败提交，我才能基于真实判题证据分析。"
    if missing_context and intent == "EXPLAIN_CODE":
        return "请贴出需要讲解的代码。"
    return "你想问概念、生成资料、练习，还是规划下一步？"


def _route_id(
    request: IntentRouteRequest,
    intent: str,
    confidence: float,
    secondary_intent: str | None,
) -> str:
    payload = {
        "message": request.message,
        "manual_kc_codes": request.manual_kc_codes,
        "current_chapter": request.current_chapter,
        "scope": request.scope,
        "source": request.source,
        "requested_intent": request.requested_intent,
        "intent": intent,
        "secondary_intent": secondary_intent,
        "confidence": round(confidence, 6),
        "interaction": request.interaction.model_dump(mode="json") if request.interaction else None,
        "learner_context": _routing_context_fingerprint(request),
    }
    canonical = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    digest = hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:16]
    return f"route_{digest}"


def _routing_context_fingerprint(request: IntentRouteRequest) -> dict[str, object] | None:
    """Exclude observational timestamps so unchanged learner facts keep stable route ids."""
    context = request.learner_context
    if context is None:
        return None
    return {
        "kc_mastery": {key: round(value, 4) for key, value in context.kc_mastery.items()},
        "kc_confidence": {key: round(value, 4) for key, value in context.kc_confidence.items()},
        "weak_kcs": context.weak_kcs,
        "recent_error_kcs": context.recent_error_kcs,
        "cognitive_load": context.cognitive_load,
        "frustration_level": context.frustration_level,
        "cognitive_style": context.cognitive_style,
        "learning_preferences": context.learning_preferences,
        "profile_major": context.profile_major,
        "python_level": context.python_level,
        "recent_resources": [
            {"kc_code": item.kc_code, "type": item.type} for item in context.recent_resources
        ],
        "recent_submissions": [item.model_dump(mode="json") for item in context.recent_submissions],
        "struggle_patterns": context.struggle_patterns,
        "intervention_hints": context.intervention_hints,
    }
