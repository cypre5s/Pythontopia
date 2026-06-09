package com.pythontopia.learner.decision;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 决策玻璃盒数据层（P7.1）：从 resource_run 读取该生最近决策，归一 scenario 为决策类型并附人话结论，
 * 供前端 GlassBox 按 push/problem/path/remediation 等分类展开「为什么 + agent + 课件证据链」。
 */
@Service
public class DecisionTraceService {

  private static final int MAX_LIMIT = 50;

  private final JdbcTemplate jdbc;

  public DecisionTraceService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<DecisionTraceDto> recent(Long userId, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
    return jdbc.query(
        "SELECT agent_name, scenario, status, evidence_page_ids,"
            + " graph_trace::text AS trace, created_at::text AS ts"
            + " FROM resource_run WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
        (rs, rowNum) -> {
          String scenario = rs.getString("scenario");
          String status = rs.getString("status");
          return new DecisionTraceDto(
              rs.getString("agent_name"),
              scenario,
              classifyDecisionKind(scenario),
              status,
              conclusionOf(status),
              readLongArray(rs.getArray("evidence_page_ids")),
              rs.getString("trace"),
              rs.getString("ts"));
        },
        userId,
        safeLimit);
  }

  /**
   * 把 resource_run.scenario 归一为前端决策类型。绑定真实决策类型：练习/题目→problem，路径→path，
   * 班级补救→remediation，推送→push，资源生成→resource，画像访谈→profile，其余回退原值小写。
   */
  static String classifyDecisionKind(String scenario) {
    if (scenario == null || scenario.isBlank()) {
      return "unknown";
    }
    String upper = scenario.toUpperCase(Locale.ROOT);
    if (upper.contains("PRACTICE") || upper.contains("CODE_LAB") || upper.contains("BOSS")) {
      return "problem";
    }
    if (upper.startsWith("CURRICULUM") || upper.contains("PATH") || upper.contains("PLAN")) {
      return "path";
    }
    if (upper.contains("REMEDIATION")) {
      return "remediation";
    }
    if (upper.contains("RECOMMEND") || upper.contains("PUSH")) {
      return "push";
    }
    if (upper.startsWith("RESOURCE_")) {
      return "resource";
    }
    if (upper.startsWith("PROFILE")) {
      return "profile";
    }
    return scenario.toLowerCase(Locale.ROOT);
  }

  /** 由 run 状态给出人话结论（决策可信展示）。 */
  static String conclusionOf(String status) {
    if (status == null) {
      return "未知";
    }
    return switch (status) {
      case "SUCCESS" -> "已完成并通过审查";
      case "PARTIAL" -> "部分完成（含降级）";
      case "FAILED" -> "已拦截 / 未通过审查";
      default -> status;
    };
  }

  private static List<Long> readLongArray(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    Object raw = array.getArray();
    if (raw instanceof Long[] values) {
      return Arrays.asList(values);
    }
    if (raw instanceof Number[] values) {
      return Arrays.stream(values).map(Number::longValue).toList();
    }
    if (raw instanceof Object[] values) {
      return Arrays.stream(values)
          .filter(Number.class::isInstance)
          .map(Number.class::cast)
          .map(Number::longValue)
          .toList();
    }
    return List.of();
  }
}
