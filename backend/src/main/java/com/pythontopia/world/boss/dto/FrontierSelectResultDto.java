package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 知识前沿选择器返回：按优先级排序的候选 KC 列表（graph 不可达时为空，前端回退本地 frontier）。 */
public record FrontierSelectResultDto(@JsonProperty("picks") List<FrontierPickDto> picks) {

  public FrontierSelectResultDto {
    picks = picks == null ? List.of() : List.copyOf(picks);
  }

  public static FrontierSelectResultDto empty() {
    return new FrontierSelectResultDto(List.of());
  }
}
