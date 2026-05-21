package com.pythontopia.learner.event;

/**
 * Marker interface for in-process learning events. Concrete events are published when KC mastery
 * may change (profile bootstrap, resource consumption, practice success/fail, debug fix).
 *
 * <p>Stage 05 introduces the contract; downstream stages (07, 09, 10, 13) publish events that the
 * mastery evaluator subscribes to via {@code LearningEventListener}.
 */
public sealed interface LearningEvent
    permits LearningEvent.ProfileBootstrapped,
        LearningEvent.ResourceConsumed,
        LearningEvent.PracticeSucceeded,
        LearningEvent.PracticeFailed,
        LearningEvent.DebugResolved {

  Long userId();

  record ProfileBootstrapped(Long userId, java.util.List<String> unlockedKcs) implements LearningEvent {}

  record ResourceConsumed(Long userId, String kcCode, String resourceKind) implements LearningEvent {}

  record PracticeSucceeded(Long userId, String kcCode, Long problemId) implements LearningEvent {}

  record PracticeFailed(Long userId, String kcCode, Long problemId, String failureKind)
      implements LearningEvent {}

  record DebugResolved(Long userId, String kcCode, Long problemId) implements LearningEvent {}
}
