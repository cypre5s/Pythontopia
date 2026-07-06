package com.pythontopia.classroom.dashboard.dto;

import java.util.List;

/**
 * 教师看板「章末大测试」聚合（R-15 主线）：每章参试人数 / 通过率 / 分数分布（箱线图数据）。
 *
 * <p>数据来源：{@code chapter_test}（{@code status='SUBMITTED'} 且 {@code score} 非空）+
 * {@code classroom_members}（ACTIVE 成员）。分数分布按「每生取最高分」（§5 计星以最高分为锚，避免重考重复计数）
 * 聚合成箱线五数概括；通过率 = 最高分 ≥ 60 的人数 / 参试人数（§5：1★ 阈值 60）。
 */
public record ChapterTestStatsDto(int totalStudents, List<ChapterTestRow> chapters) {

  /** 单章章测统计：参试人数 / 提交次数 / 通过人数 / 通过率 / 分数箱线分布。 */
  public record ChapterTestRow(
      int chapter,
      int participantCount,
      int attemptCount,
      int passedCount,
      double passRate,
      ScoreDistribution scoreDistribution) {}

  /** 箱线五数概括（基于每生最高分）；无数据时数值字段为 {@code null}，{@code count=0}。 */
  public record ScoreDistribution(
      int count, Integer min, Integer q1, Integer median, Integer q3, Integer max) {}
}
