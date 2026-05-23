package com.pythontopia.world.save.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Create-slot request payload. The four W3 extension fields are optional: omitting them lets the
 * V114 DB defaults take effect (scene=WorldScene, no unlocked buildings, 0 seconds, empty
 * inventory). The controller currently only forwards {@code displayName}; the extension fields are
 * accepted for future-proofing and to keep this DTO aligned with the matching frontend payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SaveSlotCreateRequest(
    @NotBlank @Size(max = 64) String displayName,
    @Size(max = 64) String sceneName,
    List<@Size(max = 64) String> unlockedBuildings,
    @PositiveOrZero Long accumulatedPlaySeconds,
    JsonNode inventoryItems) {

  public SaveSlotCreateRequest(String displayName) {
    this(displayName, null, null, null, null);
  }
}
