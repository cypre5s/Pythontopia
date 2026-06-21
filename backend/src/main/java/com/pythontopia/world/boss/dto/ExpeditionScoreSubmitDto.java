package com.pythontopia.world.boss.dto;

import jakarta.validation.constraints.Min;

/**
 * 一局无尽远征结束（胜利 / 体力耗尽 / 主动结束）后上报的成绩。
 *
 * <p>仅记录过程性成绩用于班级排行榜，掌握度 / 判题结果仍由权威表负责，这里不参与达标判定。
 */
public record ExpeditionScoreSubmitDto(
    @Min(0) int score,
    @Min(0) int depth,
    @Min(0) int bestMomentum,
    boolean cleared,
    boolean daily) {}
