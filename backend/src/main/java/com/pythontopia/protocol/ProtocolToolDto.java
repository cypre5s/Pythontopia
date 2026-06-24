package com.pythontopia.protocol;

public record ProtocolToolDto(
    String name,
    String protocol,
    String method,
    String path,
    String description) {}
