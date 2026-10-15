package com.pythontopia.browse.service;

import com.pythontopia.browse.dto.BrowseRunDto;
import com.pythontopia.browse.entity.BrowseRunEntity;
import com.pythontopia.browse.entity.BrowseRunStatus;
import com.pythontopia.browse.entity.BrowseStepEntity;
import com.pythontopia.browse.repository.BrowseRunRepository;
import com.pythontopia.browse.repository.BrowseStepRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes one completed browser result atomically after the remote browse call has finished. */
@Service
public class BrowseRunPersistenceService {

  private static final Logger log = LoggerFactory.getLogger(BrowseRunPersistenceService.class);
  private static final int HISTORY_RETENTION_DAYS = 90;

  private final BrowseRunRepository runRepository;
  private final BrowseStepRepository stepRepository;

  public BrowseRunPersistenceService(
      BrowseRunRepository runRepository, BrowseStepRepository stepRepository) {
    this.runRepository = runRepository;
    this.stepRepository = stepRepository;
  }

  @Transactional
  public BrowseRunDto persist(
      long userId,
      String goal,
      BrowseRunStatus status,
      String finalSummary,
      boolean truncated,
      String error,
      List<BrowseStepEntity> steps) {
    BrowseRunEntity run =
        runRepository.insert(userId, goal, status, finalSummary, truncated, error);
    List<BrowseStepEntity> persistedSteps =
        steps.stream()
            .map(
                step ->
                    new BrowseStepEntity(
                        null,
                        run.id(),
                        step.idx(),
                        step.action(),
                        step.url(),
                        step.summary(),
                        step.title(),
                        step.snippet()))
            .toList();
    stepRepository.insertAll(run.id(), persistedSteps);
    return BrowseRunDto.from(run);
  }

  @Scheduled(cron = "0 20 3 * * *", zone = "Asia/Shanghai")
  public void cleanupExpiredRuns() {
    int deleted =
        runRepository.deleteCreatedBefore(
            OffsetDateTime.now(ZoneOffset.UTC).minusDays(HISTORY_RETENTION_DAYS));
    if (deleted > 0) {
      log.info("Removed {} expired BROWSE runs", deleted);
    }
  }
}
