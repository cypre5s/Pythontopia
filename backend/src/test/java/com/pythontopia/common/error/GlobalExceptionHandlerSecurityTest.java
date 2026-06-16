package com.pythontopia.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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
  void unexpectedExceptionsDoNotExposeImplementationClassNames() {
    var response = handler.handleException(new IllegalStateException("database detail"), request);

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    assertThat(response.getBody().details()).isEmpty();
    assertThat(response.getBody().message()).doesNotContain("IllegalStateException", "database detail");
  }
}
