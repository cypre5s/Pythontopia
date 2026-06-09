package com.pythontopia.feynman.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public final class FeynmanDtos {

  private FeynmanDtos() {}

  public record StartRequest(@NotBlank String kcCode) {}

  public record TeachRequest(@NotBlank String explanation) {}

  public record SessionResponse(long id, long userId, String kcCode, String status, JsonNode tuteeState) {}

  public record TurnResponse(long id, String role, String content, String taughtFix) {}

  public record Verdict(long sessionId, boolean passed, String message) {}
}
