package com.pythontopia.learner.event;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.learner.event.LearningEventLogRepository.EventInput;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/learning/events")
@Validated
public class LearningEventController {

  private final LearningEventLogRepository repository;
  private final ClientLearningEventGuard eventGuard;

  public LearningEventController(
      LearningEventLogRepository repository, ClientLearningEventGuard eventGuard) {
    this.repository = repository;
    this.eventGuard = eventGuard;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  void logEvent(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody LearningEventLogDto dto) {
    requireStudent(user);
    eventGuard.validate(dto);
    eventGuard.requireCapacity(user.userId(), 1);
    repository.insert(
        user.userId(),
        dto.eventType(),
        dto.payload() != null ? dto.payload() : Map.of(),
        "frontend",
        EventImportance.of(dto.eventType()));
  }

  @PostMapping("/batch")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public void logBatch(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody List<LearningEventLogDto> events) {
    requireStudent(user);
    if (events == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_LEARNING_EVENT_BATCH", "事件批次不能为空");
    }
    if (events.size() > 100) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "BATCH_TOO_LARGE", "Max 100 events per batch");
    }
    events.forEach(eventGuard::validate);
    eventGuard.requireCapacity(user.userId(), events.size());
    repository.insertBatch(
        events.stream()
            .map(
                dto ->
                    new EventInput(
                        user.userId(),
                        dto.eventType(),
                        dto.payload() != null ? dto.payload() : Map.of(),
                        "frontend",
                        EventImportance.of(dto.eventType())))
            .toList());
  }

  @GetMapping
  List<Map<String, Object>> getEvents(
      @CurrentUser CurrentUserPrincipal user, @RequestParam(defaultValue = "50") int limit) {
    return repository.findByUser(user.userId(), Math.max(1, Math.min(limit, 200)));
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Only students can log learning events");
    }
  }
}
