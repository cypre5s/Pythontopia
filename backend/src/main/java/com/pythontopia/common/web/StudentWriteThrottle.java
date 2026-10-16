package com.pythontopia.common.web;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Limits weighted student writes per account. */
@Component
public class StudentWriteThrottle {

  private static final int MAX_WRITES_PER_MINUTE = 100;
  private static final long WINDOW_MILLIS = 60_000L;

  // shortcut: counters are process-local for the single backend instance; use a shared store if
  // scaled out.
  private final ConcurrentHashMap<Long, Deque<Long>> windowsByUser = new ConcurrentHashMap<>();
  private final LongSupplier nowMillis;

  public StudentWriteThrottle() {
    this(System::currentTimeMillis);
  }

  StudentWriteThrottle(LongSupplier nowMillis) {
    this.nowMillis = nowMillis;
  }

  /** Reserve all writes in one request atomically; an oversized batch consumes no allowance. */
  public boolean tryAcquire(long userId, int writeCount) {
    if (writeCount < 0 || writeCount > MAX_WRITES_PER_MINUTE) {
      return false;
    }
    if (writeCount == 0) {
      return true;
    }
    long now = nowMillis.getAsLong();
    AtomicBoolean acquired = new AtomicBoolean();
    windowsByUser.compute(
        userId,
        (ignored, existing) -> {
          Deque<Long> window = existing == null ? new ArrayDeque<>() : existing;
          discardExpired(window, now);
          if (window.size() + writeCount <= MAX_WRITES_PER_MINUTE) {
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
    windowsByUser
        .keySet()
        .forEach(
            userId ->
                windowsByUser.computeIfPresent(
                    userId,
                    (ignored, window) -> {
                      discardExpired(window, now);
                      return window.isEmpty() ? null : window;
                    }));
  }

  private void discardExpired(Deque<Long> window, long now) {
    while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MILLIS) {
      window.removeFirst();
    }
  }
}
