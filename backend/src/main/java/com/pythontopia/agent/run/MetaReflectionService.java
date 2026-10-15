package com.pythontopia.agent.run;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** agent 自我进化：周期读 resource_run 失败率 + resource_feedback 采纳/评分，规则产出策略微调并落审计。 */
@Service
public class MetaReflectionService {

  private static final Logger log = LoggerFactory.getLogger(MetaReflectionService.class);
  private static final int MIN_RUNS = 5;
  private static final int MIN_FEEDBACK = 3;
  private static final double LOW_ADOPTION = 0.4;
  private static final double LOW_RATING = 2.5;

  /** 真实写 resource_run / resource_feedback 的 canonical agent_name（与 ResourceWorkshopService.agentName 一致）。 */
  private static final String[] AGENTS = {
    "PracticeAgent",
    "ResourceDesignerAgent.handout",
    "ResourceDesignerAgent.reading",
    "MindMapAgent",
    "CodeLabAgent",
    "StoryboardAgent"
  };

  private final JdbcTemplate jdbc;

  public MetaReflectionService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 每6小时对所有 agent 做一次自我反思（成本：0 LLM 调用，纯规则）。 */
  @Scheduled(fixedRateString = "${pythontopia.meta-reflect.interval-ms:21600000}")
  public void reflectAll() {
    for (String name : AGENTS) {
      try {
        reflect(name);
      } catch (RuntimeException e) {
        log.warn("meta-reflect {} skipped errorType={}", name, e.getClass().getSimpleName());
      }
    }
  }

  /**
   * 据真实反馈调整策略：优先看 resource_feedback（采纳率/评分）——低采纳或低评分则降复杂度并增示例并落审计；
   * 反馈不足时回落看 resource_run 失败率。两路都无信号则不动（成本守恒）。
   */
  public void reflect(String agentName) {
    FeedbackStats feedback = loadFeedbackStats(agentName);
    if (feedback.total() >= MIN_FEEDBACK) {
      double adoptionRate = (double) feedback.adopted() / feedback.total();
      boolean lowAdoption = adoptionRate < LOW_ADOPTION;
      boolean lowRating = feedback.avgRating() != null && feedback.avgRating() < LOW_RATING;
      if (lowAdoption || lowRating) {
        String reason =
            String.format(
                "近7天反馈%d条，采纳率%.0f%%%s，降低复杂度并增加示例",
                feedback.total(),
                adoptionRate * 100,
                feedback.avgRating() == null
                    ? ""
                    : String.format("、平均评分%.1f", feedback.avgRating()));
        writeStrategy(agentName, Map.of("complexity", "basic", "example_density", "high"), reason);
        return;
      }
    }

    // 反馈不足/正向 → 回落到 run 失败率规则（原有逻辑）。
    List<Map<String, Object>> stats =
        jdbc.queryForList(
            "SELECT status, COUNT(*) AS cnt FROM resource_run"
                + " WHERE agent_name = ? AND created_at >= now() - INTERVAL '7 days' GROUP BY status",
            agentName);
    int total = stats.stream().mapToInt(r -> ((Number) r.get("cnt")).intValue()).sum();
    if (total < MIN_RUNS) {
      return;
    }
    long failed =
        stats.stream()
            .filter(r -> "FAILED".equals(r.get("status")))
            .mapToLong(r -> ((Number) r.get("cnt")).longValue())
            .sum();
    double failRate = (double) failed / total;

    Map<String, Object> params;
    String reason;
    if (failRate > 0.3) {
      reason = String.format("近7天失败率%.0f%%，降低复杂度", failRate * 100);
      params = Map.of("complexity", "basic");
    } else if (failRate < 0.1 && total >= 10) {
      reason = "成功率>90%，可提升难度";
      params = Map.of("complexity", "advanced");
    } else {
      return;
    }
    writeStrategy(agentName, params, reason);
  }

  /** 写一条新版本策略 + 审计理由（agent_strategy）。 */
  private void writeStrategy(String agentName, Map<String, Object> params, String reason) {
    int ver =
        jdbc.queryForObject(
            "SELECT COALESCE(MAX(version),0)+1 FROM agent_strategy WHERE agent_name=?",
            Integer.class,
            agentName);
    jdbc.update(
        "INSERT INTO agent_strategy (agent_name, strategy_params, version, reason)"
            + " VALUES (?,?::jsonb,?,?)",
        agentName,
        toJson(params),
        ver,
        reason);
    log.info("meta-reflect {} v{} strategy updated", agentName, ver);
  }

  /** 读近7天反馈聚合：条数 / 采纳数 / 平均评分（无评分则 null）。 */
  private FeedbackStats loadFeedbackStats(String agentName) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE adopted) AS adopted_cnt,"
                + " AVG(rating) AS avg_rating FROM resource_feedback"
                + " WHERE agent_name = ? AND created_at >= now() - INTERVAL '7 days'",
            agentName);
    if (rows.isEmpty()) {
      return new FeedbackStats(0, 0, null);
    }
    Map<String, Object> row = rows.get(0);
    int total = intValue(row.get("total"));
    int adopted = intValue(row.get("adopted_cnt"));
    Double avgRating = row.get("avg_rating") instanceof Number n ? n.doubleValue() : null;
    return new FeedbackStats(total, adopted, avgRating);
  }

  private static int intValue(Object value) {
    return value instanceof Number n ? n.intValue() : 0;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> latestStrategy(String agentName) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT strategy_params FROM agent_strategy WHERE agent_name=? ORDER BY version DESC LIMIT 1",
            agentName);
    if (rows.isEmpty()) {
      return Map.of();
    }
    return (Map<String, Object>) rows.get(0).get("strategy_params");
  }

  /**
   * 把目标 agent 最新策略折算成下传 graph 的 directive 字符串（P4.1 闭环）；无策略时返回 null（不下传）。
   */
  public String latestDirective(String agentName) {
    Map<String, Object> params = latestStrategy(agentName);
    if (params == null || params.isEmpty()) {
      return null;
    }
    return params.entrySet().stream()
        .map(e -> e.getKey() + "=" + e.getValue())
        .collect(Collectors.joining("; "));
  }

  private String toJson(Map<String, Object> m) {
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (var e : m.entrySet()) {
      if (!first) {
        sb.append(",");
      }
      sb.append("\"").append(e.getKey()).append("\":\"").append(e.getValue()).append("\"");
      first = false;
    }
    return sb.append("}").toString();
  }

  private record FeedbackStats(int total, int adopted, Double avgRating) {}
}
