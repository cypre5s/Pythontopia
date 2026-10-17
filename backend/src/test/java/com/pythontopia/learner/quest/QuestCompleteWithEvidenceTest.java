package com.pythontopia.learner.quest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.quest.dto.QuestCompleteRequest;
import com.pythontopia.learner.quest.dto.QuestDto;
import com.pythontopia.learner.quest.dto.QuestNodeType;
import com.pythontopia.learner.quest.dto.QuestSource;
import com.pythontopia.learner.quest.dto.QuestStatus;
import com.pythontopia.learner.quest.evidence.QuestEvidenceVerifier;
import com.pythontopia.learner.quest.repository.QuestRepository;
import com.pythontopia.learner.quest.service.QuestService;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionSource;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import com.pythontopia.workshop.dto.ContentFormat;
import com.pythontopia.workshop.dto.LearningResourceDto;
import com.pythontopia.workshop.dto.ResourceFeedbackStatus;
import com.pythontopia.workshop.dto.ResourceStatusFlag;
import com.pythontopia.workshop.dto.ResourceType;
import com.pythontopia.workshop.repository.LearningResourceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 验证教育闭环关卡：QuestService.complete() 在 quest 配置了 required_evidence_type 后必须按类型校验证据。
 * 这里隔离 QuestEvidenceVerifier 的真实实现，断言"通过 / 拒绝"两条路径都能正确路由到 QuestRepository。
 */
class QuestCompleteWithEvidenceTest {

  private final QuestRepository repository = mock(QuestRepository.class);
  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final PracticeSubmissionRepository submissions =
      mock(PracticeSubmissionRepository.class);
  private final LearningResourceRepository resources = mock(LearningResourceRepository.class);
  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final QuestEvidenceVerifier verifier =
      new QuestEvidenceVerifier(submissions, resources, jdbcTemplate);
  private final QuestService service = new QuestService(repository, publisher, verifier);
  private final ObjectMapper mapper = new ObjectMapper();

  /** 状态推进由带并发守卫的 UPDATE 决定：命中 1 行=本请求赢得推进权。 */
  @BeforeEach
  void stubWinningTheStatusTransition() {
    when(repository.updateStatus(anyLong(), anyLong(), any(QuestStatus.class))).thenReturn(1);
  }

  @Test
  void testComplete_submissionEvidence_passed() {
    QuestDto quest =
        quest(
            QuestNodeType.SOLVE_PROBLEM,
            QuestStatus.IN_PROGRESS,
            "submission",
            null,
            List.of(101L),
            null);
    when(repository.findById(eq(7L), eq(10L))).thenReturn(Optional.of(quest));
    when(submissions.findSubmission(eq(7L), eq(900L)))
        .thenReturn(Optional.of(submission(900L, 7L, 101L, SubmissionStatus.PASSED)));
    QuestDto completed =
        quest(
            QuestNodeType.SOLVE_PROBLEM,
            QuestStatus.COMPLETED,
            "submission",
            null,
            List.of(101L),
            null);
    when(repository.findOwned(eq(7L), eq(10L))).thenReturn(Optional.of(completed));

    QuestDto result =
        service.complete(
            7L,
            10L,
            new QuestCompleteRequest("done", 900L, null, null, null));

    assertThat(result.status()).isEqualTo(QuestStatus.COMPLETED);
    verify(repository).updateStatus(7L, 10L, QuestStatus.COMPLETED);
    verify(repository).recordProgress(eq(10L), eq("COMPLETED"), eq("done"));
  }

  @Test
  void testComplete_noSubmission_rejected() {
    QuestDto quest =
        quest(
            QuestNodeType.SOLVE_PROBLEM,
            QuestStatus.IN_PROGRESS,
            "submission",
            null,
            List.of(101L),
            null);
    when(repository.findById(eq(7L), eq(10L))).thenReturn(Optional.of(quest));

    assertThatThrownBy(
            () -> service.complete(7L, 10L, new QuestCompleteRequest("try")))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("EVIDENCE_REQUIRED_SUBMISSION");

    verify(repository, never())
        .updateStatus(anyLong(), anyLong(), eq(QuestStatus.COMPLETED));
  }

  @Test
  void testComplete_wrongProblem_rejected() {
    QuestDto quest =
        quest(
            QuestNodeType.SOLVE_PROBLEM,
            QuestStatus.IN_PROGRESS,
            "submission",
            null,
            List.of(101L),
            null);
    when(repository.findById(eq(7L), eq(10L))).thenReturn(Optional.of(quest));
    when(submissions.findSubmission(eq(7L), eq(901L)))
        .thenReturn(Optional.of(submission(901L, 7L, 999L, SubmissionStatus.PASSED)));

    assertThatThrownBy(
            () ->
                service.complete(
                    7L,
                    10L,
                    new QuestCompleteRequest("wrong-target", 901L, null, null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("WRONG_PROBLEM");

    verify(repository, never())
        .updateStatus(anyLong(), anyLong(), eq(QuestStatus.COMPLETED));
  }

  @Test
  void testComplete_failedSubmission_rejected() {
    QuestDto quest =
        quest(
            QuestNodeType.SOLVE_PROBLEM,
            QuestStatus.IN_PROGRESS,
            "submission",
            null,
            List.of(101L),
            null);
    when(repository.findById(eq(7L), eq(10L))).thenReturn(Optional.of(quest));
    when(submissions.findSubmission(eq(7L), eq(902L)))
        .thenReturn(Optional.of(submission(902L, 7L, 101L, SubmissionStatus.FAILED)));

    assertThatThrownBy(
            () ->
                service.complete(
                    7L,
                    10L,
                    new QuestCompleteRequest("not-passed", 902L, null, null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("INVALID_SUBMISSION");
  }

  @Test
  void testComplete_resourceEvidence_passed() {
    QuestDto quest =
        quest(
            QuestNodeType.READ_RESOURCE,
            QuestStatus.IN_PROGRESS,
            "resource",
            null,
            List.of(),
            55L);
    when(repository.findById(eq(7L), eq(11L))).thenReturn(Optional.of(quest));
    when(resources.findById(eq(7L), eq(55L)))
        .thenReturn(Optional.of(resource(55L, 7L)));
    when(jdbcTemplate.queryForObject(
            contains("learning_event_log"),
            eq(Boolean.class),
            eq(7L),
            eq("55"),
            any(OffsetDateTime.class)))
        .thenReturn(true);
    QuestDto completed =
        quest(
            QuestNodeType.READ_RESOURCE,
            QuestStatus.COMPLETED,
            "resource",
            null,
            List.of(),
            55L);
    when(repository.findOwned(eq(7L), eq(11L))).thenReturn(Optional.of(completed));

    QuestDto result =
        service.complete(
            7L, 11L, new QuestCompleteRequest("read", null, 55L, null, null));

    assertThat(result.status()).isEqualTo(QuestStatus.COMPLETED);
    verify(repository).updateStatus(7L, 11L, QuestStatus.COMPLETED);
  }

  @Test
  void testComplete_ownedResourceNotConsumed_rejected() {
    QuestDto quest =
        quest(
            QuestNodeType.READ_RESOURCE,
            QuestStatus.IN_PROGRESS,
            "resource",
            null,
            List.of(),
            55L);
    when(repository.findById(eq(7L), eq(11L))).thenReturn(Optional.of(quest));
    when(resources.findById(eq(7L), eq(55L)))
        .thenReturn(Optional.of(resource(55L, 7L)));
    when(jdbcTemplate.queryForObject(
            contains("learning_event_log"),
            eq(Boolean.class),
            eq(7L),
            eq("55"),
            any(OffsetDateTime.class)))
        .thenReturn(false);
    when(repository.findOwned(eq(7L), eq(11L)))
        .thenReturn(
            Optional.of(
                quest(
                    QuestNodeType.READ_RESOURCE,
                    QuestStatus.COMPLETED,
                    "resource",
                    null,
                    List.of(),
                    55L)));

    assertThatThrownBy(
            () -> service.complete(7L, 11L, new QuestCompleteRequest("unconsumed", null, 55L, null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("INVALID_RESOURCE");

    verify(jdbcTemplate)
        .queryForObject(
            contains("learning_event_log"),
            eq(Boolean.class),
            eq(7L),
            eq("55"),
            any(OffsetDateTime.class));
    verify(repository, never())
        .updateStatus(anyLong(), anyLong(), eq(QuestStatus.COMPLETED));
  }

  @Test
  void testComplete_pageReadEvidence_passed() {
    QuestDto quest =
        quest(
            QuestNodeType.READ_RESOURCE,
            QuestStatus.IN_PROGRESS,
            "page_read",
            null,
            List.of(),
            null);
    when(repository.findById(eq(7L), eq(12L))).thenReturn(Optional.of(quest));
    when(jdbcTemplate.queryForObject(
            contains("mainline_stage_progress"),
            eq(Boolean.class),
            eq(77L),
            eq(7L),
            eq(77L)))
        .thenReturn(true);
    QuestDto completed =
        quest(
            QuestNodeType.READ_RESOURCE,
            QuestStatus.COMPLETED,
            "page_read",
            null,
            List.of(),
            null);
    when(repository.findOwned(eq(7L), eq(12L))).thenReturn(Optional.of(completed));

    QuestDto result =
        service.complete(
            7L, 12L, new QuestCompleteRequest("page", null, null, 77L, null));

    assertThat(result.status()).isEqualTo(QuestStatus.COMPLETED);
    verify(repository, times(1)).updateStatus(7L, 12L, QuestStatus.COMPLETED);
  }

  @Test
  void testComplete_pageReadNotRecordedForStudent_rejected() {
    QuestDto quest =
        quest(
            QuestNodeType.READ_RESOURCE,
            QuestStatus.IN_PROGRESS,
            "page_read",
            null,
            List.of(),
            null);
    when(repository.findById(eq(7L), eq(12L))).thenReturn(Optional.of(quest));
    when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM course_page WHERE id = ?"), eq(Integer.class), eq(77L)))
        .thenReturn(1);
    when(jdbcTemplate.queryForObject(
            contains("mainline_stage_progress"),
            eq(Boolean.class),
            eq(77L),
            eq(7L),
            eq(77L)))
        .thenReturn(false);
    when(repository.findOwned(eq(7L), eq(12L)))
        .thenReturn(
            Optional.of(
                quest(
                    QuestNodeType.READ_RESOURCE,
                    QuestStatus.COMPLETED,
                    "page_read",
                    null,
                    List.of(),
                    null)));

    assertThatThrownBy(
            () ->
                service.complete(
                    7L, 12L, new QuestCompleteRequest("unread page", null, null, 77L, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("INVALID_PAGE_READ");

    verify(jdbcTemplate)
        .queryForObject(
            contains("mainline_stage_progress"),
            eq(Boolean.class),
            eq(77L),
            eq(7L),
            eq(77L));
    verify(repository, never())
        .updateStatus(anyLong(), anyLong(), eq(QuestStatus.COMPLETED));
  }

  @Test
  void testComplete_kcMasteryEvidence_insufficient_rejected() {
    QuestDto quest =
        quest(
            QuestNodeType.PLANT_KC,
            QuestStatus.IN_PROGRESS,
            "kc_mastery",
            new BigDecimal("0.30"),
            List.of(),
            null);
    when(repository.findById(eq(7L), eq(13L))).thenReturn(Optional.of(quest));
    when(jdbcTemplate.queryForObject(
            contains("kc_mastery_event"),
            eq(BigDecimal.class),
            eq(7L),
            eq("PY43-KC-001"),
            any(OffsetDateTime.class)))
        .thenReturn(new BigDecimal("0.10"));

    assertThatThrownBy(
            () ->
                service.complete(
                    7L,
                    13L,
                    new QuestCompleteRequest("mastery", null, null, null, 0.10)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("EVIDENCE_REQUIRED_MASTERY");

    verify(repository, never())
        .updateStatus(anyLong(), anyLong(), eq(QuestStatus.COMPLETED));
  }

  @Test
  void testComplete_kcMasteryClientClaimCannotReplaceServerEvidence() {
    QuestDto quest =
        quest(
            QuestNodeType.PLANT_KC,
            QuestStatus.IN_PROGRESS,
            "kc_mastery",
            new BigDecimal("0.30"),
            List.of(),
            null);
    when(repository.findById(eq(7L), eq(13L))).thenReturn(Optional.of(quest));
    when(jdbcTemplate.queryForObject(
            contains("kc_mastery_event"),
            eq(BigDecimal.class),
            eq(7L),
            eq("PY43-KC-001"),
            any(OffsetDateTime.class)))
        .thenReturn(BigDecimal.ZERO);
    when(repository.findOwned(eq(7L), eq(13L)))
        .thenReturn(
            Optional.of(
                quest(
                    QuestNodeType.PLANT_KC,
                    QuestStatus.COMPLETED,
                    "kc_mastery",
                    new BigDecimal("0.30"),
                    List.of(),
                    null)));

    assertThatThrownBy(
            () ->
                service.complete(
                    7L, 13L, new QuestCompleteRequest("claimed", null, null, null, 0.90)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("EVIDENCE_REQUIRED_MASTERY");

    verify(jdbcTemplate)
        .queryForObject(
            contains("kc_mastery_event"),
            eq(BigDecimal.class),
            eq(7L),
            eq("PY43-KC-001"),
            any(OffsetDateTime.class));
    verify(repository, never())
        .updateStatus(anyLong(), anyLong(), eq(QuestStatus.COMPLETED));
  }

  @Test
  void testComplete_kcMasteryEvidence_passed() {
    QuestDto quest =
        quest(
            QuestNodeType.PLANT_KC,
            QuestStatus.IN_PROGRESS,
            "kc_mastery",
            new BigDecimal("0.30"),
            List.of(),
            null);
    when(repository.findById(eq(7L), eq(13L))).thenReturn(Optional.of(quest));
    when(jdbcTemplate.queryForObject(
            contains("kc_mastery_event"),
            eq(BigDecimal.class),
            eq(7L),
            eq("PY43-KC-001"),
            any(OffsetDateTime.class)))
        .thenReturn(new BigDecimal("0.45"));
    QuestDto completed =
        quest(
            QuestNodeType.PLANT_KC,
            QuestStatus.COMPLETED,
            "kc_mastery",
            new BigDecimal("0.30"),
            List.of(),
            null);
    when(repository.findOwned(eq(7L), eq(13L))).thenReturn(Optional.of(completed));

    QuestDto result =
        service.complete(
            7L, 13L, new QuestCompleteRequest("mastery", null, null, null, 0.45));

    assertThat(result.status()).isEqualTo(QuestStatus.COMPLETED);
  }

  @Test
  void testComplete_pathPlannerTaskWithoutConfiguredEvidenceDerivesEvidenceFromNodeType() {
    QuestDto quest =
        quest(
            QuestSource.PATH_PLANNER,
            QuestNodeType.SOLVE_PROBLEM,
            QuestStatus.IN_PROGRESS,
            null,
            null,
            List.of(),
            null);
    assertThatThrownBy(() -> verifier.verify(7L, quest, new QuestCompleteRequest("done")))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("EVIDENCE_REQUIRED_SUBMISSION");
  }

  @Test
  void pathPlannerSubmissionMustMatchTheQuestKc() {
    QuestDto quest =
        quest(
            QuestSource.PATH_PLANNER,
            QuestNodeType.SOLVE_PROBLEM,
            QuestStatus.IN_PROGRESS,
            "submission",
            null,
            List.of(),
            null);
    when(submissions.findSubmission(7L, 900L))
        .thenReturn(Optional.of(submission(900L, 7L, 101L, SubmissionStatus.PASSED)));
    when(submissions.findSubmissionKcCode(7L, 900L)).thenReturn(Optional.of("PY43-KC-002"));

    assertThatThrownBy(
            () ->
                verifier.verify(
                    7L, quest, new QuestCompleteRequest("wrong kc", 900L, null, null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("WRONG_KC");
  }

  @Test
  void pathPlannerSubmissionForTheSameKcIsAccepted() {
    QuestDto quest =
        quest(
            QuestSource.PATH_PLANNER,
            QuestNodeType.SOLVE_PROBLEM,
            QuestStatus.IN_PROGRESS,
            "submission",
            null,
            List.of(),
            null);
    when(submissions.findSubmission(7L, 900L))
        .thenReturn(Optional.of(submission(900L, 7L, 101L, SubmissionStatus.PASSED)));
    when(submissions.findSubmissionKcCode(7L, 900L)).thenReturn(Optional.of("PY43-KC-001"));

    assertThatCode(
            () ->
                verifier.verify(
                    7L, quest, new QuestCompleteRequest("same kc", 900L, null, null, null)))
        .doesNotThrowAnyException();
  }

  @Test
  void pathPlannerResourceMustMatchTheQuestKc() {
    QuestDto quest =
        quest(
            QuestSource.PATH_PLANNER,
            QuestNodeType.READ_RESOURCE,
            QuestStatus.IN_PROGRESS,
            "resource",
            null,
            List.of(),
            null);
    when(resources.findById(7L, 55L))
        .thenReturn(Optional.of(resource(55L, 7L, "PY43-KC-002")));

    assertThatThrownBy(
            () -> verifier.verify(7L, quest, new QuestCompleteRequest("wrong kc", null, 55L, null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("WRONG_RESOURCE");
  }

  @Test
  void pathPlannerResourceForTheSameKcIsAcceptedWhenConsumed() {
    QuestDto quest =
        quest(
            QuestSource.PATH_PLANNER,
            QuestNodeType.READ_RESOURCE,
            QuestStatus.IN_PROGRESS,
            "resource",
            null,
            List.of(),
            null);
    when(resources.findById(7L, 55L)).thenReturn(Optional.of(resource(55L, 7L)));
    when(jdbcTemplate.queryForObject(
            contains("learning_event_log"),
            eq(Boolean.class),
            eq(7L),
            eq("55"),
            any(OffsetDateTime.class)))
        .thenReturn(true);

    assertThatCode(
            () -> verifier.verify(7L, quest, new QuestCompleteRequest("same kc", null, 55L, null, null)))
        .doesNotThrowAnyException();
  }

  @Test
  void testComplete_nullRequiredEvidence_legacyBypasses() {
    QuestDto quest =
        quest(QuestNodeType.PROFILE_UPDATE, QuestStatus.OPEN, null, null, List.of(), null);
    when(repository.findById(eq(7L), eq(14L))).thenReturn(Optional.of(quest));
    QuestDto completed =
        quest(
            QuestNodeType.PROFILE_UPDATE,
            QuestStatus.COMPLETED,
            null,
            null,
            List.of(),
            null);
    when(repository.findOwned(eq(7L), eq(14L))).thenReturn(Optional.of(completed));

    QuestDto result = service.complete(7L, 14L, new QuestCompleteRequest("legacy"));

    assertThat(result.status()).isEqualTo(QuestStatus.COMPLETED);
    verify(repository).updateStatus(7L, 14L, QuestStatus.COMPLETED);
  }

  private QuestDto quest(
      QuestNodeType nodeType,
      QuestStatus status,
      String requiredEvidenceType,
      BigDecimal requiredMasteryDelta,
      List<Long> targetProblemIds,
      Long targetResourceId) {
    return quest(
        QuestSource.CLASSROOM,
        nodeType,
        status,
        requiredEvidenceType,
        requiredMasteryDelta,
        targetProblemIds,
        targetResourceId);
  }

  private QuestDto quest(
      QuestSource source,
      QuestNodeType nodeType,
      QuestStatus status,
      String requiredEvidenceType,
      BigDecimal requiredMasteryDelta,
      List<Long> targetProblemIds,
      Long targetResourceId) {
    ObjectNode payload = mapper.createObjectNode();
    JsonNode deepLink = mapper.createObjectNode();
    return new QuestDto(
        99L,
        7L,
        source,
        500L,
        "测试任务",
        "测试描述",
        "PY43-KC-001",
        nodeType,
        payload,
        LocalDate.now(ZoneOffset.UTC),
        9,
        status,
        requiredEvidenceType,
        requiredMasteryDelta,
        targetProblemIds,
        targetResourceId,
        "/training",
        deepLink,
        OffsetDateTime.now(ZoneOffset.UTC),
        OffsetDateTime.now(ZoneOffset.UTC));
  }

  private SubmissionDto submission(Long id, Long userId, Long problemId, SubmissionStatus status) {
    return new SubmissionDto(
        id,
        userId,
        problemId,
        null,
        SubmissionSource.TRAINING,
        "PYTHON_3_11",
        "print(1)",
        OffsetDateTime.now(ZoneOffset.UTC),
        status,
        12,
        null);
  }

  private LearningResourceDto resource(Long id, Long userId) {
    return resource(id, userId, "PY43-KC-001");
  }

  private LearningResourceDto resource(Long id, Long userId, String kcCode) {
    return new LearningResourceDto(
        id,
        userId,
        43L,
        List.of(kcCode),
        null,
        ResourceType.HANDOUT,
        "demo handout",
        ContentFormat.MARKDOWN,
        mapper.createObjectNode(),
        List.of(1L, 2L),
        "summary",
        ResourceStatusFlag.PASSED,
        ResourceStatusFlag.PASSED,
        ResourceFeedbackStatus.NONE,
        null,
        OffsetDateTime.now(ZoneOffset.UTC));
  }
}
