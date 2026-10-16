package com.pythontopia.common.web;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Limits weighted write operations per account. */
@Component
public class StudentWriteThrottle {

  private static final int MAX_WRITES_PER_MINUTE = 100;
  private static final long WINDOW_MILLIS = 60_000L;
  private static final int MAX_PLAYER_POSITION_UPDATES = 10;
  private static final long PLAYER_POSITION_WINDOW_MILLIS = 1_000L;

  // shortcut: counters are process-local for the single backend instance; use a shared store if
  // scaled out.
  private final ConcurrentHashMap<Long, Deque<Long>> windowsByUser = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, Deque<Long>> playerPositionWindowsByUser =
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

  /** Position sync is frequent, so it has a separate short window from durable learning writes. */
  public boolean tryAcquirePlayerPositionUpdate(long userId) {
    return tryAcquire(
        playerPositionWindowsByUser,
        userId,
        1,
        MAX_PLAYER_POSITION_UPDATES,
        PLAYER_POSITION_WINDOW_MILLIS);
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
    cleanupExpired(playerPositionWindowsByUser, now, PLAYER_POSITION_WINDOW_MILLIS);
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
