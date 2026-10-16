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

  @Test
  void classroomQuestFanOutUsesASeparateWeightedWindow() {
    AtomicLong nowMillis = new AtomicLong();
    StudentWriteThrottle throttle = new StudentWriteThrottle(nowMillis::get);

    assertThat(throttle.tryAcquireClassroomQuestFanOut(7L, 1000)).isTrue();
    assertThat(throttle.tryAcquireClassroomQuestFanOut(7L, 1)).isFalse();
    assertThat(throttle.tryAcquireClassroomQuestFanOut(8L, 1000)).isTrue();

    nowMillis.set(Duration.ofMinutes(1).toMillis());
    assertThat(throttle.tryAcquireClassroomQuestFanOut(7L, 1001)).isFalse();
    assertThat(throttle.tryAcquireClassroomQuestFanOut(7L, 1000)).isTrue();
  }

  @Test
  void playerPositionWritesUseTheirOwnShortWindow() {
    AtomicLong nowMillis = new AtomicLong();
    StudentWriteThrottle throttle = new StudentWriteThrottle(nowMillis::get);

    for (int update = 0; update < 10; update++) {
      assertThat(throttle.tryAcquirePlayerPositionUpdate(7L)).isTrue();
    }
    assertThat(throttle.tryAcquirePlayerPositionUpdate(7L)).isFalse();
    assertThat(throttle.tryAcquirePlayerPositionUpdate(8L)).isTrue();
    assertThat(throttle.tryAcquire(7L, 100)).isTrue();

    nowMillis.set(Duration.ofSeconds(1).toMillis());
    assertThat(throttle.tryAcquirePlayerPositionUpdate(7L)).isTrue();
  }

  @Test
  void retrievalUsesItsOwnPerAccountWindow() {
    AtomicLong nowMillis = new AtomicLong();
    StudentWriteThrottle throttle = new StudentWriteThrottle(nowMillis::get);

    for (int request = 0; request < 30; request++) {
      assertThat(throttle.tryAcquireRetrievalRequest(7L)).isTrue();
    }
    assertThat(throttle.tryAcquireRetrievalRequest(7L)).isFalse();
    assertThat(throttle.tryAcquire(7L, 100)).isTrue();
    assertThat(throttle.tryAcquireRetrievalRequest(8L)).isTrue();

    nowMillis.set(Duration.ofMinutes(1).toMillis());
    assertThat(throttle.tryAcquireRetrievalRequest(7L)).isTrue();
  }

  @Test
  void gameBootstrapUsesASeparateTenPerMinuteAccountWindow() {
    AtomicLong nowMillis = new AtomicLong();
    StudentWriteThrottle throttle = new StudentWriteThrottle(nowMillis::get);

    for (int request = 0; request < 10; request++) {
      assertThat(throttle.tryAcquireGameBootstrap(7L)).isTrue();
    }
    assertThat(throttle.tryAcquireGameBootstrap(7L)).isFalse();
    assertThat(throttle.tryAcquireGameBootstrap(8L)).isTrue();

    nowMillis.set(Duration.ofMinutes(1).toMillis());
    assertThat(throttle.tryAcquireGameBootstrap(7L)).isTrue();
  }

  @Test
  void sandboxJudgingUsesASeparateSixPerMinuteAccountWindow() {
    AtomicLong nowMillis = new AtomicLong();
    StudentWriteThrottle throttle = new StudentWriteThrottle(nowMillis::get);

    for (int request = 0; request < 6; request++) {
      assertThat(throttle.tryAcquireSandboxJudge(7L)).isTrue();
    }
    assertThat(throttle.tryAcquireSandboxJudge(7L)).isFalse();
    assertThat(throttle.tryAcquireSandboxJudge(8L)).isTrue();
    assertThat(throttle.tryAcquire(7L, 100)).isTrue();

    nowMillis.set(Duration.ofMinutes(1).toMillis());
    assertThat(throttle.tryAcquireSandboxJudge(7L)).isTrue();
  }

  @Test
  void profileInterviewCreationUsesASeparateThreePerHourAccountWindow() {
    AtomicLong nowMillis = new AtomicLong();
    StudentWriteThrottle throttle = new StudentWriteThrottle(nowMillis::get);

    for (int request = 0; request < 3; request++) {
      assertThat(throttle.tryAcquireProfileInterview(7L)).isTrue();
    }
    assertThat(throttle.tryAcquireProfileInterview(7L)).isFalse();
    assertThat(throttle.tryAcquireProfileInterview(8L)).isTrue();
    assertThat(throttle.tryAcquire(7L, 100)).isTrue();

    nowMillis.set(Duration.ofHours(1).toMillis());
    assertThat(throttle.tryAcquireProfileInterview(7L)).isTrue();
  }

  @Test
  void mediaCompositionUsesASeparateThreePerMinuteAccountWindow() {
    AtomicLong nowMillis = new AtomicLong();
    StudentWriteThrottle throttle = new StudentWriteThrottle(nowMillis::get);

    for (int request = 0; request < 3; request++) {
      assertThat(throttle.tryAcquireMediaComposition(7L)).isTrue();
    }
    assertThat(throttle.tryAcquireMediaComposition(7L)).isFalse();
    assertThat(throttle.tryAcquireMediaComposition(8L)).isTrue();
    assertThat(throttle.tryAcquire(7L, 100)).isTrue();

    nowMillis.set(Duration.ofMinutes(1).toMillis());
    assertThat(throttle.tryAcquireMediaComposition(7L)).isTrue();
  }
}
