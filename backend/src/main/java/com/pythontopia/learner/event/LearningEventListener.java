package com.pythontopia.learner.event;

import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.mastery.MasterySource;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class LearningEventListener {

  private final MasteryEvaluator masteryEvaluator;

  public LearningEventListener(MasteryEvaluator masteryEvaluator) {
    this.masteryEvaluator = masteryEvaluator;
  }

  @EventListener
  public void onResourceConsumed(LearningEvent.ResourceConsumed event) {
    masteryEvaluator.applyEvent(
        event.userId(), event.kcCode(), MasterySource.RESOURCE_READ, null, "resource:" + event.resourceKind());
  }

  @EventListener
  public void onPracticePass(LearningEvent.PracticeSucceeded event) {
    masteryEvaluator.applyEvent(
        event.userId(), event.kcCode(), MasterySource.PRACTICE_PASS, null,
        "problem:" + event.problemId());
  }

  @EventListener
  public void onPracticeFail(LearningEvent.PracticeFailed event) {
    masteryEvaluator.applyEvent(
        event.userId(), event.kcCode(), MasterySource.PRACTICE_FAIL, null,
        "problem:" + event.problemId() + ":" + event.failureKind());
  }

  @EventListener
  public void onDebugResolved(LearningEvent.DebugResolved event) {
    masteryEvaluator.applyEvent(
        event.userId(), event.kcCode(), MasterySource.DEBUG_FIX, null,
        "debug:" + event.problemId());
  }

  @EventListener
  public void onProfileBootstrapped(LearningEvent.ProfileBootstrapped event) {
    for (String kc : event.unlockedKcs()) {
      masteryEvaluator.applyEvent(
          event.userId(), kc, MasterySource.BOOTSTRAP, java.math.BigDecimal.ZERO, "profile bootstrap");
    }
  }
}
