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
        new LearningEvent.PracticeSucceeded(7L, "PY43-KC-001", 101L));

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
        new LearningEvent.PracticeFailed(7L, "PY43-KC-002", 102L, "WA"));

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
  void resourceConsumedIncrementsKnowledgeBase() {
    listener.onResourceConsumed(
        new LearningEvent.ResourceConsumed(7L, "PY43-KC-004", "VIDEO"));

    ArgumentCaptor<DimensionDelta> captor = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(passiveProfileService)
        .applyDimensionUpdate(eq(7L), eq("knowledge_base"), captor.capture());
    DimensionDelta delta = captor.getValue();
    assertThat(delta.scoreDelta()).isEqualTo(0.005);
    assertThat(delta.evidenceIncrement()).isEqualTo(1);
    assertThat(delta.metadata()).containsEntry("kcCode", "PY43-KC-004");
    assertThat(delta.metadata()).containsEntry("resourceKind", "VIDEO");
    assertThat(delta.source()).isEqualTo("RESOURCE_FEEDBACK");
  }

  @Test
  void emitsFourDimensionUpdatesForFourEvents() {
    listener.onPracticeSucceeded(new LearningEvent.PracticeSucceeded(7L, "K1", 1L));
    listener.onPracticeFailed(new LearningEvent.PracticeFailed(7L, "K2", 2L, "RE"));
    listener.onDebugResolved(new LearningEvent.DebugResolved(7L, "K3", 3L));
    listener.onResourceConsumed(new LearningEvent.ResourceConsumed(7L, "K4", "ARTICLE"));

    verify(passiveProfileService, times(4))
        .applyDimensionUpdate(eq(7L), anyString(), any(DimensionDelta.class));
  }

  @Test
  void dataAccessExceptionIsSwallowedToAvoidBreakingEventChain() {
    doThrow(new DataAccessResourceFailureException("column dimensions does not exist"))
        .when(passiveProfileService)
        .applyDimensionUpdate(anyLong(), anyString(), any(DimensionDelta.class));

    listener.onPracticeSucceeded(new LearningEvent.PracticeSucceeded(7L, "K1", 1L));
    listener.onResourceConsumed(new LearningEvent.ResourceConsumed(7L, "K2", "VIDEO"));

    verify(passiveProfileService, times(2))
        .applyDimensionUpdate(anyLong(), anyString(), any(DimensionDelta.class));
  }
}
