package com.pythontopia.common.web;

import com.pythontopia.tutor.TutorDailyRequestLimitService;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class AiBudgetWebConfiguration {

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  Advisor aiBudgetAdvisor(TutorDailyRequestLimitService requestLimitService) {
    DefaultPointcutAdvisor advisor =
        new DefaultPointcutAdvisor(
            AnnotationMatchingPointcut.forMethodAnnotation(AiBudgeted.class),
            new AiBudgetMethodInterceptor(requestLimitService));
    advisor.setOrder(Ordered.LOWEST_PRECEDENCE);
    return advisor;
  }
}
