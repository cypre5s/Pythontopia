package com.pythontopia.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.HandlerMapping;

class GlobalExceptionHandlerSecurityTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
  private HttpServletRequest request;

  @BeforeEach
  void setUp() {
    request = mock(HttpServletRequest.class);
    when(request.getRequestId()).thenReturn("security-test");
  }

  @Test
  void invalidPathOrQueryTypeReturnsBadRequest() {
    MethodArgumentTypeMismatchException exception =
        new MethodArgumentTypeMismatchException(
            "security-canary", Long.class, "id", mock(MethodParameter.class), null);

    var response = handler.handleBadRequest(exception, request);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody().code()).isEqualTo("BAD_REQUEST");
  }

  @Test
  void missingMultipartPartReturnsBadRequest() {
    var response =
        handler.handleBadRequest(new MissingServletRequestPartException("file"), request);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody().code()).isEqualTo("BAD_REQUEST");
  }

  @Test
  void unacceptableResponseTypeReturnsNotAcceptable() {
    var response =
        handler.handleMediaTypeNotAcceptable(
            new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)), request);

    assertThat(response.getStatusCode().value()).isEqualTo(406);
    assertThat(response.getBody().code()).isEqualTo("NOT_ACCEPTABLE");
  }

  @Test
  void clientAbortReturnsEmptyNonErrorBody() {
    String sensitivePath = "/api/course/videos/78234";
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn(sensitivePath);
    when(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
        .thenReturn("/api/course/videos/{videoId}");

    Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.DEBUG);
    logger.addAppender(appender);
    try {
      var response =
          handler.handleClientAbort(
              new AsyncRequestNotUsableException("private connection detail"), request);

      assertThat(response.getStatusCode().value()).isEqualTo(499);
      assertThat(response.getBody()).isNull();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.getFirst().getFormattedMessage())
        .contains(
            "GET", "/api/course/videos/{videoId}", "security-test")
        .doesNotContain(sensitivePath, "78234", "private connection detail");
    assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
  }

  @Test
  void unexpectedExceptionsDoNotExposeImplementationClassNames() {
    var response = handler.handleException(new IllegalStateException("database detail"), request);

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    assertThat(response.getBody().details()).isEmpty();
    assertThat(response.getBody().message()).doesNotContain("IllegalStateException", "database detail");
  }

  @Test
  void unexpectedExceptionLogsRequestContextWithoutExceptionDetails() {
    String sensitiveDetail = "private-request-body-7fdc";
    String sensitivePath = "/api/users/78234/private-action";
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn(sensitivePath);
    when(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
        .thenReturn("/api/users/{userId}/private-action");

    Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.ERROR);
    logger.addAppender(appender);
    try {
      handler.handleException(new IllegalStateException(sensitiveDetail), request);
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.getFirst().getFormattedMessage())
        .contains(
            "POST",
            "/api/users/{userId}/private-action",
            "IllegalStateException",
            "security-test")
        .doesNotContain(sensitiveDetail, sensitivePath, "78234");
    assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
  }
}
