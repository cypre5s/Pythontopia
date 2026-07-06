package com.pythontopia.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 媒体异步合成作业（Track B）的并发护栏配置。
 *
 * <ul>
 *   <li>{@code poolSize}：合成 worker 线程池核心/最大线程数（同时在合成的作业上限）。
 *   <li>{@code ttiConcurrency} / {@code ttsConcurrency}：讯飞「文生图 / 语音合成」全局并发路数信号量，
 *       受讯飞控制台路数限制，避免突发提交击穿上游配额。
 *   <li>{@code queueCapacity}：超过线程数的作业排队上限。
 *   <li>{@code streamTimeoutMs}：单个 SSE 进度流的最长保持时间。
 * </ul>
 *
 * 默认值（{@code 2-3} 区间内）：poolSize=3、ttiConcurrency=2、ttsConcurrency=2。
 */
@ConfigurationProperties(prefix = "pythontopia.media.compose")
public record MediaComposeProperties(
    int poolSize,
    int ttsConcurrency,
    int ttiConcurrency,
    int queueCapacity,
    long streamTimeoutMs) {

  public MediaComposeProperties {
    if (poolSize <= 0) {
      poolSize = 3;
    }
    if (ttsConcurrency <= 0) {
      ttsConcurrency = 2;
    }
    if (ttiConcurrency <= 0) {
      ttiConcurrency = 2;
    }
    if (queueCapacity <= 0) {
      queueCapacity = 64;
    }
    if (streamTimeoutMs <= 0) {
      streamTimeoutMs = 300_000L;
    }
  }
}
