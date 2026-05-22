package com.pythontopia.learner.profile;

import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * 阶段 M4 - 学习事件 → 八维画像被动更新。
 *
 * <p>每个事件映射到一个主维度的 {@link PassiveProfileService#applyDimensionUpdate} 调用，由 service
 * 内部统一写 {@code learner_profile.dimensions} 字段 + 追加 {@code profile_revision} 审计行；
 * V091 限制的 source 枚举（PRACTICE_PASS / PRACTICE_FAIL / DEBUG_FIX / RESOURCE_FEEDBACK 等）由本监听器显式透传。
 *
 * <p>降级策略：捕获 {@link DataAccessException}（典型场景：{@code dimensions} 列尚未由 W2-MIGRATE 创建），
 * 仅记录 warn 日志，保证 mastery 评估等下游链路不受影响。
 *
 * <p>TODO(M4-后续)：以下事件源在 Pythontopia 当前代码库中无独立 LearningEvent，暂未接入：
 *
 * <ul>
 *   <li>资源生成请求 → learning_preference（应在 ResourceWorkshopService.create 末尾接入）
 *   <li>课件页阅读完成 → knowledge_base（应在 PageReadService / Library 模块接入）
 *   <li>班级任务完成 → emotion_persistence（应在 ClassroomQuestService.markCompleted 接入）
 * </ul>
 */
@Component
public class PassiveProfileRevisionListener {

  private static final Logger log = LoggerFactory.getLogger(PassiveProfileRevisionListener.class);

  private final PassiveProfileService passiveProfileService;

  public PassiveProfileRevisionListener(PassiveProfileService passiveProfileService) {
    this.passiveProfileService = passiveProfileService;
  }

  @EventListener
  public void onPracticeSucceeded(LearningEvent.PracticeSucceeded event) {
    DimensionDelta delta =
        new DimensionDelta(
            0.02, 1, null, contextOf(event.kcCode(), event.problemId(), null), "PRACTICE_PASS");
    safeApply(event.userId(), "practical_ability", delta);
  }

  @EventListener
  public void onPracticeFailed(LearningEvent.PracticeFailed event) {
    DimensionDelta delta =
        new DimensionDelta(
            0.02,
            1,
            event.failureKind() == null ? null : List.of(event.failureKind()),
            contextOf(event.kcCode(), event.problemId(), event.failureKind()),
            "PRACTICE_FAIL");
    safeApply(event.userId(), "error_pattern", delta);
  }

  @EventListener
  public void onDebugResolved(LearningEvent.DebugResolved event) {
    DimensionDelta delta =
        new DimensionDelta(
            -0.02, null, null, contextOf(event.kcCode(), event.problemId(), null), "DEBUG_FIX");
    safeApply(event.userId(), "error_pattern", delta);
  }

  @EventListener
  public void onResourceConsumed(LearningEvent.ResourceConsumed event) {
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("kcCode", event.kcCode());
    meta.put("resourceKind", event.resourceKind());
    DimensionDelta delta =
        new DimensionDelta(0.005, 1, null, meta, "RESOURCE_FEEDBACK");
    safeApply(event.userId(), "knowledge_base", delta);
  }

  private void safeApply(Long userId, String dimensionKey, DimensionDelta delta) {
    try {
      passiveProfileService.applyDimensionUpdate(userId, dimensionKey, delta);
    } catch (DataAccessException dae) {
      log.warn(
          "PassiveProfileRevisionListener degrade due to DataAccessException userId={} dim={} source={} cause={}",
          userId,
          dimensionKey,
          delta.source(),
          dae.getMostSpecificCause() == null ? dae.getMessage() : dae.getMostSpecificCause().getMessage());
    } catch (RuntimeException ex) {
      log.warn(
          "PassiveProfileRevisionListener skip dimension update userId={} dim={} source={}: {}",
          userId,
          dimensionKey,
          delta.source(),
          ex.getMessage());
    }
  }

  private static Map<String, Object> contextOf(String kcCode, Long problemId, String failureKind) {
    LinkedHashMap<String, Object> m = new LinkedHashMap<>();
    if (kcCode != null) {
      m.put("kcCode", kcCode);
    }
    if (problemId != null) {
      m.put("problemId", problemId);
    }
    if (failureKind != null) {
      m.put("failureKind", failureKind);
    }
    return m;
  }
}
