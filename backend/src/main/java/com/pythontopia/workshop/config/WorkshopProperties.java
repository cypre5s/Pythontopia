package com.pythontopia.workshop.config;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pythontopia.workshop")
public record WorkshopProperties(
    Integer maxResourcesPerHour,
    BigDecimal masteryDailyCapPerKc,
    List<Long> allowlist) {

  public WorkshopProperties {
    if (maxResourcesPerHour == null || maxResourcesPerHour <= 0) {
      maxResourcesPerHour = 10;
    }
    if (masteryDailyCapPerKc == null) {
      masteryDailyCapPerKc = new BigDecimal("0.15");
    }
    if (allowlist == null) {
      allowlist = List.of();
    }
  }

  public boolean isAllowlisted(Long userId) {
    return userId != null && allowlist.contains(userId);
  }
}
