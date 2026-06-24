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
            "learner.effect.trace.summary",
            "tutorbench.run");
    assertThat(descriptor.a2aAgents()).extracting(ProtocolAgentCardDto::name)
        .contains("LearningEffectAgent", "TutorBenchAgent");
    assertThat(descriptor.aguiEvents())
        .contains("RUN_STARTED", "STEP_STARTED", "TEXT_MESSAGE_CONTENT", "RUN_FINISHED");
  }
}
