package com.pythontopia.learner.profile;

import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.probe.ProbeCatalog;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 监听 {@link LearningEvent}，把每类学习事件映射为一个画像维度的被动增量更新。
 *
 * <p>这里仅把可验证学习事件转换为持久化投影任务。数据库更新由 outbox worker 在提交后完成，失败任务会 留在表中重试，避免进程内事件监听失败后静默丢失画像变化。
 */
@Component
public class PassiveProfileRevisionListener {

  private final ProfileDimensionProjectionOutboxService outboxService;

  public PassiveProfileRevisionListener(ProfileDimensionProjectionOutboxService outboxService) {
    this.outboxService = outboxService;
  }

  @EventListener
  public void onPracticeSucceeded(LearningEvent.PracticeSucceeded event) {
    DimensionDelta delta =
        new DimensionDelta(
            0.02, 1, null, contextOf(event.kcCode(), event.problemId(), null), "PRACTICE_PASS");
    enqueue(
        event.userId(),
        "practical_ability",
        delta,
        event.idempotencyKey() != null
            ? event.idempotencyKey()
            : event.submissionId() == null
                ? stableKey("practice_succeeded", event.userId(), event.problemId())
                : submissionKey(event.userId(), event.submissionId()));
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
    enqueue(
        event.userId(),
        "error_pattern",
        delta,
        event.idempotencyKey() != null
            ? event.idempotencyKey()
            : event.submissionId() == null
                ? stableKey(
                    "practice_failed",
                    event.userId(),
                    event.problemId(),
                    event.failureKind() == null ? "unspecified" : event.failureKind())
                : submissionKey(event.userId(), event.submissionId()));
  }

  @EventListener
  public void onTaughtAiSucceeded(LearningEvent.TaughtAiSucceeded event) {
    Map<String, Object> meta = contextOf(event.kcCode(), null, null);
    meta.put("sessionId", event.sessionId());
    DimensionDelta delta = new DimensionDelta(0.03, 1, null, meta, "PRACTICE_PASS");
    enqueue(
        event.userId(),
        "practical_ability",
        delta,
        stableKey("taught_ai_succeeded", event.userId(), event.sessionId()));
  }

  @EventListener
  public void onTaughtAiFailed(LearningEvent.TaughtAiFailed event) {
    Map<String, Object> meta = contextOf(event.kcCode(), null, null);
    meta.put("sessionId", event.sessionId());
    DimensionDelta delta =
        new DimensionDelta(0.015, 1, List.of("teach_back_failed"), meta, "PRACTICE_FAIL");
    enqueue(
        event.userId(),
        "error_pattern",
        delta,
        stableKey("taught_ai_failed", event.userId(), event.sessionId()));
  }

  @EventListener
  public void onDebugResolved(LearningEvent.DebugResolved event) {
    DimensionDelta delta =
        new DimensionDelta(
            -0.02, null, null, contextOf(event.kcCode(), event.problemId(), null), "DEBUG_FIX");
    enqueue(
        event.userId(),
        "error_pattern",
        delta,
        stableKey("debug_resolved", event.userId(), event.problemId()));
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
      enqueue(event.userId(), dimension, delta, resourceCheckKey(event));
      return;
    }
    List<String> tags =
        event.remediationType() == null || event.remediationType().isBlank()
            ? List.of("resource_check_failed")
            : List.of(
                "resource_check_failed",
                "remediation_" + event.remediationType().toLowerCase(Locale.ROOT));
    DimensionDelta delta = new DimensionDelta(0.015, 1, tags, meta, "PRACTICE_FAIL");
    enqueue(event.userId(), "error_pattern", delta, resourceCheckKey(event));
  }

  private static String resourceCheckKey(LearningEvent.ResourceCheckCompleted event) {
    return event.submissionId() != null && "OPERATION".equalsIgnoreCase(event.evidenceType())
        ? submissionKey(event.userId(), event.submissionId())
        : stableKey("resource_check", event.userId(), event.checkId());
  }

  private static String submissionKey(Long userId, Long submissionId) {
    return stableKey("submission", userId, submissionId);
  }

  /**
   * 多模态消费（视频 / 动画 / 图解）→ learning_preference：mediaType 作为 tag 累积、score 微增， 使推荐服务的媒介优先级能反映学生实际偏好的媒介。
   */
  @EventListener
  public void onMediaConsumed(LearningEvent.MediaConsumed event) {
    String tag =
        switch (event.mediaType() == null
            ? ""
            : event.mediaType().trim().toLowerCase(Locale.ROOT)) {
          case "video" -> "video";
          case "courseware" -> "text";
          case "storyboard", "illustration" -> "mindmap";
          default -> null;
        };
    if (tag == null) return;
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("mediaType", event.mediaType());
    if (event.chapter() != null) {
      meta.put("chapter", event.chapter());
    }
    DimensionDelta delta = new DimensionDelta(0.01, 1, List.of(tag), meta, "RESOURCE_FEEDBACK");
    enqueue(
        event.userId(),
        "learning_preference",
        delta,
        transientKey("media_consumed", event.userId()));
  }

  /** 学生明确选择讲解方式 → 记为媒介偏好，不作为认知能力证据。 */
  @EventListener
  public void onTutorStyleEngaged(LearningEvent.TutorStyleEngaged event) {
    if (event.style() == null || event.style().isBlank()) {
      return;
    }
    String mediaType =
        switch (event.style().trim().toLowerCase(Locale.ROOT)) {
          case "visual" -> "video";
          case "logical" -> "text";
          case "trial" -> "code";
          default -> null;
        };
    if (mediaType == null) return;
    Map<String, Object> meta = new LinkedHashMap<>();
    if (event.kcCode() != null) {
      meta.put("kcCode", event.kcCode());
    }
    meta.put("mediaType", mediaType);
    DimensionDelta delta = new DimensionDelta(null, null, List.of(mediaType), meta, "INTERVIEW");
    enqueue(
        event.userId(),
        "learning_preference",
        delta,
        transientKey("tutor_style_engaged", event.userId()));
  }

  /**
   * 世界内探针选择 → 由服务端权威 {@link ProbeCatalog} 解析出维度/score/tag 后回写； choiceId 不在目录内时跳过（防伪造信号）；source 复用
   * RESOURCE_FEEDBACK 枚举。
   */
  @EventListener
  public void onProbeChoiceMade(LearningEvent.ProbeChoiceMade event) {
    ProbeCatalog.lookup(event.probeKey(), event.choiceId())
        .ifPresent(
            signal -> {
              Map<String, Object> meta = new LinkedHashMap<>();
              meta.put("probeKey", event.probeKey());
              meta.put("choiceId", event.choiceId());
              boolean selfReported = "INTERVIEW".equals(signal.source());
              DimensionDelta delta =
                  new DimensionDelta(
                      selfReported ? null : signal.scoreDelta(),
                      selfReported ? null : 1,
                      List.of(signal.tag()),
                      meta,
                      signal.source());
              enqueue(
                  event.userId(),
                  signal.dimensionKey(),
                  delta,
                  transientKey("probe_choice", event.userId()));
            });
  }

  private void enqueue(
      Long userId, String dimensionKey, DimensionDelta delta, String idempotencyKey) {
    if (userId == null) {
      return;
    }
    outboxService.enqueue(
        new ProfileDimensionProjection(userId, dimensionKey, delta, idempotencyKey));
  }

  private static String stableKey(String type, Long userId, Object... identifiers) {
    if (userId == null || identifiers.length == 0) {
      return transientKey(type, userId);
    }
    for (Object identifier : identifiers) {
      if (identifier == null) {
        return transientKey(type, userId);
      }
    }
    return type
        + ":"
        + userId
        + ":"
        + String.join(":", java.util.Arrays.stream(identifiers).map(String::valueOf).toList());
  }

  private static String transientKey(String type, Long userId) {
    return type + ":" + userId + ":" + UUID.randomUUID();
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
