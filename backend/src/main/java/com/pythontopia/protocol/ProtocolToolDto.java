package com.pythontopia.protocol;

import java.util.List;

public record ProtocolToolDto(
    String name,
    String protocol,
    String method,
    String path,
    String description,
    List<String> evidenceFields) {

  public ProtocolToolDto(
      String name, String protocol, String method, String path, String description) {
    this(name, protocol, method, path, description, List.of());
  }

  public ProtocolToolDto {
    evidenceFields = evidenceFields == null ? List.of() : List.copyOf(evidenceFields);
  }
}
