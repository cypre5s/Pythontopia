package com.pythontopia.common.web;

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
}
