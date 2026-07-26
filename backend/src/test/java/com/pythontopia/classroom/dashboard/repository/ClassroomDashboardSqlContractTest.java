package com.pythontopia.classroom.dashboard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 看板聚合的<b>口径</b>回归。聚合本身是 SQL，在没有数据库的单测层无法端到端验证（Testcontainers
 * 集成测试另有一套），所以这里锁两件事：
 *
 * <ol>
 *   <li>缺陷 5：任务完成率的分子必须限定在「仍在班的成员」，分母是当前 ACTIVE 人数——两边同口径
 *       才不会算出 &gt;100%；
 *   <li>缺陷 6：KC 热力图的平均掌握度必须除以<b>全班人数</b>，而不是 AVG() 那种「只对有
 *       kc_mastery 行的学生取平均」。
 * </ol>
 */
class ClassroomDashboardSqlContractTest {

  @Test
  @DisplayName("缺陷 5：完成率分子被 EXISTS 限定在 ACTIVE 成员，避免已移出学生把完成率顶过 100%")
  void questSummaryCountsOnlyCurrentMembers() {
    String sql = normalise(ClassroomDashboardRepository.QUEST_SUMMARY_SQL);

    // 分母：当前 ACTIVE 成员数。
    assertThat(sql)
        .contains("SELECT COUNT(*) FROM classroom_members cm WHERE cm.classroom_id = "
            + "cq.classroom_id AND cm.status = 'ACTIVE'");
    // 分子：quest 行必须仍能对应一个 ACTIVE 成员，否则不参与任何计数。
    assertThat(sql)
        .contains("EXISTS ( SELECT 1 FROM classroom_members cm2 WHERE cm2.classroom_id = "
            + "cq.classroom_id AND cm2.student_id = q.user_id AND cm2.status = 'ACTIVE')");
  }

  @Test
  @DisplayName("缺陷 5：completionRate 分母为 0 时返回 0，不再用 Math.max(1, total) 掩盖倒挂")
  void completionRateHandlesEmptyClassroom() {
    assertThat(ClassroomDashboardRepository.completionRate(0, 0))
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(ClassroomDashboardRepository.completionRate(3, 5))
        .isEqualByComparingTo(new BigDecimal("0.600"));
    assertThat(ClassroomDashboardRepository.completionRate(5, 5))
        .isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  @DisplayName("缺陷 6：avg_mastery 除以全班 ACTIVE 人数，不用会跳过 NULL 的 AVG()")
  void kcHeatmapAveragesOverWholeClassroom() {
    String sql = normalise(ClassroomDashboardRepository.KC_HEATMAP_SQL);

    assertThat(sql)
        .contains("SUM(m.mastery_score) / NULLIF(COUNT(DISTINCT am.student_id), 0)");
    // AVG(m.mastery_score) 会把「没练过这个 KC 的学生」整行跳过：30 人班里 2 人练到 0.95
    // 会被涂成「掌握度 95%」，与同一格底部的「2/30 掌握」自相矛盾。
    assertThat(sql).doesNotContain("AVG(m.mastery_score)");
  }

  /** 把 text block 里的换行/缩进压成单空格，让断言不受 SQL 排版影响。 */
  private static String normalise(String sql) {
    return sql.replaceAll("--[^\\n]*", " ").replaceAll("\\s+", " ").trim();
  }
}
