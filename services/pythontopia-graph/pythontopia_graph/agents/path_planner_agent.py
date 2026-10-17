"""确定性 7 日学习路径规划器；按掌握度、近期练习、资源、易错点与班级任务排期。"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass

from pythontopia_graph.schemas.path import (
    PathPlannerGroundingResult,
    PathPlannerRequest,
    PathPlannerResponse,
    PathPlannerSafetyResult,
    PlannerClassroomQuest,
    PlannerKcSnapshot,
    PlannerNodeDraft,
    PlannerProblemSubmission,
    PlannerResourceUsage,
    ResourceTypeHint,
)

AGENT_NAME = "PathPlannerAgent"
MAX_DAYS = 7
MAX_NODES_PER_DAY = 4
TARGET_FOCUS_KCS = 5
LOW_MASTERY_THRESHOLD = 0.4


@dataclass(frozen=True)
class _CandidateKc:
    kc_code: str
    mastery: float
    chapter_id: int | None
    prerequisite_codes: tuple[str, ...]


class PathPlannerAgent:
    """据画像快照、掌握度与班级任务生成个性化 7 日路径节点；新学生用默认画像冷启动。"""

    AGENT_NAME = AGENT_NAME

    def plan(self, request: PathPlannerRequest) -> PathPlannerResponse:
        kc_index = self._build_kc_index(request.kcs)
        unlocked_kcs = [kc for kc in request.kcs if kc.unlocked]
        if not unlocked_kcs:
            return PathPlannerResponse(
                user_id=request.user_id,
                pack_id=request.pack_id,
                goal_summary="当前没有可推荐的已解锁 KC，请先从主线地图继续学习。",
                nodes=[],
                safety_result=PathPlannerSafetyResult(passed=True),
                grounding_result=PathPlannerGroundingResult(
                    passed=False, reasons=["NO_UNLOCKED_KC"]
                ),
                deterministic_signature=self._signature(request, []),
                status="FAILED",
            )

        nodes: list[PlannerNodeDraft] = []
        day_counter: dict[int, int] = {day: 0 for day in range(1, MAX_DAYS + 1)}

        classroom_kcs = self._classroom_quest_kcs(request.classroom_quests, kc_index)
        for classroom_quest in request.classroom_quests:
            for kc_code in classroom_quest.kc_codes:
                if kc_code not in kc_index:
                    continue
                self._append_node(
                    nodes,
                    day_counter,
                    day_index=1,
                    kc_code=kc_code,
                    node_type=classroom_quest.node_type,
                    reason=f"班级任务「{classroom_quest.title}」要求处理 {kc_code}",
                    evidence=f"班级任务 ID {classroom_quest.classroom_quest_id}",
                    suggested_resource_type=self._suggest_resource_type(classroom_quest.node_type),
                    source_classroom_quest_id=classroom_quest.classroom_quest_id,
                )

        error_pattern_kcs = {code for code in request.error_pattern_kcs if code}
        focus_candidates = self._select_focus_kcs(
            unlocked_kcs,
            request.recent_resources,
            request.recent_submissions,
            classroom_kcs,
            error_pattern_kcs,
        )
        # DAG：按先修关系拓扑排序，先修 KC 排到更靠前的天，保证「先学先修再学依赖」。
        focus_candidates = self._topological_order(focus_candidates)
        for index, candidate in enumerate(focus_candidates):
            day = (index % MAX_DAYS) + 1
            already_read = self._already_read(candidate.kc_code, request.recent_resources)
            if not already_read:
                self._append_node(
                    nodes,
                    day_counter,
                    day_index=day,
                    kc_code=candidate.kc_code,
                    node_type="READ_RESOURCE",
                    reason=(
                        f"{candidate.kc_code} 当前掌握度 {candidate.mastery:.2f}，"
                        "先用讲义或代码案例补充关键概念"
                    ),
                    evidence="基于 mastery 评估 + 资源工坊推荐",
                    suggested_resource_type="HANDOUT",
                )
            failed_recently = self._failed_recently(candidate.kc_code, request.recent_submissions)
            self._append_node(
                nodes,
                day_counter,
                day_index=day,
                kc_code=candidate.kc_code,
                node_type="FIX_BUG" if failed_recently else "SOLVE_PROBLEM",
                reason=(
                    f"巩固 {candidate.kc_code}：先做练习再去矿洞修 bug"
                    if failed_recently
                    else f"用 1 道难度匹配练习巩固 {candidate.kc_code}"
                ),
                evidence="基于最近练习记录" if failed_recently else "基于 mastery 与画像匹配难度",
                suggested_problem_id=self._pick_problem_id(
                    candidate.kc_code, request.recent_submissions
                ),
                suggested_resource_type=None,
            )

        grounding = self._grounding(nodes, kc_index)
        signature = self._signature(request, nodes)
        goal = self._summarise_goal(focus_candidates, request.classroom_quests)
        return PathPlannerResponse(
            user_id=request.user_id,
            pack_id=request.pack_id,
            goal_summary=goal,
            nodes=nodes,
            safety_result=PathPlannerSafetyResult(passed=True),
            grounding_result=grounding,
            deterministic_signature=signature,
            status="SUCCESS" if grounding.passed else "PARTIAL",
        )

    def _build_kc_index(self, kcs: list[PlannerKcSnapshot]) -> dict[str, PlannerKcSnapshot]:
        index: dict[str, PlannerKcSnapshot] = {}
        for kc in kcs:
            index[kc.kc_code] = kc
        return index

    def _classroom_quest_kcs(
        self,
        quests: list[PlannerClassroomQuest],
        kc_index: dict[str, PlannerKcSnapshot],
    ) -> set[str]:
        return {code for quest in quests for code in quest.kc_codes if code in kc_index}

    def _select_focus_kcs(
        self,
        unlocked: list[PlannerKcSnapshot],
        resources: list[PlannerResourceUsage],
        submissions: list[PlannerProblemSubmission],
        classroom_kcs: set[str],
        error_pattern_kcs: set[str],
    ) -> list[_CandidateKc]:
        def score(kc: PlannerKcSnapshot) -> tuple[int, float, int, int, str]:
            # error_pattern 命中作为最高优先键：把反思标记的高频坑前置（其余信号不变）。
            # 无命中时该键恒为 0，排序退化为原 (mastery_distance, classroom+failed, hash) 顺序（回归安全）。
            error_boost = 1 if kc.kc_code in error_pattern_kcs else 0
            mastery_distance = max(0.0, LOW_MASTERY_THRESHOLD - kc.mastery_score)
            classroom_boost = 1 if kc.kc_code in classroom_kcs else 0
            recent_failed = 1 if self._failed_recently(kc.kc_code, submissions) else 0
            return (
                -error_boost,
                -mastery_distance,
                -classroom_boost - recent_failed,
                hash_to_int(kc.kc_code),
                kc.kc_code,
            )

        ordered = sorted(unlocked, key=score)
        focus = ordered[:TARGET_FOCUS_KCS]
        return [
            _CandidateKc(
                kc_code=kc.kc_code,
                mastery=kc.mastery_score,
                chapter_id=kc.chapter_id,
                prerequisite_codes=tuple(kc.prerequisite_codes),
            )
            for kc in focus
        ]

    def _topological_order(self, candidates: list[_CandidateKc]) -> list[_CandidateKc]:
        """对聚焦 KC 做拓扑排序：先修（落在聚焦集合内的）排在依赖之前。

        DFS 后序 + 临时标记防环；以输入顺序为稳定 tie-break，保证确定式可复现。
        无先修时输出顺序 == 输入顺序（不影响既有行为）。
        """
        by_code = {c.kc_code: c for c in candidates}
        visited: set[str] = set()
        in_progress: set[str] = set()
        ordered: list[_CandidateKc] = []

        def visit(node: _CandidateKc) -> None:
            if node.kc_code in visited or node.kc_code in in_progress:
                return
            in_progress.add(node.kc_code)
            for prereq in node.prerequisite_codes:
                dep = by_code.get(prereq)
                if dep is not None:
                    visit(dep)
            in_progress.discard(node.kc_code)
            visited.add(node.kc_code)
            ordered.append(node)

        for candidate in candidates:
            visit(candidate)
        return ordered

    def _already_read(self, kc_code: str, resources: list[PlannerResourceUsage]) -> bool:
        return any(usage.kc_code == kc_code for usage in resources)

    def _failed_recently(self, kc_code: str, submissions: list[PlannerProblemSubmission]) -> bool:
        recent = [sub for sub in submissions if sub.kc_code == kc_code]
        if not recent:
            return False
        return not recent[-1].passed

    def _pick_problem_id(
        self, kc_code: str, submissions: list[PlannerProblemSubmission]
    ) -> int | None:
        for sub in submissions:
            if sub.kc_code == kc_code and not sub.passed:
                return sub.problem_id
        return None

    def _append_node(
        self,
        nodes: list[PlannerNodeDraft],
        day_counter: dict[int, int],
        *,
        day_index: int,
        kc_code: str,
        node_type: str,
        reason: str,
        evidence: str,
        suggested_resource_type: ResourceTypeHint | None = None,
        suggested_problem_id: int | None = None,
        source_classroom_quest_id: int | None = None,
    ) -> None:
        target_day = day_index
        for offset in range(MAX_DAYS):
            candidate_day = ((day_index - 1 + offset) % MAX_DAYS) + 1
            if day_counter[candidate_day] < MAX_NODES_PER_DAY:
                target_day = candidate_day
                break
        else:
            return
        day_counter[target_day] += 1
        order = day_counter[target_day]
        nodes.append(
            PlannerNodeDraft(
                day_index=target_day,
                order_in_day=order,
                kc_code=kc_code,
                node_type=node_type,  # type: ignore[arg-type]
                reason_summary=reason,
                evidence_summary=evidence,
                suggested_resource_type=suggested_resource_type,
                suggested_problem_id=suggested_problem_id,
                source_classroom_quest_id=source_classroom_quest_id,
            )
        )

    def _grounding(
        self, nodes: list[PlannerNodeDraft], kc_index: dict[str, PlannerKcSnapshot]
    ) -> PathPlannerGroundingResult:
        unknown = sorted({node.kc_code for node in nodes if node.kc_code not in kc_index})
        if unknown:
            return PathPlannerGroundingResult(
                passed=False,
                reasons=[f"UNKNOWN_KC:{','.join(unknown)}"],
                unknown_kc=unknown,
            )
        return PathPlannerGroundingResult(passed=True)

    def _suggest_resource_type(self, node_type: str) -> ResourceTypeHint | None:
        if node_type == "READ_RESOURCE":
            return "HANDOUT"
        return None

    def _summarise_goal(
        self,
        focus: list[_CandidateKc],
        classroom_quests: list[PlannerClassroomQuest],
    ) -> str:
        parts: list[str] = []
        if classroom_quests:
            kc_list = ",".join(
                sorted({code for quest in classroom_quests for code in quest.kc_codes})
            )
            parts.append(f"班级任务覆盖 KC: {kc_list}")
        if focus:
            parts.append("本周聚焦 " + ",".join(c.kc_code for c in focus))
        if not parts:
            parts.append("继续巩固已解锁 KC")
        return "；".join(parts)

    def _signature(self, request: PathPlannerRequest, nodes: list[PlannerNodeDraft]) -> str:
        payload = {
            "user_id": request.user_id,
            "pack_id": request.pack_id,
            "nodes": [
                {
                    "day": n.day_index,
                    "order": n.order_in_day,
                    "kc": n.kc_code,
                    "type": n.node_type,
                }
                for n in nodes
            ],
        }
        encoded = json.dumps(payload, ensure_ascii=False, sort_keys=True).encode()
        return hashlib.sha256(encoded).hexdigest()


def hash_to_int(text: str) -> int:
    digest = hashlib.sha256(text.encode()).hexdigest()
    return int(digest, 16) % (2**31 - 1)
