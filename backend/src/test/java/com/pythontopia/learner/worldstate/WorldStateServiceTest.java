package com.pythontopia.learner.worldstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pythontopia.learner.worldstate.dto.WorldStateDto;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import com.pythontopia.submission.repository.PracticeSubmissionRepository.PlannerSubmission;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** 世界状态只报告客观连击；答题行为不再映射为学生情绪。 */
class WorldStateServiceTest {

  private final PracticeSubmissionRepository submissionRepository =
      mock(PracticeSubmissionRepository.class);
  private final WorldStateService service = new WorldStateService(submissionRepository);

  @Test
  void current_showsFailedStreakWithoutInferringEmotion() {
    when(submissionRepository.findRecentForPlanner(eq(7L), anyInt())).thenReturn(List.of());

    WorldStateDto dto = service.current(7L);
    assertThat(dto.mood()).isEqualTo("sunny");
    assertThat(dto.emotionScore()).isEqualTo(0.5);
    assertThat(dto.streak()).isZero();
  }

  @Test
  void current_recentWinStreak_isSunnyEvenWhenSlowScoreLow() {
    // 升序（最新在末尾）：尾部两次通过 → streak +2。
    when(submissionRepository.findRecentForPlanner(eq(7L), anyInt()))
        .thenReturn(
            List.of(
                new PlannerSubmission(1L, "kc", false),
                new PlannerSubmission(2L, "kc", true),
                new PlannerSubmission(3L, "kc", true)));

    WorldStateDto dto = service.current(7L);
    assertThat(dto.streak()).isEqualTo(2);
    assertThat(dto.mood()).isEqualTo("sunny");
    assertThat(dto.emotionScore()).isEqualTo(0.5);
  }

  @Test
  void current_degradesToNeutralWithoutLoggingExceptionDetails() {
    String sentinel = "submission-read-private-sentinel";
    when(submissionRepository.findRecentForPlanner(eq(7L), anyInt()))
        .thenThrow(new RuntimeException(sentinel));

    Logger logger = (Logger) LoggerFactory.getLogger(WorldStateService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    WorldStateDto dto;
    try {
      dto = service.current(7L);
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(dto.emotionScore()).isEqualTo(0.5);
    assertThat(dto.streak()).isZero();
    assertThat(dto.mood()).isEqualTo("sunny");
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(message -> message.contains(sentinel));
  }
}
