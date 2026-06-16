package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.tutor.dto.TutorWorkflowAgentStateDto;
import com.pythontopia.tutor.dto.TutorWorkflowSessionDto;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TutorWorkflowSubmissionFeedbackListenerTest {

  private static final long USER = 7L;
  private static final long PROBLEM = 476L;

  private final TutorWorkflowRepository repository = mock(TutorWorkflowRepository.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final TutorWorkflowSubmissionFeedbackListener listener =
      new TutorWorkflowSubmissionFeedbackListener(repository, objectMapper);

  private final TutorWorkflowSessionDto session =
      new TutorWorkflowSessionDto(11L, "wf-7-476-abc", PROBLEM, "CODING", 5, "ACTIVE");

  @Test
  void failedSubmissionUpdatesActiveCoachState() throws Exception {
    when(repository.findActive(USER, PROBLEM)).thenReturn(Optional.of(session));
    when(repository.findAgentState(session.id()))
        .thenReturn(
            Optional.of(
                new TutorWorkflowAgentStateDto(
                    session.id(),
                    """
                    {"session_id":"wf-7-476-abc","user_id":7,"problem_id":476,
                     "phase":"CODING","attempt_count":2,"consecutive_errors":1,
                     "last_action":"EXECUTION_TRACE","last_action_effective":null}
                    """,
                    """
                    {"next_event":"EXECUTION_TRACE","tool_name":"execution_trace"}
                    """)));

    listener.onPracticeFailed(
        new LearningEvent.PracticeFailed(USER, "PY43-KC-016", PROBLEM, "WA", "basic"));

    ArgumentCaptor<String> stateJson = ArgumentCaptor.forClass(String.class);
    verify(repository).upsertAgentState(eq(session.id()), stateJson.capture(), any());
    JsonNode state = objectMapper.readTree(stateJson.getValue());
    assertThat(state.path("attempt_count").asInt()).isEqualTo(3);
    assertThat(state.path("latest_verdict").asText()).isEqualTo("WA");
    assertThat(state.path("consecutive_errors").asInt()).isEqualTo(2);
    assertThat(state.path("last_action_effective").asBoolean()).isFalse();
    verify(repository)
        .insertActionEvent(eq(session.id()), eq(5), eq("SUBMISSION_OBSERVED"), eq("WA"), any());
  }

  @Test
  void acceptedSubmissionMarksLastActionEffectiveAndClearsConsecutiveErrors() throws Exception {
    when(repository.findActive(USER, PROBLEM)).thenReturn(Optional.of(session));
    when(repository.findAgentState(session.id()))
        .thenReturn(
            Optional.of(
                new TutorWorkflowAgentStateDto(
                    session.id(),
                    """
                    {"session_id":"wf-7-476-abc","user_id":7,"problem_id":476,
                     "phase":"CODING","attempt_count":3,"consecutive_errors":2,
                     "latest_verdict":"WA","last_action":"SKELETON"}
                    """,
                    "{}")));

    listener.onPracticeSucceeded(
        new LearningEvent.PracticeSucceeded(USER, "PY43-KC-016", PROBLEM, "basic"));

    ArgumentCaptor<String> stateJson = ArgumentCaptor.forClass(String.class);
    verify(repository).upsertAgentState(eq(session.id()), stateJson.capture(), any());
    JsonNode state = objectMapper.readTree(stateJson.getValue());
    assertThat(state.path("attempt_count").asInt()).isEqualTo(4);
    assertThat(state.path("latest_verdict").asText()).isEqualTo("AC");
    assertThat(state.path("consecutive_errors").asInt()).isZero();
    assertThat(state.path("frustration_level").asText()).isEqualTo("low");
    assertThat(state.path("last_action_effective").asBoolean()).isTrue();
    verify(repository)
        .insertActionEvent(eq(session.id()), eq(5), eq("SUBMISSION_OBSERVED"), eq("AC"), any());
  }
}
