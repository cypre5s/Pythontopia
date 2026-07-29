package com.pythontopia.learner.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.common.config.GraphServiceProperties;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

/** EmbeddingClient HTTP 客户端单测：顺序一致 / 维度 / fail-soft 降级 / 空输入不发请求 / token 透传。 */
class EmbeddingClientTest {

  private final RestTemplate restTemplate = new RestTemplate();
  private final GraphServiceProperties properties =
      new GraphServiceProperties("http://graph.test", "http://rag.test", "secret");
  private final EmbeddingClient client = new RagEmbeddingClient(restTemplate, properties);

  @Test
  void embedBatchReturnsVectorsInOrder() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://rag.test/embed"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                "{\"model\":\"hash-bow-1024\",\"dim\":3,"
                    + "\"vectors\":[[1.0,0.0,0.0],[0.0,1.0,0.0]]}",
                MediaType.APPLICATION_JSON));

    List<Optional<float[]>> result = client.embedBatch(List.of("a", "b"));

    assertThat(result).hasSize(2);
    assertThat(result.get(0)).isPresent();
    assertThat(result.get(0).orElseThrow()).containsExactly(1.0f, 0.0f, 0.0f);
    assertThat(result.get(1).orElseThrow()).containsExactly(0.0f, 1.0f, 0.0f);
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void embedSingleReturnsVectorWithExpectedDimension() {
    // 2048 维（对齐智谱 embedding-3）：客户端原样解析维度，不截断。
    StringBuilder vector = new StringBuilder("0.0");
    for (int i = 1; i < 2048; i++) {
      vector.append(",0.0");
    }
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://rag.test/embed"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                "{\"model\":\"zhipu-embedding-3\",\"dim\":2048,\"vectors\":[[" + vector + "]]}",
                MediaType.APPLICATION_JSON));

    Optional<float[]> result = client.embed("列表是可变对象");

    assertThat(result).isPresent();
    assertThat(result.orElseThrow()).hasSize(2048);
  }

  @Test
  void declaredDimensionMismatchYieldsEmpty() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://rag.test/embed"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                "{\"model\":\"broken\",\"dim\":3,\"vectors\":[[1.0,0.0]]}",
                MediaType.APPLICATION_JSON));

    assertThat(client.embed("x")).isEmpty();
  }

  @Test
  void nonPositiveDeclaredDimensionYieldsEmpty() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://rag.test/embed"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                "{\"model\":\"broken\",\"dim\":0,\"vectors\":[[1.0]]}",
                MediaType.APPLICATION_JSON));

    assertThat(client.embed("x")).isEmpty();
  }

  @Test
  void timeoutYieldsEmptyWithoutThrowing() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://rag.test/embed"))
        .andRespond(
            request -> {
              throw new org.springframework.web.client.ResourceAccessException("timeout");
            });

    assertThat(client.embed("x")).isEmpty();
  }

  @Test
  void upstream5xxYieldsEmptyAlignedToInput() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://rag.test/embed"))
        .andRespond(
            MockRestResponseCreators.withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("down"));

    List<Optional<float[]>> result = client.embedBatch(List.of("a", "b"));

    assertThat(result).hasSize(2);
    assertThat(result).allMatch(Optional::isEmpty);
  }

  @Test
  void emptyTextsSkipsCallAndReturnsEmpty() {
    // 不 build server expectation：空输入不得发起 HTTP 调用。
    assertThat(client.embedBatch(List.of())).isEmpty();
    assertThat(client.embed("   ")).isEmpty();
  }

  @Test
  void carriesInternalTokenWhenConfigured() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://rag.test/embed"))
        .andExpect(MockRestRequestMatchers.header("X-Internal-Token", "secret"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                "{\"model\":\"m\",\"dim\":1,\"vectors\":[[1.0]]}", MediaType.APPLICATION_JSON));

    client.embed("x");

    server.verify(Duration.ofSeconds(2));
  }
}
