package com.pythontopia.protocol;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProtocolGatewayService {

  public ProtocolGatewayDescriptorDto descriptor() {
    return new ProtocolGatewayDescriptorDto(
        "2026-06-learning-loop",
        List.of(
            new ProtocolToolDto(
                "workshop.resource.consume",
                "MCP",
                "POST",
                "/api/workshop/resources/{id}/consumptions",
                "Record resource consumption and return the required micro-check prompt."),
            new ProtocolToolDto(
                "workshop.resource.check.submit",
                "MCP",
                "POST",
                "/api/workshop/resources/{id}/checks",
                "Submit micro-quiz or operation evidence before mastery can change.",
                List.of("checkId", "resourceId", "kcCode", "status", "masteryDelta")),
            new ProtocolToolDto(
                "learner.recommendations.read",
                "MCP",
                "GET",
                "/api/learner/recommendations",
                "Read traceable daily recommendations grounded in Learner Twin and evidence memory.",
                List.of("evidenceEventIds", "checkIds", "pageIds")),
            new ProtocolToolDto(
                "learner.twin.weekly.read",
                "MCP",
                "GET",
                "/api/learner/twin/weekly",
                "Read the Learner Twin weekly evidence brief, insights, mastery deltas, and resource runs.",
                List.of("memoryBrief", "insights", "masteryDeltas", "resourceRuns")),
            new ProtocolToolDto(
                "learner.effect.trace.summary",
                "MCP",
                "GET",
                "/api/learner/effect/summary",
                "Quantify consumption, checks, mastery deltas, and remediation rates."),
            new ProtocolToolDto(
                "tutorbench.run",
                "MCP",
                "POST",
                "/api/tutorbench/runs",
                "Run the local TutorBench-lite scorecard over the learner trace."),
            new ProtocolToolDto(
                "learner.evidence.graph.read",
                "MCP",
                "GET",
                "/api/learner/evidence-graph",
                "Read the SQL-native learning evidence graph for one learner and optional focus KC."),
            new ProtocolToolDto(
                "browse.run",
                "MCP",
                "POST",
                "/api/browse/runs",
                "Run a bounded allowlisted external research task with separated non-courseware citations.",
                List.of("runId", "allowedDomains", "citations", "externalLabel"))),
        List.of(
            new ProtocolAgentCardDto(
                "LearningEffectAgent",
                "A2A",
                "/api/learner/effect/summary",
                List.of("trace_summary", "mastery_delta_audit", "remediation_rate")),
            new ProtocolAgentCardDto(
                "TutorBenchAgent",
                "A2A",
                "/api/tutorbench/runs",
                List.of(
                    "multi_turn_learning_closure",
                    "anti_cheat_remediation",
                    "evidence_consistency",
                    "evidence_graph_connectivity"))),
        List.of(
            "RUN_STARTED",
            "STEP_STARTED",
            "STEP_FINISHED",
            "TEXT_MESSAGE_START",
            "TEXT_MESSAGE_CONTENT",
            "TEXT_MESSAGE_END",
            "TOOL_CALL_START",
            "TOOL_CALL_ARGS",
            "TOOL_CALL_END",
            "TOOL_CALL_RESULT",
            "STATE_SNAPSHOT",
            "STATE_DELTA",
            "RUN_FINISHED",
            "RUN_ERROR",
            "CUSTOM"),
        List.of(
            new ProtocolResourceDto(
                "course.pack.43",
                "MCP",
                "pythontopia://course-pack/43",
                "Python语言基础课程包，含课件页、KC、示例与题目。"),
            new ProtocolResourceDto(
                "learner.evidence.graph",
                "MCP",
                "pythontopia://learner/{userId}/evidence-graph",
                "学习事件、资源、微测、掌握度、页码与 KC 关系的可审计子图。"),
            new ProtocolResourceDto(
                "learner.evidence.memory",
                "MCP",
                "pythontopia://learner/{userId}/evidence-memory",
                "由证据图派生的选择性长期学习记忆与弱点扩散信号。")),
        List.of(
            new ProtocolPromptDto(
                "grounded_tutor_answer",
                "MCP",
                "Tutor answers must cite courseware evidence when available and mark general knowledge explicitly.",
                "answer_md + evidence_page_ids + grounding_result"),
            new ProtocolPromptDto(
                "resource_micro_check",
                "MCP",
                "Generated learning resources must end in a micro-check or operation-evidence gate.",
                "prompt + expected_evidence_type + kc_codes"),
            new ProtocolPromptDto(
                "evidence_memory_recommendation",
                "MCP",
                "Recommendation reasons must include traceable event/check/page evidence when available.",
                "kc_code + reason + evidence_event_ids + page_ids")),
        skillPackages(),
        List.of(
            "gen_ai.operation.name",
            "gen_ai.request.model",
            "gen_ai.response.model",
            "gen_ai.request.temperature",
            "gen_ai.response.finish_reasons",
            "gen_ai.usage.input_tokens",
            "gen_ai.usage.output_tokens",
            "gen_ai.tool.name",
            "gen_ai.tool.call.id",
            "gen_ai.tool.call.arguments",
            "gen_ai.tool.call.result",
            "gen_ai.retrieval.query.text",
            "gen_ai.retrieval.documents"));
  }

  private static List<ProtocolSkillPackageDto> skillPackages() {
    return List.of(
        new ProtocolSkillPackageDto(
            "evidence_grounded_workshop",
            "证据接地工坊",
            "把资源消费、微测证据、推荐证据和课程页引用打包成完整看学练测闭环。",
            List.of(
                "workshop.resource.consume",
                "workshop.resource.check.submit",
                "learner.recommendations.read"),
            List.of("course.pack.43", "learner.evidence.graph", "learner.evidence.memory"),
            List.of("resource_micro_check", "evidence_memory_recommendation"),
            List.of("checkId", "resourceId", "kcCode", "masteryDelta", "evidenceEventIds", "pageIds"),
            "workshop"),
        new ProtocolSkillPackageDto(
            "learner_twin_memory",
            "Learner Twin 记忆包",
            "把周报、画像洞察、掌握变化和 Evidence Memory 作为下一轮学习请求的可追踪上下文。",
            List.of(
                "learner.twin.weekly.read",
                "learner.recommendations.read",
                "learner.evidence.graph.read"),
            List.of("learner.evidence.graph", "learner.evidence.memory"),
            List.of("evidence_memory_recommendation"),
            List.of("memoryBrief", "insights", "masteryDeltas", "checkIds", "pageIds"),
            "workshop.right_panel"),
        new ProtocolSkillPackageDto(
            "eval_observability",
            "Eval / Observability 包",
            "把 TutorBench、学习效果摘要、AG-UI 事件和 OTel GenAI 字段组合成可验收运行面板。",
            List.of("tutorbench.run", "learner.effect.trace.summary"),
            List.of("learner.evidence.graph"),
            List.of("grounded_tutor_answer"),
            List.of("trace_summary", "mastery_delta_audit", "remediation_rate"),
            "runtime.drawer"),
        new ProtocolSkillPackageDto(
            "external_research_safety",
            "外部查证安全包",
            "把联网查证限制在 allowlist、只读步骤和外部引用标签内，不混入课件页码。",
            List.of("browse.run"),
            List.of("course.pack.43"),
            List.of("grounded_tutor_answer"),
            List.of("runId", "allowedDomains", "citations", "externalLabel"),
            "workshop.browse"));
  }
}
