package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 知识前沿选择器返回：按权威掌握度与画像排序的候选 KC 列表。 */
public record FrontierSelectResultDto(@JsonProperty("picks") List<FrontierPickDto> picks) {

  public FrontierSelectResultDto {
    picks = picks == null ? List.of() : List.copyOf(picks);
  }

  public static FrontierSelectResultDto empty() {
    return new FrontierSelectResultDto(List.of());
  }
}
