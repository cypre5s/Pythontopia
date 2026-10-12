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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.agent.run.ResourceRunRecorder;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.SseRelayExecutor;
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
 * 访谈结果必须镜像进 8 维 {@code dimensions}：8 维消费方（答疑 / NPC / 工坊 / 记忆简报 / 推荐）只读
 * dimensions，只写 learner_profile 旧列等于访谈信号对下游完全不可见。
 */
class InterviewDimensionMirrorTest {

  private final PassiveProfileService passiveProfileService = mock(PassiveProfileService.class);
  private final InterviewDimensionMirror mirror =
      new InterviewDimensionMirror(passiveProfileService);

  @Test
  void mirrorsAllEightDimensionsWithInterviewSource() {
    mirror.mirror(7L, fullDraft());

    Map<String, DimensionDelta> byKey = capturedDeltas();
    assertThat(byKey.keySet())
        .containsExactlyInAnyOrder(
            "professional_background",
            "knowledge_base",
            "learning_goal",
            "cognitive_style",
            "learning_preference",
            "error_pattern",
            "practical_ability",
            "emotion_persistence");
    assertThat(byKey.values()).allMatch(delta -> "INTERVIEW".equals(delta.source()));
  }

  @Test
  void grantsEnoughEvidenceToClearTheProfileBriefConfidenceFloor() {
    mirror.mirror(7L, fullDraft());

    // profile_brief 的门槛是 confidence >= 0.3，confidence = evidence_count / 8。
    // 证据数不够时学生做完 8 维访谈，brief 仍整体返回空串，答疑毫无个性化。
    for (DimensionDelta delta : capturedDeltas().values()) {
      assertThat(delta.evidenceIncrement()).isNotNull();
      assertThat(delta.evidenceIncrement() / 8.0).isGreaterThanOrEqualTo(0.3);
      // 同时不得压过长期被动累积（饱和值 8）。
      assertThat(delta.evidenceIncrement()).isLessThan(8);
    }
  }

  @Test
  void mirrorsTagsWithLatestDominantOrdering() {
    mirror.mirror(7L, fullDraft());
    Map<String, DimensionDelta> byKey = capturedDeltas();

    assertThat(byKey.get("professional_background").tagsAdd()).containsExactly("统计学");
    assertThat(byKey.get("learning_goal").tagsAdd()).containsExactly("exam");
    assertThat(byKey.get("error_pattern").tagsAdd()).containsExactly("off_by_one", "indent");
    // mediaPriority 首位=最偏好，而全仓消费方取末尾 tag 为主导 → 倒序追加。
    assertThat(byKey.get("learning_preference").tagsAdd()).containsExactly("text", "video");
  }

  @Test
  void keepsFreeformNumericSignalsOutOfTags() {
    mirror.mirror(7L, fullDraft());
    Map<String, DimensionDelta> byKey = capturedDeltas();

    // cognitive_style 的 tag 词表是 visual/logical/trial：塞进连续量会被 DialogContextService
    // 归一成 unknown，反而盖掉此前有效的推断（消费方只看末尾 tag）。
    assertThat(byKey.get("cognitive_style").tagsAdd()).isEmpty();
    assertThat(byKey.get("cognitive_style").metadata())
        .containsEntry("field_dependent", 0.3333333333333333)
        .containsEntry("sequential_vs_holistic", 0.25);
    // 自述心情是自由文本，同样只进 metadata。
    assertThat(byKey.get("emotion_persistence").tagsAdd()).isEmpty();
    assertThat(byKey.get("emotion_persistence").metadata()).containsEntry("recent_mood", "焦虑");
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
    when(passiveProfileService.applyDimensionUpdate(eq(7L), eq("knowledge_base"), any()))
        .thenThrow(new IllegalStateException("dimensions column missing"));

    mirror.mirror(7L, fullDraft());

    assertThat(capturedDeltas()).hasSize(8);
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
    assertThat(capturedDeltas()).hasSize(8);
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
            proxiedDraft);

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
