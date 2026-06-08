package com.pythontopia.learner.mastery.review;

import com.pythontopia.learner.mastery.MasterySource;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FSRS-lite 复习状态的应用层：把掌握度事件翻译成"一次复习"，演化 kc_review_state，
 * 并对外提供 KC 的当下复习信号（可提取性 / 是否到期 / 下次到期时间）。
 *
 * <p>引擎确定性、不调 LLM；写入与 {@code MasteryEvaluator.applyEvent} 同事务，纯增量副作用，
 * 不改动既有 kc_mastery / kc_mastery_event 行为。
 */
@Service
public class ReviewStateService {

  private final ReviewStateRepository repository;
  private final ReviewScheduler scheduler;

  public ReviewStateService(ReviewStateRepository repository, ReviewScheduler scheduler) {
    this.repository = repository;
    this.scheduler = scheduler;
  }

  /** 掌握度事件 → 复习结果；DECAY/BOOTSTRAP/MANUAL_OVERRIDE 不算一次复习。 */
  static ReviewOutcome outcomeFor(MasterySource source) {
    return switch (source) {
      case PRACTICE_PASS, DEBUG_FIX -> ReviewOutcome.SUCCESS;
      case PRACTICE_FAIL -> ReviewOutcome.LAPSE;
      case RESOURCE_READ -> ReviewOutcome.RESTUDY;
      case DECAY, BOOTSTRAP, MANUAL_OVERRIDE -> null;
    };
  }

  /** 在掌握度事件落库后调用，按结果演化复习稳定度与下次到期时间。 */
  @Transactional
  public void onMasteryEvent(Long userId, String kcCode, MasterySource source, OffsetDateTime now) {
    ReviewOutcome outcome = outcomeFor(source);
    if (outcome == null || kcCode == null || kcCode.isBlank()) {
      return;
    }
    Optional<ReviewState> existing = repository.find(userId, kcCode);
    double stability = existing.map(ReviewState::stability).orElse(0.0);
    double difficulty =
        existing.map(ReviewState::difficulty).orElse(ReviewScheduler.DIFFICULTY_BASELINE);
    int reps = existing.map(ReviewState::reps).orElse(0);
    int lapses = existing.map(ReviewState::lapses).orElse(0);
    OffsetDateTime lastReview = existing.map(ReviewState::lastReviewAt).orElse(null);

    double elapsedDays =
        lastReview == null
            ? 0.0
            : Math.max(0.0, Duration.between(lastReview, now).toMinutes() / 1440.0);

    ReviewScheduler.Projection projection =
        scheduler.next(stability, difficulty, elapsedDays, outcome);
    if (outcome == ReviewOutcome.SUCCESS) {
      reps += 1;
    } else if (outcome == ReviewOutcome.LAPSE) {
      lapses += 1;
    }
    double intervalDays =
        scheduler.intervalDays(projection.stability(), ReviewScheduler.DEFAULT_TARGET_RETENTION);
    OffsetDateTime dueAt = now.plusSeconds(Math.round(intervalDays * 86_400.0));
    repository.upsert(
        userId,
        kcCode,
        round3(projection.stability()),
        round3(projection.difficulty()),
        reps,
        lapses,
        now,
        dueAt);
  }

  /**
   * 计算某 KC 的当下复习信号；无 review_state 行时按掌握度播种（与迁移回填一致），
   * 用 lastEventAt 当作上次复习时间。
   */
  public ReviewSignal signal(
      Long userId, String kcCode, double masteryScore, OffsetDateTime lastEventAt) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    Optional<ReviewState> existing = repository.find(userId, kcCode);
    if (existing.isPresent()) {
      ReviewState state = existing.get();
      return evaluate(
          kcCode,
          state.stability(),
          state.lastReviewAt(),
          state.dueAt(),
          masteryScore,
          lastEventAt,
          state.reps(),
          state.lapses(),
          now);
    }
    return evaluate(kcCode, null, null, null, masteryScore, lastEventAt, 0, 0, now);
  }

  /**
   * 纯函数：由（可能为空的）复习状态列与掌握度直接评估当下信号，便于上层用 JOIN 一次取数后
   * 在内存里算 R / 是否到期，避免 N+1。无状态时按掌握度播种，口径与 {@link #signal} 一致。
   */
  public ReviewSignal evaluate(
      String kcCode,
      Double stability,
      OffsetDateTime lastReviewAt,
      OffsetDateTime dueAt,
      double masteryScore,
      OffsetDateTime lastEventAt,
      int reps,
      int lapses,
      OffsetDateTime now) {
    double s = (stability != null && stability > 0) ? stability : scheduler.seedStability(masteryScore);
    OffsetDateTime lastReview = lastReviewAt != null ? lastReviewAt : lastEventAt;
    OffsetDateTime effectiveDue =
        dueAt != null
            ? dueAt
            : (lastReview == null
                ? null
                : lastReview.plusSeconds(
                    Math.round(
                        scheduler.intervalDays(s, ReviewScheduler.DEFAULT_TARGET_RETENTION)
                            * 86_400.0)));
    double elapsedDays =
        lastReview == null
            ? 0.0
            : Math.max(0.0, Duration.between(lastReview, now).toMinutes() / 1440.0);
    double retrievability = scheduler.retrievability(s, elapsedDays);
    boolean due = effectiveDue != null && now.isAfter(effectiveDue);
    return new ReviewSignal(kcCode, s, retrievability, due, lastReview, effectiveDue, reps, lapses);
  }

  private static double round3(double value) {
    return Math.round(value * 1000.0) / 1000.0;
  }
}
