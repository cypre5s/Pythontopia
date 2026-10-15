package com.pythontopia.learner.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.mastery.MasterySource;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LearningEventListenerTest {

  private final MasteryEvaluator masteryEvaluator = mock(MasteryEvaluator.class);
  private final LearningEventListener listener =
      new LearningEventListener(masteryEvaluator, mock(JdbcTemplate.class));

  @Test
  void debugResolutionReusesTheVerifiedSubmissionKeyForMastery() {
    listener.onPracticePass(
        new LearningEvent.PracticeSucceeded(7L, "PY43-KC-003", 42L, "medium", 900L));
    listener.onDebugResolved(
        new LearningEvent.DebugResolved(7L, "PY43-KC-003", 42L, "submission:900"));

    verify(masteryEvaluator)
        .applyEventWithDifficulty(
            7L,
            "PY43-KC-003",
            MasterySource.PRACTICE_PASS,
            null,
            "problem:42",
            "medium",
            "submission:900");
    verify(masteryEvaluator)
        .applyEvent(
            7L,
            "PY43-KC-003",
            MasterySource.DEBUG_FIX,
            (BigDecimal) null,
            "debug:42",
            "submission:900");
  }

  @Test
  void chapterAssessmentProjectsOnlyServerJudgedCodingOutcomeOncePerKc() {
    LearningEvent.ChapterAssessmentScored event =
        new LearningEvent.ChapterAssessmentScored(7L, 100L, 1, "FINAL", "PY43-KC-003", 1, 1);

    listener.onChapterAssessmentScored(event);

    verify(masteryEvaluator)
        .applyEvent(
            7L,
            "PY43-KC-003",
            MasterySource.MICRO_CHECK_PASS,
            null,
            "chapter_assessment:FINAL:1:100 coding=1/1",
            "chapter-assessment:final:1:100:PY43-KC-003");
  }
}
