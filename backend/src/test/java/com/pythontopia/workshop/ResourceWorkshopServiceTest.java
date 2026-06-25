package com.pythontopia.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.agent.run.MetaReflectionService;
import com.pythontopia.agent.run.RunStatus;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.mastery.MasterySnapshot;
import com.pythontopia.learner.mastery.MasterySource;
import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import com.pythontopia.learner.profile.dto.BaseLevelDto;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.dto.LearningPreferenceDto;
import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.learner.profile.service.ProfileService;
import com.pythontopia.learner.reflection.LearnerInsightRepository;
import com.pythontopia.workshop.config.WorkshopProperties;
import com.pythontopia.workshop.dto.ContentFormat;
import com.pythontopia.workshop.dto.LearningResourceDto;
import com.pythontopia.workshop.dto.ResourceCheckEvidenceType;
import com.pythontopia.workshop.dto.ResourceCheckResponse;
import com.pythontopia.workshop.dto.ResourceCheckStatus;
import com.pythontopia.workshop.dto.ResourceCheckSubmitRequest;
import com.pythontopia.workshop.dto.ResourceCreateRequest;
import com.pythontopia.workshop.dto.ResourceCreateResponse;
import com.pythontopia.workshop.dto.ResourceFeedbackRequest;
import com.pythontopia.workshop.dto.ResourceFeedbackStatus;
import com.pythontopia.workshop.dto.ResourceStatusFlag;
import com.pythontopia.workshop.dto.ResourceType;
import com.pythontopia.workshop.graph.ResourceProduceRequestDto;
import com.pythontopia.workshop.graph.ResourceProduceResponseDto;
import com.pythontopia.workshop.graph.WorkshopGraphClient;
import com.pythontopia.workshop.repository.LearningResourceRepository;
import com.pythontopia.workshop.repository.ResourceLearningCheckRepository;
import com.pythontopia.workshop.repository.ResourceFeedbackRepository;
import com.pythontopia.workshop.service.ResourceWorkshopService;
import com.pythontopia.workshop.service.WorkshopRunRecorder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ResourceWorkshopServiceTest {

  private WorkshopProperties properties;
  private WorkshopGraphClient graphClient;
  private LearningResourceRepository resourceRepository;
  private ResourceLearningCheckRepository checkRepository;
  private CoursePackRepository coursePackRepository;
  private WorkshopRunRecorder runRecorder;
  private MasteryEvaluator masteryEvaluator;
  private ProfileService profileService;
  private PassiveProfileService passiveProfileService;
  private LearnerInsightRepository insightRepository;
  private MemoryRetrievalService memoryRetrievalService;
  private MetaReflectionService metaReflectionService;
  private ResourceFeedbackRepository resourceFeedbackRepository;
  private ApplicationEventPublisher eventPublisher;
  private ObjectMapper objectMapper;
  private ResourceWorkshopService service;

  @BeforeEach
  void setUp() {
    properties = new WorkshopProperties(3, new BigDecimal("0.15"), List.of());
    graphClient = Mockito.mock(WorkshopGraphClient.class);
    resourceRepository = Mockito.mock(LearningResourceRepository.class);
    checkRepository = Mockito.mock(ResourceLearningCheckRepository.class);
    coursePackRepository = Mockito.mock(CoursePackRepository.class);
    runRecorder = Mockito.mock(WorkshopRunRecorder.class);
    masteryEvaluator = Mockito.mock(MasteryEvaluator.class);
    profileService = Mockito.mock(ProfileService.class);
    passiveProfileService = Mockito.mock(PassiveProfileService.class);
    insightRepository = Mockito.mock(LearnerInsightRepository.class);
    memoryRetrievalService = Mockito.mock(MemoryRetrievalService.class);
    metaReflectionService = Mockito.mock(MetaReflectionService.class);
    resourceFeedbackRepository = Mockito.mock(ResourceFeedbackRepository.class);
    eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
    objectMapper = new ObjectMapper();
    service =
        new ResourceWorkshopService(
            properties,
            graphClient,
            resourceRepository,
            checkRepository,
            coursePackRepository,
            runRecorder,
            masteryEvaluator,
            profileService,
            passiveProfileService,
            insightRepository,
            memoryRetrievalService,
            metaReflectionService,
            resourceFeedbackRepository,
            eventPublisher,
            objectMapper);

    when(coursePackRepository.findKcs(eq(43L), eq(null)))
        .thenReturn(
            List.of(
                new CourseKc(1L, 43L, "PY43-KC-001", "输入输出", 11L, 1, "basic", "summary")));
    when(profileService.findProfile(anyLong())).thenReturn(Optional.empty());
    when(passiveProfileService.getDimensions(anyLong())).thenReturn(List.of());
    when(insightRepository.findLatestText(anyLong())).thenReturn(Optional.empty());
    when(memoryRetrievalService.retrieve(anyLong(), any(), anyInt())).thenReturn(List.of());
    when(resourceRepository.countCreatedAfter(anyLong(), any())).thenReturn(0);
    when(resourceRepository.sumMasteryRewardsToday(anyLong(), anyString(), any()))
        .thenReturn(BigDecimal.ZERO);
    when(resourceRepository.insert(
            anyLong(),
            anyLong(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(7L);
    when(masteryEvaluator.applyEvent(anyLong(), anyString(), any(), any(), anyString()))
        .thenReturn(
            new MasterySnapshot(
                1L,
                1L,
                "PY43-KC-001",
                new BigDecimal("0.05"),
                BigDecimal.ZERO,
                OffsetDateTime.now(),
                OffsetDateTime.now()));
    when(runRecorder.recordInNewTransaction(
            anyLong(),
            anyString(),
            anyString(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(UUID.class),
            any(RunStatus.class)))
        .thenReturn(42L);
  }

  @Test
  void createPersistsResourceWithoutMasteryReward() {
    when(graphClient.produce(any())).thenReturn(successResponse());

    ResourceCreateResponse response =
        service.create(1L, new ResourceCreateRequest(List.of("PY43-KC-001"), ResourceType.HANDOUT, null));

    assertThat(response.runId()).isEqualTo(42L);
    assertThat(response.resourceId()).isEqualTo(7L);
    assertThat(response.status()).isEqualTo("SUCCESS");
    verify(masteryEvaluator, Mockito.never())
        .applyEvent(eq(1L), eq("PY43-KC-001"), eq(MasterySource.RESOURCE_READ), any(), anyString());
    verify(eventPublisher, Mockito.never()).publishEvent(any(LearningEvent.ResourceConsumed.class));
  }

  @Test
  void createRejectsUnknownKc() {
    assertThatThrownBy(
            () -> service.create(1L, new ResourceCreateRequest(List.of("PY43-KC-999"), ResourceType.HANDOUT, null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("未知 KC");
    verifyNoInteractions(graphClient);
  }

  @Test
  void createEnforcesThrottle() {
    when(resourceRepository.countCreatedAfter(anyLong(), any())).thenReturn(3);
    assertThatThrownBy(
            () -> service.create(1L, new ResourceCreateRequest(List.of("PY43-KC-001"), ResourceType.HANDOUT, null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("每小时最多生成");
    verifyNoInteractions(graphClient);
  }

  @Test
  void createFailsWhenGraphReportsFailure() {
    ResourceProduceResponseDto failed = failureResponse();
    when(graphClient.produce(any())).thenReturn(failed);
    assertThatThrownBy(
            () -> service.create(1L, new ResourceCreateRequest(List.of("PY43-KC-001"), ResourceType.HANDOUT, null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("资源生成失败");
    verify(resourceRepository, Mockito.never())
        .insert(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void consumePublishesResourceConsumedWithCappedDelta() {
    when(resourceRepository.findById(1L, 7L)).thenReturn(Optional.of(resource()));

    var response = service.consume(1L, 7L);

    assertThat(response.resourceId()).isEqualTo(7L);
    assertThat(response.kcCodes()).containsExactly("PY43-KC-001");
    assertThat(response.nextAction()).isEqualTo("micro_check");
    ArgumentCaptor<LearningEvent.ResourceConsumed> captor =
        ArgumentCaptor.forClass(LearningEvent.ResourceConsumed.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().resourceId()).isEqualTo(7L);
    assertThat(captor.getValue().delta()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(captor.getValue().resourceKind()).isEqualTo("HANDOUT");
  }

  @Test
  void submitCheckPassedRecordsEvidenceAndPublishesMasteryEvent() {
    when(resourceRepository.findById(1L, 7L)).thenReturn(Optional.of(resource()));
    when(resourceRepository.sumMasteryRewardsToday(anyLong(), anyString(), any()))
        .thenReturn(new BigDecimal("0.02"));
    when(checkRepository.insert(any())).thenReturn(31L);

    ResourceCheckResponse response =
        service.submitCheck(
            1L,
            7L,
            new ResourceCheckSubmitRequest(
                "PY43-KC-001",
                ResourceCheckEvidenceType.MICRO_QUIZ,
                "input 函数用于读取用户在终端输入的内容",
                null));

    assertThat(response.status()).isEqualTo(ResourceCheckStatus.PASSED);
    assertThat(response.masteryDelta()).isEqualByComparingTo("0.05");
    assertThat(response.nextAction()).isEqualTo("continue");
    ArgumentCaptor<ResourceLearningCheckRepository.CheckInsert> insertCaptor =
        ArgumentCaptor.forClass(ResourceLearningCheckRepository.CheckInsert.class);
    verify(checkRepository).insert(insertCaptor.capture());
    assertThat(insertCaptor.getValue().status()).isEqualTo(ResourceCheckStatus.PASSED);
    assertThat(insertCaptor.getValue().masteryDelta()).isEqualByComparingTo("0.05");
    ArgumentCaptor<LearningEvent.ResourceCheckCompleted> eventCaptor =
        ArgumentCaptor.forClass(LearningEvent.ResourceCheckCompleted.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().passed()).isTrue();
    assertThat(eventCaptor.getValue().checkId()).isEqualTo(31L);
    assertThat(eventCaptor.getValue().resourceId()).isEqualTo(7L);
    assertThat(eventCaptor.getValue().delta()).isEqualByComparingTo("0.05");
  }

  @Test
  void submitCheckFailedRecordsEvidenceAndTriggersRemediationWithoutPositiveReward() {
    when(resourceRepository.findById(1L, 7L)).thenReturn(Optional.of(resource()));
    when(checkRepository.insert(any())).thenReturn(32L);

    ResourceCheckResponse response =
        service.submitCheck(
            1L,
            7L,
            new ResourceCheckSubmitRequest(
                "PY43-KC-001",
                ResourceCheckEvidenceType.MICRO_QUIZ,
                "我看完了，还行",
                null));

    assertThat(response.status()).isEqualTo(ResourceCheckStatus.FAILED);
    assertThat(response.masteryDelta()).isLessThan(BigDecimal.ZERO);
    assertThat(response.nextAction()).isEqualTo("remediation");
    assertThat(response.remediationType()).isEqualTo(ResourceType.PRACTICE);
    ArgumentCaptor<ResourceLearningCheckRepository.CheckInsert> insertCaptor =
        ArgumentCaptor.forClass(ResourceLearningCheckRepository.CheckInsert.class);
    verify(checkRepository).insert(insertCaptor.capture());
    assertThat(insertCaptor.getValue().status()).isEqualTo(ResourceCheckStatus.FAILED);
    assertThat(insertCaptor.getValue().remediationType()).isEqualTo(ResourceType.PRACTICE);
    ArgumentCaptor<LearningEvent.ResourceCheckCompleted> eventCaptor =
        ArgumentCaptor.forClass(LearningEvent.ResourceCheckCompleted.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().passed()).isFalse();
    assertThat(eventCaptor.getValue().checkId()).isEqualTo(32L);
    assertThat(eventCaptor.getValue().resourceId()).isEqualTo(7L);
    assertThat(eventCaptor.getValue().delta()).isLessThan(BigDecimal.ZERO);
  }

  @Test
  void submitCheckRejectsKcOutsideResource() {
    when(resourceRepository.findById(1L, 7L)).thenReturn(Optional.of(resource()));

    assertThatThrownBy(
            () ->
                service.submitCheck(
                    1L,
                    7L,
                    new ResourceCheckSubmitRequest(
                        "PY43-KC-999",
                        ResourceCheckEvidenceType.MICRO_QUIZ,
                        "input 用于读取输入",
                        null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不属于该资源");

    verifyNoInteractions(checkRepository);
  }

  @Test
  void submitOperationEvidencePassesWhenCodeSnippetIsProvided() {
    when(resourceRepository.findById(1L, 7L)).thenReturn(Optional.of(resource()));
    when(resourceRepository.sumMasteryRewardsToday(anyLong(), anyString(), any()))
        .thenReturn(BigDecimal.ZERO);
    when(checkRepository.insert(any())).thenReturn(33L);

    var evidence = objectMapper.createObjectNode();
    evidence.put("code", "name = input('name: ')\nprint(name)");

    ResourceCheckResponse response =
        service.submitCheck(
            1L,
            7L,
            new ResourceCheckSubmitRequest(
                "PY43-KC-001",
                ResourceCheckEvidenceType.OPERATION,
                "我运行了 input 并打印结果",
                evidence));

    assertThat(response.status()).isEqualTo(ResourceCheckStatus.PASSED);
    assertThat(response.masteryDelta()).isPositive();
  }

  @Test
  void checkRewardIsCappedByDailyPositiveLimit() {
    when(resourceRepository.findById(1L, 7L)).thenReturn(Optional.of(resource()));
    when(resourceRepository.sumMasteryRewardsToday(anyLong(), anyString(), any()))
        .thenReturn(new BigDecimal("0.14"));
    when(checkRepository.insert(any())).thenReturn(34L);

    ResourceCheckResponse response =
        service.submitCheck(
            1L,
            7L,
            new ResourceCheckSubmitRequest(
                "PY43-KC-001",
                ResourceCheckEvidenceType.MICRO_QUIZ,
                "input 函数用于读取用户输入",
                null));

    assertThat(response.status()).isEqualTo(ResourceCheckStatus.PASSED);
    assertThat(response.masteryDelta()).isEqualByComparingTo("0.01");
    ArgumentCaptor<LearningEvent.ResourceCheckCompleted> eventCaptor =
        ArgumentCaptor.forClass(LearningEvent.ResourceCheckCompleted.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().checkId()).isEqualTo(34L);
    assertThat(eventCaptor.getValue().delta()).isEqualByComparingTo("0.01");
  }

  @Test
  void createAllowsAllowlistedUsersToBypassThrottle() {
    properties = new WorkshopProperties(3, new BigDecimal("0.15"), List.of(99L));
    service =
        new ResourceWorkshopService(
            properties,
            graphClient,
            resourceRepository,
            checkRepository,
            coursePackRepository,
            runRecorder,
            masteryEvaluator,
            profileService,
            passiveProfileService,
            insightRepository,
            memoryRetrievalService,
            metaReflectionService,
            resourceFeedbackRepository,
            eventPublisher,
            objectMapper);
    when(graphClient.produce(any())).thenReturn(successResponse());
    when(resourceRepository.countCreatedAfter(anyLong(), any())).thenReturn(5);
    when(coursePackRepository.findKcs(eq(43L), eq(null)))
        .thenReturn(
            List.of(
                new CourseKc(1L, 43L, "PY43-KC-001", "输入输出", 11L, 1, "basic", "summary")));

    assertThat(service.create(99L, new ResourceCreateRequest(List.of("PY43-KC-001"), ResourceType.HANDOUT, null)).resourceId())
        .isEqualTo(7L);
  }

  @Test
  void createNormalizesProfileMajorForGraphSchema() {
    when(graphClient.produce(any())).thenReturn(successResponse());
    when(profileService.findProfile(1L))
        .thenReturn(
            Optional.of(
                new LearnerProfileDto(
                    10L,
                    1L,
                    43L,
                    "演示学生",
                    new BaseLevelDto(null, List.of("Python"), 4),
                    null,
                    null,
                    new LearningPreferenceDto(List.of("HANDOUT"), 30),
                    null,
                    null,
                    null,
                    "demo")));

    service.create(1L, new ResourceCreateRequest(List.of("PY43-KC-001"), ResourceType.HANDOUT, null));

    ArgumentCaptor<ResourceProduceRequestDto> captor =
        ArgumentCaptor.forClass(ResourceProduceRequestDto.class);
    verify(graphClient).produce(captor.capture());
    assertThat(captor.getValue().profileSnapshot().major()).isEqualTo("OTHER");
    assertThat(captor.getValue().profileSnapshot().pythonSelfRatedLevel()).isEqualTo(4);
    assertThat(captor.getValue().profileSnapshot().mediaPriority()).containsExactly("HANDOUT");
  }

  @Test
  void createPassesCognitiveStyleAndErrorTagsFromDimensions() {
    when(graphClient.produce(any())).thenReturn(successResponse());
    when(passiveProfileService.getDimensions(1L))
        .thenReturn(
            List.of(
                new ProfileDimensionDto(
                    "cognitive_style", "认知风格", 0.6, 0.5, 4, false, List.of("visual")),
                new ProfileDimensionDto(
                    "error_pattern", "易错模式", 0.5, 0.5, 4, false, List.of("缩进", "冒号"))));

    service.create(1L, new ResourceCreateRequest(List.of("PY43-KC-001"), ResourceType.HANDOUT, null));

    ArgumentCaptor<ResourceProduceRequestDto> captor =
        ArgumentCaptor.forClass(ResourceProduceRequestDto.class);
    verify(graphClient).produce(captor.capture());
    assertThat(captor.getValue().profileSnapshot().cognitiveStyle()).isEqualTo("visual");
    assertThat(captor.getValue().profileSnapshot().errorPatterns()).containsExactly("缩进", "冒号");
  }

  @Test
  void createFoldsRecentInsightAndMemoryHighlightsIntoSnapshot() {
    when(graphClient.produce(any())).thenReturn(successResponse());
    when(insightRepository.findLatestText(1L)).thenReturn(Optional.of("可变对象别名反复出错"));
    when(memoryRetrievalService.retrieve(eq(1L), eq("PY43-KC-001"), anyInt()))
        .thenReturn(
            List.of(
                new MemoryItemDto(
                    9L,
                    "practice_failed",
                    "练习失败：PY43-KC-001（WA）",
                    List.of("PY43-KC-001"),
                    "2026-06-08T00:00:00Z",
                    4,
                    1.0,
                    0.8,
                    1.0,
                    0.9)));

    service.create(1L, new ResourceCreateRequest(List.of("PY43-KC-001"), ResourceType.HANDOUT, null));

    ArgumentCaptor<ResourceProduceRequestDto> captor =
        ArgumentCaptor.forClass(ResourceProduceRequestDto.class);
    verify(graphClient).produce(captor.capture());
    assertThat(captor.getValue().profileSnapshot().recentInsight()).isEqualTo("可变对象别名反复出错");
    assertThat(captor.getValue().profileSnapshot().memoryHighlights())
        .containsExactly("练习失败：PY43-KC-001（WA）");
    // 记忆检索须以首个 KC 作为聚焦点。
    verify(memoryRetrievalService).retrieve(eq(1L), eq("PY43-KC-001"), anyInt());
  }

  @Test
  void createStreamPersistsAndReturnsCreateResponsePayload() throws Exception {
    service.createStream(
        1L, new ResourceCreateRequest(List.of("PY43-KC-001"), ResourceType.HANDOUT, null));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Function<String, String>> resultHandlerCaptor =
        ArgumentCaptor.forClass(Function.class);
    verify(graphClient).produceStream(any(), any(SseEmitter.class), resultHandlerCaptor.capture());

    String outboundJson =
        resultHandlerCaptor.getValue().apply(objectMapper.writeValueAsString(successResponse()));
    ResourceCreateResponse response = objectMapper.readValue(outboundJson, ResourceCreateResponse.class);

    assertThat(response.runId()).isEqualTo(42L);
    assertThat(response.resourceId()).isEqualTo(7L);
    assertThat(response.status()).isEqualTo("SUCCESS");
    verify(resourceRepository, times(1))
        .insert(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(masteryEvaluator, Mockito.never())
        .applyEvent(eq(1L), eq("PY43-KC-001"), eq(MasterySource.RESOURCE_READ), any(), anyString());
  }

  @Test
  void targetDifficultyEasierForLowMastery() {
    when(graphClient.produce(any())).thenReturn(successResponse());
    when(masteryEvaluator.snapshot(1L, "PY43-KC-001"))
        .thenReturn(
            Optional.of(
                new MasterySnapshot(
                    1L, 1L, "PY43-KC-001", new BigDecimal("0.20"), new BigDecimal("0.80"),
                    OffsetDateTime.now(), OffsetDateTime.now())));

    service.create(1L, new ResourceCreateRequest(List.of("PY43-KC-001"), ResourceType.HANDOUT, null));

    ArgumentCaptor<ResourceProduceRequestDto> captor =
        ArgumentCaptor.forClass(ResourceProduceRequestDto.class);
    verify(graphClient).produce(captor.capture());
    assertThat(captor.getValue().targetDifficulty()).isEqualTo("basic");
  }

  @Test
  void targetDifficultyHarderForHighMasteryHighConfidence() {
    when(graphClient.produce(any())).thenReturn(successResponse());
    when(masteryEvaluator.snapshot(1L, "PY43-KC-001"))
        .thenReturn(
            Optional.of(
                new MasterySnapshot(
                    1L, 1L, "PY43-KC-001", new BigDecimal("0.85"), new BigDecimal("0.80"),
                    OffsetDateTime.now(), OffsetDateTime.now())));

    service.create(1L, new ResourceCreateRequest(List.of("PY43-KC-001"), ResourceType.HANDOUT, null));

    ArgumentCaptor<ResourceProduceRequestDto> captor =
        ArgumentCaptor.forClass(ResourceProduceRequestDto.class);
    verify(graphClient).produce(captor.capture());
    assertThat(captor.getValue().targetDifficulty()).isEqualTo("hard");
  }

  @Test
  void targetDifficultyLowConfidenceEasesBand() {
    // 临界掌握(0.5)但低置信(0.2)：信号不足 → 由 medium 降一档为 basic（先建立信心）。
    when(graphClient.produce(any())).thenReturn(successResponse());
    when(masteryEvaluator.snapshot(1L, "PY43-KC-001"))
        .thenReturn(
            Optional.of(
                new MasterySnapshot(
                    1L, 1L, "PY43-KC-001", new BigDecimal("0.50"), new BigDecimal("0.20"),
                    OffsetDateTime.now(), OffsetDateTime.now())));

    service.create(1L, new ResourceCreateRequest(List.of("PY43-KC-001"), ResourceType.HANDOUT, null));

    ArgumentCaptor<ResourceProduceRequestDto> captor =
        ArgumentCaptor.forClass(ResourceProduceRequestDto.class);
    verify(graphClient).produce(captor.capture());
    assertThat(captor.getValue().targetDifficulty()).isEqualTo("basic");
  }

  @Test
  void injectsAgentStrategyDirectiveIntoGraphRequest() {
    when(graphClient.produce(any())).thenReturn(successResponse());
    when(metaReflectionService.latestDirective("ResourceDesignerAgent.handout"))
        .thenReturn("complexity=basic; example_density=high");

    service.create(1L, new ResourceCreateRequest(List.of("PY43-KC-001"), ResourceType.HANDOUT, null));

    ArgumentCaptor<ResourceProduceRequestDto> captor =
        ArgumentCaptor.forClass(ResourceProduceRequestDto.class);
    verify(graphClient).produce(captor.capture());
    assertThat(captor.getValue().agentStrategyDirective())
        .isEqualTo("complexity=basic; example_density=high");
  }

  @Test
  void submitFeedbackLikedWritesResourceFeedbackForGeneratingAgent() {
    when(resourceRepository.findById(eq(1L), eq(7L))).thenReturn(Optional.of(feedbackResourceDto()));
    when(resourceFeedbackRepository.findAgentForResource(7L)).thenReturn("PracticeAgent");

    service.submitFeedback(
        1L, 7L, new ResourceFeedbackRequest(ResourceFeedbackStatus.LIKED, "好用"));

    verify(resourceFeedbackRepository).insert(eq(1L), eq(7L), eq("PracticeAgent"), eq(5), eq(true));
  }

  @Test
  void submitFeedbackNoneDoesNotWriteResourceFeedback() {
    when(resourceRepository.findById(eq(1L), eq(7L))).thenReturn(Optional.of(feedbackResourceDto()));

    service.submitFeedback(1L, 7L, new ResourceFeedbackRequest(ResourceFeedbackStatus.NONE, null));

    verify(resourceFeedbackRepository, Mockito.never())
        .insert(anyLong(), anyLong(), anyString(), any(), anyBoolean());
  }

  private LearningResourceDto feedbackResourceDto() {
    return new LearningResourceDto(
        7L,
        1L,
        43L,
        List.of("PY43-KC-001"),
        11L,
        ResourceType.HANDOUT,
        "讲义",
        ContentFormat.MARKDOWN,
        objectMapper.createObjectNode(),
        List.of(101L),
        "input 函数用于读取用户输入",
        ResourceStatusFlag.PASSED,
        ResourceStatusFlag.PASSED,
        ResourceFeedbackStatus.NONE,
        null,
        OffsetDateTime.now());
  }

  private LearningResourceDto resource() {
    return feedbackResourceDto();
  }

  private ResourceProduceResponseDto successResponse() {
    JsonNode handout = objectMapper.createObjectNode()
        .set("sections", objectMapper.createArrayNode());
    ResourceProduceResponseDto.ResourceDraftDto draft =
        new ResourceProduceResponseDto.ResourceDraftDto(
            "讲义", "ok", "MARKDOWN", handout, null, null, null, null, null);
    return new ResourceProduceResponseDto(
        draft,
        "HANDOUT",
        List.of(101L),
        new ResourceProduceResponseDto.GroundingResultDto(true, List.of(), false, false),
        new ResourceProduceResponseDto.SafetyResultDto(true, List.of()),
        List.of(),
        "SUCCESS");
  }

  private ResourceProduceResponseDto failureResponse() {
    JsonNode handout = objectMapper.createObjectNode()
        .set("sections", objectMapper.createArrayNode());
    ResourceProduceResponseDto.ResourceDraftDto draft =
        new ResourceProduceResponseDto.ResourceDraftDto(
            "讲义", "ok", "MARKDOWN", handout, null, null, null, null, null);
    return new ResourceProduceResponseDto(
        draft,
        "HANDOUT",
        List.of(),
        new ResourceProduceResponseDto.GroundingResultDto(false, List.of("SAFETY_FAILED"), false, false),
        new ResourceProduceResponseDto.SafetyResultDto(false, List.of("ANSWER_LEAK:answer_key")),
        List.of(),
        "FAILED");
  }
}
