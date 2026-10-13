package com.pythontopia.learner.oracle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.workshop.dto.ResourceCreateRequest;
import com.pythontopia.workshop.dto.ResourceCreateResponse;
import com.pythontopia.workshop.dto.ResourceType;
import com.pythontopia.workshop.service.ResourceWorkshopService;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * P5.1 预防包预生成：命中高风险阈值的 KC，<b>异步</b>走既有真协同资源生成链（{@link ResourceWorkshopService}）
 * 提前生成一份预防讲义，并把记录连同证据（真实 event id + 先修依赖路径 + 风险/理由）落 {@code prevention_pack}，
 * 供学生抵达该薄弱 KC 时推送。成本守恒：同一 user+KC 近 {@value #IDEMPOTENCY_DAYS} 天内已有预防包则跳过。
 */
@Service
public class PreventionPackService {

  private static final Logger log = LoggerFactory.getLogger(PreventionPackService.class);

  /** 幂等窗口：近 N 天内同一 user+KC 已有预防包则不重复生成（成本守恒）。 */
  private static final int IDEMPOTENCY_DAYS = 7;

  private final ResourceWorkshopService resourceWorkshopService;
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final TutorDailyRequestLimitService requestLimit;

  public PreventionPackService(
      ResourceWorkshopService resourceWorkshopService,
      JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper,
      TutorDailyRequestLimitService requestLimit) {
    this.resourceWorkshopService = resourceWorkshopService;
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.requestLimit = requestLimit;
  }

  /**
   * 异步预生成预防包。后台 best-effort：任何失败（graph 不可用 / 限流 / 落库失败）记 WARN 不抛出，
   * 不影响发起预测的请求线程；幂等命中则直接返回。
   */
  @Async
  public void pregenerate(Long userId, OracleService.RiskPrediction prediction) {
    if (userId == null || prediction == null || prediction.kcCode() == null) {
      return;
    }
    try {
      if (hasRecentPack(userId, prediction.kcCode())) {
        log.debug(
            "prevention pack skipped (recent exists) user={} kc={}", userId, prediction.kcCode());
        return;
      }
      requestLimit.reserve(userId);
      ResourceCreateResponse response =
          resourceWorkshopService.create(
              userId,
              new ResourceCreateRequest(
                  List.of(prediction.kcCode()),
                  ResourceType.HANDOUT,
                  "预防式讲义：" + prediction.reason()));
      persist(userId, prediction, response.resourceId());
      log.info(
          "prevention pack generated user={} kc={} resource={} risk={}",
          userId,
          prediction.kcCode(),
          response.resourceId(),
          prediction.risk());
    } catch (RuntimeException ex) {
      log.warn(
          "prevention pack pregenerate failed user={} kc={}: {}",
          userId,
          prediction.kcCode(),
          ex.getMessage());
    }
  }

  private boolean hasRecentPack(Long userId, String kcCode) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM prevention_pack"
                + " WHERE user_id = ? AND kc_code = ? AND created_at >= now() - (? * interval '1 day')",
            Integer.class,
            userId,
            kcCode,
            IDEMPOTENCY_DAYS);
    return count != null && count > 0;
  }

  private void persist(Long userId, OracleService.RiskPrediction prediction, Long resourceId) {
    jdbcTemplate.update(
        "INSERT INTO prevention_pack (user_id, kc_code, resource_id, evidence)"
            + " VALUES (?, ?, ?, ?::jsonb)",
        userId,
        prediction.kcCode(),
        resourceId,
        evidenceJson(prediction));
  }

  /** 证据 JSON：风险、理由、真实 event id、先修依赖路径——满足「预测 100% 可溯源」红线。 */
  private String evidenceJson(OracleService.RiskPrediction prediction) {
    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("risk", prediction.risk());
    evidence.put("reason", prediction.reason());
    evidence.put(
        "eventIds",
        prediction.evidenceEventIds() == null ? List.of() : prediction.evidenceEventIds());
    evidence.put(
        "dependencyPath",
        prediction.dependencyPath() == null ? List.of() : prediction.dependencyPath());
    try {
      return objectMapper.writeValueAsString(evidence);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }
}
