package com.pythontopia.learner.event;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/learning/events")
@Validated
public class LearningEventController {

  private final LearningEventLogRepository repository;

  public LearningEventController(LearningEventLogRepository repository) {
    this.repository = repository;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  void logEvent(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody LearningEventLogDto dto) {
    requireStudent(user);
    validateClientEvent(dto);
    repository.insert(
        user.userId(),
        dto.eventType(),
        dto.payload() != null ? dto.payload() : Map.of(),
        "frontend",
        EventImportance.of(dto.eventType()));
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
    events.forEach(this::validateClientEvent);
    for (LearningEventLogDto dto : events) {
      repository.insert(
          user.userId(),
          dto.eventType(),
          dto.payload() != null ? dto.payload() : Map.of(),
          "frontend",
          EventImportance.of(dto.eventType()));
    }
  }

  private void validateClientEvent(LearningEventLogDto dto) {
    if (dto == null || !ClientLearningEventPolicy.allows(dto.eventType())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_LEARNING_EVENT",
          "不允许客户端上报该事件");
    }
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
