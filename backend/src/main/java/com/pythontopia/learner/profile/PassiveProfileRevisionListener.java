package com.pythontopia.learner.profile;

import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.probe.ProbeCatalog;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * 监听 {@link LearningEvent}，把每类学习事件映射为一个画像维度的被动增量更新。
 *
 * <p>每个事件转成一次 {@link PassiveProfileService#applyDimensionUpdate} 调用，由 service 统一写
 * {@code learner_profile.dimensions} 并追加 {@code profile_revision} 审计；透传的 source 必须落在
 * profile_revision 允许的枚举内（PRACTICE_PASS / PRACTICE_FAIL / DEBUG_FIX / RESOURCE_FEEDBACK 等）。
 *
 * <p>降级：捕获 {@link DataAccessException}（如 {@code dimensions} 列尚未创建）时仅记录 warn 日志，
 * 不影响 mastery 评估等下游链路。
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
  public void onTaughtAiSucceeded(LearningEvent.TaughtAiSucceeded event) {
    Map<String, Object> meta = contextOf(event.kcCode(), null, null);
    meta.put("sessionId", event.sessionId());
    DimensionDelta delta = new DimensionDelta(0.03, 1, null, meta, "PRACTICE_PASS");
    safeApply(event.userId(), "practical_ability", delta);
  }

  @EventListener
  public void onTaughtAiFailed(LearningEvent.TaughtAiFailed event) {
    Map<String, Object> meta = contextOf(event.kcCode(), null, null);
    meta.put("sessionId", event.sessionId());
    DimensionDelta delta =
        new DimensionDelta(0.015, 1, List.of("teach_back_failed"), meta, "PRACTICE_FAIL");
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
    // 资源消费只说明学生看过材料；画像与掌握度必须等微测/操作/提交等可验证证据再变化。
  }

  @EventListener
  public void onResourceCheckCompleted(LearningEvent.ResourceCheckCompleted event) {
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("kcCode", event.kcCode());
    meta.put("checkId", event.checkId());
    meta.put("resourceId", event.resourceId());
    meta.put("evidenceType", event.evidenceType());
    meta.put("passed", event.passed());
    if (event.delta() != null) {
      meta.put("masteryDelta", event.delta());
    }
    if (event.remediationType() != null && !event.remediationType().isBlank()) {
      meta.put("remediationType", event.remediationType());
    }
    if (event.passed()) {
      String dimension =
          "OPERATION".equalsIgnoreCase(event.evidenceType())
              ? "practical_ability"
              : "knowledge_base";
      DimensionDelta delta = new DimensionDelta(0.015, 1, null, meta, "RESOURCE_FEEDBACK");
      safeApply(event.userId(), dimension, delta);
      return;
    }
    List<String> tags =
        event.remediationType() == null || event.remediationType().isBlank()
            ? List.of("resource_check_failed")
            : List.of(
                "resource_check_failed",
                "remediation_" + event.remediationType().toLowerCase(Locale.ROOT));
    DimensionDelta delta = new DimensionDelta(0.015, 1, tags, meta, "PRACTICE_FAIL");
    safeApply(event.userId(), "error_pattern", delta);
  }

  /**
   * 多模态消费（视频 / 动画 / 图解）→ learning_preference：mediaType 作为 tag 累积、score 微增，
   * 使推荐服务的媒介优先级能反映学生实际偏好的媒介。
   */
  @EventListener
  public void onMediaConsumed(LearningEvent.MediaConsumed event) {
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("mediaType", event.mediaType());
    if (event.chapter() != null) {
      meta.put("chapter", event.chapter());
    }
    String tag = event.mediaType() == null || event.mediaType().isBlank() ? "video" : event.mediaType();
    DimensionDelta delta = new DimensionDelta(0.01, 1, List.of(tag), meta, "RESOURCE_FEEDBACK");
    safeApply(event.userId(), "learning_preference", delta);
  }

  /**
   * 学生采纳某讲法（视觉/逻辑/试错）→ cognitive_style：style 作为 tag 累积、evidence+1、score 微增，
   * 多次累积才稳定；source 复用 RESOURCE_FEEDBACK 枚举。
   */
  @EventListener
  public void onTutorStyleEngaged(LearningEvent.TutorStyleEngaged event) {
    if (event.style() == null || event.style().isBlank()) {
      return;
    }
    Map<String, Object> meta = new LinkedHashMap<>();
    if (event.kcCode() != null) {
      meta.put("kcCode", event.kcCode());
    }
    DimensionDelta delta =
        new DimensionDelta(0.01, 1, List.of(event.style()), meta, "RESOURCE_FEEDBACK");
    safeApply(event.userId(), "cognitive_style", delta);
  }

  /**
   * 世界内探针选择 → 由服务端权威 {@link ProbeCatalog} 解析出维度/score/tag 后回写；
   * choiceId 不在目录内时跳过（防伪造信号）；source 复用 RESOURCE_FEEDBACK 枚举。
   */
  @EventListener
  public void onProbeChoiceMade(LearningEvent.ProbeChoiceMade event) {
    ProbeCatalog.lookup(event.probeKey(), event.choiceId())
        .ifPresent(
            signal -> {
              Map<String, Object> meta = new LinkedHashMap<>();
              meta.put("probeKey", event.probeKey());
              meta.put("choiceId", event.choiceId());
              DimensionDelta delta =
                  new DimensionDelta(
                      signal.scoreDelta(), 1, List.of(signal.tag()), meta, signal.source());
              safeApply(event.userId(), signal.dimensionKey(), delta);
            });
  }

  /** 失败后重试 → emotion_persistence 轻微正向（愿意重试=韧性）；tag=retry，evidence+1。 */
  @EventListener
  public void onPracticeRetried(LearningEvent.PracticeRetried event) {
    DimensionDelta delta =
        new DimensionDelta(
            0.02, 1, List.of("retry"), contextOf(event.kcCode(), event.problemId(), null), "PRACTICE_FAIL");
    safeApply(event.userId(), "emotion_persistence", delta);
  }

  /** 失败后放弃 → emotion_persistence 负向（tag=abandon，evidence+1）。 */
  @EventListener
  public void onPracticeAbandoned(LearningEvent.PracticeAbandoned event) {
    DimensionDelta delta =
        new DimensionDelta(
            -0.03,
            1,
            List.of("abandon"),
            contextOf(event.kcCode(), event.problemId(), null),
            "PRACTICE_FAIL");
    safeApply(event.userId(), "emotion_persistence", delta);
  }

  /** 逆袭（≥2 次失败后通过）→ emotion_persistence 较强正向（tag=comeback，evidence+1）。 */
  @EventListener
  public void onComebackSolved(LearningEvent.ComebackSolved event) {
    Map<String, Object> meta = contextOf(event.kcCode(), event.problemId(), null);
    meta.put("priorFails", event.priorFails());
    DimensionDelta delta = new DimensionDelta(0.04, 1, List.of("comeback"), meta, "PRACTICE_PASS");
    safeApply(event.userId(), "emotion_persistence", delta);
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
