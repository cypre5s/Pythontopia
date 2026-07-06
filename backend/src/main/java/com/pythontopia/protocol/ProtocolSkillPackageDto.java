package com.pythontopia.protocol;

import java.util.List;

public record ProtocolSkillPackageDto(
    String key,
    String label,
    String description,
    List<String> toolNames,
    List<String> resourceNames,
    List<String> promptNames,
    List<String> evidenceFields,
    String runtimeSurface) {

  public ProtocolSkillPackageDto {
    toolNames = toolNames == null ? List.of() : List.copyOf(toolNames);
    resourceNames = resourceNames == null ? List.of() : List.copyOf(resourceNames);
    promptNames = promptNames == null ? List.of() : List.copyOf(promptNames);
    evidenceFields = evidenceFields == null ? List.of() : List.copyOf(evidenceFields);
  }
}
