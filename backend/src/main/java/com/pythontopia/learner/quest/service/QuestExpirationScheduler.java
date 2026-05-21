package com.pythontopia.learner.quest.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QuestExpirationScheduler {

  private final QuestService questService;

  public QuestExpirationScheduler(QuestService questService) {
    this.questService = questService;
  }

  /**
   * Scans overdue quests every 30 minutes (1,800,000 ms) and flips them to EXPIRED so the
   * board panel never shows stale items. Scheduling is enabled via Spring's default scheduling
   * support that the application already activates.
   */
  @Scheduled(fixedDelay = 1_800_000L, initialDelay = 60_000L)
  public void expireOverdue() {
    questService.expireOverdue();
  }
}
