package com.pythontopia.learner.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Shared trust-boundary checks for browser-submitted learning events. */
@Component
public class ClientLearningEventGuard {

  private static final int MAX_PAYLOAD_BYTES = 4 * 1024;

  private final ObjectMapper objectMapper;
  private final StudentWriteThrottle throttle;

  public ClientLearningEventGuard(ObjectMapper objectMapper, StudentWriteThrottle throttle) {
    this.objectMapper = objectMapper;
    this.throttle = throttle;
  }

  public void validate(LearningEventLogDto event) {
    validate(event == null ? null : event.eventType(), event == null ? null : event.payload());
  }

  public void validate(String eventType, Object payload) {
    if (!ClientLearningEventPolicy.allows(eventType)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_LEARNING_EVENT", "不允许客户端上报该事件");
    }
    if (payload == null) {
      return;
    }
    try {
      if (objectMapper.writeValueAsBytes(payload).length > MAX_PAYLOAD_BYTES) {
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

  public void requireCapacity(long userId, int eventCount) {
    if (!throttle.tryAcquire(userId, eventCount)) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "STUDENT_WRITE_RATE_LIMITED",
          "每个账号每分钟最多写入 100 条学习记录");
    }
  }
}
