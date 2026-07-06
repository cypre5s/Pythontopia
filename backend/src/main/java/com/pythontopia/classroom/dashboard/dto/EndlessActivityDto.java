package com.pythontopia.classroom.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 教师看板「无尽活跃度」聚合（R-15 主线）：近 7 日无尽补星轮次 / 补星数 / 活跃人数（按天 + 按章）。
 *
 * <p><b>数据来源与口径（重要 · 待与 A1 迁移对齐）</b>：§3.2 当前<b>没有</b>「无尽单轮明细表」，
 * 因此本端点以 {@code star_award_event}（{@code source='ENDLESS_RECOVERY'}）为唯一权威来源。
 * 由 §7「每日每章至多记 1 次有效补星判定」可知：每条 ENDLESS_RECOVERY 事件 = 一次达标的无尽补星轮次，
 * 故 {@code recoveryRounds == recoveryStars}（达标轮次数）。
 *
 * <p><b>缺口</b>：「总轮次」与「逐轮正确率」需一张无尽轮次日志（§3.2 未定义）；{@link #accuracyPct} 在该表
 * 落地前固定为 {@code null}（前端显示「暂未采集」）。详见交付报告「与 §3.2 的列/表假设」。
 */
public record EndlessActivityDto(
    LocalDate periodStart,
    LocalDate periodEnd,
    int totalStudents,
    int activeStudentCount,
    int recoveryRoundTotal,
    int recoveryStarTotal,
    Double accuracyPct,
    List<DailyEndless> daily,
    List<ChapterEndless> chapters) {

  /** 单日无尽补星活跃：达标补星轮次 + 发放补星数（口径相同，见类注释）。 */
  public record DailyEndless(LocalDate day, int recoveryRounds, int recoveryStars) {}

  /** 单章无尽补星活跃：达标补星轮次 + 发放补星数 + 参与人数。 */
  public record ChapterEndless(int chapter, int recoveryRounds, int recoveryStars, int studentCount) {}
}
