package com.pythontopia.workshop.upload.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pythontopia.learner.memory.EmbeddingClient;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class UploadCorpusIndexingServiceTest {

  @Test
  @SuppressWarnings("unchecked")
  void indexingFailureDoesNotLogExceptionMessage() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UploadChunkRepository repository = mock(UploadChunkRepository.class);
    EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenThrow(new IllegalStateException("private extracted text must stay out of logs"));
    UploadCorpusIndexingService service =
        new UploadCorpusIndexingService(jdbc, new UploadChunker(), repository, embeddingClient);

    Logger logger = (Logger) LoggerFactory.getLogger(UploadCorpusIndexingService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThat(service.indexUpload(42L)).isZero();

      assertThat(appender.list).hasSize(1);
      assertThat(appender.list.getFirst().getFormattedMessage())
          .contains("uploadId=42")
          .doesNotContain("private extracted text must stay out of logs");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }
}
