package com.pythontopia.learner.event;

import com.pythontopia.learner.memory.MemoryEmbeddingWriter;
import com.pythontopia.npc.NpcMemoryService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  private final MemoryEmbeddingWriter embeddingWriter;
  private final NpcMemoryService npcMemoryService;

  public MemoryStreamMirror(
      LearningEventLogRepository repository,
      MemoryEmbeddingWriter embeddingWriter,
      NpcMemoryService npcMemoryService) {
    this.repository = repository;
    this.embeddingWriter = embeddingWriter;
    this.npcMemoryService = npcMemoryService;
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
    put(payload, "resourceId", event.resourceId());
    put(payload, "kcCode", event.kcCode());
    put(payload, "resourceKind", event.resourceKind());
    mirror(event.userId(), "resource_consumed", payload);
  }

  @EventListener
  public void onResourceCheckCompleted(LearningEvent.ResourceCheckCompleted event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    put(payload, "checkId", event.checkId());
    put(payload, "resourceId", event.resourceId());
    put(payload, "kcCode", event.kcCode());
    put(payload, "evidenceType", event.evidenceType());
    put(payload, "delta", event.delta());
    put(payload, "remediationType", event.remediationType());
    mirror(
        event.userId(),
        event.passed() ? "resource_check_passed" : "resource_check_failed",
        payload);
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

  @EventListener
  public void onTaughtAiSucceeded(LearningEvent.TaughtAiSucceeded event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    put(payload, "kcCode", event.kcCode());
    put(payload, "sessionId", event.sessionId());
    mirror(event.userId(), "taught_ai_succeeded", payload);
  }

  @EventListener
  public void onTaughtAiFailed(LearningEvent.TaughtAiFailed event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    put(payload, "kcCode", event.kcCode());
    put(payload, "sessionId", event.sessionId());
    mirror(event.userId(), "taught_ai_failed", payload);
  }

  @EventListener
  public void onGuideActionClicked(LearningEvent.GuideActionClicked event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    put(payload, "kcCode", event.kcCode());
    put(payload, "problemId", event.problemId());
    put(payload, "actionKey", event.actionKey());
    mirror(event.userId(), "guide_action_clicked", payload);
  }

  @EventListener
  public void onGuideCheckpointCompleted(LearningEvent.GuideCheckpointCompleted event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    put(payload, "kcCode", event.kcCode());
    put(payload, "problemId", event.problemId());
    put(payload, "cardKey", event.cardKey());
    mirror(event.userId(), "guide_checkpoint_completed", payload);
  }

  private void mirror(Long userId, String eventType, Map<String, Object> payload) {
    try {
      long eventId = repository.insert(userId, eventType, payload, SOURCE, EventImportance.of(eventType));
      embeddingWriter.embedAndStore(eventId, eventType, payload);
      npcKeyFor(eventType).ifPresent(npcKey -> npcMemoryService.remember(userId, npcKey, eventId, 0.5));
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

  private static Optional<String> npcKeyFor(String eventType) {
    if (eventType == null) {
      return Optional.empty();
    }
    if (eventType.startsWith("practice_")
        || eventType.startsWith("guide_")
        || "comeback_solved".equals(eventType)) {
      return Optional.of("coach_q");
    }
    if (eventType.startsWith("taught_ai_")) {
      return Optional.of("student_a");
    }
    return switch (eventType) {
      case "resource_consumed", "media_consumed", "resource_check_passed", "resource_check_failed" ->
          Optional.of("librarian_doc");
      case "debug_resolved" -> Optional.of("foreman_bug");
      case "profile_bootstrapped" -> Optional.of("mayor_py");
      default -> Optional.empty();
    };
  }
}
