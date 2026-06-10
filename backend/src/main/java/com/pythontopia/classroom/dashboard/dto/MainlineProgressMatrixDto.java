package com.pythontopia.classroom.dashboard.dto;

import java.util.List;

/**
 * 教师看板「成员主线进度」聚合（R-15 主线）：成员 × 章节 的 stage 完成度矩阵。
 *
 * <p>数据来源：{@code mainline_stage}（各章 stage 总数，全局静态 seed）+ {@code mainline_stage_progress}
 * （{@code completed_at IS NOT NULL} 视为该 stage 完成）+ {@code classroom_members}（ACTIVE 成员）+
 * {@code chapter_star}（成员累计星数，用于排序辅助）。
 */
public record MainlineProgressMatrixDto(
    int totalStudents,
    int totalStages,
    List<ChapterStageCount> chapterStageCounts,
    List<MemberProgressRow> members) {

  /** 单章 stage 总数（来自 {@code mainline_stage} 静态定义）。 */
  public record ChapterStageCount(int chapter, int stageCount) {}

  /** 单个成员的主线进度行：跨章完成 stage 合计 + 累计星数 + 逐章完成度单元格。 */
  public record MemberProgressRow(
      long studentId,
      String displayName,
      int completedStages,
      int totalStars,
      List<ChapterCell> chapters) {}

  /** 成员在某章的完成度单元格：已完成 stage / 该章 stage 总数。 */
  public record ChapterCell(int chapter, int completedStages, int totalStages) {}
}
