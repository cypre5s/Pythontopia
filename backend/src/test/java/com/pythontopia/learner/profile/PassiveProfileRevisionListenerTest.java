package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

class PassiveProfileRevisionListenerTest {

  private final PassiveProfileService passiveProfileService = mock(PassiveProfileService.class);
  private final PassiveProfileRevisionListener listener =
      new PassiveProfileRevisionListener(passiveProfileService);

  @Test
  void practiceSucceededUpdatesPracticalAbility() {
    listener.onPracticeSucceeded(
        new LearningEvent.PracticeSucceeded(7L, "PY43-KC-001", 101L, "hard"));

    ArgumentCaptor<DimensionDelta> captor = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(passiveProfileService)
        .applyDimensionUpdate(eq(7L), eq("practical_ability"), captor.capture());
    DimensionDelta delta = captor.getValue();
    assertThat(delta.scoreDelta()).isEqualTo(0.02);
    assertThat(delta.evidenceIncrement()).isEqualTo(1);
    assertThat(delta.source()).isEqualTo("PRACTICE_PASS");
    assertThat(delta.metadata()).containsEntry("kcCode", "PY43-KC-001");
    assertThat(delta.metadata()).containsEntry("problemId", 101L);
  }

  @Test
  void practiceFailedUpdatesErrorPatternWithTag() {
    listener.onPracticeFailed(
        new LearningEvent.PracticeFailed(7L, "PY43-KC-002", 102L, "WA", "medium"));

    ArgumentCaptor<DimensionDelta> captor = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(passiveProfileService)
        .applyDimensionUpdate(eq(7L), eq("error_pattern"), captor.capture());
    DimensionDelta delta = captor.getValue();
    assertThat(delta.scoreDelta()).isEqualTo(0.02);
    assertThat(delta.tagsAdd()).containsExactly("WA");
    assertThat(delta.source()).isEqualTo("PRACTICE_FAIL");
  }

  @Test
  void debugResolvedReducesErrorPatternScore() {
    listener.onDebugResolved(new LearningEvent.DebugResolved(7L, "PY43-KC-003", 103L));

    ArgumentCaptor<DimensionDelta> captor = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(passiveProfileService)
        .applyDimensionUpdate(eq(7L), eq("error_pattern"), captor.capture());
    DimensionDelta delta = captor.getValue();
    assertThat(delta.scoreDelta()).isEqualTo(-0.02);
    assertThat(delta.source()).isEqualTo("DEBUG_FIX");
  }

  @Test
  void resourceConsumedDoesNotUpdateDimensions() {
    listener.onResourceConsumed(
        new LearningEvent.ResourceConsumed(7L, "PY43-KC-004", "VIDEO"));

    verifyNoInteractions(passiveProfileService);
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

    ArgumentCaptor<DimensionDelta> captor = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(passiveProfileService)
        .applyDimensionUpdate(eq(7L), eq("knowledge_base"), captor.capture());
    DimensionDelta delta = captor.getValue();
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

    ArgumentCaptor<DimensionDelta> captor = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(passiveProfileService)
        .applyDimensionUpdate(eq(7L), eq("practical_ability"), captor.capture());
    DimensionDelta delta = captor.getValue();
    assertThat(delta.scoreDelta()).isEqualTo(0.015);
    assertThat(delta.evidenceIncrement()).isEqualTo(1);
    assertThat(delta.source()).isEqualTo("RESOURCE_FEEDBACK");
    assertThat(delta.metadata()).containsEntry("checkId", 32L);
    assertThat(delta.metadata()).containsEntry("evidenceType", "OPERATION");
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

    ArgumentCaptor<DimensionDelta> captor = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(passiveProfileService)
        .applyDimensionUpdate(eq(7L), eq("error_pattern"), captor.capture());
    DimensionDelta delta = captor.getValue();
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
            7L,
            34L,
            99L,
            "K4",
            "MICRO_QUIZ",
            true,
            new java.math.BigDecimal("0.05"),
            null));

    verify(passiveProfileService, times(4))
        .applyDimensionUpdate(eq(7L), anyString(), any(DimensionDelta.class));
  }

  @Test
  void dataAccessExceptionIsSwallowedToAvoidBreakingEventChain() {
    doThrow(new DataAccessResourceFailureException("column dimensions does not exist"))
        .when(passiveProfileService)
        .applyDimensionUpdate(anyLong(), anyString(), any(DimensionDelta.class));

    listener.onPracticeSucceeded(new LearningEvent.PracticeSucceeded(7L, "K1", 1L, "medium"));
    listener.onResourceCheckCompleted(
        new LearningEvent.ResourceCheckCompleted(
            7L,
            35L,
            99L,
            "K2",
            "MICRO_QUIZ",
            false,
            new java.math.BigDecimal("-0.02"),
            "PRACTICE"));

    verify(passiveProfileService, times(2))
        .applyDimensionUpdate(anyLong(), anyString(), any(DimensionDelta.class));
  }

  @Test
  void taughtAiSucceededUpdatesPracticalAbility() {
    listener.onTaughtAiSucceeded(new LearningEvent.TaughtAiSucceeded(7L, "PY43-KC-010", 33L));

    ArgumentCaptor<DimensionDelta> captor = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(passiveProfileService)
        .applyDimensionUpdate(eq(7L), eq("practical_ability"), captor.capture());
    DimensionDelta delta = captor.getValue();
    assertThat(delta.scoreDelta()).isEqualTo(0.03);
    assertThat(delta.evidenceIncrement()).isEqualTo(1);
    assertThat(delta.source()).isEqualTo("PRACTICE_PASS");
    assertThat(delta.metadata()).containsEntry("sessionId", 33L);
    assertThat(delta.metadata()).containsEntry("kcCode", "PY43-KC-010");
  }

  @Test
  void taughtAiFailedUpdatesErrorPattern() {
    listener.onTaughtAiFailed(new LearningEvent.TaughtAiFailed(7L, "PY43-KC-011", 34L));

    ArgumentCaptor<DimensionDelta> captor = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(passiveProfileService)
        .applyDimensionUpdate(eq(7L), eq("error_pattern"), captor.capture());
    DimensionDelta delta = captor.getValue();
    assertThat(delta.scoreDelta()).isEqualTo(0.015);
    assertThat(delta.tagsAdd()).containsExactly("teach_back_failed");
    assertThat(delta.source()).isEqualTo("PRACTICE_FAIL");
    assertThat(delta.metadata()).containsEntry("sessionId", 34L);
  }
}
