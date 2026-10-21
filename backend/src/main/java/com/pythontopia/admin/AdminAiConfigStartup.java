package com.pythontopia.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AdminAiConfigStartup {
  private static final Logger log = LoggerFactory.getLogger(AdminAiConfigStartup.class);
  private final AdminAiConfigService service;

  public AdminAiConfigStartup(AdminAiConfigService service) { this.service = service; }

  @EventListener(ApplicationReadyEvent.class)
  public void restoreSavedProvider() {
    try {
      service.restoreSavedConfiguration();
    } catch (RuntimeException exception) {
      log.warn("Stored AI provider config was not restored ({})", exception.getClass().getSimpleName());
    }
  }
}
