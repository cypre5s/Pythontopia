package com.pythontopia.learner.event;

import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.mastery.MasterySource;
import com.pythontopia.world.crop.CropStateService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class LearningEventListener {

  private final MasteryEvaluator masteryEvaluator;
  private final CropStateService cropStateService;

  public LearningEventListener(
      MasteryEvaluator masteryEvaluator, CropStateService cropStateService) {
    this.masteryEvaluator = masteryEvaluator;
    this.cropStateService = cropStateService;
  }

  @EventListener
  public void onResourceConsumed(LearningEvent.ResourceConsumed event) {
    masteryEvaluator.applyEvent(
        event.userId(), event.kcCode(), MasterySource.RESOURCE_READ, null, "resource:" + event.resourceKind());
    cropStateService.applyMasteryUpdate(event.userId(), event.kcCode());
  }

  @EventListener
  public void onPracticePass(LearningEvent.PracticeSucceeded event) {
    masteryEvaluator.applyEvent(
        event.userId(), event.kcCode(), MasterySource.PRACTICE_PASS, null,
        "problem:" + event.problemId());
    cropStateService.applyMasteryUpdate(event.userId(), event.kcCode());
  }

  @EventListener
  public void onPracticeFail(LearningEvent.PracticeFailed event) {
    masteryEvaluator.applyEvent(
        event.userId(), event.kcCode(), MasterySource.PRACTICE_FAIL, null,
        "problem:" + event.problemId() + ":" + event.failureKind());
    cropStateService.applyMasteryUpdate(event.userId(), event.kcCode());
  }

  @EventListener
  public void onDebugResolved(LearningEvent.DebugResolved event) {
    masteryEvaluator.applyEvent(
        event.userId(), event.kcCode(), MasterySource.DEBUG_FIX, null,
        "debug:" + event.problemId());
    cropStateService.applyMasteryUpdate(event.userId(), event.kcCode());
  }

  @EventListener
  public void onProfileBootstrapped(LearningEvent.ProfileBootstrapped event) {
    for (String kc : event.unlockedKcs()) {
      masteryEvaluator.applyEvent(
          event.userId(), kc, MasterySource.BOOTSTRAP, java.math.BigDecimal.ZERO, "profile bootstrap");
    }
  }
}
