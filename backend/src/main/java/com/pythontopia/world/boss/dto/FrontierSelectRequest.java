package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pythontopia.world.boss.dto.BossProblemRequest.ProfileSignalsPayload;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * 无尽远征「知识前沿选择器」请求 body。
 *
 * <p>掌握度由后端从 {@code MasteryRepository} 权威读取，不从前端传（防刷 / 单一事实源）；
 * 前端只传画像信号、近期 KC（间隔重复）、聚焦章节、排除集与候选数量。
 *
 * @param profileSignals 学生画像信号（弱项 / 成功率 / 韧性等）
 * @param recentKcs 近期练过的 KC（最近在前），用于间隔重复
 * @param focusChapter 聚焦章节 1..7（null=全局自适应）
 * @param exclude 排除的 KC（本局已用，避免重复）
 * @param count 需要的候选数量 1..40
 */
public record FrontierSelectRequest(
    @JsonProperty("profileSignals") ProfileSignalsPayload profileSignals,
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
