package com.pythontopia.mainline;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * video-progress 服务端限频（§9：每 stage 每分钟至多 N 次有效计入，默认 2）。
 *
 * <p>内存滑动窗口（60s）按 (userId, stageId) 计数；超限返回 false 让服务层做 no-op（幂等软节流，
 * 不报错以容忍前端批量上报）。单实例假设下足够（演示/单 worker，AGENTS.md）；多实例需改 DB/Redis 限频
 * （已在交付报告标注）。{@code nowMillis} 可注入便于确定性单测。
 */
@Component
public class VideoProgressThrottle {

  private final int writesPerMin;
  private final LongSupplier nowMillis;
  private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();

  @Autowired
  public VideoProgressThrottle(MainlineProperties properties) {
    this(properties.videoWritesPerMin(), System::currentTimeMillis);
  }

  VideoProgressThrottle(int writesPerMin, LongSupplier nowMillis) {
    this.writesPerMin = Math.max(1, writesPerMin);
    this.nowMillis = nowMillis;
  }

  /** @return true 表示本次允许计入；false 表示已达 60s 窗口上限，应 no-op。 */
  public boolean tryAcquire(long userId, long stageId) {
    String key = userId + ":" + stageId;
    Deque<Long> window = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
    long now = nowMillis.getAsLong();
    synchronized (window) {
      while (!window.isEmpty() && now - window.peekFirst() >= 60_000L) {
        window.pollFirst();
      }
      if (window.size() >= writesPerMin) {
        return false;
      }
      window.addLast(now);
      return true;
    }
  }
}
