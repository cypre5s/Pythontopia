package com.pythontopia.protocol;

public record ProtocolPromptDto(
    String name,
    String protocol,
    String description,
    String outputContract) {}
