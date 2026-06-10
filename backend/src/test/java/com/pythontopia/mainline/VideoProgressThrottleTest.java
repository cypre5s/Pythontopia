package com.pythontopia.mainline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** video-progress 限频：每 stage 每分钟至多 N 次（注入虚拟时钟，确定性）。 */
class VideoProgressThrottleTest {

  @Test
  void allowsUpToLimitThenThrottlesWithinWindow() {
    AtomicLong clock = new AtomicLong(0);
    VideoProgressThrottle throttle = new VideoProgressThrottle(2, clock::get);

    assertThat(throttle.tryAcquire(7L, 10L)).isTrue(); // 1
    assertThat(throttle.tryAcquire(7L, 10L)).isTrue(); // 2
    assertThat(throttle.tryAcquire(7L, 10L)).isFalse(); // 3 超限
  }

  @Test
  void windowSlidesAfter60Seconds() {
    AtomicLong clock = new AtomicLong(0);
    VideoProgressThrottle throttle = new VideoProgressThrottle(2, clock::get);

    assertThat(throttle.tryAcquire(7L, 10L)).isTrue();
    assertThat(throttle.tryAcquire(7L, 10L)).isTrue();
    assertThat(throttle.tryAcquire(7L, 10L)).isFalse();

    clock.set(60_000L); // 满 60s，旧记录滑出窗口
    assertThat(throttle.tryAcquire(7L, 10L)).isTrue();
  }

  @Test
  void perStageAndPerUserIsolation() {
    AtomicLong clock = new AtomicLong(0);
    VideoProgressThrottle throttle = new VideoProgressThrottle(1, clock::get);

    assertThat(throttle.tryAcquire(7L, 10L)).isTrue();
    assertThat(throttle.tryAcquire(7L, 10L)).isFalse(); // 同 user 同 stage 超限
    assertThat(throttle.tryAcquire(7L, 11L)).isTrue(); // 不同 stage 独立
    assertThat(throttle.tryAcquire(8L, 10L)).isTrue(); // 不同 user 独立
  }
}
