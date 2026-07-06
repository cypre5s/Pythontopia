package com.pythontopia;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test that boots the Spring context without a real datasource. A no-op {@link JdbcTemplate}
 * stand-in lets repositories wire up so we can verify bean wiring stays valid as new modules land.
 */
@ActiveProfiles("test")
@SpringBootTest(classes = PythontopiaApplication.class)
@Import(PythontopiaApplicationTests.NoDatabaseConfig.class)
class PythontopiaApplicationTests {

  @Test
  void contextLoads() {}

  @TestConfiguration(proxyBeanMethods = false)
  static class NoDatabaseConfig {

    @Bean
    JdbcTemplate jdbcTemplate() {
      return org.mockito.Mockito.mock(JdbcTemplate.class);
    }

    @Bean
    JdbcClient jdbcClient() {
      return org.mockito.Mockito.mock(JdbcClient.class);
    }
  }
}
