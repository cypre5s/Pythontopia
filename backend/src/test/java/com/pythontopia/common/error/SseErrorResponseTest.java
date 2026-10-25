package com.pythontopia.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 回归：流式端点（{@code produces = text/event-stream}，客户端只发 {@code Accept: text/event-stream}）
 * 抛业务异常时必须回可解析的 JSON 错误体，而不是 {@code HttpMediaTypeNotAcceptableException}（406）。
 *
 * <p>历史缺陷：{@code /api/tutor/workflow/runs} 额度用尽返回 429 时，Spring 因无兼容消息转换器
 * 抛 406 "No acceptable representation"，前端只看到泛化失败文案。
 */
class SseErrorResponseTest {

  @RestController
  static class SseEndpoint {

    @PostMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter run() {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS, "TUTOR_DAILY_LIMIT_REACHED", "今日 AI 请求额度已达 30 次，请明天再试");
    }
  }

  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(new SseEndpoint())
          .setControllerAdvice(new GlobalExceptionHandler())
          .build();

  @Test
  void eventStreamClientStillReceivesJsonErrorBody() throws Exception {
    mockMvc
        .perform(post("/sse").accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("TUTOR_DAILY_LIMIT_REACHED"))
        .andExpect(jsonPath("$.message").value("今日 AI 请求额度已达 30 次，请明天再试"));
  }

}
