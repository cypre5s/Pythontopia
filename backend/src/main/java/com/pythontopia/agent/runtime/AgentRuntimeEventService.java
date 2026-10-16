package com.pythontopia.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.event.ClientLearningEventGuard;
import com.pythontopia.learner.event.EventImportance;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AgentRuntimeEventService {

  private final AgentRuntimeEventRepository repository;
  private final ClientLearningEventGuard eventGuard;

  public AgentRuntimeEventService(
      AgentRuntimeEventRepository repository, ClientLearningEventGuard eventGuard) {
    this.repository = repository;
    this.eventGuard = eventGuard;
  }

  public record EventRecord(
      Long id,
      Long userId,
      String eventType,
      JsonNode payload,
      String source,
      int importance,
      OffsetDateTime createdAt) {}

  public EventRecord recordEvent(Long authUserId, String eventType, JsonNode payload) {
    if (authUserId == null) {
      throw badRequest("AGENT_EVENT_USER_REQUIRED", "认证用户缺失");
    }
    eventGuard.validate(eventType, payload);
    eventGuard.requireCapacity(authUserId, 1);
    return event(
        repository.insert(
            authUserId, eventType, payload, "frontend", EventImportance.of(eventType)));
  }

  private EventRecord event(AgentRuntimeEventRepository.EventRow row) {
    return new EventRecord(
        row.id(),
        row.userId(),
        row.eventType(),
        row.payload(),
        row.source(),
        row.importance(),
        row.createdAt());
  }

  private BusinessException badRequest(String code, String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
  }
}
