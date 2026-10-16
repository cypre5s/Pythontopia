package com.pythontopia.common.web;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Enforces account-scoped budgets for durable writes and expensive retrieval requests. */
@Component
public class StudentWriteThrottle {

  private static final int MAX_WRITES_PER_MINUTE = 100;
  private static final int MAX_TEACHER_KC_HEATMAPS_PER_MINUTE = 10;
  // shortcut: class-task fan-out caps at 1,000 writes/minute;
  // batch asynchronously if larger classes need support.
  public static final int MAX_CLASSROOM_QUEST_FANOUT_WRITES_PER_MINUTE = 1000;
  private static final long WINDOW_MILLIS = 60_000L;
  private static final int MAX_RETRIEVALS_PER_MINUTE = 30;
  private static final int MAX_GAME_BOOTSTRAPS_PER_MINUTE = 10;
  private static final int MAX_TUTOR_BENCH_RUNS_PER_MINUTE = 10;
  private static final int MAX_SANDBOX_JUDGES_PER_MINUTE = 6;
  private static final int MAX_MEDIA_COMPOSITIONS_PER_MINUTE = 3;
  private static final int MAX_PROFILE_INTERVIEWS_PER_HOUR = 3;
  private static final long HOUR_MILLIS = 3_600_000L;
  private static final int MAX_PLAYER_POSITION_UPDATES = 10;
  private static final long PLAYER_POSITION_WINDOW_MILLIS = 1_000L;

  // shortcut: counters are process-local for the single backend instance; use a shared store if
  // scaled out.
  private final ConcurrentHashMap<Long, Deque<Long>> windowsByUser = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, Deque<Long>> teacherKcHeatmapWindowsByUser =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, Deque<Long>> playerPositionWindowsByUser =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, Deque<Long>> retrievalWindowsByUser =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, Deque<Long>> gameBootstrapWindowsByUser =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, Deque<Long>> tutorBenchWindowsByUser =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, Deque<Long>> sandboxJudgeWindowsByUser =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, Deque<Long>> mediaCompositionWindowsByUser =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, Deque<Long>> classroomQuestFanOutWindowsByUser =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, Deque<Long>> profileInterviewWindowsByUser =
      new ConcurrentHashMap<>();
  private final LongSupplier nowMillis;

  public StudentWriteThrottle() {
    this(System::currentTimeMillis);
  }

  StudentWriteThrottle(LongSupplier nowMillis) {
    this.nowMillis = nowMillis;
  }

  /** Reserve all writes in one request atomically; an oversized batch consumes no allowance. */
  public boolean tryAcquire(long userId, int writeCount) {
    return tryAcquire(windowsByUser, userId, writeCount, MAX_WRITES_PER_MINUTE, WINDOW_MILLIS);
  }

  /** Bound expensive teacher heatmap refreshes without consuming the ordinary write allowance. */
  public boolean tryAcquireTeacherKcHeatmap(long userId) {
    return tryAcquire(
        teacherKcHeatmapWindowsByUser,
        userId,
        1,
        MAX_TEACHER_KC_HEATMAPS_PER_MINUTE,
        WINDOW_MILLIS);
  }

  /** Bound the one-to-many writes created by publishing a classroom quest. */
  public boolean tryAcquireClassroomQuestFanOut(long userId, int writeCount) {
    return tryAcquire(
        classroomQuestFanOutWindowsByUser,
        userId,
        writeCount,
        MAX_CLASSROOM_QUEST_FANOUT_WRITES_PER_MINUTE,
        WINDOW_MILLIS);
  }

  /** Position sync is frequent, so it has a separate short window from durable learning writes. */
  public boolean tryAcquirePlayerPositionUpdate(long userId) {
    return tryAcquire(
        playerPositionWindowsByUser,
        userId,
        1,
        MAX_PLAYER_POSITION_UPDATES,
        PLAYER_POSITION_WINDOW_MILLIS);
  }

  /** Courseware and personal-corpus searches share one per-account retrieval window. */
  public boolean tryAcquireRetrievalRequest(long userId) {
    return tryAcquire(
        retrievalWindowsByUser, userId, 1, MAX_RETRIEVALS_PER_MINUTE, WINDOW_MILLIS);
  }

  /** Bootstrap builds a multi-service snapshot, so cap repeated account refreshes separately. */
  public boolean tryAcquireGameBootstrap(long userId) {
    return tryAcquire(
        gameBootstrapWindowsByUser, userId, 1, MAX_GAME_BOOTSTRAPS_PER_MINUTE, WINDOW_MILLIS);
  }

  /** The learning benchmark builds an evidence graph, so bound repeated per-account runs. */
  public boolean tryAcquireTutorBenchRun(long userId) {
    return tryAcquire(
        tutorBenchWindowsByUser, userId, 1, MAX_TUTOR_BENCH_RUNS_PER_MINUTE, WINDOW_MILLIS);
  }

  /** Bound repeated sandbox-backed judging before code reaches the shared worker pool. */
  public boolean tryAcquireSandboxJudge(long userId) {
    return tryAcquire(
        sandboxJudgeWindowsByUser,
        userId,
        1,
        MAX_SANDBOX_JUDGES_PER_MINUTE,
        WINDOW_MILLIS);
  }

  /** Bound per-account submissions to the costly image and voice composition queue. */
  public boolean tryAcquireMediaComposition(long userId) {
    return tryAcquire(
        mediaCompositionWindowsByUser,
        userId,
        1,
        MAX_MEDIA_COMPOSITIONS_PER_MINUTE,
        WINDOW_MILLIS);
  }

  /** New profile interviews are low-frequency durable records, so limit them per account hourly. */
  public boolean tryAcquireProfileInterview(long userId) {
    return tryAcquire(
        profileInterviewWindowsByUser, userId, 1, MAX_PROFILE_INTERVIEWS_PER_HOUR, HOUR_MILLIS);
  }

  private boolean tryAcquire(
      ConcurrentHashMap<Long, Deque<Long>> windows,
      long userId,
      int writeCount,
      int maxWrites,
      long windowMillis) {
    if (writeCount < 0 || writeCount > maxWrites) {
      return false;
    }
    if (writeCount == 0) {
      return true;
    }
    long now = nowMillis.getAsLong();
    AtomicBoolean acquired = new AtomicBoolean();
    windows.compute(
        userId,
        (ignored, existing) -> {
          Deque<Long> window = existing == null ? new ArrayDeque<>() : existing;
          discardExpired(window, now, windowMillis);
          if (window.size() + writeCount <= maxWrites) {
            for (int i = 0; i < writeCount; i++) {
              window.addLast(now);
            }
            acquired.set(true);
          }
          return window;
        });
    return acquired.get();
  }

  @Scheduled(fixedDelay = WINDOW_MILLIS, initialDelay = WINDOW_MILLIS)
  void cleanupExpiredWindows() {
    long now = nowMillis.getAsLong();
    cleanupExpired(windowsByUser, now, WINDOW_MILLIS);
    cleanupExpired(teacherKcHeatmapWindowsByUser, now, WINDOW_MILLIS);
    cleanupExpired(playerPositionWindowsByUser, now, PLAYER_POSITION_WINDOW_MILLIS);
    cleanupExpired(retrievalWindowsByUser, now, WINDOW_MILLIS);
    cleanupExpired(gameBootstrapWindowsByUser, now, WINDOW_MILLIS);
    cleanupExpired(tutorBenchWindowsByUser, now, WINDOW_MILLIS);
    cleanupExpired(sandboxJudgeWindowsByUser, now, WINDOW_MILLIS);
    cleanupExpired(mediaCompositionWindowsByUser, now, WINDOW_MILLIS);
    cleanupExpired(classroomQuestFanOutWindowsByUser, now, WINDOW_MILLIS);
    cleanupExpired(profileInterviewWindowsByUser, now, HOUR_MILLIS);
  }

  private void cleanupExpired(
      ConcurrentHashMap<Long, Deque<Long>> windows, long now, long windowMillis) {
    windows
        .keySet()
        .forEach(
            userId ->
                windows.computeIfPresent(
                    userId,
                    (ignored, window) -> {
                      discardExpired(window, now, windowMillis);
                      return window.isEmpty() ? null : window;
                    }));
  }

  private void discardExpired(Deque<Long> window, long now, long windowMillis) {
    while (!window.isEmpty() && now - window.peekFirst() >= windowMillis) {
      window.removeFirst();
    }
  }
}
