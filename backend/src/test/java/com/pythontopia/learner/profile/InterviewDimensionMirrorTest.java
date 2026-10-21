package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.agent.run.ResourceRunRecorder;
import com.pythontopia.common.config.GraphHttpClientProperties;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.profile.dto.BaseLevelDto;
import com.pythontopia.learner.profile.dto.CognitiveStyleDto;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.dto.EmotionPersistenceDto;
import com.pythontopia.learner.profile.dto.ErrorPatternsDto;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.dto.LearningGoalDto;
import com.pythontopia.learner.profile.dto.LearningPreferenceDto;
import com.pythontopia.learner.profile.dto.PracticeCapabilityDto;
import com.pythontopia.learner.profile.dto.ProfileTurnResponse;
import com.pythontopia.learner.profile.dto.ProfileTurnRequest;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.pythontopia.learner.profile.service.InterviewDimensionMirror;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.learner.profile.service.ProfileDraftService;
import com.pythontopia.learner.profile.service.ProfileService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpEntity;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;

/**
 * 访谈只把自述目标、经验和偏好镜像到 dimensions；不能为能力画像增加证据或镜像 AI 洞察。
 */
class InterviewDimensionMirrorTest {

  private final PassiveProfileService passiveProfileService = mock(PassiveProfileService.class);
  private final InterviewDimensionMirror mirror =
      new InterviewDimensionMirror(passiveProfileService);

  @Test
  void mirrorsOnlySelfReportedDimensionsWithInterviewSource() {
    mirror.mirror(7L, fullDraft());

    Map<String, DimensionDelta> byKey = capturedDeltas();
    assertThat(byKey.keySet())
        .containsExactlyInAnyOrder(
            "professional_background",
            "knowledge_base",
            "learning_goal",
            "learning_preference");
    assertThat(byKey.values()).allMatch(delta -> "INTERVIEW".equals(delta.source()));
  }

  @Test
  void interviewDoesNotCreateAbilityEvidenceOrScores() {
    mirror.mirror(7L, fullDraft());

    for (DimensionDelta delta : capturedDeltas().values()) {
      assertThat(delta.evidenceIncrement()).isNull();
      assertThat(delta.scoreDelta()).isNull();
    }
  }

  @Test
  void mirrorsTagsWithLatestDominantOrdering() {
    mirror.mirror(7L, fullDraft());
    Map<String, DimensionDelta> byKey = capturedDeltas();

    assertThat(byKey.get("professional_background").tagsAdd()).containsExactly("统计学");
    assertThat(byKey.get("learning_goal").tagsAdd()).containsExactly("exam");
    // mediaPriority 首位=最偏好，而全仓消费方取末尾 tag 为主导 → 倒序追加。
    assertThat(byKey.get("learning_preference").tagsAdd()).containsExactly("text", "video");
  }

  @Test
  void omitsCognitiveAndEmotionInterpretationsFromInterviewProjection() {
    mirror.mirror(7L, fullDraft());
    Map<String, DimensionDelta> byKey = capturedDeltas();

    assertThat(byKey).doesNotContainKeys("cognitive_style", "error_pattern", "practical_ability", "emotion_persistence");
    assertThat(byKey.get("learning_preference").tagsAdd()).containsExactly("text", "video");
  }

  @Test
  void skipsDimensionsWithoutAnySignal() {
    LearnerProfileDto sparse =
        new LearnerProfileDto(
            1L, 7L, 43L, null, null, null, null, null, null, null, null, "summary");

    mirror.mirror(7L, sparse);

    verify(passiveProfileService, never()).applyDimensionUpdate(any(), anyString(), any());
  }

  @Test
  void singleDimensionFailureDoesNotAbortTheRest() {
    String sentinel = "private-interview-answer-sentinel";
    when(passiveProfileService.applyDimensionUpdate(eq(7L), eq("knowledge_base"), any()))
        .thenThrow(new IllegalStateException(sentinel));

    Logger logger = (Logger) LoggerFactory.getLogger(InterviewDimensionMirror.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      mirror.mirror(7L, fullDraft());
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(capturedDeltas()).hasSize(4);
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(message -> message.contains(sentinel));
  }

  @Test
  void applyDraftMirrorsDraftIntoDimensions() {
    ProfileRepository repository = mock(ProfileRepository.class);
    when(repository.completeInterview(7L, "session-1")).thenReturn(true);
    ProfileDraftService service = new ProfileDraftService(repository, mirror);

    service.applyDraft(7L, "session-1", fullDraft());

    verify(repository).upsertProfile(eq(7L), any());
    verify(repository).addRevision(eq(7L), eq("INTERVIEW"), any());
    verify(repository).completeInterview(7L, "session-1");
    assertThat(capturedDeltas()).hasSize(4);
  }

  @Test
  void duplicateDraftDoesNotRewriteACompletedInterview() {
    ProfileRepository repository = mock(ProfileRepository.class);
    when(repository.completeInterview(7L, "session-1")).thenReturn(false);
    InterviewDimensionMirror draftMirror = mock(InterviewDimensionMirror.class);
    ProfileDraftService service = new ProfileDraftService(repository, draftMirror);

    service.applyDraft(7L, "session-1", fullDraft());

    verify(repository).completeInterview(7L, "session-1");
    verify(repository, never()).upsertProfile(anyLong(), any());
    verify(repository, never()).addRevision(anyLong(), anyString(), any());
    verify(draftMirror, never()).mirror(anyLong(), any());
  }

  @Test
  void sendTurnClaimsAndWritesDraftInsideATransaction() {
    ProfileRepository repository = mock(ProfileRepository.class);
    when(repository.ownsInterview(7L, "session-1")).thenReturn(true);
    AtomicBoolean claimInsideTransaction = new AtomicBoolean();
    when(repository.completeInterview(7L, "session-1")).thenAnswer(
        invocation -> {
          claimInsideTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
          return claimInsideTransaction.get();
        });
    CoursePackRepository coursePack = mock(CoursePackRepository.class);
    when(coursePack.findKcs(43L, null)).thenReturn(List.of());
    RestTemplate graph = mock(RestTemplate.class);
    when(graph.postForObject(
            anyString(), any(HttpEntity.class), eq(ProfileTurnResponse.class)))
        .thenReturn(new ProfileTurnResponse(null, fullDraft(), true, List.of(), List.of(), null, null));
    ProfileDraftService draftTarget = new ProfileDraftService(repository, mirror);
    var proxyFactory = new ProxyFactory(draftTarget);
    proxyFactory.addAdvice(
        new TransactionInterceptor(
            new TestTransactionManager(), new AnnotationTransactionAttributeSource()));
    ProfileDraftService proxiedDraft = (ProfileDraftService) proxyFactory.getProxy();
    ProfileService service =
        new ProfileService(
            repository,
            coursePack,
            mock(ResourceRunRecorder.class),
            graph,
            new GraphServiceProperties("http://graph", "http://rag", "token"),
            new ObjectMapper(),
            mock(SseRelayExecutor.class),
            proxiedDraft,
            new StudentWriteThrottle());

    service.sendTurn(7L, "session-1", new ProfileTurnRequest("我学过 Python", List.of()));

    assertThat(claimInsideTransaction.get()).isTrue();
    verify(repository).upsertProfile(eq(7L), any());
  }

  private static final class TestTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }

  private Map<String, DimensionDelta> capturedDeltas() {
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<DimensionDelta> deltaCaptor = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(passiveProfileService, org.mockito.Mockito.atLeast(0))
        .applyDimensionUpdate(eq(7L), keyCaptor.capture(), deltaCaptor.capture());
    List<String> keys = new ArrayList<>(keyCaptor.getAllValues());
    List<DimensionDelta> deltas = new ArrayList<>(deltaCaptor.getAllValues());
    Map<String, DimensionDelta> byKey = new java.util.LinkedHashMap<>();
    for (int i = 0; i < keys.size(); i++) {
      byKey.put(keys.get(i), deltas.get(i));
    }
    return byKey;
  }

  private static LearnerProfileDto fullDraft() {
    return new LearnerProfileDto(
        1L,
        7L,
        43L,
        "统计学",
        new BaseLevelDto(1, List.of("r", "sql"), 2),
        new LearningGoalDto("exam", "2026-01-15", List.of("PY43-KC-001")),
        // 高精度小数正是撑爆 VARCHAR(64) 的那类取值。
        new CognitiveStyleDto(0.3333333333333333, 0.25),
        new LearningPreferenceDto(List.of("video", "text"), 45),
        new ErrorPatternsDto(List.of("PY43-KC-002"), List.of("off_by_one", "indent")),
        new PracticeCapabilityDto(2.5, 0.6),
        new EmotionPersistenceDto(0.4, "焦虑"),
        "narrative");
  }
}
