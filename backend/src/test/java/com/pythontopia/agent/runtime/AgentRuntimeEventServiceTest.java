package com.pythontopia.agent.runtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.common.error.BusinessException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentRuntimeEventServiceTest {

  private AgentRuntimeEventRepository repository;
  private AgentRuntimeEventService service;

  @BeforeEach
  void setUp() {
    repository = mock(AgentRuntimeEventRepository.class);
    service = new AgentRuntimeEventService(repository);
  }

  @Test
  void clientCannotSetAuthoritativeSourceOrImportance() {
    ObjectNode payload = new ObjectMapper().createObjectNode().put("feedback", "helpful");
    when(repository.insert(7L, "answer_feedback", payload, "frontend", 1))
        .thenReturn(
            new AgentRuntimeEventRepository.EventRow(
                701L,
                7L,
                "answer_feedback",
                payload,
                "frontend",
                1,
                OffsetDateTime.parse("2026-10-14T00:00:00Z")));

    service.recordEvent(7L, "answer_feedback", payload);

    verify(repository).insert(7L, "answer_feedback", payload, "frontend", 1);
  }

  @Test
  void clientCannotReportAuthoritativePracticeEventsDirectly() {
    assertThatThrownBy(
            () -> service.recordEvent(7L, "practice_failed", new ObjectMapper().createObjectNode()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不允许客户端上报该事件");
  }

  @Test
  void missingEventTypeIsRejectedAsInvalidInput() {
    assertThatThrownBy(
            () -> service.recordEvent(7L, null, new ObjectMapper().createObjectNode()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不允许客户端上报该事件");
  }
}
