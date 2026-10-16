package com.pythontopia.agent.runtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.learner.event.ClientLearningEventGuard;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentRuntimeEventServiceTest {

  private AgentRuntimeEventRepository repository;
  private AgentRuntimeEventService service;

  @BeforeEach
  void setUp() {
    repository = mock(AgentRuntimeEventRepository.class);
    when(repository.insert(anyLong(), anyString(), any(), anyString(), anyInt()))
        .thenAnswer(
            invocation ->
                new AgentRuntimeEventRepository.EventRow(
                    701L,
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2),
                    invocation.getArgument(3),
                    invocation.getArgument(4),
                    OffsetDateTime.parse("2026-10-16T00:00:00Z")));
    service = new AgentRuntimeEventService(
        repository,
        new ClientLearningEventGuard(new ObjectMapper(), new StudentWriteThrottle()));
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

  @Test
  void oversizedClientPayloadIsRejectedBeforeWrite() {
    ObjectNode payload = new ObjectMapper().createObjectNode().put("note", "x".repeat(4097));

    assertThatThrownBy(() -> service.recordEvent(7L, "answer_feedback", payload))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("4096");

    verifyNoInteractions(repository);
  }

  @Test
  void clientCannotExceedSharedPerAccountWriteLimitThroughAgentRuntimeRoute() {
    ObjectNode payload = new ObjectMapper().createObjectNode().put("useful", true);

    for (int index = 0; index < 100; index++) {
      service.recordEvent(7L, "answer_feedback", payload);
    }

    assertThatThrownBy(() -> service.recordEvent(7L, "answer_feedback", payload))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("每分钟最多");

    verify(repository, times(100))
        .insert(eq(7L), eq("answer_feedback"), eq(payload), eq("frontend"), eq(1));
  }
}
