package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pythontopia.world.boss.dto.BossProblemRequest.ProfileSignalsPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * 无尽远征「知识前沿选择器」请求 body。
 *
 * <p>掌握度、画像与练习时间由后端权威读取；近期 KC 字段保留给旧客户端兼容但不参与排序。
 *
 * @param profileSignals 学生画像信号（经验证的 8 维目标/偏好，以及弱项、练习情况）
 * @param recentKcs 旧客户端字段，服务端忽略并使用正式练习事件时间
 * @param focusChapter 聚焦章节 1..7（null=全局自适应）
 * @param exclude 排除的 KC（本局已用，避免重复）
 * @param count 需要的候选数量 1..40
 */
public record FrontierSelectRequest(
    @JsonProperty("profileSignals") @Valid ProfileSignalsPayload profileSignals,
    @JsonProperty("recentKcs") List<String> recentKcs,
    @JsonProperty("focusChapter") @Min(1) @Max(7) Integer focusChapter,
    @JsonProperty("exclude") List<String> exclude,
    @JsonProperty("count") int count) {

  public FrontierSelectRequest {
    recentKcs = recentKcs == null ? List.of() : List.copyOf(recentKcs);
    exclude = exclude == null ? List.of() : List.copyOf(exclude);
    count = Math.max(1, Math.min(40, count));
  }
}
