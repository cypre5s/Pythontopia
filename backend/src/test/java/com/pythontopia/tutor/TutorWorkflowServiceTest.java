package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.domain.CoursePage;
import com.pythontopia.coursepack.repository.CoursePackRepository;
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
import com.pythontopia.tutor.TutorWorkflowAttachmentService;
import com.pythontopia.tutor.dto.TutorWorkflowAgentStateDto;
import com.pythontopia.tutor.dto.TutorWorkflowCardDto;
import com.pythontopia.tutor.dto.TutorWorkflowFeedbackRequest;
import com.pythontopia.tutor.dto.TutorWorkflowRunRequest;
import com.pythontopia.tutor.dto.TutorWorkflowSessionDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

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
  private final TutorWorkflowAttachmentService attachmentService =
      mock(TutorWorkflowAttachmentService.class);
  private final TutorWorkflowGraphClient graphClient = mock(TutorWorkflowGraphClient.class);
  private final TutorService tutorService = mock(TutorService.class);
  private final PublicProblemRepository problemRepository = mock(PublicProblemRepository.class);
  private final CoursePackRepository coursePackRepository = mock(CoursePackRepository.class);
  private final SubmissionService submissionService = mock(SubmissionService.class);
  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

  private final TutorWorkflowService service =
      new TutorWorkflowService(
          repository,
          attachmentService,
          graphClient,
          tutorService,
          problemRepository,
          coursePackRepository,
          submissionService,
          eventPublisher,
          new ObjectMapper(),
          mock(TutorDailyRequestLimitService.class));

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
    when(repository.claimAutoGenRun(anyLong(), any(UUID.class), any()))
        .thenAnswer(
            invocation -> {
              UUID runId = invocation.getArgument(1);
              String fingerprint = invocation.getArgument(2);
              return Optional.of(
                  new TutorWorkflowRepository.AutoGenCheckpoint(
                      runId, fingerprint, -1, "", "{}", "RUNNING"));
            });
    when(tutorService.resolveProfileBrief(USER, null)).thenReturn("认知风格: visual");
  }

  @Test
  void malformedCheckpointDoesNotLogStoredLearnerText() {
    String privateAnswer = "privateStudentAnswerSentinel";
    when(repository.findAgentState(session.id()))
        .thenReturn(
            Optional.of(new TutorWorkflowAgentStateDto(session.id(), privateAnswer, "{}")));
    Logger logger = (Logger) LoggerFactory.getLogger(TutorWorkflowService.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.WARN);
    logger.addAppender(appender);
    try {
      service.activeSession(USER, PROBLEM);

      assertThat(appender.list).hasSize(1);
      assertThat(appender.list.getFirst().getFormattedMessage()).doesNotContain(privateAnswer);
      assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> capturedBody() {
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(graphClient).runStream(eq(USER), captor.capture(), any(), any(), any(), any());
    return captor.getValue();
  }

  @Test
  void runBuildsGraphBodyWithProblemAndRestoreFields() {
    service.run(USER, new TutorWorkflowRunRequest(PROBLEM, "READING", Map.of()));

    Map<String, Object> body = capturedBody();
    assertThat(body.get("session_id")).isEqualTo("wf-7-476-abc");
    assertThat(body.get("event")).isEqualTo("READING");
    assertThat(body.get("restore_phase")).isEqualTo("READING");
    assertThat(body.get("restore_cards")).isEqualTo(List.of());
    assertThat(body.get("restore_chat_history")).isEqualTo(List.of());
    assertThat(body.get("coach_mode")).isEqualTo(true);
    assertThat(body.get("profile_brief")).isEqualTo("认知风格: visual");
    assertThat(body.get("run_id")).isInstanceOf(String.class);
    assertThat(body.get("request_fingerprint")).asString().matches("[0-9a-f]{64}");
    @SuppressWarnings("unchecked")
    Map<String, Object> problem = (Map<String, Object>) body.get("problem");
    assertThat(problem.get("problem_id")).isEqualTo(PROBLEM);
    assertThat(problem.get("kc_codes")).isEqualTo(List.of("PY43-KC-016"));
    assertThat(body).doesNotContainKey("diagnosis");
  }

  @Test
  void runRestoresNativeAutoGenCheckpointForTheClaimedRun() throws Exception {
    UUID runId = UUID.randomUUID();
    String checkpointJson =
        "{\"stage_index\":2,\"stage_name\":\"generate_card\","
            + "\"team_state\":{\"agent_states\":[]},\"workflow_state\":\"state\"}";
    when(repository.claimAutoGenRun(anyLong(), any(UUID.class), any()))
        .thenAnswer(
            invocation ->
                Optional.of(
                    new TutorWorkflowRepository.AutoGenCheckpoint(
                        runId,
                        invocation.getArgument(2),
                        2,
                        "generate_card",
                        checkpointJson,
                        "RUNNING")));

    service.run(USER, new TutorWorkflowRunRequest(PROBLEM, "READING", Map.of()));

    Map<String, Object> body = capturedBody();
    assertThat(body.get("run_id")).isEqualTo(runId.toString());
    assertThat(body.get("request_fingerprint")).asString().matches("[0-9a-f]{64}");
    assertThat(body.get("restore_autogen_checkpoint"))
        .isEqualTo(new ObjectMapper().readValue(checkpointJson, Map.class));
  }

  @Test
  void runRejectsAnotherActiveRunForTheSameSession() {
    when(repository.claimAutoGenRun(anyLong(), any(UUID.class), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.run(USER, new TutorWorkflowRunRequest(PROBLEM, "READING", Map.of())))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("导学会话正在处理其他请求");
  }

  @Test
  void completedIdempotentRunReplaysStoredResultWithoutCallingGraphAgain() {
    UUID requestId = UUID.randomUUID();
    TutorWorkflowRunRequest request =
        new TutorWorkflowRunRequest(requestId, PROBLEM, "READING", Map.of());
    service.run(USER, request);

    ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
    verify(repository).claimAutoGenRun(eq(session.id()), eq(requestId), fingerprint.capture());
    when(repository.findAutoGenCheckpoint(session.id(), requestId))
        .thenReturn(
            Optional.of(
                new TutorWorkflowRepository.AutoGenCheckpoint(
                    requestId, fingerprint.getValue(), -1, "", "{}", "COMPLETED")));
    when(repository.findAutoGenRunResult(session.id(), requestId))
        .thenReturn(
            Optional.of(
                "{\"session_id\":\"wf-7-476-abc\",\"event\":\"READING\","
                    + "\"phase\":\"READING\",\"card_key\":null,\"card\":null,"
                    + "\"available_actions\":[],\"seq\":1}"));
    when(tutorService.resolveProfileBrief(USER, null)).thenReturn("changed profile");

    assertThat(service.run(USER, request)).isNotNull();

    verify(graphClient, times(1)).runStream(eq(USER), any(), any(), any(), any(), any());
    verify(repository).findAutoGenRunResult(session.id(), requestId);
  }

  @Test
  void idempotencyKeyCannotBeReusedForDifferentRequest() {
    UUID requestId = UUID.randomUUID();
    when(repository.findAutoGenCheckpoint(session.id(), requestId))
        .thenReturn(
            Optional.of(
                new TutorWorkflowRepository.AutoGenCheckpoint(
                    requestId, "f".repeat(64), -1, "", "{}", "COMPLETED")));

    assertThatThrownBy(
            () ->
                service.run(
                    USER,
                    new TutorWorkflowRunRequest(requestId, PROBLEM, "READING", Map.of())))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("请求标识");

    verify(graphClient, times(0)).runStream(eq(USER), any(), any(), any(), any(), any());
  }

  @Test
  void checkpointWriteRequiresOwnerAndActiveRunAndSavesNativeTeamState() throws Exception {
    UUID runId = UUID.randomUUID();
    String fingerprint = "c".repeat(64);
    JsonNode checkpoint =
        new ObjectMapper()
            .readTree(
                "{\"stage_index\":2,\"stage_name\":\"generate_card\","
                    + "\"team_state\":{\"agent_states\":[]},\"workflow_state\":\"state\"}");
    when(repository.findAutoGenCheckpoint(session.id(), runId))
        .thenReturn(
            Optional.of(
                new TutorWorkflowRepository.AutoGenCheckpoint(
                    runId, fingerprint, 1, "fetch_evidence", "{}", "RUNNING")));

    service.persistAutoGenCheckpoint(USER, session.sessionKey(), runId, fingerprint, checkpoint);

    ArgumentCaptor<String> savedJson = ArgumentCaptor.forClass(String.class);
    verify(repository)
        .saveAutoGenCheckpoint(
            eq(session.id()), eq(runId), eq(fingerprint), eq(2), eq("generate_card"), savedJson.capture());
    assertThat(new ObjectMapper().readTree(savedJson.getValue())).isEqualTo(checkpoint);
  }

  @Test
  void checkpointWriteRejectsUnknownStageNames() throws Exception {
    JsonNode checkpoint =
        new ObjectMapper()
            .readTree(
                "{\"stage_index\":0,\"stage_name\":\"unknown\","
                    + "\"team_state\":{},\"workflow_state\":\"state\"}");

    assertThatThrownBy(
            () ->
                service.persistAutoGenCheckpoint(
                    USER, session.sessionKey(), UUID.randomUUID(), "e".repeat(64), checkpoint))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("恢复点无效");
  }

  @Test
  void checkpointWriteRejectsImagePayloads() throws Exception {
    UUID runId = UUID.randomUUID();
    String fingerprint = "d".repeat(64);
    JsonNode checkpoint =
        new ObjectMapper()
            .readTree(
                "{\"stage_index\":3,\"stage_name\":\"generate_card\","
                    + "\"team_state\":{\"private_image_data\":\"data:image/png;base64,AAAA\"},"
                    + "\"workflow_state\":\"state\"}");

    assertThatThrownBy(
            () ->
                service.persistAutoGenCheckpoint(
                    USER, session.sessionKey(), runId, fingerprint, checkpoint))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("恢复点无效");
  }

  @Test
  void checkpointWriteCannotCrossTheOwnedSessionBoundary() throws Exception {
    when(repository.findBySessionKey(8L, session.sessionKey())).thenReturn(Optional.empty());
    JsonNode checkpoint =
        new ObjectMapper()
            .readTree(
                "{\"stage_index\":0,\"stage_name\":\"validate_transition\","
                    + "\"team_state\":{},\"workflow_state\":\"state\"}");

    assertThatThrownBy(
            () ->
                service.persistAutoGenCheckpoint(
                    8L, session.sessionKey(), UUID.randomUUID(), "e".repeat(64), checkpoint))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("会话不存在");
  }

  @Test
  void chatReferencesAreResolvedFromTheOwnedSessionCards() throws Exception {
    String cardId = "wf-7-476-abc:x1";
    when(repository.findCards(session.id()))
        .thenReturn(
            List.of(
                new TutorWorkflowCardDto(
                    1,
                    "CHAT",
                    "ai_reply",
                    new ObjectMapper().readTree(
                        """
                        {"card_id":"wf-7-476-abc:x1","content":"先检查半径输入是否正确。"}
                        """),
                    OffsetDateTime.parse("2026-10-12T12:00:00Z"))));

    service.run(
        USER,
        new TutorWorkflowRunRequest(
            PROBLEM,
            "CHAT",
            Map.of(
                "message", "@card:" + cardId + " 请继续解释",
                "references", List.of(Map.of("kind", "card", "snippet", "伪造内容")))));

    @SuppressWarnings("unchecked")
    Map<String, Object> eventData = (Map<String, Object>) capturedBody().get("event_data");
    assertThat(eventData.get("references"))
        .isEqualTo(
            List.of(
                Map.of(
                    "kind", "card",
                    "card_id", cardId,
                    "card_key", "ai_reply",
                    "snippet", "先检查半径输入是否正确。")));
  }

  @Test
  void chatRejectsReferencesOutsideTheOwnedSession() {
    assertThatThrownBy(
            () ->
                service.run(
                    USER,
                    new TutorWorkflowRunRequest(
                        PROBLEM, "CHAT", Map.of("message", "@card:wf-7-476-other:x1"))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("引用内容不可用");
  }

  @Test
  void chatReferencesOnlyPagesFromTheCoursePack() {
    when(coursePackRepository.findPageById(88L))
        .thenReturn(
            Optional.of(
                new CoursePage(
                    88L, 43L, 12L, 4L, 17, "for 循环", "课件完整内容", "课件摘录")));

    service.run(
        USER,
        new TutorWorkflowRunRequest(
            PROBLEM, "CHAT", Map.of("message", "@courseware:88 请结合这页说明")));

    @SuppressWarnings("unchecked")
    Map<String, Object> eventData = (Map<String, Object>) capturedBody().get("event_data");
    assertThat(eventData.get("references"))
        .isEqualTo(
            List.of(
                Map.of(
                    "kind", "courseware",
                    "page_id", 88L,
                    "page_no", 17,
                    "chapter_id", 4L,
                    "title", "for 循环",
                    "snippet", "课件摘录")));
  }

  @Test
  void chatRejectsCoursewareOutsideTheCoursePack() {
    when(coursePackRepository.findPageById(88L))
        .thenReturn(
            Optional.of(
                new CoursePage(
                    88L, 99L, 12L, 4L, 17, "他人课件", "不应暴露", "不应暴露")));

    assertThatThrownBy(
            () ->
                service.run(
                    USER,
                    new TutorWorkflowRunRequest(
                        PROBLEM, "CHAT", Map.of("message", "@courseware:88"))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("引用内容不可用");
  }

  @Test
  void activeSessionReturnsPersistedEpisodePlan() throws Exception {
    when(repository.findAgentState(session.id()))
        .thenReturn(
            Optional.of(
                new TutorWorkflowAgentStateDto(
                    session.id(),
                    """
                    {"student_state":{"hint_level":1},
                     "episode_plan":{"plan_id":"plan-1","revision":3,"status":"ACTIVE"},
                     "chat_history":[]}
                    """,
                    "{}")));

    Map<String, Object> snapshot = service.activeSession(USER, PROBLEM);

    @SuppressWarnings("unchecked")
    Map<String, Object> episodePlan = (Map<String, Object>) snapshot.get("episodePlan");
    assertThat(episodePlan).containsEntry("plan_id", "plan-1").containsEntry("revision", 3);
  }

  @Test
  void runRebuildsAutogenChatContextFromPersistedCards() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    when(repository.findRecentCards(session.id(), 12))
        .thenReturn(
            List.of(
                new TutorWorkflowCardDto(
                    1,
                    "CHAT",
                    "ai_reply",
                    mapper.readTree(
                        """
                        {"card_id":"card-1","user_message":"旧问题","content":"旧回答"}
                        """),
                    OffsetDateTime.parse("2026-10-12T12:00:00Z")),
                new TutorWorkflowCardDto(
                    2,
                    "AC_REVIEW",
                    "post_ac",
                    mapper.readTree(
                        """
                        {"card_id":"card-2","success_summary":"已通过","next_practice_direction":"尝试列表变式"}
                        """),
                    OffsetDateTime.parse("2026-10-12T12:01:00Z"))));

    service.run(USER, new TutorWorkflowRunRequest(PROBLEM, "CHAT", Map.of("message", "继续")));

    Map<String, Object> body = capturedBody();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> cards = (List<Map<String, Object>>) body.get("restore_cards");
    assertThat(cards)
        .containsExactly(
            Map.of("card_id", "card-1", "card_key", "ai_reply", "short_text", "旧回答"),
            Map.of(
                "card_id", "card-2",
                "card_key", "post_ac",
                "short_text", "已通过",
                "next_practice_direction", "尝试列表变式"));
    assertThat(body.get("restore_chat_history"))
        .isEqualTo(
            List.of(
                Map.of("role", "user", "content", "旧问题"),
                Map.of("role", "assistant", "content", "旧回答")));
  }

  @Test
  void chatInputRejectsOversizedTextAndImagesOnOtherEvents() {
    assertThatThrownBy(
            () ->
                service.run(
                    USER,
                    new TutorWorkflowRunRequest(
                        PROBLEM, "CHAT", Map.of("message", "x".repeat(2001)))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("超过长度限制");
    assertThatThrownBy(
            () ->
                service.run(
                    USER,
                    new TutorWorkflowRunRequest(
                        PROBLEM,
                        "IDEATING",
                        Map.of("images", List.of("data:image/png;base64,aGVsbG8=")))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("仅支持导学对话");
  }

  @Test
  void chatForwardsCurrentEditorCodeToGraph() {
    service.run(
        USER,
        new TutorWorkflowRunRequest(
            PROBLEM,
            "CHAT",
            Map.of("message", "解释当前代码", "code", "print(radius * radius)")));

    @SuppressWarnings("unchecked")
    Map<String, Object> eventData = (Map<String, Object>) capturedBody().get("event_data");
    assertThat(eventData)
        .containsEntry("message", "解释当前代码")
        .containsEntry("code", "print(radius * radius)");
  }

  @Test
  void chatRejectsInvalidOrOversizedEditorCode() {
    assertThatThrownBy(
            () ->
                service.run(
                    USER,
                    new TutorWorkflowRunRequest(
                        PROBLEM,
                        "CHAT",
                        Map.of("message", "解释当前代码", "code", "x".repeat(20_001)))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("代码");

    assertThatThrownBy(
            () ->
                service.run(
                    USER,
                    new TutorWorkflowRunRequest(
                        PROBLEM, "CHAT", Map.of("message", "解释当前代码", "code", 42))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("代码");
  }

  @Test
  void runRejectsInlineImageBytesBeforeTheyEnterTheGraphEvent() {
    assertThatThrownBy(
            () ->
                service.run(
                    USER,
                    new TutorWorkflowRunRequest(
                        PROBLEM,
                        "CHAT",
                        Map.of(
                            "message", "请看图",
                            "images", List.of("data:image/png;base64,aGVsbG8=")))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("私有附件");
  }

  @Test
  void imageOnlyChatGetsTextForPersistenceWithoutSavingImageData() throws Exception {
    String attachmentId = "5db6f261-913a-4c12-b1c8-f40303dbf8a9";
    AtomicReference<Consumer<String>> onResult = new AtomicReference<>();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              onResult.set(invocation.getArgument(3));
              return null;
            })
        .when(graphClient)
        .runStream(any(), any(), any(), any(), any(), any());

    service.run(
        USER,
        new TutorWorkflowRunRequest(
            PROBLEM, "CHAT", Map.of("attachment_ids", List.of(attachmentId))));
    @SuppressWarnings("unchecked")
    Map<String, Object> eventData = (Map<String, Object>) capturedBody().get("event_data");
    assertThat(eventData).containsEntry("message", "请结合图片帮我分析。");
    assertThat(eventData)
        .containsEntry("attachment_ids", List.of(attachmentId))
        .doesNotContainKey("images");
    assertThat(capturedBody()).doesNotContainKey("private_images");

    onResult
        .get()
        .accept(
            """
            {"result": {"session_id":"wf-7-476-abc","event":"CHAT","phase":"READING",
             "card_key":"ai_reply","card":{"card_id":"wf-7-476-abc:x1","content":"看起来是半径输入"},
             "available_actions":[],"seq":1}}
            """);

    TutorWorkflowRepository.RunProjection projection = capturedProjection();
    assertThat(projection.cardPayloadJson())
        .contains("请结合图片帮我分析")
        .doesNotContain("data:image");
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
  void runRestoresWrappedCheckpointPlanAndChatHistory() {
    when(repository.findAgentState(session.id()))
        .thenReturn(
            Optional.of(
                new TutorWorkflowAgentStateDto(
                    session.id(),
                    """
                    {
                      "student_state":{"session_id":"wf-7-476-abc","user_id":7,"problem_id":476,
                        "phase":"CODING","hint_level":2},
                      "episode_plan":{
                        "plan_id":"plan-1","session_id":"wf-7-476-abc","problem_id":476,
                        "user_id":7,"objective":"完成圆面积程序","trigger_event":"ERROR_FEEDBACK",
                        "revision":2,"current_step_id":"step-2","status":"ACTIVE",
                        "steps":[
                          {"step_id":"step-1","event":"EXECUTION_TRACE","title":"观察变量",
                           "status":"DONE"},
                          {"step_id":"step-2","event":"SKELETON","title":"整理计算步骤",
                           "status":"ACTIVE"}
                        ]
                      },
                      "chat_history":[
                        {"role":"user","content":"旧问题"},
                        {"role":"assistant","content":"旧回答"}
                      ]
                    }
                    """,
                    "{}")));

    service.run(USER, new TutorWorkflowRunRequest(PROBLEM, "CHAT", Map.of("message", "继续")));

    Map<String, Object> body = capturedBody();
    @SuppressWarnings("unchecked")
    Map<String, Object> state = (Map<String, Object>) body.get("student_state");
    assertThat(state).containsEntry("hint_level", 2);
    @SuppressWarnings("unchecked")
    Map<String, Object> plan = (Map<String, Object>) body.get("episode_plan");
    assertThat(plan).containsEntry("plan_id", "plan-1").containsEntry("revision", 2);
    assertThat(body.get("restore_chat_history"))
        .isEqualTo(
            List.of(
                Map.of("role", "user", "content", "旧问题"),
                Map.of("role", "assistant", "content", "旧回答")));
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
        .runStream(any(), any(), any(), any(), any(), any());

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

    TutorWorkflowRepository.RunProjection projection = capturedProjection();
    assertThat(projection.phase()).isEqualTo("READING");
    assertThat(projection.seq()).isEqualTo(3);
    assertThat(projection.cardPayloadJson())
        .contains("user_message")
        .contains("怎么保留小数？");
  }

  @Test
  void persistProjectionStoresWrappedStatePlanAndChatHistory() throws Exception {
    AtomicReference<Consumer<String>> onResult = new AtomicReference<>();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              onResult.set(invocation.getArgument(3));
              return null;
            })
        .when(graphClient)
        .runStream(any(), any(), any(), any(), any(), any());

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
             "episode_plan": {"plan_id":"plan-1","session_id":"wf-7-476-abc","problem_id":476,
               "user_id":7,"objective":"完成圆面积程序","trigger_event":"ERROR_FEEDBACK",
               "revision":2,"current_step_id":"step-2","status":"ACTIVE",
               "steps":[{"step_id":"step-1","event":"EXECUTION_TRACE","title":"观察变量","status":"DONE"},
                 {"step_id":"step-2","event":"SKELETON","title":"整理计算步骤","status":"ACTIVE"}]},
             "resume_chat_history": [{"role":"user","content":"旧问题"},
               {"role":"assistant","content":"旧回答"}],
             "private_images": ["data:image/png;base64,SECRET_IMAGE_BYTES"],
             "coach_decision": {"next_event": "EXECUTION_TRACE", "tool_name": "execution_trace",
               "hint_level": 1, "action_goal": "看清实际行为", "stop_condition": "说出偏差",
               "confidence": 0.82, "reason_summary": "WA 早期先看执行轨迹"},
             "available_actions": [], "seq": 4}}
            """);

    TutorWorkflowRepository.RunProjection projection = capturedProjection();
    JsonNode savedState = new ObjectMapper().readTree(projection.stateJson());
    assertThat(savedState.path("student_state").path("hint_level").asInt()).isEqualTo(1);
    assertThat(savedState.path("episode_plan").path("plan_id").asText()).isEqualTo("plan-1");
    assertThat(savedState.path("chat_history")).hasSize(2);
    assertThat(projection.stateJson())
        .doesNotContain("private_images", "data:image", "SECRET_IMAGE_BYTES");
    assertThat(projection.decisionJson()).contains("\"next_event\":\"EXECUTION_TRACE\"");
    assertThat(projection.actionPayloadJson()).contains("\"plan_id\":\"plan-1\"");
  }

  @Test
  void duplicateTerminalCallbacksShareOneRunId() {
    AtomicReference<Consumer<String>> onResult = new AtomicReference<>();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              onResult.set(invocation.getArgument(3));
              return null;
            })
        .when(graphClient)
        .runStream(any(), any(), any(), any(), any(), any());

    service.run(USER, new TutorWorkflowRunRequest(PROBLEM, "PLAN_START", Map.of("plan_id", "p1")));
    String result = """
        {"result":{"event":"PLAN_START","phase":"READING","seq":2}}
        """;
    onResult.get().accept(result);
    onResult.get().accept(result);

    ArgumentCaptor<TutorWorkflowRepository.RunProjection> projection =
        ArgumentCaptor.forClass(TutorWorkflowRepository.RunProjection.class);
    verify(repository, times(2)).persistRunProjection(eq(session.id()), projection.capture());
    assertThat(projection.getAllValues().get(0).runId())
        .isEqualTo(projection.getAllValues().get(1).runId());
  }

  @Test
  void projectionFailurePropagatesToSseRelay() {
    AtomicReference<Consumer<String>> onResult = new AtomicReference<>();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              onResult.set(invocation.getArgument(3));
              return null;
            })
        .when(graphClient)
        .runStream(any(), any(), any(), any(), any(), any());
    org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
        .when(repository)
        .persistRunProjection(eq(session.id()), any(TutorWorkflowRepository.RunProjection.class));

    service.run(USER, new TutorWorkflowRunRequest(PROBLEM, "READING", Map.of()));

    assertThatThrownBy(
            () ->
                onResult
                    .get()
                    .accept("{\"result\":{\"event\":\"READING\",\"phase\":\"READING\",\"seq\":1}}"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Failed to persist tutor workflow projection");
  }

  private TutorWorkflowRepository.RunProjection capturedProjection() {
    ArgumentCaptor<TutorWorkflowRepository.RunProjection> projection =
        ArgumentCaptor.forClass(TutorWorkflowRepository.RunProjection.class);
    verify(repository).persistRunProjection(eq(session.id()), projection.capture());
    return projection.getValue();
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
  void rejectsOversizedFeedbackPayloadBeforePersistingEvent() {
    var request =
        new TutorWorkflowFeedbackRequest(
            "ACTION_CLICKED", "execution_trace", null, Map.of("note", "x".repeat(5000)));

    assertThatThrownBy(() -> service.recordFeedback(USER, session.sessionKey(), request))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).status())
        .isEqualTo(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE);

    verify(repository, never())
        .insertActionEvent(
            eq(session.id()), eq(session.seq()), eq("ACTION_CLICKED"), eq("execution_trace"), anyString());
    verify(eventPublisher, never()).publishEvent(any());
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
