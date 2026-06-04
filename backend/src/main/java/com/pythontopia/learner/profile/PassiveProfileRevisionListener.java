package com.pythontopia.learner.profile;

import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.probe.ProbeCatalog;
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
 * <p>已接入数据源：练习成败、Debug 修复、资源消费（knowledge_base）、多模态消费（learning_preference）、
 * 讲法采纳（cognitive_style，用①）、世界内探针（cognitive_style/emotion/learning_preference，玩中建画像③）、
 * 练习重试/放弃/逆袭（emotion_persistence，情绪化小镇②）。
 *
 * <p>TODO(M4-后续)：课件页阅读完成 → knowledge_base（应在 PageReadService / Library 模块接入）。
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

  /**
   * 多模态消费（章节视频 / AI 动画 / 知识点图解）→ learning_preference（媒介偏好）。把 mediaType 作为
   * tag 累积，配合 score 微增，使推荐服务的媒介优先级能反映「学生确实在看视觉/视频内容」。
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
   * 答疑/讲解中学生采纳某讲法（视觉/逻辑/试错）→ cognitive_style 回测（闭环，补「认知风格死维」缺口）。
   * style 作为 tag 累积 + evidence+1 + 微小 score；多次累积才稳；source 复用 RESOURCE_FEEDBACK 枚举。
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
   * 世界内探针选择（玩中建画像③）→ 由服务端权威 {@link ProbeCatalog} 解析出维度/score/tag 后回写。
   * choiceId 不在目录内时跳过（防伪造信号）；source 复用 RESOURCE_FEEDBACK 枚举，evidence+1，tag 累积。
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

  /**
   * 失败后重试（情绪化小镇②）→ emotion_persistence 轻微正向（愿意重试=韧性）。tag=retry，evidence+1。
   */
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
