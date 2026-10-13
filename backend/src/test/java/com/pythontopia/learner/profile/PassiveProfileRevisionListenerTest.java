package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

class PassiveProfileRevisionListenerTest {

  private final ProfileDimensionProjectionOutboxService outboxService =
      mock(ProfileDimensionProjectionOutboxService.class);
  private final PassiveProfileRevisionListener listener =
      new PassiveProfileRevisionListener(outboxService);

  @Test
  void practiceSucceededUpdatesPracticalAbility() {
    listener.onPracticeSucceeded(
        new LearningEvent.PracticeSucceeded(7L, "PY43-KC-001", 101L, "hard"));

    ProfileDimensionProjection projection = singleProjection();
    DimensionDelta delta = projection.delta();
    assertThat(projection.userId()).isEqualTo(7L);
    assertThat(projection.dimensionKey()).isEqualTo("practical_ability");
    assertThat(projection.idempotencyKey()).isEqualTo("practice_succeeded:7:101");
    assertThat(delta.scoreDelta()).isEqualTo(0.02);
    assertThat(delta.evidenceIncrement()).isEqualTo(1);
    assertThat(delta.source()).isEqualTo("PRACTICE_PASS");
    assertThat(delta.metadata()).containsEntry("kcCode", "PY43-KC-001");
    assertThat(delta.metadata()).containsEntry("problemId", 101L);
  }

  @Test
  void verifiedBossAttemptUsesItsStableIdempotencyKeyForProfileEvidence() {
    listener.onPracticeSucceeded(
        new LearningEvent.PracticeSucceeded(
            7L, "PY43-KC-001", null, "hard", null, "boss-attempt:attempt-123"));

    assertThat(singleProjection().idempotencyKey()).isEqualTo("boss-attempt:attempt-123");
  }

  @Test
  void practiceFailedUpdatesErrorPatternWithTag() {
    listener.onPracticeFailed(
        new LearningEvent.PracticeFailed(7L, "PY43-KC-002", 102L, "WA", "medium"));

    ProfileDimensionProjection projection = singleProjection();
    DimensionDelta delta = projection.delta();
    assertThat(projection.dimensionKey()).isEqualTo("error_pattern");
    assertThat(projection.idempotencyKey()).isEqualTo("practice_failed:7:102:WA");
    assertThat(delta.scoreDelta()).isEqualTo(0.02);
    assertThat(delta.tagsAdd()).containsExactly("WA");
    assertThat(delta.source()).isEqualTo("PRACTICE_FAIL");
  }

  @Test
  void debugResolvedReducesErrorPatternScore() {
    listener.onDebugResolved(new LearningEvent.DebugResolved(7L, "PY43-KC-003", 103L));

    ProfileDimensionProjection projection = singleProjection();
    DimensionDelta delta = projection.delta();
    assertThat(projection.dimensionKey()).isEqualTo("error_pattern");
    assertThat(delta.scoreDelta()).isEqualTo(-0.02);
    assertThat(delta.source()).isEqualTo("DEBUG_FIX");
  }

  @Test
  void resourceConsumedDoesNotUpdateDimensions() {
    listener.onResourceConsumed(new LearningEvent.ResourceConsumed(7L, "PY43-KC-004", "VIDEO"));

    verifyNoInteractions(outboxService);
  }

  @Test
  void tutorStyleChoiceBecomesMediaPreferenceWithoutEvidenceOrScore() {
    listener.onTutorStyleEngaged(new LearningEvent.TutorStyleEngaged(7L, "visual", "PY43-KC-001"));

    ProfileDimensionProjection projection = singleProjection();
    assertThat(projection.dimensionKey()).isEqualTo("learning_preference");
    assertThat(projection.delta().tagsAdd()).containsExactly("video");
    assertThat(projection.delta().scoreDelta()).isNull();
    assertThat(projection.delta().evidenceIncrement()).isNull();
    assertThat(projection.delta().source()).isEqualTo("INTERVIEW");
  }

  @Test
  void coursewareConsumptionMapsToSupportedTextPreference() {
    listener.onMediaConsumed(
        new LearningEvent.MediaConsumed(7L, "courseware", 2, java.util.List.of()));

    ProfileDimensionProjection projection = singleProjection();
    assertThat(projection.dimensionKey()).isEqualTo("learning_preference");
    assertThat(projection.delta().tagsAdd()).containsExactly("text");
  }

  @Test
  void microQuizResourceCheckPassedUpdatesKnowledgeBaseWithCheckMetadata() {
    listener.onResourceCheckCompleted(
        new LearningEvent.ResourceCheckCompleted(
            7L,
            31L,
            99L,
            "PY43-KC-004",
            "MICRO_QUIZ",
            true,
            new java.math.BigDecimal("0.05"),
            null));

    ProfileDimensionProjection projection = singleProjection();
    DimensionDelta delta = projection.delta();
    assertThat(projection.dimensionKey()).isEqualTo("knowledge_base");
    assertThat(delta.scoreDelta()).isEqualTo(0.015);
    assertThat(delta.evidenceIncrement()).isEqualTo(1);
    assertThat(delta.metadata()).containsEntry("kcCode", "PY43-KC-004");
    assertThat(delta.metadata()).containsEntry("checkId", 31L);
    assertThat(delta.metadata()).containsEntry("resourceId", 99L);
    assertThat(delta.metadata()).containsEntry("evidenceType", "MICRO_QUIZ");
    assertThat(delta.source()).isEqualTo("RESOURCE_FEEDBACK");
  }

  @Test
  void operationResourceCheckPassedUpdatesPracticalAbility() {
    listener.onResourceCheckCompleted(
        new LearningEvent.ResourceCheckCompleted(
            7L,
            32L,
            99L,
            "PY43-KC-004",
            "OPERATION",
            true,
            new java.math.BigDecimal("0.05"),
            null));

    ProfileDimensionProjection projection = singleProjection();
    DimensionDelta delta = projection.delta();
    assertThat(projection.dimensionKey()).isEqualTo("practical_ability");
    assertThat(delta.scoreDelta()).isEqualTo(0.015);
    assertThat(delta.evidenceIncrement()).isEqualTo(1);
    assertThat(delta.source()).isEqualTo("RESOURCE_FEEDBACK");
    assertThat(delta.metadata()).containsEntry("checkId", 32L);
    assertThat(delta.metadata()).containsEntry("evidenceType", "OPERATION");
  }

  @Test
  void operationCheckAndPracticePassForTheSameSubmissionShareOneIdempotencyKey() {
    listener.onPracticeSucceeded(
        new LearningEvent.PracticeSucceeded(7L, "PY43-KC-004", 104L, "medium", 501L));
    listener.onResourceCheckCompleted(
        new LearningEvent.ResourceCheckCompleted(
            7L,
            35L,
            99L,
            "PY43-KC-004",
            "OPERATION",
            true,
            new java.math.BigDecimal("0.05"),
            null,
            501L));

    ArgumentCaptor<ProfileDimensionProjection> captor =
        ArgumentCaptor.forClass(ProfileDimensionProjection.class);
    verify(outboxService, times(2)).enqueue(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(ProfileDimensionProjection::idempotencyKey)
        .containsExactly("submission:7:501", "submission:7:501");
  }

  @Test
  void resourceCheckFailedUpdatesErrorPatternWithRemediationTag() {
    listener.onResourceCheckCompleted(
        new LearningEvent.ResourceCheckCompleted(
            7L,
            33L,
            99L,
            "PY43-KC-004",
            "MICRO_QUIZ",
            false,
            new java.math.BigDecimal("-0.02"),
            "PRACTICE"));

    ProfileDimensionProjection projection = singleProjection();
    DimensionDelta delta = projection.delta();
    assertThat(projection.dimensionKey()).isEqualTo("error_pattern");
    assertThat(delta.scoreDelta()).isEqualTo(0.015);
    assertThat(delta.evidenceIncrement()).isEqualTo(1);
    assertThat(delta.tagsAdd()).containsExactly("resource_check_failed", "remediation_practice");
    assertThat(delta.source()).isEqualTo("PRACTICE_FAIL");
    assertThat(delta.metadata()).containsEntry("checkId", 33L);
    assertThat(delta.metadata()).containsEntry("remediationType", "PRACTICE");
  }

  @Test
  void emitsFourDimensionUpdatesForFourEvents() {
    listener.onPracticeSucceeded(new LearningEvent.PracticeSucceeded(7L, "K1", 1L, "medium"));
    listener.onPracticeFailed(new LearningEvent.PracticeFailed(7L, "K2", 2L, "RE", "medium"));
    listener.onDebugResolved(new LearningEvent.DebugResolved(7L, "K3", 3L));
    listener.onResourceCheckCompleted(
        new LearningEvent.ResourceCheckCompleted(
            7L, 34L, 99L, "K4", "MICRO_QUIZ", true, new java.math.BigDecimal("0.05"), null));

    verify(outboxService, times(4)).enqueue(any(ProfileDimensionProjection.class));
  }

  @Test
  void dataAccessExceptionIsNotSilentlyDropped() {
    doThrow(new DataAccessResourceFailureException("outbox unavailable"))
        .when(outboxService)
        .enqueue(any(ProfileDimensionProjection.class));

    assertThatThrownBy(
            () ->
                listener.onPracticeSucceeded(
                    new LearningEvent.PracticeSucceeded(7L, "K1", 1L, "medium")))
        .isInstanceOf(DataAccessResourceFailureException.class);
  }

  @Test
  void taughtAiSucceededUpdatesPracticalAbility() {
    listener.onTaughtAiSucceeded(new LearningEvent.TaughtAiSucceeded(7L, "PY43-KC-010", 33L));

    ProfileDimensionProjection projection = singleProjection();
    DimensionDelta delta = projection.delta();
    assertThat(projection.dimensionKey()).isEqualTo("practical_ability");
    assertThat(delta.scoreDelta()).isEqualTo(0.03);
    assertThat(delta.evidenceIncrement()).isEqualTo(1);
    assertThat(delta.source()).isEqualTo("PRACTICE_PASS");
    assertThat(delta.metadata()).containsEntry("sessionId", 33L);
    assertThat(delta.metadata()).containsEntry("kcCode", "PY43-KC-010");
  }

  @Test
  void taughtAiFailedUpdatesErrorPattern() {
    listener.onTaughtAiFailed(new LearningEvent.TaughtAiFailed(7L, "PY43-KC-011", 34L));

    ProfileDimensionProjection projection = singleProjection();
    DimensionDelta delta = projection.delta();
    assertThat(projection.dimensionKey()).isEqualTo("error_pattern");
    assertThat(delta.scoreDelta()).isEqualTo(0.015);
    assertThat(delta.tagsAdd()).containsExactly("teach_back_failed");
    assertThat(delta.source()).isEqualTo("PRACTICE_FAIL");
    assertThat(delta.metadata()).containsEntry("sessionId", 34L);
  }

  private ProfileDimensionProjection singleProjection() {
    ArgumentCaptor<ProfileDimensionProjection> captor =
        ArgumentCaptor.forClass(ProfileDimensionProjection.class);
    verify(outboxService).enqueue(captor.capture());
    return captor.getValue();
  }
}
