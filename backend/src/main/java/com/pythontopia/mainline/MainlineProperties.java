package com.pythontopia.mainline;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 主线域可调参数（前缀 pythontopia.mainline，均带默认值，无需 yml 即可运行）。
 *
 * <ul>
 *   <li>pregen-concurrency（2，§11.2）：章测预生成信号量并发上限。
 *   <li>video-delta-max-sec（120，§9）：单次 video-progress 计入上限。
 *   <li>video-writes-per-min（2，§9）：每 stage 每分钟有效次数上限。
 *   <li>test-generate-max-retries（2，§4.1）：graph 生成失败重试上限，超限走 golden。
 * </ul>
 */
@ConfigurationProperties(prefix = "pythontopia.mainline")
public record MainlineProperties(
    Integer pregenConcurrency,
    Integer videoDeltaMaxSec,
    Integer videoWritesPerMin,
    Integer testGenerateMaxRetries) {

  public MainlineProperties {
    if (pregenConcurrency == null || pregenConcurrency < 1) {
      pregenConcurrency = 2;
    }
    if (videoDeltaMaxSec == null || videoDeltaMaxSec < 1) {
      videoDeltaMaxSec = 120;
    }
    if (videoWritesPerMin == null || videoWritesPerMin < 1) {
      videoWritesPerMin = 2;
    }
    if (testGenerateMaxRetries == null || testGenerateMaxRetries < 0) {
      testGenerateMaxRetries = 2;
    }
  }
}
