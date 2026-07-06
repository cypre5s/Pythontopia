package com.pythontopia.learner.oracle;

import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.mastery.MasterySnapshot;
import com.pythontopia.learner.reflection.LearnerInsightRepository;
import com.pythontopia.learner.reflection.dto.LearnerInsightDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 预防式个性化预言家：据 mastery+confidence+failureKind 频次+先修DAG 预测高风险KC，并异步预生成预防包。 */
@Service
public class OracleService {

  /** 进入预测（返回给前端 / 落证据）的风险门槛。 */
  private static final double RISK_THRESHOLD = 0.35;

  /** 触发异步预防包预生成的更高门槛（成本守恒：仅对确属高风险的 KC 提前生成）。 */
  private static final double PREVENTION_THRESHOLD = 0.6;

  /** 先修被判「弱」的掌握度阈值（低于此或无记录视为薄弱先修）。 */
  private static final double WEAK_PREREQ_THRESHOLD = 0.5;

  /** 先修依赖链回溯最大跳数（V125 为线性链，足够覆盖）。 */
  private static final int MAX_PREREQ_HOPS = 5;

  private static final int TOP_K = 3;

  private final MasteryRepository masteryRepo;
  private final LearnerInsightRepository insightRepo;
  private final JdbcTemplate jdbc;
  private final PreventionPackService preventionPackService;

  public OracleService(
      MasteryRepository masteryRepo,
      LearnerInsightRepository insightRepo,
      JdbcTemplate jdbc,
      PreventionPackService preventionPackService) {
    this.masteryRepo = masteryRepo;
    this.insightRepo = insightRepo;
    this.jdbc = jdbc;
    this.preventionPackService = preventionPackService;
  }

  /**
   * 一条高风险预测。{@code evidenceEventIds} 指向真实 learning_event_log.id；{@code dependencyPath} 为
   * 导致风险的先修依赖链（薄弱先修的传播路径），不再只是一个扁平先修集合——满足「可溯源到真实
   * event id / 掌握度 / 先修依赖」红线。
   */
  public record RiskPrediction(
      String kcCode,
      double risk,
      List<Long> evidenceEventIds,
      String reason,
      List<String> dependencyPath) {}

  public List<RiskPrediction> predict(Long userId) {
    List<MasterySnapshot> mastery = masteryRepo.findAll(userId);
    if (mastery.isEmpty()) {
      return List.of();
    }
    List<LearnerInsightDto> insights = insightRepo.findByUser(userId, 5);
    Set<String> weakKcs =
        insights.stream()
            .filter(i -> "error_pattern".equals(i.dimensionKey()))
            .flatMap(i -> i.tags().stream())
            .collect(Collectors.toSet());
    Map<String, List<String>> prereqEdges = loadPrerequisiteEdges();
    Map<String, Integer> failureCounts = loadFailureCounts(userId);
    Map<String, Double> masteryByKc = new HashMap<>();
    for (MasterySnapshot s : mastery) {
      if (s.masteryScore() != null) {
        masteryByKc.put(s.kcCode(), s.masteryScore().doubleValue());
      }
    }

    List<RiskPrediction> predictions = new ArrayList<>();
    for (MasterySnapshot s : mastery) {
      if (s.masteryScore() == null) {
        continue;
      }
      double risk = 1.0 - s.masteryScore().doubleValue();
      // 低置信度 → 风险上调（自评把握不足）。
      if (s.confidence() != null && s.confidence().doubleValue() < 0.5) {
        risk += 0.1;
      }
      // 反思标记为易错。
      boolean fromInsight = weakKcs.contains(s.kcCode());
      if (fromInsight) {
        risk = Math.min(1.0, risk + 0.2);
      }
      // 近 7 天该类高频失败。
      int fails = failureCounts.getOrDefault(s.kcCode(), 0);
      if (fails >= 3) {
        risk = Math.min(1.0, risk + 0.15);
      }
      // 先修依赖：导致本 KC 风险的薄弱先修链（沿 DAG 回溯）。
      List<String> dependencyPath = weakPrerequisitePath(s.kcCode(), prereqEdges, masteryByKc);
      if (!dependencyPath.isEmpty()) {
        risk = Math.min(1.0, risk + 0.1);
      }

      if (risk >= RISK_THRESHOLD) {
        String reason = buildReason(risk, fromInsight, fails, dependencyPath);
        predictions.add(
            new RiskPrediction(
                s.kcCode(),
                Math.round(risk * 1000.0) / 1000.0,
                evidenceFor(userId, s.kcCode()),
                reason,
                dependencyPath));
      }
    }
    predictions.sort((a, b) -> Double.compare(b.risk(), a.risk()));
    List<RiskPrediction> top = predictions.subList(0, Math.min(TOP_K, predictions.size()));

    // 命中更高阈值 → 异步走真协同链预生成预防包（成本守恒：仅高风险 + 幂等去重在 service 内）。
    for (RiskPrediction prediction : top) {
      if (prediction.risk() >= PREVENTION_THRESHOLD) {
        preventionPackService.pregenerate(userId, prediction);
      }
    }
    return top;
  }

  /**
   * 沿先修 DAG 从 {@code kcCode} 回溯，收集「薄弱先修」链（掌握度低于阈值或无记录的先修）。
   * V125 为线性链，故依次取首个先修；遇到非薄弱先修即停止，限制最大跳数防环。
   */
  private List<String> weakPrerequisitePath(
      String kcCode, Map<String, List<String>> prereqEdges, Map<String, Double> masteryByKc) {
    List<String> path = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    String current = kcCode;
    while (path.size() < MAX_PREREQ_HOPS) {
      List<String> prereqs = prereqEdges.get(current);
      if (prereqs == null || prereqs.isEmpty()) {
        break;
      }
      String prereq = prereqs.get(0);
      if (prereq == null || seen.contains(prereq)) {
        break;
      }
      Double prereqScore = masteryByKc.get(prereq);
      boolean weak = prereqScore == null || prereqScore < WEAK_PREREQ_THRESHOLD;
      if (!weak) {
        break;
      }
      path.add(prereq);
      seen.add(prereq);
      current = prereq;
    }
    return path;
  }

  private String buildReason(
      double risk, boolean fromInsight, int fails, List<String> dependencyPath) {
    List<String> parts = new ArrayList<>();
    if (risk > 0.7) {
      parts.add("掌握度很低");
    }
    if (fromInsight) {
      parts.add("反思标记为易错");
    }
    if (fails >= 3) {
      parts.add("近期失败" + fails + "次");
    }
    if (!dependencyPath.isEmpty()) {
      parts.add("先修薄弱链：" + String.join("→", dependencyPath));
    }
    return parts.isEmpty() ? "综合评估" : String.join("+", parts);
  }

  /** 先修边：kc_code → 其 prerequisite_codes 列表（解析 Postgres 数组字面量）。读失败降级空。 */
  private Map<String, List<String>> loadPrerequisiteEdges() {
    Map<String, List<String>> edges = new HashMap<>();
    try {
      List<Map<String, Object>> rows =
          jdbc.queryForList(
              "SELECT kc_code, prerequisite_codes::text AS prereq FROM course_kc"
                  + " WHERE prerequisite_codes IS NOT NULL");
      for (Map<String, Object> row : rows) {
        Object kc = row.get("kc_code");
        if (kc == null) {
          continue;
        }
        edges.put(String.valueOf(kc), parseArrayLiteral(row.get("prereq")));
      }
    } catch (RuntimeException e) {
      return Map.of();
    }
    return edges;
  }

  /** 解析 Postgres text[] 字面量 {@code {A,B}} → List。空 / null → 空列表。 */
  private static List<String> parseArrayLiteral(Object raw) {
    if (raw == null) {
      return List.of();
    }
    String text = String.valueOf(raw).trim();
    if (text.length() < 2 || !text.startsWith("{") || !text.endsWith("}")) {
      return List.of();
    }
    String inner = text.substring(1, text.length() - 1).trim();
    if (inner.isEmpty()) {
      return List.of();
    }
    List<String> codes = new ArrayList<>();
    for (String part : inner.split(",")) {
      String code = part.trim().replaceAll("^\"|\"$", "");
      if (!code.isEmpty()) {
        codes.add(code);
      }
    }
    return codes;
  }

  private Map<String, Integer> loadFailureCounts(Long userId) {
    Map<String, Integer> counts = new HashMap<>();
    try {
      List<Map<String, Object>> rows =
          jdbc.queryForList(
              "SELECT payload->>'kcCode' AS kc, COUNT(*) AS cnt FROM learning_event_log"
                  + " WHERE user_id = ? AND event_type = 'practice_failed'"
                  + " AND created_at >= now() - INTERVAL '7 days'"
                  + " GROUP BY payload->>'kcCode'",
              userId);
      for (Map<String, Object> row : rows) {
        Object kc = row.get("kc");
        Object cnt = row.get("cnt");
        if (kc != null && cnt instanceof Number number) {
          counts.put(String.valueOf(kc), number.intValue());
        }
      }
    } catch (RuntimeException e) {
      return Map.of();
    }
    return counts;
  }

  private List<Long> evidenceFor(Long userId, String kcCode) {
    try {
      return new LinkedHashSet<>(
              jdbc.queryForList(
                  "SELECT id FROM learning_event_log WHERE user_id = ? AND payload::text LIKE ?"
                      + " ORDER BY created_at DESC LIMIT 5",
                  Long.class,
                  userId,
                  "%" + kcCode + "%"))
          .stream()
          .toList();
    } catch (RuntimeException e) {
      return List.of();
    }
  }
}
