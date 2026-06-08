package com.pythontopia.learner.event;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 把进程内 {@link LearningEvent}（由 {@code ApplicationEventPublisher} 发布）镜像写入
 * {@code learning_event_log} 表，与前端上报通道统一成单一「记忆流」底座，供反思 / 检索
 * （Grounded Generative Agents）消费。
 *
 * <p>每个事件转成规范 snake_case {@code event_type} + payload Map + {@link EventImportance} 规则重要度，
 * 调 {@link LearningEventLogRepository#insert} 落库（{@code source=backend}）。
 *
 * <p>记忆镜像是旁路：任何写表异常都被吞掉（仅 warn 日志），绝不影响事件主链路
 * （mastery 评估、{@code PassiveProfileRevisionListener} 画像回写）。这与
 * {@link com.pythontopia.learner.profile.PassiveProfileRevisionListener} 的降级语义一致。
 */
@Component
public class MemoryStreamMirror {

  /** 镜像来源标识，对齐 learning_event_log.source 枚举说明（frontend / backend / graph）。 */
  static final String SOURCE = "backend";

  private static final Logger log = LoggerFactory.getLogger(MemoryStreamMirror.class);

  private final LearningEventLogRepository repository;

  public MemoryStreamMirror(LearningEventLogRepository repository) {
    this.repository = repository;
  }

  @EventListener
  public void onProfileBootstrapped(LearningEvent.ProfileBootstrapped event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("unlockedKcs", event.unlockedKcs() == null ? List.of() : event.unlockedKcs());
    mirror(event.userId(), "profile_bootstrapped", payload);
  }

  @EventListener
  public void onResourceConsumed(LearningEvent.ResourceConsumed event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    put(payload, "kcCode", event.kcCode());
    put(payload, "resourceKind", event.resourceKind());
    mirror(event.userId(), "resource_consumed", payload);
  }

  @EventListener
  public void onPracticeSucceeded(LearningEvent.PracticeSucceeded event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    put(payload, "kcCode", event.kcCode());
    put(payload, "problemId", event.problemId());
    mirror(event.userId(), "practice_succeeded", payload);
  }

  @EventListener
  public void onPracticeFailed(LearningEvent.PracticeFailed event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    put(payload, "kcCode", event.kcCode());
    put(payload, "problemId", event.problemId());
    put(payload, "failureKind", event.failureKind());
    mirror(event.userId(), "practice_failed", payload);
  }

  @EventListener
  public void onDebugResolved(LearningEvent.DebugResolved event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    put(payload, "kcCode", event.kcCode());
    put(payload, "problemId", event.problemId());
    mirror(event.userId(), "debug_resolved", payload);
  }

  @EventListener
  public void onMediaConsumed(LearningEvent.MediaConsumed event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    put(payload, "mediaType", event.mediaType());
    put(payload, "chapter", event.chapter());
    put(payload, "kcCodes", event.kcCodes());
    mirror(event.userId(), "media_consumed", payload);
  }

  @EventListener
  public void onTutorStyleEngaged(LearningEvent.TutorStyleEngaged event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    put(payload, "style", event.style());
    put(payload, "kcCode", event.kcCode());
    mirror(event.userId(), "tutor_style_engaged", payload);
  }

  @EventListener
  public void onProbeChoiceMade(LearningEvent.ProbeChoiceMade event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    put(payload, "probeKey", event.probeKey());
    put(payload, "choiceId", event.choiceId());
    mirror(event.userId(), "probe_choice_made", payload);
  }

  @EventListener
  public void onPracticeRetried(LearningEvent.PracticeRetried event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    put(payload, "kcCode", event.kcCode());
    put(payload, "problemId", event.problemId());
    mirror(event.userId(), "practice_retried", payload);
  }

  @EventListener
  public void onPracticeAbandoned(LearningEvent.PracticeAbandoned event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    put(payload, "kcCode", event.kcCode());
    put(payload, "problemId", event.problemId());
    mirror(event.userId(), "practice_abandoned", payload);
  }

  @EventListener
  public void onComebackSolved(LearningEvent.ComebackSolved event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    put(payload, "kcCode", event.kcCode());
    put(payload, "problemId", event.problemId());
    payload.put("priorFails", event.priorFails());
    mirror(event.userId(), "comeback_solved", payload);
  }

  private void mirror(Long userId, String eventType, Map<String, Object> payload) {
    try {
      repository.insert(userId, eventType, payload, SOURCE, EventImportance.of(eventType));
    } catch (RuntimeException ex) {
      log.warn(
          "MemoryStreamMirror skip event userId={} type={}: {}",
          userId,
          eventType,
          ex.getMessage());
    }
  }

  private static void put(Map<String, Object> payload, String key, Object value) {
    if (value != null) {
      payload.put(key, value);
    }
  }
}
