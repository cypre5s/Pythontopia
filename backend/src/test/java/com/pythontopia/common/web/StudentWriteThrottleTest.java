package com.pythontopia.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class StudentWriteThrottleTest {

  @Test
  void sharesThePerAccountWriteWindowAndExpiresEntriesAfterOneMinute() {
    AtomicLong nowMillis = new AtomicLong();
    StudentWriteThrottle throttle = new StudentWriteThrottle(nowMillis::get);

    assertThat(throttle.tryAcquire(7L, 99)).isTrue();
    assertThat(throttle.tryAcquire(7L, 1)).isTrue();
    assertThat(throttle.tryAcquire(7L, 1)).isFalse();
    assertThat(throttle.tryAcquire(8L, 100)).isTrue();

    nowMillis.set(Duration.ofMinutes(1).toMillis());
    assertThat(throttle.tryAcquire(7L, 100)).isTrue();
  }

  @Test
  void oversizedRequestDoesNotConsumeAccountCapacity() {
    StudentWriteThrottle throttle = new StudentWriteThrottle(() -> 0L);

    assertThat(throttle.tryAcquire(7L, 101)).isFalse();
    assertThat(throttle.tryAcquire(7L, 100)).isTrue();
  }
}
