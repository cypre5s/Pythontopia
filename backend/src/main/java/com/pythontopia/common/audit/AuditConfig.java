package com.pythontopia.common.audit;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import jakarta.persistence.EntityManagerFactory;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
@EnableJpaAuditing
@ConditionalOnBean(EntityManagerFactory.class)
public class AuditConfig {

  @Bean
  AuditorAware<Long> auditorAware() {
    return () -> {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserPrincipal principal)) {
        return Optional.empty();
      }
      return Optional.of(principal.userId());
    };
  }
}
