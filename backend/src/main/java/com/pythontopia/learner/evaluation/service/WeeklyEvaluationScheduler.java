package com.pythontopia.learner.evaluation.service;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每周自动为「近 7 天有学习活动、且本周尚无评估」的学生生成一份复盘周报。
 *
 * <p>与手动「立即评估」并存：调度器只对符合条件的学生逐个调 {@link LearnerEvaluationService#compute}
 * （经 Spring 代理，各自独立事务），单个失败仅记日志、不影响其余学生。幂等闸门（近 6 天已有评估则跳过）
 * 保证重复运行安全；通过 {@code pyt.evaluation.weekly-auto-enabled=false} 可整体关闭。
 */
@Component
public class WeeklyEvaluationScheduler {

  private static final Logger LOGGER = LoggerFactory.getLogger(WeeklyEvaluationScheduler.class);

  private final LearnerEvaluationService service;
  private final TutorDailyRequestLimitService requestLimitService;
  private final boolean enabled;

  public WeeklyEvaluationScheduler(
      LearnerEvaluationService service,
      TutorDailyRequestLimitService requestLimitService,
      @Value("${pyt.evaluation.weekly-auto-enabled:true}") boolean enabled) {
    this.service = service;
    this.requestLimitService = requestLimitService;
    this.enabled = enabled;
  }

  /** 默认每周一 03:00（UTC）跑一次；cron 与开关均可经配置覆盖，幂等闸门保证可重复安全。 */
  @Scheduled(cron = "${pyt.evaluation.weekly-cron:0 0 3 * * MON}", zone = "UTC")
  public void generateWeeklyReports() {
    if (!enabled) {
      return;
    }
    List<Long> learners;
    try {
      learners = service.findLearnersNeedingWeeklyEvaluation();
    } catch (RuntimeException ex) {
      LOGGER.warn(
          "Weekly evaluation batch aborted while listing learners errorType={}",
          ex.getClass().getSimpleName());
      return;
    }
    int generated = 0;
    int failed = 0;
    int skipped = 0;
    for (Long userId : learners) {
      try {
        requestLimitService.reserve(userId);
        service.compute(userId, null);
        generated++;
      } catch (RuntimeException ex) {
        failed++;
        if (ex instanceof BusinessException business
            && "AI_PROVIDER_DAILY_LIMIT_REACHED".equals(business.code())) {
          skipped = learners.size() - generated - failed;
          LOGGER.warn("Weekly evaluation batch stopped: shared AI provider daily budget reached");
          break;
        }
        LOGGER.warn("Weekly evaluation failed errorType={}", ex.getClass().getSimpleName());
      }
    }
    LOGGER.info(
        "Weekly evaluation batch done: {} eligible, {} generated, {} failed, {} skipped",
        learners.size(),
        generated,
        failed,
        skipped);
  }
}
