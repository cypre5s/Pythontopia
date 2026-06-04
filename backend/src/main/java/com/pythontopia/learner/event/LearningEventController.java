package com.pythontopia.learner.event;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/learning/events")
@Validated
public class LearningEventController {

  private static final String MEDIA_CONSUMED = "media_consumed";

  private final LearningEventLogRepository repository;
  private final ApplicationEventPublisher eventPublisher;

  public LearningEventController(
      LearningEventLogRepository repository, ApplicationEventPublisher eventPublisher) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  void logEvent(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody LearningEventLogDto dto) {
    requireStudent(user);
    repository.insert(
        user.userId(),
        dto.eventType(),
        dto.payload() != null ? dto.payload() : Map.of(),
        "frontend");
    maybePublishDomainEvent(user.userId(), dto);
  }

  @PostMapping("/batch")
  @ResponseStatus(HttpStatus.CREATED)
  void logBatch(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody List<LearningEventLogDto> events) {
    requireStudent(user);
    if (events.size() > 100) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "BATCH_TOO_LARGE", "Max 100 events per batch");
    }
    for (LearningEventLogDto dto : events) {
      repository.insert(
          user.userId(),
          dto.eventType(),
          dto.payload() != null ? dto.payload() : Map.of(),
          "frontend");
      maybePublishDomainEvent(user.userId(), dto);
    }
  }

  /**
   * 把前端上报的「多模态消费」事件提升为进程内 {@link LearningEvent.MediaConsumed} 领域事件，
   * 供 {@code PassiveProfileRevisionListener} 回流到画像 {@code learning_preference} 维度。
   * 其余 eventType 仅审计落库（领域事件由对应后端服务在真实业务点发布）。
   */
  private void maybePublishDomainEvent(Long userId, LearningEventLogDto dto) {
    if (!MEDIA_CONSUMED.equals(dto.eventType())) {
      return;
    }
    Map<String, Object> payload = dto.payload() != null ? dto.payload() : Map.of();
    Object mediaTypeRaw = payload.get("mediaType");
    String mediaType = mediaTypeRaw == null ? "video" : String.valueOf(mediaTypeRaw);
    Integer chapter = null;
    Object chapterRaw = payload.get("chapter");
    if (chapterRaw instanceof Number number) {
      chapter = number.intValue();
    }
    List<String> kcCodes = new ArrayList<>();
    Object kcRaw = payload.get("kcCodes");
    if (kcRaw instanceof List<?> list) {
      for (Object item : list) {
        if (item != null) {
          kcCodes.add(String.valueOf(item));
        }
      }
    }
    eventPublisher.publishEvent(
        new LearningEvent.MediaConsumed(userId, mediaType, chapter, kcCodes));
  }

  @GetMapping
  List<Map<String, Object>> getEvents(
      @CurrentUser CurrentUserPrincipal user,
      @RequestParam(defaultValue = "50") int limit) {
    return repository.findByUser(user.userId(), Math.min(limit, 200));
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only students can log learning events");
    }
  }
}
