package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * R-13 §5 Phase 6 续：单条测试用例。Backend 内部使用，<b>不</b> 序列化返给前端。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BossTestCasePayload(
    @JsonProperty("input") String input,
    @JsonProperty("expectedOutput") String expectedOutput,
    @JsonProperty("isSample") boolean isSample) {

  public BossTestCasePayload {
    if (input == null) {
      input = "";
    }
    if (expectedOutput == null) {
      expectedOutput = "";
    }
  }
}
