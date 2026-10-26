package com.pythontopia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Smoke test that boots the Spring context without a real datasource. A no-op {@link JdbcTemplate}
 * stand-in lets repositories wire up so we can verify bean wiring stays valid as new modules land.
 */
@ActiveProfiles("test")
@SpringBootTest(classes = PythontopiaApplication.class)
@Import(PythontopiaApplicationTests.NoDatabaseConfig.class)
class PythontopiaApplicationTests {

  @Autowired private ApplicationContext applicationContext;

  @Test
  void contextLoadsWithoutStartingBackgroundSchedulers() {
    assertThat(applicationContext.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class))
        .isEmpty();
  }

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

    @Bean
    PlatformTransactionManager transactionManager() {
      return org.mockito.Mockito.mock(PlatformTransactionManager.class);
    }
  }
}
