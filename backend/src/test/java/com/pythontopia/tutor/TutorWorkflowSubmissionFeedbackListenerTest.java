package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.tutor.dto.TutorWorkflowAgentStateDto;
import com.pythontopia.tutor.dto.TutorWorkflowSessionDto;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

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

    TutorWorkflowRepository.SubmissionFeedbackProjection projection = capturedProjection();
    JsonNode state = objectMapper.readTree(projection.stateJson());
    JsonNode studentState = state.path("student_state");
    assertThat(studentState.path("attempt_count").asInt()).isEqualTo(3);
    assertThat(studentState.path("latest_verdict").asText()).isEqualTo("WA");
    assertThat(studentState.path("consecutive_errors").asInt()).isEqualTo(2);
    assertThat(studentState.path("last_action_effective").asBoolean()).isFalse();
    assertThat(projection.seq()).isEqualTo(5);
    assertThat(projection.verdict()).isEqualTo("WA");
    assertThat(projection.phase()).isEqualTo("ERROR_FEEDBACK");
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

    TutorWorkflowRepository.SubmissionFeedbackProjection projection = capturedProjection();
    JsonNode state = objectMapper.readTree(projection.stateJson());
    JsonNode studentState = state.path("student_state");
    assertThat(studentState.path("attempt_count").asInt()).isEqualTo(4);
    assertThat(studentState.path("latest_verdict").asText()).isEqualTo("AC");
    assertThat(studentState.path("consecutive_errors").asInt()).isZero();
    assertThat(studentState.path("frustration_level").asText()).isEqualTo("low");
    assertThat(studentState.path("last_action_effective").asBoolean()).isTrue();
    assertThat(projection.seq()).isEqualTo(5);
    assertThat(projection.verdict()).isEqualTo("AC");
    assertThat(projection.phase()).isEqualTo("AC_REVIEW");
  }

  @Test
  void persistsSubmissionIdentityForTutorRecovery() throws Exception {
    when(repository.findActive(USER, PROBLEM)).thenReturn(Optional.of(session));
    when(repository.findAgentState(session.id()))
        .thenReturn(Optional.of(new TutorWorkflowAgentStateDto(session.id(), "{}", "{}")));

    listener.onPracticeSucceeded(
        new LearningEvent.PracticeSucceeded(USER, "PY43-KC-016", PROBLEM, "basic", 1234L));

    TutorWorkflowRepository.SubmissionFeedbackProjection projection = capturedProjection();
    JsonNode state = objectMapper.readTree(projection.stateJson()).path("student_state");
    assertThat(state.path("latest_submission_id").asLong()).isEqualTo(1234L);
    assertThat(state.path("latest_submission_problem_id").asLong()).isEqualTo(PROBLEM);
    assertThat(state.path("latest_verdict").asText()).isEqualTo("AC");
    assertThat(projection.actionPayloadJson()).contains("\"submission_id\":1234");
  }

  @Test
  void submissionAdvancesOnlyMatchingActiveStepThatRequiresSubmission() throws Exception {
    when(repository.findActive(USER, PROBLEM)).thenReturn(Optional.of(session));
    when(repository.findAgentState(session.id()))
        .thenReturn(
            Optional.of(
                new TutorWorkflowAgentStateDto(
                    session.id(),
                    """
                    {
                      "student_state":{"session_id":"wf-7-476-abc","user_id":7,"problem_id":476,
                        "phase":"CODING","attempt_count":0,"consecutive_errors":0},
                      "episode_plan":{"plan_id":"plan-1","session_id":"wf-7-476-abc",
                        "problem_id":476,"user_id":7,"objective":"finish","trigger_event":"ERROR_FEEDBACK",
                        "revision":1,"current_step_id":"step-1","status":"ACTIVE",
                        "steps":[
                          {"step_id":"step-1","event":"CODING","title":"提交当前程序",
                           "status":"ACTIVE","expected_evidence":"SUBMISSION_OBSERVED"},
                          {"step_id":"step-2","event":"TRANSFER","title":"尝试迁移题",
                           "status":"PENDING","depends_on":["step-1"],
                           "expected_evidence":"TRANSFER_ATTEMPT"}
                        ]},
                      "chat_history":[{"role":"user","content":"旧问题"}]
                    }
                    """,
                    "{}")));

    listener.onPracticeFailed(
        new LearningEvent.PracticeFailed(USER, "PY43-KC-016", PROBLEM, "WA", "basic"));

    TutorWorkflowRepository.SubmissionFeedbackProjection projection = capturedProjection();
    JsonNode state = objectMapper.readTree(projection.stateJson());
    assertThat(state.path("student_state").path("attempt_count").asInt()).isEqualTo(1);
    assertThat(state.path("episode_plan").path("current_step_id").asText()).isEqualTo("step-2");
    assertThat(state.path("episode_plan").path("revision").asInt()).isEqualTo(2);
    assertThat(state.path("episode_plan").path("steps").get(0).path("status").asText())
        .isEqualTo("DONE");
    assertThat(state.path("episode_plan").path("steps").get(1).path("status").asText())
        .isEqualTo("ACTIVE");
    assertThat(state.path("chat_history").get(0).path("content").asText()).isEqualTo("旧问题");
  }

  @Test
  void submissionDoesNotAdvanceCardEvidenceStep() throws Exception {
    when(repository.findActive(USER, PROBLEM)).thenReturn(Optional.of(session));
    when(repository.findAgentState(session.id()))
        .thenReturn(
            Optional.of(
                new TutorWorkflowAgentStateDto(
                    session.id(),
                    """
                    {"student_state":{"attempt_count":0},
                     "episode_plan":{"plan_id":"plan-1","session_id":"wf-7-476-abc",
                       "problem_id":476,"user_id":7,"objective":"finish","trigger_event":"ERROR_FEEDBACK",
                       "revision":1,"current_step_id":"step-1","status":"ACTIVE",
                       "steps":[{"step_id":"step-1","event":"CODING","title":"先看讲解",
                         "status":"ACTIVE","expected_evidence":"CARD_VIEWED"},
                         {"step_id":"step-2","event":"AC_REVIEW","title":"通过后复盘",
                          "status":"PENDING","depends_on":["step-1"],
                          "expected_evidence":"AC_SUBMISSION"}]},
                     "chat_history":[]}
                    """,
                    "{}")));

    listener.onPracticeSucceeded(
        new LearningEvent.PracticeSucceeded(USER, "PY43-KC-016", PROBLEM, "basic"));

    JsonNode plan = objectMapper.readTree(capturedProjection().stateJson()).path("episode_plan");
    assertThat(plan.path("current_step_id").asText()).isEqualTo("step-1");
    assertThat(plan.path("revision").asInt()).isEqualTo(1);
    assertThat(plan.path("steps").get(0).path("status").asText()).isEqualTo("ACTIVE");
  }

  @Test
  void ordinarySubmissionDoesNotCountAsATutorTransferAttempt() throws Exception {
    when(repository.findActive(USER, PROBLEM)).thenReturn(Optional.of(session));
    when(repository.findAgentState(session.id()))
        .thenReturn(
            Optional.of(
                new TutorWorkflowAgentStateDto(
                    session.id(),
                    """
                    {"student_state":{"attempt_count":0},
                     "episode_plan":{"plan_id":"plan-1","session_id":"wf-7-476-abc",
                       "problem_id":476,"user_id":7,"objective":"finish","trigger_event":"AC_REVIEW",
                       "revision":1,"current_step_id":"step-1","status":"ACTIVE",
                       "steps":[{"step_id":"step-1","event":"TRANSFER","title":"尝试迁移题",
                         "status":"ACTIVE","expected_evidence":"TRANSFER_ATTEMPT"}]},
                     "chat_history":[]}
                    """,
                    "{}")));

    listener.onPracticeSucceeded(
        new LearningEvent.PracticeSucceeded(USER, "PY43-KC-016", PROBLEM, "basic"));

    JsonNode plan = objectMapper.readTree(capturedProjection().stateJson()).path("episode_plan");
    assertThat(plan.path("current_step_id").asText()).isEqualTo("step-1");
    assertThat(plan.path("revision").asInt()).isEqualTo(1);
    assertThat(plan.path("steps").get(0).path("status").asText()).isEqualTo("ACTIVE");
  }

  @Test
  void formallyJudgedSubmissionForBoundTransferProblemAdvancesOnlyOneSession() throws Exception {
    long transferProblemId = 500L;
    TutorWorkflowSessionDto transferSession =
        new TutorWorkflowSessionDto(12L, "wf-7-476-transfer", PROBLEM, "TRANSFER", 8, "ACTIVE");
    TutorWorkflowSessionDto otherTransferSession =
        new TutorWorkflowSessionDto(13L, "wf-7-477-transfer", 477L, "TRANSFER", 8, "ACTIVE");
    when(repository.findActiveTransferSession(
            USER, transferSession.sessionKey(), transferProblemId))
        .thenReturn(Optional.of(transferSession));
    when(repository.findAgentState(transferSession.id()))
        .thenReturn(
            Optional.of(
                new TutorWorkflowAgentStateDto(
                    transferSession.id(),
                    """
                    {"student_state":{"session_id":"wf-7-476-transfer","user_id":7,
                      "problem_id":476,"attempt_count":0,"consecutive_errors":0},
                     "episode_plan":{"plan_id":"plan-1","session_id":"wf-7-476-transfer",
                       "problem_id":476,"user_id":7,"objective":"finish","trigger_event":"AC_REVIEW",
                       "revision":1,"current_step_id":"step-transfer","status":"ACTIVE",
                       "steps":[{"step_id":"step-transfer","event":"TRANSFER","title":"正式提交迁移题",
                         "status":"ACTIVE","expected_evidence":"TRANSFER_ATTEMPT"}]},
                     "chat_history":[]}
                    """,
                    "{}")));
    listener.onPracticeFailed(
        new LearningEvent.PracticeFailed(
            USER,
            "PY43-KC-016",
            transferProblemId,
            "WA",
            "basic",
            800L,
            null,
            transferSession.sessionKey()));

    ArgumentCaptor<TutorWorkflowRepository.SubmissionFeedbackProjection> projection =
        ArgumentCaptor.forClass(TutorWorkflowRepository.SubmissionFeedbackProjection.class);
    verify(repository, times(1))
        .persistSubmissionFeedback(eq(transferSession.id()), projection.capture());
    verify(repository, org.mockito.Mockito.never())
        .persistSubmissionFeedback(eq(otherTransferSession.id()), any());
    JsonNode state = objectMapper.readTree(projection.getValue().stateJson());
    assertThat(state.path("episode_plan").path("status").asText()).isEqualTo("COMPLETED");
    assertThat(state.path("episode_plan").path("steps").get(0).path("status").asText())
        .isEqualTo("DONE");
  }

  @Test
  void duplicateTransferSubmissionDoesNotAdvanceTheCodingStep() throws Exception {
    long transferProblemId = 500L;
    TutorWorkflowSessionDto transferSession =
        new TutorWorkflowSessionDto(12L, "wf-7-476-transfer", PROBLEM, "TRANSFER", 8, "ACTIVE");
    when(repository.findActiveTransferSession(
            USER, transferSession.sessionKey(), transferProblemId))
        .thenReturn(Optional.of(transferSession));
    when(repository.findAgentState(transferSession.id()))
        .thenReturn(
            Optional.of(
                new TutorWorkflowAgentStateDto(
                    transferSession.id(),
                    """
                    {"student_state":{"session_id":"wf-7-476-transfer","user_id":7,
                      "problem_id":476,"attempt_count":1,"consecutive_errors":0},
                     "episode_plan":{"plan_id":"plan-1","session_id":"wf-7-476-transfer",
                       "problem_id":476,"user_id":7,"objective":"finish","trigger_event":"AC_REVIEW",
                       "revision":2,"current_step_id":"step-coding","status":"ACTIVE",
                       "steps":[
                         {"step_id":"step-transfer","event":"TRANSFER","title":"迁移题",
                          "status":"DONE","expected_evidence":"TRANSFER_ATTEMPT"},
                         {"step_id":"step-coding","event":"CODING","title":"完成原题",
                          "status":"ACTIVE","expected_evidence":"SUBMISSION_OBSERVED"},
                         {"step_id":"step-review","event":"AC_REVIEW","title":"复盘",
                          "status":"PENDING","depends_on":["step-coding"],
                          "expected_evidence":"AC_SUBMISSION"}]},
                     "chat_history":[]}
                    """,
                    "{}")));

    listener.onPracticeSucceeded(
        new LearningEvent.PracticeSucceeded(
            USER,
            "PY43-KC-016",
            transferProblemId,
            "basic",
            801L,
            null,
            transferSession.sessionKey()));

    ArgumentCaptor<TutorWorkflowRepository.SubmissionFeedbackProjection> projection =
        ArgumentCaptor.forClass(TutorWorkflowRepository.SubmissionFeedbackProjection.class);
    verify(repository).persistSubmissionFeedback(eq(transferSession.id()), projection.capture());
    JsonNode plan = objectMapper.readTree(projection.getValue().stateJson()).path("episode_plan");
    assertThat(plan.path("current_step_id").asText()).isEqualTo("step-coding");
    assertThat(plan.path("revision").asInt()).isEqualTo(2);
    assertThat(plan.path("steps").get(1).path("status").asText()).isEqualTo("ACTIVE");
    assertThat(plan.path("steps").get(2).path("status").asText()).isEqualTo("PENDING");
  }

  @Test
  void transferSubmissionDoesNotAdvanceTheReviewStep() throws Exception {
    long transferProblemId = 500L;
    TutorWorkflowSessionDto transferSession =
        new TutorWorkflowSessionDto(12L, "wf-7-476-transfer", PROBLEM, "TRANSFER", 8, "ACTIVE");
    when(repository.findActiveTransferSession(
            USER, transferSession.sessionKey(), transferProblemId))
        .thenReturn(Optional.of(transferSession));
    when(repository.findAgentState(transferSession.id()))
        .thenReturn(
            Optional.of(
                new TutorWorkflowAgentStateDto(
                    transferSession.id(),
                    """
                    {"student_state":{"session_id":"wf-7-476-transfer","user_id":7,
                      "problem_id":476,"attempt_count":2,"consecutive_errors":0},
                     "episode_plan":{"plan_id":"plan-1","session_id":"wf-7-476-transfer",
                       "problem_id":476,"user_id":7,"objective":"finish","trigger_event":"AC_REVIEW",
                       "revision":3,"current_step_id":"step-review","status":"ACTIVE",
                       "steps":[
                         {"step_id":"step-transfer","event":"TRANSFER","title":"迁移题",
                          "status":"DONE","expected_evidence":"TRANSFER_ATTEMPT"},
                         {"step_id":"step-coding","event":"CODING","title":"完成原题",
                          "status":"DONE","expected_evidence":"SUBMISSION_OBSERVED"},
                         {"step_id":"step-review","event":"AC_REVIEW","title":"复盘",
                          "status":"ACTIVE","expected_evidence":"AC_SUBMISSION"}]},
                     "chat_history":[]}
                    """,
                    "{}")));

    listener.onPracticeSucceeded(
        new LearningEvent.PracticeSucceeded(
            USER,
            "PY43-KC-016",
            transferProblemId,
            "basic",
            802L,
            null,
            transferSession.sessionKey()));

    ArgumentCaptor<TutorWorkflowRepository.SubmissionFeedbackProjection> projection =
        ArgumentCaptor.forClass(TutorWorkflowRepository.SubmissionFeedbackProjection.class);
    verify(repository).persistSubmissionFeedback(eq(transferSession.id()), projection.capture());
    JsonNode plan = objectMapper.readTree(projection.getValue().stateJson()).path("episode_plan");
    assertThat(plan.path("current_step_id").asText()).isEqualTo("step-review");
    assertThat(plan.path("revision").asInt()).isEqualTo(3);
    assertThat(plan.path("steps").get(2).path("status").asText()).isEqualTo("ACTIVE");
  }

  @Test
  void malformedStoredStateDoesNotLogStudentContent() {
    String sentinel = "private-tutor-feedback-log-sentinel";
    when(repository.findActive(USER, PROBLEM)).thenReturn(Optional.of(session));
    when(repository.findAgentState(session.id()))
        .thenReturn(
            Optional.of(
                new TutorWorkflowAgentStateDto(
                    session.id(), "{\"student_text\":\"" + sentinel, "{}")));
    Logger logger = (Logger) LoggerFactory.getLogger(TutorWorkflowSubmissionFeedbackListener.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      listener.onPracticeFailed(
          new LearningEvent.PracticeFailed(USER, "PY43-KC-016", PROBLEM, "WA", "basic"));
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("errorType=JsonEOFException"));
    assertThat(appender.list)
        .extracting(ILoggingEvent::getThrowableProxy)
        .allSatisfy(throwable -> assertThat(throwable).isNull());
  }

  @Test
  void repositoryFailureDoesNotLogExceptionBody() {
    String sentinel = "private-tutor-feedback-db-log-sentinel";
    when(repository.findActive(USER, PROBLEM)).thenThrow(new IllegalStateException(sentinel));
    Logger logger = (Logger) LoggerFactory.getLogger(TutorWorkflowSubmissionFeedbackListener.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      listener.onPracticeFailed(
          new LearningEvent.PracticeFailed(USER, "PY43-KC-016", PROBLEM, "WA", "basic"));
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("errorType=IllegalStateException"));
    assertThat(appender.list)
        .extracting(ILoggingEvent::getThrowableProxy)
        .allSatisfy(throwable -> assertThat(throwable).isNull());
  }

  private TutorWorkflowRepository.SubmissionFeedbackProjection capturedProjection() {
    ArgumentCaptor<TutorWorkflowRepository.SubmissionFeedbackProjection> projection =
        ArgumentCaptor.forClass(TutorWorkflowRepository.SubmissionFeedbackProjection.class);
    verify(repository).persistSubmissionFeedback(eq(session.id()), projection.capture());
    return projection.getValue();
  }
}
