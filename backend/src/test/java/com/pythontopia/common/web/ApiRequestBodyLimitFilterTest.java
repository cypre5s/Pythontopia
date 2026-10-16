package com.pythontopia.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiRequestBodyLimitFilterTest {

  private static final int MAX_INTERNAL_CHECKPOINT_BYTES = 3 * 1024 * 1024;

  @Test
  void disabledAsrReturnsUnavailableBeforeForwardingMultipartRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/media/asr");
    request.setContentType("multipart/form-data; boundary=upload-boundary");
    request.setContent(new byte[9 * 1024 * 1024]);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    new ApiRequestBodyLimitFilter(new ObjectMapper()).doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentAsString(StandardCharsets.UTF_8))
        .isEqualTo("{\"text\":\"\",\"degraded\":true}");
    verifyNoInteractions(chain);
  }

  @Test
  void agentRuntimeEventBodyIsRejectedBeforeForwardingWhenTooLarge() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/events");
    request.setContentType("application/json");
    request.setContent(new byte[16 * 1024 + 1]);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    new ApiRequestBodyLimitFilter(
            new ObjectMapper().registerModule(new JavaTimeModule()))
        .doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentAsString(StandardCharsets.UTF_8))
        .contains("LEARNING_EVENT_REQUEST_TOO_LARGE");
    verifyNoInteractions(chain);
  }

  @Test
  void workshopChatHistoryBodyIsRejectedBeforeJsonBindingWhenTooLarge() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/workshop/chat-history/turns");
    request.setContentType("application/json");
    request.setContent(new byte[ApiRequestBodyLimitFilter.MAX_WORKSHOP_CHAT_HISTORY_BYTES + 1]);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    new ApiRequestBodyLimitFilter(
            new ObjectMapper().registerModule(new JavaTimeModule()))
        .doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentAsString(StandardCharsets.UTF_8))
        .contains("WORKSHOP_CHAT_HISTORY_REQUEST_TOO_LARGE");
    verifyNoInteractions(chain);
  }

  @Test
  void judgeSubmissionBodiesAreRejectedBeforeJsonBindingWhenTooLarge() throws Exception {
    for (String path :
        List.of(
            "/api/submissions",
            "/api/world/boss/3/attempt",
            "/api/bug-mine/sessions/123/retry-submit",
            "/api/bug-mine/sessions/123/variant-submit")) {
      MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
      request.setContentType("application/json");
      request.setContent(new byte[64 * 1024 + 1]);
      MockHttpServletResponse response = new MockHttpServletResponse();
      FilterChain chain = mock(FilterChain.class);

      new ApiRequestBodyLimitFilter(
              new ObjectMapper().registerModule(new JavaTimeModule()))
          .doFilter(request, response, chain);

      assertThat(response.getStatus()).isEqualTo(413);
      assertThat(response.getContentAsString(StandardCharsets.UTF_8))
          .contains("JUDGE_SUBMISSION_REQUEST_TOO_LARGE");
      verifyNoInteractions(chain);
    }
  }

  @Test
  void internalTutorCheckpointBodyIsRejectedBeforeForwardingWhenTooLarge() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest(
            "PUT",
            "/api/internal/tutor/workflow/sessions/session-1/autogen-checkpoints/"
                + "6bcb4c5d-5b3a-4761-a2ec-03bf248f4199");
    request.setContentType("application/json");
    request.setContent(new byte[MAX_INTERNAL_CHECKPOINT_BYTES + 1]);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    new ApiRequestBodyLimitFilter(
            new ObjectMapper().registerModule(new JavaTimeModule()))
        .doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentAsString(StandardCharsets.UTF_8))
        .contains("TUTOR_CHECKPOINT_REQUEST_TOO_LARGE");
    verifyNoInteractions(chain);
  }
}
