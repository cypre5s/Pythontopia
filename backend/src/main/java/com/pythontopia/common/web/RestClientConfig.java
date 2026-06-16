package com.pythontopia.common.web;

import com.pythontopia.common.config.ChapterTestHttpClientProperties;
import com.pythontopia.common.config.GraphHttpClientProperties;
import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

  /**
   * graph/rag 阻塞调用共用的 {@link RestTemplate}：配置 connect/read 超时（默认 3s/30s，可经 {@link
   * GraphHttpClientProperties} 覆盖），确保下游不可达时在阈值内 fail-fast，而非无限挂起占满 Tomcat 线程。
   */
  @Bean
  RestTemplate graphRestTemplate(
      RestTemplateBuilder builder, GraphHttpClientProperties httpProperties) {
    return builder
        .setConnectTimeout(Duration.ofMillis(httpProperties.connectTimeoutMs()))
        .setReadTimeout(Duration.ofMillis(httpProperties.readTimeoutMs()))
        .build();
  }

  /**
   * 章节试卷生成专用客户端。33/99 题需要多轮模型调用，允许更长读取时间，但连接失败仍沿用 Graph
   * 客户端的快速失败阈值。
   */
  @Bean
  RestTemplate chapterTestRestTemplate(
      RestTemplateBuilder builder,
      GraphHttpClientProperties graphHttpProperties,
      ChapterTestHttpClientProperties chapterTestHttpProperties) {
    return builder
        .setConnectTimeout(Duration.ofMillis(graphHttpProperties.connectTimeoutMs()))
        .setReadTimeout(Duration.ofMillis(chapterTestHttpProperties.readTimeoutMs()))
        .build();
  }

  /**
   * 记忆向量化（{@code RagEmbeddingClient} → pythontopia-rag {@code /embed}）专用的 {@link RestTemplate}，
   * 与 {@link #graphRestTemplate} 物理隔离。embedding 走 {@code @Async} 旁路，若复用 graph 实例，集成测试里
   * {@code MockRestServiceServer.bindTo(graphRestTemplate)} 会拦到这条异步请求并因「未预期」判失败（时序 flaky）。
   * 独立 bean 后，绑定 graph 模板的测试不再受 embedding 异步调用干扰。
   */
  @Bean
  RestTemplate ragRestTemplate(
      RestTemplateBuilder builder, GraphHttpClientProperties httpProperties) {
    return builder
        .setConnectTimeout(Duration.ofMillis(httpProperties.connectTimeoutMs()))
        .setReadTimeout(Duration.ofMillis(httpProperties.readTimeoutMs()))
        .build();
  }
}
