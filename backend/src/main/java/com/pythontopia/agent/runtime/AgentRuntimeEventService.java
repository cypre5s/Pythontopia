package com.pythontopia.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.event.EventImportance;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AgentRuntimeEventService {

  private static final int MAX_EVENT_TYPE_LEN = 64;
  private static final int MAX_SOURCE_LEN = 32;

  private final AgentRuntimeEventRepository repository;

  public AgentRuntimeEventService(AgentRuntimeEventRepository repository) {
    this.repository = repository;
  }

  public record EventRequest(
      @NotBlank @Size(max = MAX_EVENT_TYPE_LEN) String eventType,
      JsonNode payload,
      @Size(max = MAX_SOURCE_LEN) String source,
      @Min(1) @Max(5) Integer importance) {}

  public record EventRecord(
      Long id,
      Long userId,
      String eventType,
      JsonNode payload,
      String source,
      int importance,
      OffsetDateTime createdAt) {}

  public EventRecord recordEvent(Long authUserId, EventRequest request) {
    if (authUserId == null) {
      throw badRequest("AGENT_EVENT_USER_REQUIRED", "认证用户缺失");
    }
    if (request == null) {
      throw badRequest("AGENT_EVENT_BODY_REQUIRED", "事件请求体不能为空");
    }
    String eventType = requireText(request.eventType(), "eventType", MAX_EVENT_TYPE_LEN);
    String source = normalizeSource(request.source());
    int importance = normalizeImportance(request.importance(), eventType);
    return event(repository.insert(authUserId, eventType, request.payload(), source, importance));
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

  private String normalizeSource(String source) {
    if (source == null || source.isBlank()) {
      return "backend";
    }
    return requireText(source, "source", MAX_SOURCE_LEN);
  }

  private int normalizeImportance(Integer value, String eventType) {
    int resolved = value == null ? EventImportance.of(eventType) : value;
    if (resolved < 1 || resolved > 5) {
      throw badRequest("AGENT_EVENT_IMPORTANCE_INVALID", "importance 必须在 1..5 之间");
    }
    return resolved;
  }

  private String requireText(String value, String field, int maxLen) {
    if (value == null || value.isBlank()) {
      throw badRequest("AGENT_EVENT_FIELD_REQUIRED", field + " 不能为空");
    }
    String trimmed = value.strip();
    if (trimmed.length() > maxLen) {
      throw badRequest("AGENT_EVENT_FIELD_TOO_LONG", field + " 超过 " + maxLen + " 字符");
    }
    return trimmed;
  }

  private BusinessException badRequest(String code, String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
  }
}
