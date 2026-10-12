package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.submission.dto.JudgeResultDto;
import com.pythontopia.submission.dto.PublicProblemDto;
import com.pythontopia.submission.dto.SubmissionDetailDto;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionSource;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.dto.Verdict;
import com.pythontopia.submission.repository.PublicProblemRepository;
import com.pythontopia.submission.service.SubmissionService;
import com.pythontopia.tutor.dto.TutorWorkflowFeedbackRequest;
import com.pythontopia.tutor.dto.TutorWorkflowRunRequest;
import com.pythontopia.tutor.dto.TutorWorkflowAgentStateDto;
import com.pythontopia.tutor.dto.TutorWorkflowSessionDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;

/**
 * 导学工作流编排单测：
 *
 * <ul>
 *   <li>run 请求体组装：题面投影 / restore 回放字段 / 判题证据（含校验红线）。
 *   <li>AC_REVIEW 必须 AC、ERROR_FEEDBACK 必须非 AC、submission 与题目必须匹配。
 *   <li>连错统计：从最新提交起连续 FAILED 计数，遇非失败截断。
 *   <li>result 投影：phase/seq 落库 + 卡片入时间线（ai_reply 附 user_message）。
 * </ul>
 */
class TutorWorkflowServiceTest {

  private final TutorWorkflowRepository repository = mock(TutorWorkflowRepository.class);
  private final TutorWorkflowGraphClient graphClient = mock(TutorWorkflowGraphClient.class);
  private final TutorService tutorService = mock(TutorService.class);
  private final PublicProblemRepository problemRepository = mock(PublicProblemRepository.class);
  private final SubmissionService submissionService = mock(SubmissionService.class);
  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

  private final TutorWorkflowService service =
      new TutorWorkflowService(
          repository,
          graphClient,
          tutorService,
          problemRepository,
          submissionService,
          eventPublisher,
          new ObjectMapper());

  private static final long USER = 7L;
  private static final long PROBLEM = 476L;

  private final TutorWorkflowSessionDto session =
      new TutorWorkflowSessionDto(11L, "wf-7-476-abc", PROBLEM, "READING", 0, "ACTIVE");

  @BeforeEach
  void setUp() {
    when(problemRepository.findPublic(43L, PROBLEM))
        .thenReturn(
            Optional.of(
                new PublicProblemDto(
                    PROBLEM,
                    43L,
                    "PY43-KC-016",
                    "圆面积计算",
                    "读取半径输出面积",
                    "一个浮点数",
                    "面积保留 4 位",
                    "Low",
                    null)));
    when(repository.findActive(USER, PROBLEM)).thenReturn(Optional.of(session));
    when(repository.findBySessionKey(USER, session.sessionKey())).thenReturn(Optional.of(session));
    when(repository.findAgentState(session.id())).thenReturn(Optional.empty());
    when(tutorService.resolveProfileBrief(USER, null)).thenReturn("认知风格: visual");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> capturedBody() {
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(graphClient).runStream(eq(USER), captor.capture(), any(), any());
    return captor.getValue();
  }

  @Test
  void runBuildsGraphBodyWithProblemAndRestoreFields() {
    service.run(USER, new TutorWorkflowRunRequest(PROBLEM, "READING", Map.of()));

    Map<String, Object> body = capturedBody();
    assertThat(body.get("session_id")).isEqualTo("wf-7-476-abc");
    assertThat(body.get("event")).isEqualTo("READING");
    assertThat(body.get("restore_phase")).isEqualTo("READING");
    assertThat(body.get("coach_mode")).isEqualTo(true);
    assertThat(body.get("profile_brief")).isEqualTo("认知风格: visual");
    @SuppressWarnings("unchecked")
    Map<String, Object> problem = (Map<String, Object>) body.get("problem");
    assertThat(problem.get("problem_id")).isEqualTo(PROBLEM);
    assertThat(problem.get("kc_codes")).isEqualTo(List.of("PY43-KC-016"));
    assertThat(body).doesNotContainKey("diagnosis");
  }

  @Test
  void runRestoresPersistedAgentStateIntoGraphBody() {
    when(repository.findAgentState(session.id()))
        .thenReturn(
            Optional.of(
                new TutorWorkflowAgentStateDto(
                    session.id(),
                    """
                    {"session_id":"wf-7-476-abc","user_id":7,"problem_id":476,
                     "phase":"CODING","hint_level":2,"latest_verdict":"WA"}
                    """,
                    """
                    {"next_event":"EXECUTION_TRACE","reason_summary":"先看轨迹"}
                    """)));

    service.run(USER, new TutorWorkflowRunRequest(PROBLEM, "READING", Map.of()));

    @SuppressWarnings("unchecked")
    Map<String, Object> state = (Map<String, Object>) capturedBody().get("student_state");
    assertThat(state).containsEntry("hint_level", 2).containsEntry("latest_verdict", "WA");
  }

  @Test
  void errorFeedbackRequiresSubmissionId() {
    assertThatThrownBy(
            () -> service.run(USER, new TutorWorkflowRunRequest(PROBLEM, "ERROR_FEEDBACK", Map.of())))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("submission_id");
  }

  @Test
  void acReviewRejectsFailedSubmission() {
    when(submissionService.findOne(USER, 99L))
        .thenReturn(detail(99L, PROBLEM, Verdict.WA, SubmissionStatus.FAILED));
    assertThatThrownBy(
            () ->
                service.run(
                    USER,
                    new TutorWorkflowRunRequest(PROBLEM, "AC_REVIEW", Map.of("submission_id", 99))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("未通过");
  }

  @Test
  void errorFeedbackRejectsAcceptedSubmissionAndMismatchedProblem() {
    when(submissionService.findOne(USER, 99L))
        .thenReturn(detail(99L, PROBLEM, Verdict.AC, SubmissionStatus.PASSED));
    assertThatThrownBy(
            () ->
                service.run(
                    USER,
                    new TutorWorkflowRunRequest(
                        PROBLEM, "ERROR_FEEDBACK", Map.of("submission_id", 99))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("已通过");

    when(submissionService.findOne(USER, 100L))
        .thenReturn(detail(100L, PROBLEM + 1, Verdict.WA, SubmissionStatus.FAILED));
    assertThatThrownBy(
            () ->
                service.run(
                    USER,
                    new TutorWorkflowRunRequest(
                        PROBLEM, "ERROR_FEEDBACK", Map.of("submission_id", 100))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不匹配");
  }

  @Test
  void errorFeedbackAttachesDiagnosisWithConsecutiveErrors() {
    when(submissionService.findOne(USER, 99L))
        .thenReturn(detail(99L, PROBLEM, Verdict.WA, SubmissionStatus.FAILED));
    when(submissionService.list(eq(USER), eq(PROBLEM), any(), anyInt(), anyInt()))
        .thenReturn(
            List.of(
                submission(99L, SubmissionStatus.FAILED),
                submission(98L, SubmissionStatus.FAILED),
                submission(97L, SubmissionStatus.PASSED),
                submission(96L, SubmissionStatus.FAILED)));

    service.run(
        USER, new TutorWorkflowRunRequest(PROBLEM, "ERROR_FEEDBACK", Map.of("submission_id", 99)));

    @SuppressWarnings("unchecked")
    Map<String, Object> diagnosis = (Map<String, Object>) capturedBody().get("diagnosis");
    assertThat(diagnosis.get("submission_id")).isEqualTo(99L);
    assertThat(diagnosis.get("verdict")).isEqualTo("WA");
    // 连错从最新起算，遇 PASSED 截断 → 2。
    assertThat(diagnosis.get("consecutive_errors")).isEqualTo(2);
  }

  @Test
  void persistProjectionUpdatesPhaseAndInsertsCardWithUserMessage() throws Exception {
    AtomicReference<Consumer<String>> onResult = new AtomicReference<>();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              onResult.set(invocation.getArgument(3));
              return null;
            })
        .when(graphClient)
        .runStream(any(), any(), any(), any());

    service.run(
        USER, new TutorWorkflowRunRequest(PROBLEM, "CHAT", Map.of("message", "怎么保留小数？")));

    // 模拟 graph 终态（AG-UI RUN_FINISHED 包裹形状）。
    onResult
        .get()
        .accept(
            """
            {"result": {"session_id": "wf-7-476-abc", "event": "CHAT", "phase": "READING",
             "card_key": "ai_reply", "card": {"card_id": "wf-7-476-abc:x1", "content": "想想格式化输出"},
             "available_actions": [], "seq": 3}}
            """);

    verify(repository).updateProgress(11L, "READING", 3);
    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    verify(repository).insertCard(eq(11L), eq(3), eq("CHAT"), eq("ai_reply"), payload.capture());
    assertThat(payload.getValue()).contains("user_message").contains("怎么保留小数？");
  }

  @Test
  void persistProjectionStoresCoachStateDecisionAndActionEvent() throws Exception {
    AtomicReference<Consumer<String>> onResult = new AtomicReference<>();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              onResult.set(invocation.getArgument(3));
              return null;
            })
        .when(graphClient)
        .runStream(any(), any(), any(), any());

    service.run(
        USER, new TutorWorkflowRunRequest(PROBLEM, "CHAT", Map.of("message", "先看哪里？")));

    onResult
        .get()
        .accept(
            """
            {"result": {"session_id": "wf-7-476-abc", "event": "EXECUTION_TRACE", "phase": "READING",
             "card_key": "execution_trace", "card": {"card_id": "wf-7-476-abc:x2", "summary": "看变量"},
             "student_state": {"session_id": "wf-7-476-abc", "user_id": 7, "problem_id": 476,
               "phase": "READING", "hint_level": 1, "next_best_action": "EXECUTION_TRACE"},
             "coach_decision": {"next_event": "EXECUTION_TRACE", "tool_name": "execution_trace",
               "hint_level": 1, "action_goal": "看清实际行为", "stop_condition": "说出偏差",
               "confidence": 0.82, "reason_summary": "WA 早期先看执行轨迹"},
             "available_actions": [], "seq": 4}}
            """);

    ArgumentCaptor<String> stateJson = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> decisionJson = ArgumentCaptor.forClass(String.class);
    verify(repository).upsertAgentState(eq(11L), stateJson.capture(), decisionJson.capture());
    assertThat(stateJson.getValue()).contains("\"hint_level\":1");
    assertThat(decisionJson.getValue()).contains("\"next_event\":\"EXECUTION_TRACE\"");
    verify(repository)
        .insertActionEvent(eq(11L), eq(4), eq("CARD_GENERATED"), eq("execution_trace"), any());
  }

  @Test
  void recordFeedbackStoresActionClickAndPublishesLearningEvent() {
    service.recordFeedback(
        USER,
        session.sessionKey(),
        new TutorWorkflowFeedbackRequest(
            "ACTION_CLICKED", "execution_trace", null, Map.of("source", "button")));

    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    verify(repository)
        .insertActionEvent(
            eq(11L), eq(0), eq("ACTION_CLICKED"), eq("execution_trace"), payload.capture());
    assertThat(payload.getValue()).contains("\"source\":\"button\"");
    verify(eventPublisher)
        .publishEvent(
            new LearningEvent.GuideActionClicked(
                USER, PROBLEM, "PY43-KC-016", "execution_trace"));
  }

  @Test
  void recordFeedbackStoresCheckpointCompletedAndPublishesLearningEvent() {
    service.recordFeedback(
        USER,
        session.sessionKey(),
        new TutorWorkflowFeedbackRequest(
            "CHECKPOINT_COMPLETED", null, "execution_trace", Map.of("answer", "变量变了")));

    verify(repository)
        .insertActionEvent(eq(11L), eq(0), eq("CHECKPOINT_COMPLETED"), eq("execution_trace"), any());
    verify(eventPublisher)
        .publishEvent(
            new LearningEvent.GuideCheckpointCompleted(
                USER, PROBLEM, "PY43-KC-016", "execution_trace"));
  }

  @Test
  void countConsecutiveErrorsDegradesToZeroOnFailure() {
    when(submissionService.list(anyLong(), anyLong(), any(), anyInt(), anyInt()))
        .thenThrow(new RuntimeException("db down"));
    assertThat(service.countConsecutiveErrors(USER, PROBLEM)).isZero();
  }

  private static SubmissionDetailDto detail(
      long submissionId, long problemId, Verdict verdict, SubmissionStatus status) {
    return new SubmissionDetailDto(
        submission(submissionId, problemId, status),
        new JudgeResultDto(1L, submissionId, verdict, 1, 3, "WA on case 2", null, OffsetDateTime.now()));
  }

  private static SubmissionDto submission(long id, SubmissionStatus status) {
    return submission(id, PROBLEM, status);
  }

  private static SubmissionDto submission(long id, long problemId, SubmissionStatus status) {
    return new SubmissionDto(
        id,
        USER,
        problemId,
        null,
        SubmissionSource.TRAINING,
        "python",
        "print()",
        OffsetDateTime.now(),
        status,
        10,
        1024);
  }
}
