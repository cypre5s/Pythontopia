package com.pythontopia.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.pythontopia.common.config.GraphServiceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AdminAiConfigGraphClientTest {

  @Test
  void sendsConnectionTestToGraphTestRoute() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(requestTo("http://graph.test/internal/admin/llm-config/test"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                "{\"success\":true,\"model\":\"deepseek-flash\",\"message\":\"ok\"}",
                MediaType.APPLICATION_JSON));
    AdminAiConfigGraphClient client =
        new AdminAiConfigGraphClient(
            restTemplate, new GraphServiceProperties("http://graph.test", "http://rag.test", ""));

    AdminAiConfigGraphClient.GraphTestResult result = client.test("deepseek-flash", "test-key-123");

    assertThat(result.success()).isTrue();
    assertThat(result.model()).isEqualTo("deepseek-flash");
    server.verify();
  }
}
