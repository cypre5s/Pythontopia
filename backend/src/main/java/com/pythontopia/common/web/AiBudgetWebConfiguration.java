package com.pythontopia.common.web;

import com.pythontopia.tutor.TutorDailyRequestLimitService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class AiBudgetWebConfiguration {

  @Bean
  AiBudgetInterceptor aiBudgetInterceptor(TutorDailyRequestLimitService requestLimitService) {
    return new AiBudgetInterceptor(requestLimitService);
  }

  @Bean
  WebMvcConfigurer aiBudgetWebConfigurer(AiBudgetInterceptor interceptor) {
    return new WebMvcConfigurer() {
      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
      }
    };
  }
}
