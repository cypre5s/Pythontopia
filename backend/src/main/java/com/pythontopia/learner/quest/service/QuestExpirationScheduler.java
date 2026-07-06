package com.pythontopia.learner.quest.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QuestExpirationScheduler {

  private static final Logger LOGGER = LoggerFactory.getLogger(QuestExpirationScheduler.class);

  private final QuestService questService;

  public QuestExpirationScheduler(QuestService questService) {
    this.questService = questService;
  }

  /** 每 30 分钟扫描逾期任务并置为 EXPIRED，避免公告板显示过期项。 */
  @Scheduled(fixedDelay = 1_800_000L, initialDelay = 60_000L)
  public void expireOverdue() {
    try {
      questService.expireOverdue();
    } catch (RuntimeException ex) {
      LOGGER.warn(
          "Quest expiration maintenance failed; it will retry on the next schedule: {}",
          ex.toString());
    }
  }
}
