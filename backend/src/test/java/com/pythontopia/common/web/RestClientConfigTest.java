package com.pythontopia.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pythontopia.common.config.BossProblemHttpClientProperties;
import com.pythontopia.common.config.ChapterTestHttpClientProperties;
import com.pythontopia.common.config.FeynmanTuteeHttpClientProperties;
import com.pythontopia.common.config.GraphHttpClientProperties;
import com.pythontopia.common.config.WorkshopHttpClientProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * 验证 {@code graphRestTemplate} 已配置可控读超时：对一个故意挂起的本地假服务端，后端必须在
 * 远早于服务端响应前因读超时 fail-fast，而不是无限挂起占满 Tomcat 线程。
 */
class RestClientConfigTest {

  private HttpServer server;
  private final CountDownLatch release = new CountDownLatch(1);

  @BeforeEach
  void startSlowServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/slow",
        exchange -> {
          try {
            // 一直阻塞到测试结束（或客户端先读超时），模拟下游 graph/rag 不响应。
            release.await(5, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    release.countDown();
    server.stop(0);
  }

  @Test
  void graphRestTemplateFailsFastOnReadTimeout() {
    RestTemplate restTemplate =
        new RestClientConfig()
            .graphRestTemplate(new RestTemplateBuilder(), new GraphHttpClientProperties(1000, 300));
    String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow";

    long startNanos = System.nanoTime();
    assertThatThrownBy(() -> restTemplate.getForObject(url, String.class))
        .isInstanceOf(ResourceAccessException.class);
    long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

    // 读超时设为 300ms，必须远早于服务端 5s 才触发，证明不会无限挂起。
    assertThat(elapsedMs).isLessThan(3000L);
  }

  @Test
  void defaultsAppliedWhenTimeoutsMissing() {
    GraphHttpClientProperties defaults = new GraphHttpClientProperties(null, null);
    assertThat(defaults.connectTimeoutMs()).isEqualTo(3000);
    assertThat(defaults.readTimeoutMs()).isEqualTo(30000);
    assertThat(new BossProblemHttpClientProperties(null).readTimeoutMs()).isEqualTo(180000);
    assertThat(new ChapterTestHttpClientProperties(null).readTimeoutMs()).isEqualTo(600000);
    assertThat(new FeynmanTuteeHttpClientProperties(null).readTimeoutMs()).isEqualTo(90000);
    assertThat(new WorkshopHttpClientProperties(null).readTimeoutMs()).isEqualTo(180000);
  }

  @Test
  void chapterTestRestTemplateUsesItsOwnReadTimeout() {
    RestTemplate restTemplate =
        new RestClientConfig()
            .chapterTestRestTemplate(
                new RestTemplateBuilder(),
                new GraphHttpClientProperties(1000, 30000),
                new ChapterTestHttpClientProperties(300));
    String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow";

    long startNanos = System.nanoTime();
    assertThatThrownBy(() -> restTemplate.getForObject(url, String.class))
        .isInstanceOf(ResourceAccessException.class);
    long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

    assertThat(elapsedMs).isLessThan(3000L);
  }

  @Test
  void bossProblemRestTemplateUsesItsOwnReadTimeout() {
    RestTemplate restTemplate =
        new RestClientConfig()
            .bossProblemRestTemplate(
                new RestTemplateBuilder(),
                new GraphHttpClientProperties(1000, 30000),
                new BossProblemHttpClientProperties(300));
    String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow";

    long startNanos = System.nanoTime();
    assertThatThrownBy(() -> restTemplate.getForObject(url, String.class))
        .isInstanceOf(ResourceAccessException.class);
    long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

    assertThat(elapsedMs).isLessThan(3000L);
  }

  @Test
  void feynmanTuteeRestTemplateUsesItsOwnReadTimeout() {
    RestTemplate restTemplate =
        new RestClientConfig()
            .feynmanTuteeRestTemplate(
                new RestTemplateBuilder(),
                new GraphHttpClientProperties(1000, 30000),
                new FeynmanTuteeHttpClientProperties(300));
    String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow";

    long startNanos = System.nanoTime();
    assertThatThrownBy(() -> restTemplate.getForObject(url, String.class))
        .isInstanceOf(ResourceAccessException.class);
    long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

    assertThat(elapsedMs).isLessThan(3000L);
  }

  @Test
  void workshopRestTemplateUsesItsOwnReadTimeout() {
    RestTemplate restTemplate =
        new RestClientConfig()
            .workshopRestTemplate(
                new RestTemplateBuilder(),
                new GraphHttpClientProperties(1000, 30000),
                new WorkshopHttpClientProperties(300));
    String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow";

    long startNanos = System.nanoTime();
    assertThatThrownBy(() -> restTemplate.getForObject(url, String.class))
        .isInstanceOf(ResourceAccessException.class);
    long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

    assertThat(elapsedMs).isLessThan(3000L);
  }
}
