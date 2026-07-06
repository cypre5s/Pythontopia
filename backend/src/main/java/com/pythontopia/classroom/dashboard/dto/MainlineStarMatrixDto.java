package com.pythontopia.classroom.dashboard.dto;

import java.util.List;

/**
 * 教师看板「星级分布」聚合（R-15 主线）：每章 0/1/2/3★ 人数矩阵 + 总星 TopN 排行。
 *
 * <p>数据来源：{@code chapter_star}（每生每章星级，{@code stars} 0..3）+ {@code classroom_members}
 * （ACTIVE 成员）。无 {@code chapter_star} 行的成员按 0★ 计：{@code zeroStar = totalStudents -
 * oneStar - twoStar - threeStar}（见 §3.2 / §5）。
 */
public record MainlineStarMatrixDto(
    int totalStudents, List<ChapterStarRow> chapters, List<StudentStarTotal> topStudents) {

  /** 单章星级人数分布（0/1/2/3★ 各档人数）+ 平均星级。 */
  public record ChapterStarRow(
      int chapter, int zeroStar, int oneStar, int twoStar, int threeStar, double avgStars) {}

  /** 总星排行项：跨 7 章累计星数 + 已得星章节数。 */
  public record StudentStarTotal(
      long studentId, String displayName, int totalStars, int starredChapters) {}
}
