package com.pythontopia.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class SecurityConfigCorsTest {

  @Test
  void corsShouldAllowConfiguredOrigins() {
    CorsConfigurationSource source =
        new SecurityConfig()
            .corsConfigurationSource(
                java.util.List.of("http://localhost:8080", "http://127.0.0.1:8080"));

    CorsConfiguration config = ((UrlBasedCorsConfigurationSource) source).getCorsConfigurations().get("/**");

    assertThat(config).isNotNull();
    assertThat(config.getAllowedOriginPatterns())
        .contains("http://127.0.0.1:8080", "http://localhost:8080");
  }
}
