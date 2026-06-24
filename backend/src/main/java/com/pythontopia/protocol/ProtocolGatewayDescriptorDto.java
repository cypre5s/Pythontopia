package com.pythontopia.protocol;

import java.util.List;

public record ProtocolGatewayDescriptorDto(
    String version,
    List<ProtocolToolDto> mcpTools,
    List<ProtocolAgentCardDto> a2aAgents,
    List<String> aguiEvents) {}
