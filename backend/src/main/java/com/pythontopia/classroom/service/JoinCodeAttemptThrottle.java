package com.pythontopia.classroom.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;

/** Limits classroom join-code lookups per authenticated account. The public Nginx edge also limits by IP. */
@Component
public class JoinCodeAttemptThrottle {

  private static final int MAX_ATTEMPTS = 30;
  private static final long WINDOW_MILLIS = 10 * 60 * 1000L;
  private static final int CLEANUP_EVERY_REQUESTS = 256;

  private final ConcurrentHashMap<Long, Deque<Long>> attemptsByUser = new ConcurrentHashMap<>();
  private final AtomicInteger requestCount = new AtomicInteger();
  private final LongSupplier nowMillis;

  public JoinCodeAttemptThrottle() {
    this(System::currentTimeMillis);
  }

  JoinCodeAttemptThrottle(LongSupplier nowMillis) {
    this.nowMillis = nowMillis;
  }

  public boolean tryAcquire(long userId) {
    long now = nowMillis.getAsLong();
    AtomicBoolean allowed = new AtomicBoolean();
    attemptsByUser.compute(
        userId,
        (ignored, attempts) -> {
          Deque<Long> window = attempts == null ? new ArrayDeque<>() : attempts;
          discardExpired(window, now);
          allowed.set(window.size() < MAX_ATTEMPTS);
          if (allowed.get()) {
            window.addLast(now);
          }
          return window;
        });
    if (requestCount.incrementAndGet() % CLEANUP_EVERY_REQUESTS == 0) {
      // shortcut: sweep inactive accounts every 256 requests; tighten only if this map becomes material.
      attemptsByUser.keySet().forEach(this::removeExpiredWindow);
    }
    return allowed.get();
  }

  private void removeExpiredWindow(Long userId) {
    long now = nowMillis.getAsLong();
    attemptsByUser.computeIfPresent(
        userId,
        (ignored, attempts) -> {
          discardExpired(attempts, now);
          return attempts.isEmpty() ? null : attempts;
        });
  }

  private void discardExpired(Deque<Long> window, long now) {
    while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MILLIS) {
      window.removeFirst();
    }
  }
}
