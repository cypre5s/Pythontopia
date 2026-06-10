package com.pythontopia.learner.event;

import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.mastery.MasterySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LearningEventListener {

  private static final Logger log = LoggerFactory.getLogger(LearningEventListener.class);
  private final MasteryEvaluator masteryEvaluator;
  private final JdbcTemplate jdbc;

  public LearningEventListener(MasteryEvaluator masteryEvaluator, JdbcTemplate jdbc) {
    this.masteryEvaluator = masteryEvaluator;
    this.jdbc = jdbc;
  }

  @EventListener
  public void onResourceConsumed(LearningEvent.ResourceConsumed event) {
    masteryEvaluator.applyEvent(event.userId(), event.kcCode(),
        MasterySource.RESOURCE_READ, null, "resource:" + event.resourceKind());
  }

  @EventListener
  public void onPracticePass(LearningEvent.PracticeSucceeded event) {
    masteryEvaluator.applyEventWithDifficulty(event.userId(), event.kcCode(),
        MasterySource.PRACTICE_PASS, null, "problem:" + event.problemId(),
        event.difficulty() == null ? loadDifficulty(event.problemId()) : event.difficulty());
  }

  @EventListener
  public void onPracticeFail(LearningEvent.PracticeFailed event) {
    masteryEvaluator.applyEventWithDifficulty(event.userId(), event.kcCode(),
        MasterySource.PRACTICE_FAIL, null,
        "problem:" + event.problemId() + ":" + event.failureKind(),
        event.difficulty() == null ? loadDifficulty(event.problemId()) : event.difficulty());
  }

  @EventListener
  public void onDebugResolved(LearningEvent.DebugResolved event) {
    masteryEvaluator.applyEventWithDifficulty(event.userId(), event.kcCode(),
        MasterySource.DEBUG_FIX, null, "debug:" + event.problemId(), null);
  }

  @EventListener
  public void onProfileBootstrapped(LearningEvent.ProfileBootstrapped event) {
    for (String kc : event.unlockedKcs()) {
      masteryEvaluator.applyEvent(event.userId(), kc,
          MasterySource.BOOTSTRAP, java.math.BigDecimal.ZERO, "profile bootstrap");
    }
  }

  private String loadDifficulty(Long problemId) {
    if (problemId == null) return null;
    try {
      return jdbc.queryForObject(
          "SELECT difficulty FROM course_problem WHERE id = ?", String.class, problemId);
    } catch (RuntimeException e) {
      log.debug("difficulty lookup failed for problem {}: {}", problemId, e.getMessage());
      return null;
    }
  }
}
