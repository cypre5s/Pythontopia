package com.pythontopia.common.web;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

  @Bean
  RestTemplate graphRestTemplate(RestTemplateBuilder builder) {
    return builder.build();
  }
}
