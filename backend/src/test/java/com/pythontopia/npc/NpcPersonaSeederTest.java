package com.pythontopia.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.DefaultApplicationArguments;

class NpcPersonaSeederTest {

  @Test
  void seedFailureLogDoesNotIncludeExceptionDetails() {
    NpcPersonaService personaService = mock(NpcPersonaService.class);
    doThrow(new IllegalStateException("private-seed-error-sentinel"))
        .when(personaService)
        .seedFromMeta();
    NpcPersonaSeeder seeder = new NpcPersonaSeeder(personaService);
    Logger logger = (Logger) LoggerFactory.getLogger(NpcPersonaSeeder.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.WARN);
    logger.addAppender(appender);
    try {
      seeder.run(new DefaultApplicationArguments());

      assertThat(appender.list)
          .singleElement()
          .satisfies(
              event -> {
                assertThat(event.getFormattedMessage())
                    .contains("IllegalStateException")
                    .doesNotContain("private-seed-error-sentinel");
                assertThat(event.getThrowableProxy()).isNull();
              });
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }
}
