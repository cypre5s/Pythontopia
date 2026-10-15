package com.pythontopia.classroom.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class JoinCodeAttemptThrottleTest {

  @Test
  void limitsPerAccountAndReleasesAttemptsAfterTenMinutes() {
    AtomicLong nowMillis = new AtomicLong();
    JoinCodeAttemptThrottle throttle = new JoinCodeAttemptThrottle(nowMillis::get);

    for (int attempt = 0; attempt < 30; attempt++) {
      assertThat(throttle.tryAcquire(7L)).isTrue();
    }
    assertThat(throttle.tryAcquire(7L)).isFalse();
    assertThat(throttle.tryAcquire(8L)).isTrue();

    nowMillis.set(Duration.ofMinutes(10).toMillis());
    assertThat(throttle.tryAcquire(7L)).isTrue();
  }
}
