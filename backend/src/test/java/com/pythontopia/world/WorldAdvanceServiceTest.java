package com.pythontopia.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.world.dto.WorldSnapshotDto;
import com.pythontopia.world.repository.GameBootstrapRepository;
import com.pythontopia.world.repository.GameBootstrapRepository.BootstrapRow;
import com.pythontopia.world.repository.WorldRepository;
import com.pythontopia.world.calendar.WorldEventRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class WorldAdvanceServiceTest {

  @Test
  void decayFailureLogDoesNotIncludeStudentIdOrExceptionDetails() {
    long userId = 74019L;
    WorldSnapshotDto world =
        new WorldSnapshotDto(
            1L,
            1,
            1,
            "monday",
            "spring",
            "night",
            "SUNNY",
            1200,
            OffsetDateTime.now(),
            OffsetDateTime.now());
    GameBootstrapRepository bootstrapRepository = mock(GameBootstrapRepository.class);
    MasteryEvaluator masteryEvaluator = mock(MasteryEvaluator.class);
    when(bootstrapRepository.findRow(userId)).thenReturn(Optional.of(new BootstrapRow(world, null)));
    doThrow(new IllegalStateException("private-mastery-error-sentinel"))
        .when(masteryEvaluator)
        .decayAll(userId);
    Random random = mock(Random.class);
    when(random.nextInt(4)).thenReturn(0);
    WorldAdvanceService service =
        new WorldAdvanceService(
            mock(WorldRepository.class),
            bootstrapRepository,
            mock(WorldEventRepository.class),
            masteryEvaluator,
            random);

    Logger logger = (Logger) LoggerFactory.getLogger(WorldAdvanceService.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.WARN);
    logger.addAppender(appender);
    try {
      assertThat(service.sleep(userId)).isNotNull();

      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .allSatisfy(
              message ->
                  assertThat(message)
                      .doesNotContain(Long.toString(userId))
                      .doesNotContain("private-mastery-error-sentinel"));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }
}
