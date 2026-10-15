package com.pythontopia.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pythontopia.common.error.BusinessException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;

class GraphClientRetryHelperTest {

  @Test
  void retryDoesNotLogRemoteErrorMessage() {
    String privateContent = "private-graph-response-sentinel";
    String clientName = "privacy-test-" + UUID.randomUUID();
    AtomicInteger attempts = new AtomicInteger();
    Logger logger = (Logger) LoggerFactory.getLogger(GraphClientRetryHelper.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.DEBUG);
    logger.addAppender(appender);
    try {
      assertThatThrownBy(
              () ->
                  GraphClientRetryHelper.withRetry(
                      clientName,
                      () -> {
                        attempts.incrementAndGet();
                        throw new ResourceAccessException(privateContent);
                      }))
          .isInstanceOf(BusinessException.class)
          .hasMessageNotContaining(privateContent);

      assertThat(attempts).hasValue(2);
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .allSatisfy(message -> assertThat(message).doesNotContain(privateContent));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }
}
