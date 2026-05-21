package com.pythontopia.learner.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.profile.service.ProfileService;
import org.junit.jupiter.api.Test;

class PassiveProfileRevisionListenerTest {

  private final ProfileService profileService = mock(ProfileService.class);
  private final PassiveProfileRevisionListener listener =
      new PassiveProfileRevisionListener(profileService);

  @Test
  void writesPracticePassRevision() {
    listener.onPracticeSucceeded(
        new LearningEvent.PracticeSucceeded(7L, "PY43-KC-001", 101L));
    verify(profileService).applyPassiveRevision(eq(7L), eq("PRACTICE_PASS"), any());
  }

  @Test
  void writesPracticeFailRevision() {
    listener.onPracticeFailed(
        new LearningEvent.PracticeFailed(7L, "PY43-KC-002", 102L, "WA"));
    verify(profileService).applyPassiveRevision(eq(7L), eq("PRACTICE_FAIL"), any());
  }

  @Test
  void writesDebugFixRevision() {
    listener.onDebugResolved(
        new LearningEvent.DebugResolved(7L, "PY43-KC-003", 103L));
    verify(profileService).applyPassiveRevision(eq(7L), eq("DEBUG_FIX"), any());
  }

  @Test
  void emitsThreeRevisionsForThreeEvents() {
    listener.onPracticeSucceeded(
        new LearningEvent.PracticeSucceeded(7L, "PY43-KC-001", 101L));
    listener.onPracticeFailed(
        new LearningEvent.PracticeFailed(7L, "PY43-KC-002", 102L, "RE"));
    listener.onDebugResolved(
        new LearningEvent.DebugResolved(7L, "PY43-KC-003", 103L));
    verify(profileService, times(3)).applyPassiveRevision(any(), any(), any());
  }
}
