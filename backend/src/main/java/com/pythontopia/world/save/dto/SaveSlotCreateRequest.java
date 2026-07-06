package com.pythontopia.world.save.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 建 slot 请求 body。四个扩展字段可选：省略时由 V114 DB 默认值生效
 * （scene=WorldScene、无 unlocked buildings、0 秒、空 inventory）。controller 当前只转发
 * {@code displayName}，扩展字段保留以与前端 payload 形状对齐。
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
