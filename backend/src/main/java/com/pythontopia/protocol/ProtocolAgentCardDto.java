package com.pythontopia.protocol;

import java.util.List;

public record ProtocolAgentCardDto(
    String name,
    String role,
    String endpoint,
    List<String> capabilities) {}
