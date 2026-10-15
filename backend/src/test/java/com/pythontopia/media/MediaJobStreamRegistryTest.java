package com.pythontopia.media;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class MediaJobStreamRegistryTest {

  @Test
  void serializationFailureDoesNotLogMediaEventOrExceptionBody() {
    String sentinel = "private-media-stream-payload-sentinel";
    ObjectMapper mapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    module.addSerializer(
        FailingPayload.class,
        new JsonSerializer<>() {
          @Override
          public void serialize(FailingPayload value, JsonGenerator generator, SerializerProvider serializers)
              throws IOException {
            throw new IOException(sentinel);
          }
        });
    mapper.registerModule(module);
    MediaJobStreamRegistry registry =
        new MediaJobStreamRegistry(mapper, new MediaComposeProperties(2, 2, 2, 64, 300_000L));

    Logger logger = (Logger) LoggerFactory.getLogger(MediaJobStreamRegistry.class);
    Level previousLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThat(registry.send(new SseEmitter(), "trace", new FailingPayload())).isFalse();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("errorType="))
        .noneMatch(message -> message.contains(sentinel));
    assertThat(appender.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
  }

  private record FailingPayload() {}
}
