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
                "Run the local TutorBench-lite scorecard over the learner trace.")),
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
                List.of("multi_turn_learning_closure", "anti_cheat_remediation", "evidence_consistency"))),
        List.of("RUN_STARTED", "STEP_STARTED", "TEXT_MESSAGE_CONTENT", "RUN_FINISHED", "RUN_ERROR"));
  }
}
