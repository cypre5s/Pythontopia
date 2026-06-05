package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 单条测试用例。Backend 内部使用，<b>不</b>序列化返给前端（避免 hidden 答案泄露）。 */
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
