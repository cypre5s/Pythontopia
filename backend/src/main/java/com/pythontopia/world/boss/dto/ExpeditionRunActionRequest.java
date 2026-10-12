package com.pythontopia.world.boss.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Typed command to change server-owned gameplay state in an active expedition run. */
public record ExpeditionRunActionRequest(
    @NotBlank
        @Pattern(
            regexp =
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        String runId,
    @Pattern(regexp = "^[A-Za-z0-9_-]{1,32}$") String nodeId,
    @NotNull ExpeditionRunAction action,
    @Pattern(regexp = "^[a-z][a-z0-9_]{0,31}$") String relicId,
    @Pattern(regexp = "^[A-Za-z0-9_-]{1,256}$") String problemId) {

  public ExpeditionRunActionRequest(
      String runId, String nodeId, ExpeditionRunAction action, String relicId) {
    this(runId, nodeId, action, relicId, null);
  }
}
