package com.pythontopia.common.web;

import com.pythontopia.common.config.BossProblemHttpClientProperties;
import com.pythontopia.common.config.BrowseHttpClientProperties;
import com.pythontopia.common.config.ChapterTestHttpClientProperties;
import com.pythontopia.common.config.FeynmanTuteeHttpClientProperties;
import com.pythontopia.common.config.GraphHttpClientProperties;
import com.pythontopia.common.config.WorkshopHttpClientProperties;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

  /** Piston 判题专用客户端；与 Graph/RAG 线程池和超时隔离，远端故障时快速 fail closed。 */
  @Bean("pistonRestTemplate")
  @ConditionalOnProperty(name = "pythontopia.judge.provider", havingValue = "piston")
  RestTemplate pistonRestTemplate(
      RestTemplateBuilder builder,
      @Value("${pythontopia.judge.piston.connect-timeout-ms:3000}") int connectTimeoutMs,
      @Value("${pythontopia.judge.piston.read-timeout-ms:10000}") int readTimeoutMs) {
    return builder
        .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
        .setReadTimeout(Duration.ofMillis(readTimeoutMs))
        .build();
  }

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

  /** Bounded browser execution may take 45s plus shutdown; keep it outside Graph's 30s window. */
  @Bean("browseRestTemplate")
  RestTemplate browseRestTemplate(
      RestTemplateBuilder builder,
      GraphHttpClientProperties graphHttpProperties,
      BrowseHttpClientProperties browseHttpProperties) {
    return builder
        .setConnectTimeout(Duration.ofMillis(graphHttpProperties.connectTimeoutMs()))
        .setReadTimeout(Duration.ofMillis(browseHttpProperties.readTimeoutMs()))
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
   * Boss / endless 现场出题专用客户端。LLM 出题 + 审查偶发超过普通 graph 读超时，单独放宽读窗口；
   * 连接失败仍沿用 graph 客户端的快速失败阈值。
   */
  @Bean
  RestTemplate bossProblemRestTemplate(
      RestTemplateBuilder builder,
      GraphHttpClientProperties graphHttpProperties,
      BossProblemHttpClientProperties bossProblemHttpProperties) {
    return builder
        .setConnectTimeout(Duration.ofMillis(graphHttpProperties.connectTimeoutMs()))
        .setReadTimeout(Duration.ofMillis(bossProblemHttpProperties.readTimeoutMs()))
        .build();
  }

  /**
   * 费曼 AI 学弟教学回合专用客户端。真实 LLM 还要同步返回误区状态和可判题代码，生产实测会超过普通
   * Graph 30 秒读窗口；只放宽该端点，避免让意图路由、RAG 等短请求长期占用 Tomcat 线程。
   */
  @Bean
  RestTemplate feynmanTuteeRestTemplate(
      RestTemplateBuilder builder,
      GraphHttpClientProperties graphHttpProperties,
      FeynmanTuteeHttpClientProperties feynmanTuteeHttpProperties) {
    return builder
        .setConnectTimeout(Duration.ofMillis(graphHttpProperties.connectTimeoutMs()))
        .setReadTimeout(Duration.ofMillis(feynmanTuteeHttpProperties.readTimeoutMs()))
        .build();
  }

  /**
   * 百晓工坊资源生成专用客户端。资源图包含证据检索、设计和语义接地审查，生产耗时会超过普通
   * Graph 30 秒读窗口；只放宽该端点，避免意图路由等短请求失去 fail-fast。
   */
  @Bean
  RestTemplate workshopRestTemplate(
      RestTemplateBuilder builder,
      GraphHttpClientProperties graphHttpProperties,
      WorkshopHttpClientProperties workshopHttpProperties) {
    return builder
        .setConnectTimeout(Duration.ofMillis(graphHttpProperties.connectTimeoutMs()))
        .setReadTimeout(Duration.ofMillis(workshopHttpProperties.readTimeoutMs()))
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
