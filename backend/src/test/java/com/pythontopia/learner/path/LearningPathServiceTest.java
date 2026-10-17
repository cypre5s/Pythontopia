package com.pythontopia.learner.path;

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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.classroom.quest.repository.ClassroomQuestRepository;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.path.dto.LearningPathDto;
import com.pythontopia.learner.path.dto.LearningPathStatus;
import com.pythontopia.learner.path.dto.PlanPathResponse;
import com.pythontopia.learner.path.graph.PathPlannerGraphClient;
import com.pythontopia.learner.path.graph.PathPlannerGraphRequest;
import com.pythontopia.learner.path.graph.PathPlannerGraphResponse;
import com.pythontopia.learner.path.repository.LearningPathRepository;
import com.pythontopia.learner.path.service.LearningPathService;
import com.pythontopia.learner.profile.dto.BaseLevelDto;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.service.ProfileService;
import com.pythontopia.learner.quest.dto.QuestStatus;
import com.pythontopia.learner.quest.repository.QuestRepository;
import com.pythontopia.learner.quest.repository.QuestRepository.QuestInsert;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LearningPathServiceTest {

  private final LearningPathRepository pathRepository = mock(LearningPathRepository.class);
  private final QuestRepository questRepository = mock(QuestRepository.class);
  private final ClassroomQuestRepository classroomQuestRepository =
      mock(ClassroomQuestRepository.class);
  private final CoursePackRepository coursePackRepository = mock(CoursePackRepository.class);
  private final ProfileService profileService = mock(ProfileService.class);
  private final MasteryEvaluator masteryEvaluator = mock(MasteryEvaluator.class);
  private final PathPlannerGraphClient graphClient = mock(PathPlannerGraphClient.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final com.pythontopia.submission.repository.PracticeSubmissionRepository
      submissionRepository =
          mock(com.pythontopia.submission.repository.PracticeSubmissionRepository.class);
  private final com.pythontopia.workshop.repository.LearningResourceRepository
      learningResourceRepository =
          mock(com.pythontopia.workshop.repository.LearningResourceRepository.class);
  private final com.pythontopia.learner.reflection.LearnerInsightRepository insightRepository =
      mock(com.pythontopia.learner.reflection.LearnerInsightRepository.class);

  private final LearningPathService service =
      new LearningPathService(
          pathRepository,
          questRepository,
          classroomQuestRepository,
          coursePackRepository,
          profileService,
          masteryEvaluator,
          graphClient,
          objectMapper,
          submissionRepository,
          learningResourceRepository,
          insightRepository);

  @Test
  void planRequiresProfile() {
    when(profileService.findProfile(7L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.plan(7L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("PROFILE_REQUIRED");
  }

  @Test
  void planArchivesPreviousActivePathsAndCreatesQuests() {
    LearnerProfileDto profile = profile();
    when(profileService.findProfile(7L)).thenReturn(Optional.of(profile));
    when(coursePackRepository.findKcs(eq(43L), eq(null)))
        .thenReturn(
            List.of(
                new CourseKc(1L, 43L, "PY43-KC-001", "变量", 1L, 1, "basic", "summary"),
                new CourseKc(2L, 43L, "PY43-KC-002", "字符串", 1L, 2, "basic", "summary")));
    when(masteryEvaluator.snapshots(7L)).thenReturn(List.of());
    when(classroomQuestRepository.listActiveForStudent(eq(7L), any())).thenReturn(List.of());
    when(graphClient.plan(any(PathPlannerGraphRequest.class)))
        .thenReturn(graphResponse());
    when(pathRepository.archiveActivePaths(7L, 43L)).thenReturn(List.of(10L, 11L));
    when(pathRepository.nextVersion(7L, 43L)).thenReturn(3);
    when(pathRepository.insertPath(eq(7L), eq(43L), eq(3), any(), any(), any()))
        .thenReturn(50L);
    when(pathRepository.insertNode(eq(50L), any())).thenReturn(500L).thenReturn(501L);
    when(pathRepository.findById(7L, 50L)).thenReturn(Optional.of(samplePath(50L, 3)));

    PlanPathResponse response = service.plan(7L);

    assertThat(response.path().id()).isEqualTo(50L);
    assertThat(response.archivedPathIds()).containsExactly(10L, 11L);
    assertThat(response.generatedQuestCount()).isEqualTo(2);
    verify(questRepository).archiveQuestsForPath(7L, 10L, QuestStatus.EXPIRED);
    verify(questRepository).archiveQuestsForPath(7L, 11L, QuestStatus.EXPIRED);
    verify(questRepository, times(1)).archiveSystemDefaults(7L);
    org.mockito.ArgumentCaptor<QuestInsert> quests =
        org.mockito.ArgumentCaptor.forClass(QuestInsert.class);
    verify(questRepository, times(2)).insert(quests.capture());
    assertThat(quests.getAllValues())
        .extracting(QuestInsert::requiredEvidenceType)
        .containsExactly("resource", "submission");
    assertThat(quests.getAllValues().getFirst().deepLinkParams().toString())
        .contains("\"kc\":\"PY43-KC-001\"")
        .doesNotContain("kcCode");
    assertThat(quests.getAllValues().get(1).targetProblemIds()).containsExactly(7L);
  }

  @Test
  void legacyPlantNodeCreatesMainlineQuest() {
    when(profileService.findProfile(7L)).thenReturn(Optional.of(profile()));
    when(coursePackRepository.findKcs(eq(43L), eq(null)))
        .thenReturn(List.of(new CourseKc(1L, 43L, "PY43-KC-001", "变量", 1L, 1, "basic", "summary")));
    when(masteryEvaluator.snapshots(7L)).thenReturn(List.of());
    when(classroomQuestRepository.listActiveForStudent(eq(7L), any())).thenReturn(List.of());
    when(graphClient.plan(any(PathPlannerGraphRequest.class)))
        .thenReturn(
            new PathPlannerGraphResponse(
                7L,
                43L,
                "legacy path",
                List.of(
                    new PathPlannerGraphResponse.NodeDraft(
                        1, 1, "PY43-KC-001", "PLANT_KC", "unlock", "legacy", null, null, null)),
                new PathPlannerGraphResponse.SafetyResult(true, List.of()),
                new PathPlannerGraphResponse.GroundingResult(true, List.of(), List.of()),
                "signature",
                "SUCCESS"));
    when(pathRepository.archiveActivePaths(7L, 43L)).thenReturn(List.of());
    when(pathRepository.nextVersion(7L, 43L)).thenReturn(1);
    when(pathRepository.insertPath(eq(7L), eq(43L), eq(1), any(), any(), any())).thenReturn(50L);
    when(pathRepository.insertNode(eq(50L), any())).thenReturn(500L);
    when(pathRepository.findById(7L, 50L)).thenReturn(Optional.of(samplePath(50L, 1)));

    service.plan(7L);

    org.mockito.ArgumentCaptor<QuestInsert> captor =
        org.mockito.ArgumentCaptor.forClass(QuestInsert.class);
    verify(questRepository).insert(captor.capture());
    assertThat(captor.getValue().title()).isEqualTo("前往主线解锁 KC PY43-KC-001");
    assertThat(captor.getValue().requiredEvidenceType()).isEqualTo("kc_mastery");
    assertThat(captor.getValue().deepLinkRoute()).isEqualTo("/mainline-map");
    assertThat(captor.getValue().deepLinkParams().toString()).doesNotContain("focus");
  }

  @Test
  void planDraftRequiresProfileSameAsPlan() {
    when(profileService.findProfile(9L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.planDraft(9L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("PROFILE_REQUIRED");
    verify(pathRepository, never()).insertDraftPath(anyLong(), anyLong(), anyInt(), any(), any(), any());
  }

  @Test
  void planFailsWhenGraphReturnsFailedStatus() {
    when(profileService.findProfile(7L)).thenReturn(Optional.of(profile()));
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of());
    when(masteryEvaluator.snapshots(7L)).thenReturn(List.of());
    when(classroomQuestRepository.listActiveForStudent(eq(7L), any())).thenReturn(List.of());
    when(graphClient.plan(any(PathPlannerGraphRequest.class)))
        .thenReturn(
            new PathPlannerGraphResponse(
                7L,
                43L,
                "failed",
                List.of(),
                new PathPlannerGraphResponse.SafetyResult(true, List.of()),
                new PathPlannerGraphResponse.GroundingResult(false, List.of("PROFILE_REQUIRED"), List.of()),
                "sig",
                "FAILED"));
    assertThatThrownBy(() -> service.plan(7L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("PATH_PLANNING_FAILED");
    verify(pathRepository, never()).insertPath(anyLong(), anyLong(), anyInt(), any(), any(), any());
  }

  @Test
  void planForwardsErrorPatternKcsFilteredToKnownKcs() {
    when(profileService.findProfile(7L)).thenReturn(Optional.of(profile()));
    when(coursePackRepository.findKcs(eq(43L), eq(null)))
        .thenReturn(
            List.of(
                new CourseKc(1L, 43L, "PY43-KC-001", "变量", 1L, 1, "basic", "summary"),
                new CourseKc(2L, 43L, "PY43-KC-002", "字符串", 1L, 2, "basic", "summary")));
    when(masteryEvaluator.snapshots(7L)).thenReturn(List.of());
    when(classroomQuestRepository.listActiveForStudent(eq(7L), any())).thenReturn(List.of());
    when(insightRepository.findByUser(eq(7L), anyInt()))
        .thenReturn(
            List.of(
                new com.pythontopia.learner.reflection.dto.LearnerInsightDto(
                    1L,
                    "近期在 PY43-KC-002 上反复失败",
                    "error_pattern",
                    List.of("PY43-KC-002", "WA"),
                    List.of(10L, 11L),
                    0.4,
                    "RULE",
                    "2026-06-08T00:00:00Z"),
                new com.pythontopia.learner.reflection.dto.LearnerInsightDto(
                    2L,
                    "认知风格偏视觉",
                    "cognitive_style",
                    List.of("PY43-KC-001"),
                    List.of(12L),
                    0.5,
                    "GRAPH",
                    "2026-06-08T00:00:00Z")));
    when(graphClient.plan(any(PathPlannerGraphRequest.class))).thenReturn(graphResponse());
    when(pathRepository.archiveActivePaths(7L, 43L)).thenReturn(List.of());
    when(pathRepository.nextVersion(7L, 43L)).thenReturn(1);
    when(pathRepository.insertPath(eq(7L), eq(43L), eq(1), any(), any(), any())).thenReturn(50L);
    when(pathRepository.insertNode(eq(50L), any())).thenReturn(500L).thenReturn(501L);
    when(pathRepository.findById(7L, 50L)).thenReturn(Optional.of(samplePath(50L, 1)));

    service.plan(7L);

    org.mockito.ArgumentCaptor<PathPlannerGraphRequest> captor =
        org.mockito.ArgumentCaptor.forClass(PathPlannerGraphRequest.class);
    verify(graphClient).plan(captor.capture());
    // 仅保留命中已知 KC 的 error_pattern tag：WA 被滤掉，cognitive_style 维度不计入。
    assertThat(captor.getValue().errorPatternKcs()).containsExactly("PY43-KC-002");
    assertThat(captor.getValue().profile().daysSinceLastProfileUpdate()).isEqualTo(999);
  }

  @Test
  void insightReadFailureDoesNotLogExceptionDetails() {
    String sentinel = "learner-insight-private-sentinel";
    when(profileService.findProfile(7L)).thenReturn(Optional.of(profile()));
    when(coursePackRepository.findKcs(eq(43L), eq(null)))
        .thenReturn(List.of(new CourseKc(1L, 43L, "PY43-KC-001", "变量", 1L, 1, "basic", "summary")));
    when(coursePackRepository.findKcPrerequisites(43L)).thenReturn(Map.of());
    when(masteryEvaluator.snapshots(7L)).thenReturn(List.of());
    when(classroomQuestRepository.listActiveForStudent(eq(7L), any())).thenReturn(List.of());
    when(insightRepository.findByUser(eq(7L), anyInt())).thenThrow(new RuntimeException(sentinel));
    when(graphClient.plan(any(PathPlannerGraphRequest.class)))
        .thenReturn(
            new PathPlannerGraphResponse(
                7L,
                43L,
                "failed",
                List.of(),
                new PathPlannerGraphResponse.SafetyResult(true, List.of()),
                new PathPlannerGraphResponse.GroundingResult(false, List.of("PROFILE_REQUIRED"), List.of()),
                "sig",
                "FAILED"));

    Logger logger = (Logger) LoggerFactory.getLogger(LearningPathService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThatThrownBy(() -> service.plan(7L)).isInstanceOf(BusinessException.class);
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(message -> message.contains(sentinel));
  }

  @Test
  void planNormalizesFreeTextMajorToGraphEnum() {
    LearnerProfileDto freeTextMajor =
        new LearnerProfileDto(
            100L,
            7L,
            43L,
            "计算机科学（大一）",
            new BaseLevelDto(1, List.of("C"), 3),
            null,
            null,
            null,
            null,
            null,
            null,
            "测试画像");
    when(profileService.findProfile(7L)).thenReturn(Optional.of(freeTextMajor));
    when(coursePackRepository.findKcs(eq(43L), eq(null)))
        .thenReturn(
            List.of(new CourseKc(1L, 43L, "PY43-KC-001", "变量", 1L, 1, "basic", "summary")));
    when(masteryEvaluator.snapshots(7L)).thenReturn(List.of());
    when(classroomQuestRepository.listActiveForStudent(eq(7L), any())).thenReturn(List.of());
    when(graphClient.plan(any(PathPlannerGraphRequest.class))).thenReturn(graphResponse());
    when(pathRepository.archiveActivePaths(7L, 43L)).thenReturn(List.of());
    when(pathRepository.nextVersion(7L, 43L)).thenReturn(1);
    when(pathRepository.insertPath(eq(7L), eq(43L), eq(1), any(), any(), any())).thenReturn(50L);
    when(pathRepository.insertNode(eq(50L), any())).thenReturn(500L).thenReturn(501L);
    when(pathRepository.findById(7L, 50L)).thenReturn(Optional.of(samplePath(50L, 1)));

    service.plan(7L);

    org.mockito.ArgumentCaptor<PathPlannerGraphRequest> captor =
        org.mockito.ArgumentCaptor.forClass(PathPlannerGraphRequest.class);
    verify(graphClient).plan(captor.capture());
    // 自由文本中文专业名归一到 graph 接受的枚举，避免 422 → PATH_PLANNER_GRAPH_UNAVAILABLE。
    assertThat(captor.getValue().profile().major()).isEqualTo("CS");
  }

  private PathPlannerGraphResponse graphResponse() {
    return new PathPlannerGraphResponse(
        7L,
        43L,
        "本周聚焦 PY43-KC-001, PY43-KC-002",
        List.of(
            new PathPlannerGraphResponse.NodeDraft(
                1,
                1,
                "PY43-KC-001",
                "READ_RESOURCE",
                "先补讲义",
                "evidence",
                "HANDOUT",
                null,
                null),
            new PathPlannerGraphResponse.NodeDraft(
                1,
                2,
                "PY43-KC-001",
                "SOLVE_PROBLEM",
                "做 1 道题",
                "evidence",
                null,
                7L,
                null)),
        new PathPlannerGraphResponse.SafetyResult(true, List.of()),
        new PathPlannerGraphResponse.GroundingResult(true, List.of(), List.of()),
        "signature",
        "SUCCESS");
  }

  private LearnerProfileDto profile() {
    return new LearnerProfileDto(
        100L,
        7L,
        43L,
        "CS",
        new BaseLevelDto(1, List.of("C"), 3),
        null,
        null,
        null,
        null,
        null,
        null,
        "测试画像");
  }

  private LearningPathDto samplePath(Long id, int version) {
    return new LearningPathDto(
        id,
        7L,
        43L,
        version,
        LearningPathStatus.ACTIVE,
        "demo",
        OffsetDateTime.now(ZoneOffset.UTC),
        OffsetDateTime.now(ZoneOffset.UTC).plusDays(7),
        null,
        List.of());
  }
}
