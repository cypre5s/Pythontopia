package com.pythontopia.learner.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.mastery.MasterySource;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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

  @Test
  void failedDifficultyLookupDoesNotLogDatabaseExceptionDetails() {
    MasteryEvaluator masteryEvaluator = mock(MasteryEvaluator.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(String.class), eq(42L)))
        .thenThrow(new IllegalStateException("private-db-error-sentinel"));
    LearningEventListener listener = new LearningEventListener(masteryEvaluator, jdbc);
    Logger logger = (Logger) LoggerFactory.getLogger(LearningEventListener.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.DEBUG);
    logger.addAppender(appender);
    try {
      listener.onPracticeFail(
          new LearningEvent.PracticeFailed(7L, "PY43-KC-016", 42L, "WA", null));

      verify(masteryEvaluator)
          .applyEventWithDifficulty(
              7L,
              "PY43-KC-016",
              MasterySource.PRACTICE_FAIL,
              null,
              "problem:42:WA",
              null,
              null);
      assertThat(appender.list)
          .singleElement()
          .satisfies(
              event -> {
                assertThat(event.getFormattedMessage()).contains("IllegalStateException");
                assertThat(event.getFormattedMessage()).doesNotContain("private-db-error-sentinel");
                assertThat(event.getThrowableProxy()).isNull();
              });
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }
}
