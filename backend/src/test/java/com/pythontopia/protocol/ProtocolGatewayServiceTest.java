package com.pythontopia.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** MCP / A2A / AG-UI 只是包装层，但必须导出真实学习闭环与 TutorBench 能力。 */
class ProtocolGatewayServiceTest {

  private final ProtocolGatewayService service = new ProtocolGatewayService();

  @Test
  void exportsLearningLoopAndTutorBenchAsMcpA2aAndAguiCapabilities() {
    ProtocolGatewayDescriptorDto descriptor = service.descriptor();

    assertThat(descriptor.mcpTools()).extracting(ProtocolToolDto::name)
        .contains(
            "workshop.resource.consume",
            "workshop.resource.check.submit",
            "learner.recommendations.read",
            "learner.effect.trace.summary",
            "tutorbench.run");
    ProtocolToolDto checkSubmit =
        descriptor.mcpTools().stream()
            .filter(tool -> "workshop.resource.check.submit".equals(tool.name()))
            .findFirst()
            .orElseThrow();
    assertThat(checkSubmit.evidenceFields()).contains("checkId", "resourceId", "masteryDelta");
    ProtocolToolDto recommendations =
        descriptor.mcpTools().stream()
            .filter(tool -> "learner.recommendations.read".equals(tool.name()))
            .findFirst()
            .orElseThrow();
    assertThat(recommendations.evidenceFields())
        .contains("evidenceEventIds", "checkIds", "pageIds");
    assertThat(descriptor.a2aAgents()).extracting(ProtocolAgentCardDto::name)
        .contains("LearningEffectAgent", "TutorBenchAgent");
    assertThat(descriptor.aguiEvents())
        .contains("RUN_STARTED", "STEP_STARTED", "TEXT_MESSAGE_CONTENT", "RUN_FINISHED");
    assertThat(descriptor.mcpResources())
        .extracting(ProtocolResourceDto::name)
        .contains("course.pack.43", "learner.evidence.graph", "learner.evidence.memory");
    assertThat(descriptor.mcpPrompts())
        .extracting(ProtocolPromptDto::name)
        .contains("grounded_tutor_answer", "resource_micro_check", "evidence_memory_recommendation");
    assertThat(descriptor.otelGenAiAttributes())
        .contains(
            "gen_ai.operation.name",
            "gen_ai.request.model",
            "gen_ai.response.model",
            "gen_ai.tool.name",
            "gen_ai.retrieval.query.text");
  }
}
