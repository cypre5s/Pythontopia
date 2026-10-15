package com.pythontopia.learner.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
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

  private static final int MAX_PAYLOAD_BYTES = 4 * 1024;

  private final LearningEventLogRepository repository;
  private final ObjectMapper objectMapper;
  private final StudentWriteThrottle throttle;

  public LearningEventController(
      LearningEventLogRepository repository,
      ObjectMapper objectMapper,
      StudentWriteThrottle throttle) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.throttle = throttle;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  void logEvent(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody LearningEventLogDto dto) {
    requireStudent(user);
    validateClientEvent(dto);
    requireCapacity(user.userId(), 1);
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
    if (events == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_LEARNING_EVENT_BATCH", "事件批次不能为空");
    }
    if (events.size() > 100) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "BATCH_TOO_LARGE", "Max 100 events per batch");
    }
    events.forEach(this::validateClientEvent);
    requireCapacity(user.userId(), events.size());
    for (LearningEventLogDto dto : events) {
      repository.insert(
          user.userId(),
          dto.eventType(),
          dto.payload() != null ? dto.payload() : Map.of(),
          "frontend",
          EventImportance.of(dto.eventType()));
    }
  }

  private void requireCapacity(long userId, int eventCount) {
    if (!throttle.tryAcquire(userId, eventCount)) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS, "STUDENT_WRITE_RATE_LIMITED", "每个账号每分钟最多写入 100 条学习记录");
    }
  }

  private void validateClientEvent(LearningEventLogDto dto) {
    if (dto == null || !ClientLearningEventPolicy.allows(dto.eventType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_LEARNING_EVENT", "不允许客户端上报该事件");
    }
    if (dto.payload() == null) {
      return;
    }
    try {
      if (objectMapper.writeValueAsBytes(dto.payload()).length > MAX_PAYLOAD_BYTES) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "LEARNING_EVENT_PAYLOAD_TOO_LARGE",
            "学习事件 payload 不能超过 4096 字节");
      }
    } catch (JsonProcessingException ex) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_LEARNING_EVENT_PAYLOAD", "学习事件 payload 无法序列化");
    }
  }

  @GetMapping
  List<Map<String, Object>> getEvents(
      @CurrentUser CurrentUserPrincipal user, @RequestParam(defaultValue = "50") int limit) {
    return repository.findByUser(user.userId(), Math.min(limit, 200));
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Only students can log learning events");
    }
  }
}
