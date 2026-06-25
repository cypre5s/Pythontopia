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
                "Submit micro-quiz or operation evidence before mastery can change."),
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
                "Read the SQL-native learning evidence graph for one learner and optional focus KC.")),
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
}
